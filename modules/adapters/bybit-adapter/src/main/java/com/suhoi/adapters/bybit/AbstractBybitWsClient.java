package com.suhoi.adapters.bybit;

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
import java.util.concurrent.atomic.AtomicBoolean;

import static com.suhoi.adapters.bybit.BybitJson.MAPPER;
import static com.suhoi.adapters.bybit.BybitSymbols.extractBaseOrThrow;

/**
 * Базовый WS-клиент Bybit v5 по топику tickers.{symbol}.
 * <p>
 * Особенности:
 * - Мультиподписка через {"op":"subscribe","args":[ "tickers.BTCUSDT", ... ]}
 * - Авто ping каждые 20с (рекомендация Bybit)
 * - reconnect с простым backoff
 * - Парсит bid1Price/ask1Price → Tick (mid, bid, ask). Если один прайс отсутствует — mid=доступной котировке.
 * <p>
 * Эндпоинты:
 * spot:   wss://stream.bybit.com/v5/public/spot
 * linear: wss://stream.bybit.com/v5/public/linear
 * <p>
 * Лимиты аргументов:
 * - Spot: до 10 args в одном запросе подписки (делаем чанкинг по 10)
 * - Linear: без явного лимита, но ограничим разумно (например 100)
 */
abstract class AbstractBybitWsClient implements StreamClient {

    private static final int MAX_ARGS_SPOT = 10;
    private static final int MAX_ARGS_LINEAR = 100;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "bybit-ws-maintainer");
        t.setDaemon(true);
        return t;
    });

    private final Set<StreamSubscription> live = ConcurrentHashMap.newKeySet();

    private final String venue;     // "BYBIT"
    private final String kind;      // "SPOT" | "PERP"
    private final String wsBase;    // category socket
    private final int maxArgsPerMsg;

    protected AbstractBybitWsClient(String venue, String kind, String wsBase) {
        this.venue = venue;
        this.kind = kind;
        this.wsBase = wsBase;
        this.maxArgsPerMsg = "SPOT".equalsIgnoreCase(kind) ? MAX_ARGS_SPOT : MAX_ARGS_LINEAR;
    }

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        var composite = new CompositeSub();
        for (var chunk : chunk(nativeSymbols, maxArgsPerMsg)) {
            var url = wsBase;
            composite.add(openOne(url, handler, chunk));
        }
        live.add(composite);
        return composite;
    }

    @Override
    public void close() {
        live.forEach(StreamSubscription::close);
        live.clear();
        scheduler.shutdownNow();
    }

    private StreamSubscription openOne(String url, TickHandler handler, Collection<String> syms) {
        var listener = new WebSocket.Listener() {
            private volatile WebSocket socket;
            private final AtomicBoolean subscribed = new AtomicBoolean(false);
            private ScheduledFuture<?> pingTask;

            @Override
            public void onOpen(WebSocket ws) {
                this.socket = ws;
                sendSubscribe(ws, syms);
                // периодический ping
                pingTask = scheduler.scheduleAtFixedRate(() -> safePing(ws), 20, 20, TimeUnit.SECONDS);
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                try {
                    JsonNode n = MAPPER.readTree(data.toString());
                    System.out.println(n);
                    // Подтверждение подписки — просто игнорируем
                    if (n.has("op") && "subscribe".equalsIgnoreCase(n.path("op").asText())) {
                        ws.request(1);
                        return null;
                    }
                    // Понг от сервера (варианты форматов) — игнорируем
                    if ("pong".equalsIgnoreCase(n.path("op").asText()) || "pong".equalsIgnoreCase(n.path("ret_msg").asText())) {
                        ws.request(1);
                        return null;
                    }

                    String topic = n.path("topic").asText("");
                    if (!topic.startsWith("tickers.")) {
                        ws.request(1);
                        return null;
                    }

                    JsonNode dataNode = n.get("data");
//                    System.out.println(dataNode);
                    if (dataNode == null || dataNode.isNull()) {
                        ws.request(1);
                        return null;
                    }
                    // По доке data может быть объект; на всякий — поддержим массив с 1 элементом
                    JsonNode item = dataNode.isArray() && dataNode.size() > 0 ? dataNode.get(0) : dataNode;

                    String symbolUpper = item.path("symbol").asText("").toUpperCase(Locale.ROOT);
                    if (!symbolUpper.endsWith("USDT")) {
                        ws.request(1);
                        return null;
                    }

                    String bStr = item.path("bid1Price").asText(null);
                    String aStr = item.path("ask1Price").asText(null);
                    BigDecimal bid = bStr != null && !bStr.isEmpty() ? new BigDecimal(bStr) : null;
                    BigDecimal ask = aStr != null && !aStr.isEmpty() ? new BigDecimal(aStr) : null;

                    BigDecimal mid = null;
                    if (bid != null && ask != null) mid = bid.add(ask).divide(BigDecimal.valueOf(2));
                    else if (bid != null) mid = bid;
                    else if (ask != null) mid = ask;
                    if (mid == null) {
                        ws.request(1);
                        return null;
                    }

                    long tsMs = n.path("ts").asLong(System.currentTimeMillis());
                    Instant ts = Instant.ofEpochMilli(tsMs);

                    var tick = new Tick(
                            ts,
                            extractBaseOrThrow(symbolUpper), // asset=BASE
                            venue,
                            kind,
                            bid,
                            ask,
                            mid,
                            null,         // depthUsd50 — нет в топике tickers
                            ts,           // heartbeatTs
                            null,         // marketId неизвестен на уровне адаптера
                            symbolUpper   // nativeSymbol
                    );
                    handler.onTick(tick);
                } catch (Exception ignore) { /* пропускаем мусор */ }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onPing(WebSocket ws, ByteBuffer msg) {
                ws.sendPong(msg);
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                if (pingTask != null) pingTask.cancel(true);
                scheduleReconnect(url, handler, syms);
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
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
        for (var s : syms) args.add("tickers." + s.toUpperCase(Locale.ROOT));
        var payload = Map.of("op", "subscribe", "args", args);
        try {
            var json = MAPPER.writeValueAsString(payload);
            ws.sendText(json, true);
        } catch (Exception ignore) {
        }
    }

    private void safePing(WebSocket ws) {
        try {
            // public pong варианты: {"op":"ping"} -> {"op":"pong",...} — норм
            ws.sendText("{\"op\":\"ping\"}", true);
        } catch (Exception ignore) {
        }
    }

    private void scheduleReconnect(String url, TickHandler handler, Collection<String> syms) {
        scheduler.schedule(() -> {
            try {
                openOne(url, handler, syms);
            } catch (Throwable ignore) {
            }
        }, 1, TimeUnit.SECONDS);
    }

    private static <T> List<List<T>> chunk(Collection<T> all, int size) {
        var it = all.iterator();
        var out = new ArrayList<List<T>>();
        while (it.hasNext()) {
            var b = new ArrayList<T>(size);
            for (int i = 0; i < size && it.hasNext(); i++) b.add(it.next());
            out.add(b);
        }
        return out;
    }

    /**
     * Несколько WS-сессий как один StreamSubscription.
     */
    private static final class CompositeSub implements StreamSubscription {
        private final List<StreamSubscription> list = new CopyOnWriteArrayList<>();

        void add(StreamSubscription s) {
            list.add(s);
        }

        @Override
        public void close() {
            list.forEach(StreamSubscription::close);
            list.clear();
        }
    }
}

