package com.suhoi.bus.nats;

import com.suhoi.bus.EventBus;
import com.suhoi.bus.MessageHandler;
import com.suhoi.bus.Subscription;
import io.nats.client.Connection;
import io.nats.client.Consumer;
import io.nats.client.Dispatcher;
import io.nats.client.ErrorListener;
import io.nats.client.Nats;
import io.nats.client.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;

/**
 * EventBus-наслойка над NATS (core NATS, без JetStream).
 * <p>
 * Ключевые свойства:
 * <ul>
 *   <li>Автоматическое переподключение (maxReconnects, reconnectWait)</li>
 *   <li>Асинхронные подписки через {@link Dispatcher}</li>
 *   <li>Чистое завершение: flush(+drain) перед закрытием соединения</li>
 * </ul>
 *
 * <b>Семантика доставki:</b> Core NATS — это at-most-once best effort. Если consumer оффлайн —
 * опубликованные сообщения не буферятся брокером (для durability нужен JetStream).
 * Для live-трансляции тикеров это ок: потерянные тики компенсирует следующий.
 */
public final class NatsEventBus implements EventBus {
    private static final Logger log = LoggerFactory.getLogger(NatsEventBus.class);

    private final NatsEventBusConfig cfg;
    private final Connection nc;

    /**
     * Создаёт и открывает соединение с NATS по заданной конфигурации.
     *
     * @param cfg параметры подключения и поведения.
     * @throws RuntimeException если соединение не удалось установить.
     */
    public NatsEventBus(NatsEventBusConfig cfg) {
        this.cfg = Objects.requireNonNull(cfg, "config");
        try {
            Options.Builder ob = new Options.Builder()
                    .connectionTimeout(cfg.connectTimeout())
                    .pingInterval(cfg.pingInterval())
                    .reconnectWait(cfg.reconnectWait())
                    .maxReconnects(cfg.maxReconnects());

            for (String s : cfg.servers()) {
                ob.server(s);
            }

            ob.errorListener(new ErrorListener() {
                @Override public void errorOccurred(Connection conn, String error) {
                    log.warn("NATS error: {}", error);
                }
                @Override public void exceptionOccurred(Connection conn, Exception exp) {
                    log.warn("NATS exception", exp);
                }
                @Override public void slowConsumerDetected(Connection conn, Consumer consumer) {
                    log.warn("NATS slow consumer detected: {}", consumer);
                }
            });

            ob.connectionListener((conn, type) -> {
                switch (type) {
                    case CONNECTED -> log.info("NATS connected: {}", conn.getConnectedUrl());
                    case RECONNECTED -> log.info("NATS reconnected: {}", conn.getConnectedUrl());
                    case DISCONNECTED -> log.warn("NATS disconnected");
                    case CLOSED -> log.info("NATS closed");
                    default -> log.debug("NATS connection event: {}", type);
                }
            });

            this.nc = Nats.connect(ob.build());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Failed to connect to NATS", e);
        }
    }

    /**
     * Публикует байтовую нагрузку в заданный subject.
     * Ошибки транслируются как RuntimeException.
     */
    @Override
    public void publish(String subject, byte[] payload) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(payload, "payload");
        try {
            nc.publish(subject, payload);
        } catch (Exception e) {
            throw new RuntimeException("NATS publish failed for subject " + subject, e);
        }
    }

    /**
     * Создаёт асинхронную подписку на subject с обработчиком сообщений.
     * Подписка изолирована собственным Dispatcher для чистого закрытия.
     */
    @Override
    public Subscription subscribe(String subject, MessageHandler handler) {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(handler, "handler");

        // Отдельный dispatcher под одного handler — проще управлять жизненным циклом
        Dispatcher dispatcher = nc.createDispatcher(msg -> {});
        dispatcher.subscribe(subject, msg -> {
            try {
                handler.onMessage(msg.getSubject(), msg.getData());
            } catch (Throwable t) {
                log.warn("Handler error on subject {}: {}", subject, t.toString(), t);
            }
        });

        return new NatsSubscription(nc, dispatcher, subject);
    }

    /**
     * Корректно завершаем соединение:
     * <ol>
     *     <li>Опциональный flush — дождаться отсылки публикаций</li>
     *     <li>Опциональный drain — попросить NATS «докрутить» обработку</li>
     *     <li>Закрыть соединение</li>
     * </ol>
     */
    @Override
    public void close() {
        try {
            if (cfg.flushOnClose()) {
                try {
                    nc.flush(Duration.ofSeconds(2));
                } catch (Exception e) {
                    log.debug("Flush on close failed: {}", e.toString());
                }
                try {
                    nc.drain(Duration.ofSeconds(3));
                } catch (Exception e) {
                    log.debug("Drain on close failed: {}", e.toString());
                }
            }
            nc.close();
        } catch (Exception e) {
            log.warn("NATS close error: {}", e.toString());
        }
    }

    /**
     * Хэндл подписки, знающий свой dispatcher и subject.
     */
    private static final class NatsSubscription implements Subscription {
        private final Connection nc;
        private final Dispatcher dispatcher;
        private final String subject;
        private volatile boolean closed = false;

        NatsSubscription(Connection nc, Dispatcher dispatcher, String subject) {
            this.nc = nc;
            this.dispatcher = dispatcher;
            this.subject = subject;
        }

        /**
         * Отписывает subject и мягко дренит dispatcher.
         * Соединение не закрывает.
         */
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            try {
                dispatcher.unsubscribe(subject);
            } catch (Exception ignore) {}
            try {
                // Дать времени обработать уже принятые сообщения
                dispatcher.drain(Duration.ofSeconds(2));
            } catch (Exception ignore) {}
            try {
                // Полностью закрыть диспетчер (освободить ресурсы).
                // В jnats это делает Connection, явного stop() у Dispatcher нет.
                nc.closeDispatcher(dispatcher);
            } catch (Exception ignore) {}
        }
    }
}
