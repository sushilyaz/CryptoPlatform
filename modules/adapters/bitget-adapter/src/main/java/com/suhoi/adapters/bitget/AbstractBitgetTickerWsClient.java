package com.suhoi.adapters.bitget;


import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.StreamClient;
import com.suhoi.api.adapter.StreamSubscription;
import com.suhoi.api.adapter.TickHandler;
import com.suhoi.events.Tick;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.suhoi.adapters.bitget.BitgetJson.MAPPER;
import static com.suhoi.adapters.bitget.BitgetSymbols.extractBaseOrThrow;


/**
 * Базовый клиент Bitget WS (v2 public) для канала {@code ticker}.
 * Особенности:
 *  - батч-подписка (args — список объектов), чанкуем по N;
 *  - ping (строка "ping") каждые 30с, ждём "pong";
 *  - auto-reconnect с небольшим backoff;
 *  - парсим push с "action": snapshot/update и массивом data.
 */
abstract class AbstractBitgetTickerWsClient implements StreamClient {

    private static final int MAX_ARGS_PER_WS = 100; // рекомендация <50, но выдержим запас по чанкам

    protected final String venue;   // "BITGET"
    protected final String kind;    // "SPOT" | "PERP"
    protected final String instType;// "SPOT" | "USDT-FUTURES"
    protected final String wsUrl;   // wss://ws.bitget.com/v2/ws/public

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "bitget-ws-ping"); t.setDaemon(true); return t;
    });
    private final Set<StreamSubscription> live = ConcurrentHashMap.newKeySet();

    protected AbstractBitgetTickerWsClient(String venue, String kind, String instType, String wsUrl) {
        this.venue = venue; this.kind = kind; this.instType = instType; this.wsUrl = wsUrl;
    }

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        var composite = new CompositeSub();
        for (var chunk : chunk(nativeSymbols, MAX_ARGS_PER_WS)) {
            composite.add(openOne(wsUrl, handler, chunk));
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
                pingTask = scheduler.scheduleAtFixedRate(() -> safePing(ws), 30, 30, TimeUnit.SECONDS);
                ws.request(1);
            }

            @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                String txt = data.toString();
                try {
                    if ("pong".equalsIgnoreCase(txt)) { ws.request(1); return null; }

                    JsonNode root = MAPPER.readTree(txt);
                    JsonNode arg = root.path("arg");
                    String channel = arg.path("channel").asText("");
                    if (!"ticker".equalsIgnoreCase(channel)) { ws.request(1); return null; }

                    long tsMs = root.path("ts").asLong(System.currentTimeMillis());
                    JsonNode arr = root.path("data");
                    if (!arr.isArray() || arr.size() == 0) { ws.request(1); return null; }

                    for (JsonNode d : arr) {
                        String symbolUpper = d.path("instId").asText("").toUpperCase(Locale.ROOT);
                        if (!symbolUpper.endsWith("USDT")) continue;

                        String bStr = d.path("bidPr").asText(null);
                        String aStr = d.path("askPr").asText(null);
                        BigDecimal bid = (bStr == null || bStr.isEmpty()) ? null : new BigDecimal(bStr);
                        BigDecimal ask = (aStr == null || aStr.isEmpty()) ? null : new BigDecimal(aStr);
                        if (bid == null && ask == null) continue;

                        BigDecimal mid = (bid != null && ask != null)
                                ? bid.add(ask).divide(BigDecimal.valueOf(2))
                                : (bid != null ? bid : ask);

                        Instant ts = Instant.ofEpochMilli(tsMs);
                        handler.onTick(new Tick(
                                ts,
                                extractBaseOrThrow(symbolUpper),
                                venue,
                                kind,
                                bid, ask, mid,
                                null,  // depthUsd50 — недоступно в этом канале
                                ts,
                                null,
                                symbolUpper
                        ));
                    }
                } catch (Exception ignore) { /* пропускаем мусор */ }
                ws.request(1);
                return null;
            }

            @Override public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                ws.request(1); return null;
            }
            @Override public CompletionStage<?> onPing(WebSocket ws, ByteBuffer msg) {
                ws.sendPong(msg); ws.request(1); return null;
            }
            @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                scheduleReconnect(url, handler, syms); return null;
            }
            @Override public void onError(WebSocket ws, Throwable error) {
                scheduleReconnect(url, handler, syms);
            }
        };

        var ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), listener)
                .orTimeout(15, TimeUnit.SECONDS)
                .join();

        return new BitgetWsSubscription(ws);
    }

    private void sendSubscribe(WebSocket ws, Collection<String> syms) {
        var args = new ArrayList<Map<String, String>>(syms.size());
        for (var s : syms) {
            args.add(Map.of(
                    "instType", instType,
                    "channel", "ticker",
                    "instId", s.toUpperCase(Locale.ROOT)
            ));
        }
        var payload = Map.of("op", "subscribe", "args", args);
        try { ws.sendText(MAPPER.writeValueAsString(payload), true); } catch (Exception ignore) {}
    }

    private void safePing(WebSocket ws) {
        try { ws.sendText("ping", true); } catch (Exception ignore) {}
    }

    private void scheduleReconnect(String url, TickHandler handler, Collection<String> syms) {
        Executors.newSingleThreadScheduledExecutor(r -> {
            var t = new Thread(r, "bitget-ws-reconnect"); t.setDaemon(true); return t;
        }).schedule(() -> { try { openOne(url, handler, syms); } catch (Throwable ignore) {} }, 1, TimeUnit.SECONDS);
    }

    private static <T> List<List<T>> chunk(Collection<T> all, int size) {
        var it = all.iterator(); var out = new ArrayList<List<T>>();
        while (it.hasNext()) { var b = new ArrayList<T>(size); for (int i=0;i<size && it.hasNext();i++) b.add(it.next()); out.add(b); }
        return out;
    }

    /** Несколько WS-сессий как один StreamSubscription. */
    private static final class CompositeSub implements StreamSubscription {
        private final List<StreamSubscription> list = new CopyOnWriteArrayList<>();
        void add(StreamSubscription s) { list.add(s); }
        @Override public void close() { list.forEach(StreamSubscription::close); list.clear(); }
    }
}

