package com.suhoi.adapters.mexc;

import com.mxc.push.common.protobuf.PublicAggreBookTickerV3Api;
import com.mxc.push.common.protobuf.PushDataV3ApiWrapper;
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
import java.util.stream.Collectors;

/**
 * MEXC SPOT (v3) WebSocket клиент, декодирующий protobuf-пуши bookTicker.
 * <p>
 * Важное:
 * - endpoint: wss://wbs-api.mexc.com/ws
 * - подписка текстом: {"method":"SUBSCRIPTION","params":["spot@public.aggre.bookTicker.v3.api.pb@100ms@BTCUSDT", ...]}
 * - ответы приходят в BINARY (protobuf), а НЕ в JSON-тексте
 * - для декодирования используются классы, сгенерированные из официальных .proto
 *
 * Требуются сгенерённые классы из .proto (см. README в модуле и build.gradle/protobuf).
 * Ниже импорты указаны через FQN в месте вызова parseFrom(...) — чтобы тебе было проще подправить пакет,
 * если у официальных .proto другой java_package.
 */
public final class MexcSpotPbStreamClient implements StreamClient {

    private static final String VENUE = "MEXC";
    private static final String KIND  = "SPOT";
    private static final String WS_URL = "wss://wbs-api.mexc.com/ws";

    // безопасный лимит подписок на одно соединение (в доке рекомендуют ~30)
    private static final int MAX_CHANNELS_PER_WS = 30;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        var t = new Thread(r, "mexc-spotpb-ws");
        t.setDaemon(true);
        return t;
    });

    private final Set<StreamSubscription> live = ConcurrentHashMap.newKeySet();

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        // Каналы строго верхним регистром
        List<String> channels = nativeSymbols.stream()
                .map(sym -> "spot@public.aggre.bookTicker.v3.api.pb@100ms@" + sym.toUpperCase(Locale.ROOT))
                .collect(Collectors.toList());

        var composite = new CompositeSub();
        for (var chunk : chunk(channels, MAX_CHANNELS_PER_WS)) {
            composite.add(openOne(WS_URL, handler, chunk));
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

    private StreamSubscription openOne(String url, TickHandler handler, List<String> channels) {
        var listener = new WebSocket.Listener() {
            private ScheduledFuture<?> pingTask;

            @Override
            public void onOpen(WebSocket ws) {
                // Текстовая подписка
                String sub = "{\"method\":\"SUBSCRIPTION\",\"params\":" + toJsonArray(channels) + "}";
                ws.sendText(sub, true);

                // Периодический keepalive
                pingTask = scheduler.scheduleAtFixedRate(() -> {
                    try { ws.sendText("{\"method\":\"PING\"}", true); } catch (Exception ignore) {}
                }, 20, 20, TimeUnit.SECONDS);

                ws.request(1);
            }

            @Override
            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                // На SPOT-v3 полезные данные идут BINARY-протобафом; текст тут — только SUBSCRIPTION/PING ответы.
                ws.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket ws, ByteBuffer bb, boolean last) {
                try {
                    // Считываем все байты
                    byte[] bytes = new byte[bb.remaining()];
                    bb.get(bytes);

                    PushDataV3ApiWrapper wrapper = PushDataV3ApiWrapper.parseFrom(bytes);
                    String symbol = wrapper.getSymbol();
                    long sendTs = wrapper.getSendTime();
                    String bidStr = null;
                    String askStr = null;
                    if (wrapper.hasPublicAggreBookTicker()) {
                        PublicAggreBookTickerV3Api publicAggreBookTicker = wrapper.getPublicAggreBookTicker();
                        bidStr = publicAggreBookTicker.getBidPrice();
                        askStr = publicAggreBookTicker.getAskPrice();
                    }
                    if (symbol == null || symbol.isEmpty() || bidStr == null || askStr == null) {
                        ws.request(1);
                        return null;
                    }

                    BigDecimal bid = new BigDecimal(bidStr);
                    BigDecimal ask = new BigDecimal(askStr);
                    BigDecimal mid = bid.add(ask).divide(BigDecimal.valueOf(2));

                    Instant ts = Instant.ofEpochMilli(sendTs == 0 ? System.currentTimeMillis() : sendTs);

                    var tick = new Tick(
                            ts,
                            MexcSymbols.extractSpotBaseOrThrow(symbol.toUpperCase(Locale.ROOT)),
                            VENUE,
                            KIND,
                            bid, ask, mid,
                            null,       // depthUsd50 нет в этом канале
                            ts,         // heartbeat
                            null,       // marketId неизвестен адаптеру
                            symbol.toUpperCase(Locale.ROOT)
                    );
                    handler.onTick(tick);
                } catch (Exception ignore) {
                    // В случае несовпадений версий .proto будет исключение parseFrom(...).
                    // Проверь FQN классов и актуальность .proto из официального репозитория.
                }
                ws.request(1);
                return null;
            }

            @Override public CompletionStage<?> onPing(WebSocket ws, ByteBuffer msg) {
                ws.sendPong(msg); ws.request(1); return null;
            }
            @Override public CompletionStage<?> onClose(WebSocket ws, int status, String reason) {
                if (pingTask != null) pingTask.cancel(true);
                // можно добавить auto-reconnect при желании
                return null;
            }
            @Override public void onError(WebSocket ws, Throwable error) {
                if (pingTask != null) pingTask.cancel(true);
                // можно добавить auto-reconnect при желании
            }
        };

        var ws = http.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(url), listener)
                .orTimeout(15, TimeUnit.SECONDS)
                .join();

        // Подписка с пингом уже управляется внутри listener (через ScheduledExecutorService)
        return new WsSubscriptionWithPing(ws, null);
    }

    private static String toJsonArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(list.get(i)).append('"');
        }
        return sb.append(']').toString();
    }

    private static <T> List<List<T>> chunk(Collection<T> all, int size) {
        var it = all.iterator(); var out = new ArrayList<List<T>>();
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
