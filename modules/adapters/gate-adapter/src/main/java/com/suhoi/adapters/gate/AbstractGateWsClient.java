package com.suhoi.adapters.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.StreamClient;
import com.suhoi.api.adapter.StreamSubscription;
import com.suhoi.api.adapter.TickHandler;
import com.suhoi.events.Tick;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.suhoi.adapters.gate.GateJson.MAPPER;

/**
 * Базовый WS-клиент Gate WS v4:
 * - поддержка подписки на канал book_ticker (spot/futures),
 * - чанкинг множества символов на несколько соединений,
 * - pong на ping, auto-reconnect с простым backoff,
 * - нормализация в {@link Tick} делегируется наследникам.
 */
abstract class AbstractGateWsClient implements StreamClient {
    private static final int MAX_SYMBOLS_PER_WS = 200; // безопасный лимит
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "gate-ws-reconnect");
        t.setDaemon(true);
        return t;
    });

    private final Set<StreamSubscription> live = ConcurrentHashMap.newKeySet();

    private final String venue;     // "GATE"
    private final String kind;      // "SPOT" | "PERP"
    private final String wsBase;    //
    private final String channel;   // "spot.book_ticker" | "futures.book_ticker"

    protected AbstractGateWsClient(String venue, String kind, String wsBase, String channel) {
        this.venue = venue;
        this.kind = kind;
        this.wsBase = wsBase;
        this.channel = channel;
    }

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        var composite = new CompositeSub();
        for (var chunk : chunk(nativeSymbols, MAX_SYMBOLS_PER_WS)) {
            composite.add(openOne(wsBase, channel, List.copyOf(chunk), handler));
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

    private StreamSubscription openOne(String url, String channel, List<String> symbols, TickHandler handler) {
        var listener = new WebSocket.Listener() {
            @Override
            public void onOpen(WebSocket ws) {
                try {
                    String msg = buildSubscribeMessage(channel, symbols);
                    System.out.println(msg);
                    ws.sendText(msg, true);
                } catch (Exception e) {
                }
                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                try {
                    JsonNode root = MAPPER.readTree(data.toString());

                    String event = root.path("event").asText("");
                    String ch = root.path("channel").asText("");
                    if (!channel.equals(ch)) {
                        ws.request(1);
                        return null;
                    }

                    if ("update".equalsIgnoreCase(event)) {
                        JsonNode result = root.path("result");
//                        System.out.println(result);
                        if (!result.isMissingNode()) {
                            Tick tick = parseTick(result, venue, kind);
                            if (tick != null) handler.onTick(tick);
                        }
                    }
                } catch (Exception e) {
                }
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer data, boolean last) {
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onPing(WebSocket ws, ByteBuffer message) {
                ws.sendPong(message);
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                scheduleReconnect(url, channel, symbols, handler);
                return null;
            }

            @Override
            public void onError(WebSocket ws, Throwable error) {
                scheduleReconnect(url, channel, symbols, handler);
            }
        };

        var ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), listener)
                .orTimeout(15, TimeUnit.SECONDS)
                .join();

        return new GateWsSubscription(ws);
    }

    private void scheduleReconnect(String url, String channel, List<String> symbols, TickHandler handler) {
        scheduler.schedule(() -> {
            try {
                openOne(url, channel, symbols, handler);
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
     * Формирует JSON подписки Gate WS v4.
     */
    private static String buildSubscribeMessage(String channel, List<String> symbols) {
        long nowSec = System.currentTimeMillis() / 1000;
        // {"time":<sec>,"channel":"spot.book_ticker","event":"subscribe","payload":["BTC_USDT",...]}
        return String.format(Locale.ROOT,
                "{\"time\":%d,\"channel\":\"%s\",\"event\":\"subscribe\",\"payload\":%s}",
                nowSec, channel, toJsonArray(symbols));
    }

    private static String toJsonArray(List<String> syms) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < syms.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(syms.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    /**
     * Парсинг {@code result} конкретного канала в {@link Tick}.
     * Реализация в наследниках, т.к. у spot/futures разный payload.
     */
    protected abstract Tick parseTick(JsonNode result, String venue, String kind);

    /**
     * Составная подписка (несколько WS как один дескриптор).
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

/**
 * Дескриптор одной WS-сессии Gate (с уникальным именем класса, чтобы не конфликтовать с Binance).
 */
final class GateWsSubscription implements StreamSubscription {
    private final WebSocket ws;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    GateWsSubscription(WebSocket ws) {
        this.ws = ws;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye")
                        .orTimeout(2, TimeUnit.SECONDS)
                        .exceptionally(ex -> null);
            } catch (Exception ignore) {
            }
        }
    }
}

