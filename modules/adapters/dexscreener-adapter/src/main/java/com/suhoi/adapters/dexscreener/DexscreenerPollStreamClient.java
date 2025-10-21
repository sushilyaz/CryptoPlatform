package com.suhoi.adapters.dexscreener;


import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.StreamClient;
import com.suhoi.api.adapter.StreamSubscription;
import com.suhoi.api.adapter.TickHandler;
import com.suhoi.events.Tick;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static com.suhoi.adapters.dexscreener.DexscreenerJson.MAPPER;
import static com.suhoi.adapters.dexscreener.DexscreenerSymbols.*;

/**
 * "Стрим" для DexScreener через быстрый REST-поллинг. <br>
 * Для каждого nativeSymbol (= chainId:pairAddress) опрашивает:<br>
 *   GET /latest/dex/pairs/{chainId}/{pairId}<br>
 * и формирует Tick (mid = priceUsd; bid=ask=mid; depth=null).<br>
 *<br>
 * Rate-limit DexScreener: 300 rpm (≈5 rps). По-умолчанию поллим каждые 2с на пул.<br>
 */
public final class DexscreenerPollStreamClient implements StreamClient {

    private static final String VENUE = "DEXSCREENER";
    private static final String KIND  = "DEX";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6)).build();

    private final String apiBase;
    private final long pollIntervalMs;

    // Пул потоков под опрос (1–2 потока достаточно)
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        var t = new Thread(r, "dexscr-poller");
        t.setDaemon(true); return t;
    });

    // Активные задачи по символу
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    public DexscreenerPollStreamClient() {
        this("https://api.dexscreener.com", 2000L);
    }
    public DexscreenerPollStreamClient(String apiBase, long pollIntervalMs) {
        this.apiBase = Objects.requireNonNull(apiBase);
        this.pollIntervalMs = Math.max(500L, pollIntervalMs); // защита от слишком частого опроса
    }

    @Override
    public StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler) {
        if (nativeSymbols == null || nativeSymbols.isEmpty())
            throw new IllegalArgumentException("symbols empty");

        var subs = new ArrayList<ScheduledFuture<?>>();
        for (String nativeSymbol : nativeSymbols) {
            String chain = chainFromNative(nativeSymbol);
            String pair  = pairFromNative(nativeSymbol);
            String url   = apiBase + "/latest/dex/pairs/" + chain + "/" + pair;

            // Стартуем опрос
            ScheduledFuture<?> f = scheduler.scheduleAtFixedRate(() -> pollOne(url, nativeSymbol, handler),
                    0, pollIntervalMs, TimeUnit.MILLISECONDS);
            subs.add(f);
            tasks.put(nativeSymbol, f);
        }

        return () -> {
            for (var f : subs) { try { f.cancel(true); } catch (Exception ignore) {} }
            for (String k : nativeSymbols) tasks.remove(k);
        };
    }

    private void pollOne(String url, String nativeSymbol, TickHandler handler) {
        try {
            var req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(5)).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return;

            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode pairs = root.get("pairs");
            if (pairs == null || !pairs.isArray() || pairs.isEmpty()) return;

            JsonNode p = pairs.get(0);
            String priceUsdStr = p.path("priceUsd").asText(null);
            if (priceUsdStr == null) return;

            String baseSym = p.path("baseToken").path("symbol").asText("");
            long tsMs = p.path("updatedAt").asLong(0L);
            if (tsMs == 0L) tsMs = System.currentTimeMillis();

            BigDecimal mid = new BigDecimal(priceUsdStr);
            var ts = Instant.ofEpochMilli(tsMs);

            var tick = new Tick(
                    ts,
                    assetFromBaseSymbol(baseSym),
                    VENUE,
                    KIND,
                    mid,             // bid (нет — принимаем mid)
                    mid,             // ask (нет — принимаем mid)
                    mid,
                    null,            // depthUsd50 недоступен
                    ts,
                    null,
                    nativeSymbol
            );
            handler.onTick(tick);
        } catch (Exception ignore) {
            // сетевые/парсинг — терпим при поллинге
        }
    }

    @Override
    public void close() {
        for (var f : tasks.values()) { try { f.cancel(true); } catch (Exception ignore) {} }
        tasks.clear();
        scheduler.shutdownNow();
    }
}

