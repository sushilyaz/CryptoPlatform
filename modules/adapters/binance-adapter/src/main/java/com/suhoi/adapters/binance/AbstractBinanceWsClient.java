package com.suhoi.adapters.binance;

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

import static com.suhoi.adapters.binance.BinanceJson.MAPPER;
import static com.suhoi.adapters.binance.BinanceSymbols.extractBaseOrThrow;

/**
 * Базовый WS-клиент комбо-потоков @bookTicker.
 * Особенности:
 *  - чанкуем список символов (по умолчанию 200 на соединение);
 *  - авто-ответ на ping (sendPong);
 *  - auto-reconnect при ошибке/закрытии (простой backoff);
 *  - парсим wrapper {"stream":"...","data":{...}} и raw {"e":"bookTicker", ...}.
 */
abstract class AbstractBinanceWsClient implements StreamClient {
    private static final int MAX_STREAMS_PER_WS = 200;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "binance-ws-reconnect");
        t.setDaemon(true); return t;
    });

    private final Set<StreamSubscription> live = ConcurrentHashMap.newKeySet();

    private final String venue;  // BINANCE
    private final String kind;   // SPOT | PERP
    private final String wsBase; // wss://stream.binance.com/stream | wss://fstream.binance.com/stream

    protected AbstractBinanceWsClient(String venue, String kind, String wsBase) {
        this.venue = venue; this.kind = kind; this.wsBase = wsBase;
    }

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        var composite = new CompositeSub();
        for (var chunk : chunk(nativeSymbols, MAX_STREAMS_PER_WS)) {
            var streams = new ArrayList<String>(chunk.size());
            for (var sym : chunk) streams.add(BinanceSymbols.toWsSymbol(sym));
            var url = wsBase + "?streams=" + String.join("/", streams);
            composite.add(openOne(url, handler));
        }
        live.add(composite);
        return composite;
    }

    @Override public void close() {
        live.forEach(StreamSubscription::close);
        live.clear();
        scheduler.shutdownNow();
    }

    private StreamSubscription openOne(String url, TickHandler handler) {
        var listener = new WebSocket.Listener() {
            @Override public void onOpen(WebSocket ws) { ws.request(1); }

            @Override public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                try {
                    JsonNode node = MAPPER.readTree(data.toString());
                    JsonNode payload = node.has("data") ? node.get("data") : node;

                    // поля bookTicker: s (symbol), b (bid), a (ask), E (eventTime)
                    String symbolUpper = payload.path("s").asText(payload.path("symbol").asText("")).toUpperCase();
                    if (!symbolUpper.endsWith("USDT")) { ws.request(1); return null; }

                    String bStr = payload.path("b").asText(payload.path("bestBid").asText(null));
                    String aStr = payload.path("a").asText(payload.path("bestAsk").asText(null));
                    if (bStr == null || aStr == null) { ws.request(1); return null; }

                    BigDecimal bid = new BigDecimal(bStr);
                    BigDecimal ask = new BigDecimal(aStr);
                    BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2));

                    long e = payload.path("E").asLong(System.currentTimeMillis());
                    Instant ts = Instant.ofEpochMilli(e);

                    var tick = new Tick(
                            ts,
                            extractBaseOrThrow(symbolUpper), // asset = BASE
                            venue,
                            kind,
                            bid,
                            ask,
                            mid,
                            null,           // depthUsd50 недоступен в этом стриме
                            ts,             // heartbeatTs = eventTime
                            null,           // marketId неизвестен адаптеру
                            symbolUpper     // nativeSymbol
                    );
                    handler.onTick(tick);
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
                scheduleReconnect(url, handler); return null;
            }
            @Override public void onError(WebSocket ws, Throwable error) {
                scheduleReconnect(url, handler);
            }
        };

        var ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), listener)
                .orTimeout(15, TimeUnit.SECONDS)
                .join();

        return new WsSubscription(ws);
    }

    private void scheduleReconnect(String url, TickHandler handler) {
        scheduler.schedule(() -> {
            try { openOne(url, handler); } catch (Throwable ignore) {}
        }, 1, TimeUnit.SECONDS);
    }

    private static <T> List<List<T>> chunk(Collection<T> all, int size) {
        var it = all.iterator();
        var out = new ArrayList<List<T>>();
        while (it.hasNext()) {
            var b = new ArrayList<T>(size);
            for (int i=0; i<size && it.hasNext(); i++) b.add(it.next());
            out.add(b);
        }
        return out;
    }

    /** Несколько WS-сессий как один StreamSubscription. */
    private static final class CompositeSub implements StreamSubscription {
        private final List<StreamSubscription> list = new CopyOnWriteArrayList<>();
        void add(StreamSubscription s) { list.add(s); }
        @Override public void close() { list.forEach(StreamSubscription::close); list.clear(); }
    }
}
