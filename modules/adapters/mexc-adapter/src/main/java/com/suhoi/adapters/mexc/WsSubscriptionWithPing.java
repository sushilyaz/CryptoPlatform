package com.suhoi.adapters.mexc;

import com.suhoi.api.adapter.StreamSubscription;

import java.net.http.WebSocket;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Управление WS-сессией + периодический ping (если задан). */
final class WsSubscriptionWithPing implements StreamSubscription {
    private final WebSocket ws;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ScheduledFuture<?> pingTask;

    WsSubscriptionWithPing(WebSocket ws, ScheduledFuture<?> pingTask) {
        this.ws = ws;
        this.pingTask = pingTask;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            try {
                if (pingTask != null) pingTask.cancel(true);
            } catch (Exception ignored) {}
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                        .orTimeout(2, TimeUnit.SECONDS)
                        .exceptionally(ex -> null);
            } catch (Exception ignored) {}
        }
    }
}

