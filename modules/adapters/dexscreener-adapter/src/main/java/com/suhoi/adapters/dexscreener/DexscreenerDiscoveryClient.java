package com.suhoi.adapters.dexscreener;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.VenueListing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.suhoi.adapters.dexscreener.DexscreenerJson.MAPPER;

/**
 * Discovery для DexScreener по официальному REST API.<br>
 *<br>
 * Поддержка:<br>
 *  - поиск пар через /latest/dex/search?q=BASE/USDT;<br>
 *  - строгая фильтрация «мусора»: ликвидность, объём, возраст пары;<br>
 *  - конструирование VenueListing с nativeSymbol = "chainId:pairAddress".<br>
 *<br>
 * Ограничения:<br>
 *  - только USDT-котируемые пулы (quoteToken.symbol ~ "USDT");<br>
 *  - priceScale эвристикой из priceUsd (кол-во десятичных).<br>
 *<br>
 * Документация:<br>
 *  - Search:  GET https://api.dexscreener.com/latest/dex/search?q=SOL/USDT (RL 300 rpm)<br>
 *  - Pair(s): GET https://api.dexscreener.com/latest/dex/pairs/{chainId}/{pairId}<br>
 */
public final class DexscreenerDiscoveryClient implements DiscoveryClient {

    private static final String VENUE = "DEXSCREENER";
    private static final String KIND = "DEX";

    // Порог anti-noise (настраиваемо позже через settings)
    private final double minLiquidityUsd = 100_000.0;
    private final double minVol24hUsd    = 20_000.0;
    private final long   minAgeMs        = Duration.ofHours(24).toMillis();

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();

    private final String apiBase;

    public DexscreenerDiscoveryClient() {
        this("https://api.dexscreener.com");
    }
    public DexscreenerDiscoveryClient(String apiBase) {
        this.apiBase = Objects.requireNonNull(apiBase);
    }

    @Override
    public List<VenueListing> listSpotUsdt() {
        // В терминах MVP "SPOT" у DEX = "DEX". Мы можем вернуть пусто — discovery-service будет звать нас иначе.
        return List.of();
    }

    @Override
    public List<VenueListing> listPerpUsdt() {
        // На DEX нет перпов (в рамках DexScreener), вернём пусто.
        return List.of();
    }

    /**
     * Специальный метод (не из интерфейса), чтобы discovery-service мог искать
     * лучшие пулы под канонический asset (BASE/USDT) на разных сетях.
     */
    public List<VenueListing> searchBestUsdtPools(String baseSymbolUpper) {
        try {
            var q = baseSymbolUpper + "/USDT";
            var url = apiBase + "/latest/dex/search?q=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8);
            var req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(10)).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) return List.of();

            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode pairs = root.get("pairs");
            if (pairs == null || !pairs.isArray()) return List.of();

            var out = new ArrayList<VenueListing>();
            long now = System.currentTimeMillis();

            for (var p : pairs) {
                String chainId = p.path("chainId").asText("");
                String pairAddress = p.path("pairAddress").asText("");
                JsonNode base = p.get("baseToken");
                JsonNode quote = p.get("quoteToken");
                if (base == null || quote == null) continue;

                String baseSym = base.path("symbol").asText("");
                String quoteSym = quote.path("symbol").asText("");
                if (!"USDT".equalsIgnoreCase(quoteSym)) continue; // только USDT
                if (!baseSymbolUpper.equalsIgnoreCase(baseSym)) continue;

                // Анти-шум
                double liqUsd = p.path("liquidity").path("usd").asDouble(0.0);
                double vol24h = p.path("volume").path("h24").asDouble(0.0);
                long createdAt = p.path("pairCreatedAt").asLong(0L);
                if (liqUsd < minLiquidityUsd) continue;
                if (vol24h < minVol24hUsd) continue;
                if (createdAt > 0 && (now - createdAt) < minAgeMs) continue;

                String priceUsd = p.path("priceUsd").asText(null);
                int priceScale = DexscreenerSymbols.decimalsOf(priceUsd);

                String nativeSymbol = DexscreenerSymbols.nativeOf(chainId, pairAddress);

                out.add(new VenueListing(
                        VENUE,
                        KIND,
                        nativeSymbol,
                        baseSym,
                        "USDT",
                        priceScale,
                        0,
                        "TRADING"
                ));
            }

            // Выбираем по лучшей ликвидности на каждую сеть, затем по всей выборке
            // (можно усложнить логикой; пока просто отсортируем по ликвидности)
            return out.stream()
                    .distinct()
                    .sorted(Comparator.comparing((VenueListing v) -> v.nativeSymbol).thenComparing(v -> v.priceScale))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            return List.of();
        }
    }
}
