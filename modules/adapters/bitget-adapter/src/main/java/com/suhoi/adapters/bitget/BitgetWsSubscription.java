package com.suhoi.adapters.bitget;

import com.suhoi.api.adapter.StreamSubscription;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Управляет жизненным циклом одного WS-подключения. */
final class BitgetWsSubscription implements StreamSubscription {
    private final WebSocket ws;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    BitgetWsSubscription(WebSocket ws) { this.ws = ws; }
    @Override public void close() {
        if (closed.compareAndSet(false, true) && ws != null) {
            try { ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                    .orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(ex -> null); }
            catch (Exception ignore) {}
        }
    }
}

