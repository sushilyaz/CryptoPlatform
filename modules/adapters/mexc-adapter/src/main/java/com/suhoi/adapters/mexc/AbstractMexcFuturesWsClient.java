package com.suhoi.adapters.mexc;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.StreamClient;
import com.suhoi.api.adapter.StreamSubscription;
import com.suhoi.api.adapter.TickHandler;
import com.suhoi.events.Tick;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.suhoi.adapters.mexc.MexcJson.MAPPER;
import static com.suhoi.adapters.mexc.MexcSymbols.extractPerpBaseOrThrow;

/**
 * PERP (USDT-M) WS клиент «ticker» для MEXC Futures.
 *
 * Базовый эндпойнт: wss://contract.mexc.com/edge
 * Подписка на каждый символ отдельным сообщением:
 *   {"method":"sub.ticker","param":{"symbol":"BTC_USDT"}}
 * Ответ событие: channel: "push.ticker", data: { bid1, ask1, ... }, symbol: "BTC_USDT", ts: <ms>
 *
 * Пинг: {"method":"ping"} — сервер отвечает {"channel":"pong", "data":<ts>}
 */
abstract class AbstractMexcFuturesWsClient implements StreamClient {
    private static final String VENUE = "MEXC";
    private static final String KIND = "PERP";

    private final String wsBase;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "mexc-perp-ws");
        t.setDaemon(true); return t;
    });

    private volatile StreamSubscription current;

    protected AbstractMexcFuturesWsClient(String wsBase) {
        this.wsBase = Objects.requireNonNull(wsBase);
    }

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        String url = wsBase;
        var listener = new WebSocket.Listener() {
            @Override public void onOpen(WebSocket ws) {
                // Подписываемся на каждый контракт
                for (String s : nativeSymbols) {
                    String sym = s.toUpperCase(Locale.ROOT); // "BTC_USDT"
                    String sub = "{\"method\":\"sub.ticker\",\"param\":{\"symbol\":\"" + sym + "\"}}";
                    ws.sendText(sub, true);
                }
                ws.request(1);
            }

            @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                try {
                    JsonNode node = MAPPER.readTree(data.toString());
                    String channel = node.path("channel").asText("");
                    if ("push.ticker".equals(channel)) {
                        String symbol = node.path("symbol").asText(node.path("data").path("symbol").asText(""));
                        JsonNode d = node.get("data");
                        if (d != null) {
                            String bStr = d.path("bid1").asText(null);
                            String aStr = d.path("ask1").asText(null);
                            if (bStr != null && aStr != null && !symbol.isEmpty()) {
                                BigDecimal bid = new BigDecimal(bStr);
                                BigDecimal ask = new BigDecimal(aStr);
                                BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2));

                                long tsMillis = node.path("ts").asLong(System.currentTimeMillis());
                                if (tsMillis == 0L) tsMillis = d.path("timestamp").asLong(System.currentTimeMillis());
                                Instant ts = Instant.ofEpochMilli(tsMillis);

                                var tick = new Tick(
                                        ts,
                                        extractPerpBaseOrThrow(symbol),
                                        VENUE,
                                        KIND,
                                        bid, ask, mid,
                                        null,
                                        ts,
                                        null,
                                        symbol
                                );
                                handler.onTick(tick);
                            }
                        }
                    }
                } catch (Exception ignore) { }
                ws.request(1);
                return null;
            }

            @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                ws.request(1); return null;
            }
            @Override public CompletionStage<?> onPing(WebSocket ws, ByteBuffer msg) {
                ws.sendPong(msg); ws.request(1); return null;
            }
            @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) { return null; }
            @Override public void onError(WebSocket ws, Throwable error) { /* при необходимости добавим reconnect */ }
        };

        var ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), listener)
                .orTimeout(15, TimeUnit.SECONDS)
                .join();

        // периодический ping
        var pingTask = scheduler.scheduleAtFixedRate(() -> {
            try { ws.sendText("{\"method\":\"ping\"}", true); } catch (Exception ignore) {}
        }, 15, 15, TimeUnit.SECONDS);

        var sub = new WsSubscriptionWithPing(ws, pingTask);
        current = sub;
        return sub;
    }

    @Override public void close() {
        try {
            if (current != null) current.close();
        } finally {
            scheduler.shutdownNow();
        }
    }
}
