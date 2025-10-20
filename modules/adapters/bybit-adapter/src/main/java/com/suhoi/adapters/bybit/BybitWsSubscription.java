package com.suhoi.adapters.bybit;

import com.suhoi.api.adapter.StreamSubscription;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Жизненный цикл одного WS-подключения. */
final class BybitWsSubscription implements StreamSubscription {
    private final WebSocket ws;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    BybitWsSubscription(WebSocket ws) { this.ws = ws; }
    @Override public void close() {
        if (closed.compareAndSet(false, true) && ws != null) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                    .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(ex -> null);
            } catch (Exception ignore) {}
        }
    }
}

