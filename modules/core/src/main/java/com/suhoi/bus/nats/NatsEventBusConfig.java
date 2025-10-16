package com.suhoi.bus.nats;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Иммутабельный конфиг для {@link NatsEventBus}.
 * Позволяет настраивать таймауты, интервалы ping, стратегию reconnect и список серверов.
 */
public final class NatsEventBusConfig {
    private final List<String> servers;
    private final Duration connectTimeout;
    private final Duration reconnectWait;
    private final int maxReconnects;
    private final Duration pingInterval;
    private final boolean flushOnClose;

    private NatsEventBusConfig(Builder b) {
        this.servers = Collections.unmodifiableList(new ArrayList<>(b.servers));
        this.connectTimeout = b.connectTimeout;
        this.reconnectWait = b.reconnectWait;
        this.maxReconnects = b.maxReconnects;
        this.pingInterval = b.pingInterval;
        this.flushOnClose = b.flushOnClose;
    }

    public List<String> servers() { return servers; }
    public Duration connectTimeout() { return connectTimeout; }
    public Duration reconnectWait() { return reconnectWait; }
    public int maxReconnects() { return maxReconnects; }
    public Duration pingInterval() { return pingInterval; }
    public boolean flushOnClose() { return flushOnClose; }

    public static Builder builder() { return new Builder(); }

    /**
     * Builder с безопасными дефолтами для разработки.
     */
    public static final class Builder {
        private final List<String> servers = new ArrayList<>();
        private Duration connectTimeout = Duration.ofSeconds(3);
        private Duration reconnectWait = Duration.ofMillis(500);
        private int maxReconnects = -1; // -1 = бесконечные попытки
        private Duration pingInterval = Duration.ofSeconds(10);
        private boolean flushOnClose = true;

        public Builder server(String url) { this.servers.add(url); return this; }
        public Builder servers(List<String> urls) { this.servers.addAll(urls); return this; }
        public Builder connectTimeout(Duration d) { this.connectTimeout = d; return this; }
        public Builder reconnectWait(Duration d) { this.reconnectWait = d; return this; }
        public Builder maxReconnects(int n) { this.maxReconnects = n; return this; }
        public Builder pingInterval(Duration d) { this.pingInterval = d; return this; }
        public Builder flushOnClose(boolean v) { this.flushOnClose = v; return this; }

        public NatsEventBusConfig build() {
            if (servers.isEmpty()) {
                servers.add("nats://127.0.0.1:4222");
            }
            return new NatsEventBusConfig(this);
        }
    }
}
