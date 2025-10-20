package com.suhoi.apiservice.config;

import com.suhoi.bus.EventBus;
import com.suhoi.bus.nats.NatsEventBus;
import com.suhoi.bus.nats.NatsEventBusConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Конфигурация EventBus (NATS) для api-service.
 * <p>Создаёт singleton-бин {@link EventBus}. Закрывается автоматически при остановке приложения.</p>
 */
@Configuration
public class NatsConfig {

    @Bean(destroyMethod = "close")
    public EventBus eventBus(@Value("${nats.url}") String url,
                             @Value("${nats.connectTimeoutMs:3000}") int connectTimeoutMs,
                             @Value("${nats.reconnectWaitMs:500}") int reconnectWaitMs,
                             @Value("${nats.maxReconnects:-1}") int maxReconnects,
                             @Value("${nats.pingIntervalSec:10}") int pingIntervalSec,
                             @Value("${nats.flushOnClose:true}") boolean flushOnClose) {
        return new NatsEventBus(
                NatsEventBusConfig.builder()
                        .server(url)
                        .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .reconnectWait(Duration.ofMillis(reconnectWaitMs))
                        .maxReconnects(maxReconnects)
                        .pingInterval(Duration.ofSeconds(pingIntervalSec))
                        .flushOnClose(flushOnClose)
                        .build()
        );
    }
}
