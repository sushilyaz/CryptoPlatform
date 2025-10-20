package com.suhoi.adapters.bybit;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.StreamClient;
import com.suhoi.api.adapter.StreamSubscription;
import com.suhoi.api.adapter.TickHandler;
import com.suhoi.events.Tick;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static com.suhoi.adapters.bybit.BybitJson.MAPPER;
import static com.suhoi.adapters.bybit.BybitSymbols.extractBaseOrThrow;

/**
 * Bybit v5 SPOT: orderbook Level-1 как источник best bid/ask.
 * WS URL: wss://stream.bybit.com/v5/public/spot
 * Подписка: {"op":"subscribe","args":["orderbook.1.BTCUSDT", ...]}
 *
 * Документация (Orderbook topic, Spot, L1): topic = orderbook.{depth}.{symbol}, snapshot-only для L1.
 */
public final class BybitSpotOrderbookL1StreamClient implements StreamClient {
    private static final String VENUE = "BYBIT";
    private static final String KIND  = "SPOT";
    private static final String WS_URL = "wss://stream.bybit.com/v5/public/spot";
    private static final int MAX_ARGS = 10; // консервативно

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "bybit-spot-pinger"); t.setDaemon(true); return t;
    });
    private final Set<StreamSubscription> live = ConcurrentHashMap.newKeySet();

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        var composite = new CompositeSub();
        for (var chunk : chunk(nativeSymbols, MAX_ARGS)) {
            composite.add(openOne(WS_URL, handler, chunk));
        }
        live.add(composite);
        return composite;
    }

    @Override public void close() {
        live.forEach(StreamSubscription::close);
        live.clear();
        scheduler.shutdownNow();
    }

    private StreamSubscription openOne(String url, TickHandler handler, Collection<String> syms) {
        var listener = new WebSocket.Listener() {
            private ScheduledFuture<?> pingTask;

            @Override public void onOpen(WebSocket ws) {
                sendSubscribe(ws, syms);
                pingTask = scheduler.scheduleAtFixedRate(() -> safePing(ws), 20, 20, TimeUnit.SECONDS);
                ws.request(1);
            }
            @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                try {
                    JsonNode n = MAPPER.readTree(data.toString());
                    if ("pong".equalsIgnoreCase(n.path("op").asText())) { ws.request(1); return null; }

                    String topic = n.path("topic").asText("");
                    if (!topic.startsWith("orderbook.1.")) { ws.request(1); return null; }

                    JsonNode d = n.path("data");
                    if (!d.isObject()) { ws.request(1); return null; }

                    // b / a — массивы [ [price, size], ... ]
                    JsonNode bids = d.path("b");
                    JsonNode asks = d.path("a");
                    if (!bids.isArray() || bids.size() == 0 || !asks.isArray() || asks.size() == 0) {
                        ws.request(1); return null;
                    }
                    String bStr = optArrayPrice(bids.get(0));
                    String aStr = optArrayPrice(asks.get(0));
                    if (bStr == null || aStr == null) { ws.request(1); return null; }

                    BigDecimal bid = new BigDecimal(bStr);
                    BigDecimal ask = new BigDecimal(aStr);
                    BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2));

                    String symbolUpper = d.path("s").asText("").toUpperCase(Locale.ROOT);
                    if (!symbolUpper.endsWith("USDT")) { ws.request(1); return null; }

                    long tsMs = n.path("ts").asLong(System.currentTimeMillis());
                    Instant ts = Instant.ofEpochMilli(tsMs);

                    handler.onTick(new Tick(
                            ts,
                            extractBaseOrThrow(symbolUpper),
                            VENUE,
                            KIND,
                            bid, ask, mid,
                            null,           // depthUsd50 отсутствует в этом канале
                            ts,
                            null,
                            symbolUpper
                    ));
                } catch (Exception ignore) { /*skip*/ }
                ws.request(1);
                return null;
            }
            @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer bb, boolean last) { ws.request(1); return null; }
            @Override public CompletionStage<?> onPing(WebSocket ws, ByteBuffer msg) { ws.sendPong(msg); ws.request(1); return null; }
            @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                if (pingTask != null) pingTask.cancel(true);
                scheduleReconnect(url, handler, syms);
                return null;
            }
            @Override public void onError(WebSocket ws, Throwable error) {
                if (pingTask != null) pingTask.cancel(true);
                scheduleReconnect(url, handler, syms);
            }
        };

        var ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), listener)
                .orTimeout(15, TimeUnit.SECONDS)
                .join();

        return new BybitWsSubscription(ws);
    }

    private void sendSubscribe(WebSocket ws, Collection<String> syms) {
        var args = new ArrayList<String>(syms.size());
        for (var s : syms) args.add("orderbook.1." + s.toUpperCase(Locale.ROOT));
        var payload = Map.of("op","subscribe","args",args);
        try { ws.sendText(MAPPER.writeValueAsString(payload), true); } catch (Exception ignore) {}
    }

    private void safePing(WebSocket ws) { try { ws.sendText("{\"op\":\"ping\"}", true); } catch (Exception ignore) {} }

    private void scheduleReconnect(String url, TickHandler handler, Collection<String> syms) {
        Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"bybit-spot-reconnect"); t.setDaemon(true); return t; })
                .schedule(() -> { try { openOne(url, handler, syms); } catch (Throwable ignore) {} }, 1, TimeUnit.SECONDS);
    }

    private static String optArrayPrice(JsonNode arr2) {
        if (arr2 == null || !arr2.isArray() || arr2.size() < 1) return null;
        JsonNode p = arr2.get(0);
        return p.isTextual() ? p.asText() : null;
    }

    private static <T> List<List<T>> chunk(Collection<T> all, int size) {
        var it = all.iterator(); var out = new ArrayList<List<T>>();
        while (it.hasNext()) { var b = new ArrayList<T>(size); for (int i=0;i<size && it.hasNext();i++) b.add(it.next()); out.add(b); }
        return out;
    }

    private static final class CompositeSub implements StreamSubscription {
        private final List<StreamSubscription> list = new CopyOnWriteArrayList<>();
        void add(StreamSubscription s){ list.add(s); }
        @Override public void close(){ list.forEach(StreamSubscription::close); list.clear(); }
    }
}

