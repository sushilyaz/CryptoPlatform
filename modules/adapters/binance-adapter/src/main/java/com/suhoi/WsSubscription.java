package com.suhoi;

import com.suhoi.adapter.StreamSubscription;

import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Управляет жизненным циклом одного WS-подключения (комбинированный поток). */
final class WsSubscription implements StreamSubscription {
    private final WebSocket ws;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    WsSubscription(WebSocket ws) {
        this.ws = ws;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ws != null) {
            try {
                CompletableFuture<WebSocket> cf = ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
                cf.orTimeout(2, java.util.concurrent.TimeUnit.SECONDS).exceptionally(ex -> null);
            } catch (Exception ignore) { }
        }
    }
}

