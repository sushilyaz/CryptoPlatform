package com.suhoi.apiservice.echo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhoi.bus.EventBus;
import com.suhoi.bus.Subscription;
import com.suhoi.events.Subjects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Подписчик на subject {@code control.echo}.
 * <p>Нужен для smoke-проверки: мы же и публикуем, и читаем собственные сообщения.</p>
 */
@Component
public class EchoSubscriber {
    private static final Logger log = LoggerFactory.getLogger(EchoSubscriber.class);

    public static final String SUBJECT = Subjects.CONTROL_ECHO;

    private final EventBus bus;
    private final ObjectMapper om;
    private final String appName;

    private volatile Subscription sub;

    public EchoSubscriber(EventBus bus,
                          ObjectMapper objectMapper,
                          @Value("${spring.application.name:api-service}") String appName) {
        this.bus = bus;
        this.om = objectMapper;
        this.appName = appName;
    }

    /**
     * Регистрирует подписку при старте приложения.
     */
    @PostConstruct
    public void start() {
        this.sub = bus.subscribe(SUBJECT, (subject, payload) -> {
            try {
                // Пытаемся распарсить как EchoMessage, иначе логируем raw
                EchoMessage msg = om.readValue(payload, EchoMessage.class);
                log.info("ECHO RX [{}] from={} msg='{}' ts={}", msg.getId(), msg.getSender(), msg.getMessage(), msg.getTs());
            } catch (Exception parseErr) {
                log.info("ECHO RX raw: {}", new String(payload, StandardCharsets.UTF_8));
            }
        });

        // Для наглядности: отправим приветственное сообщение при запуске
        try {
            EchoMessage hello = new EchoMessage(UUID.randomUUID().toString(), 0L, appName, "echo-listener is up");
            bus.publish(SUBJECT, om.writeValueAsBytes(hello));
        } catch (Exception e) {
            log.warn("Failed to publish hello echo: {}", e.toString());
        }

        log.info("EchoSubscriber is subscribed to subject '{}'", SUBJECT);
    }

    /**
     * Закрывает подписку при остановке приложения.
     */
    @PreDestroy
    public void stop() {
        if (sub != null) {
            try { sub.close(); } catch (Exception ignore) {}
        }
    }
}

