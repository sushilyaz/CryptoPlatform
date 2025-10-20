package com.suhoi.adapters.mexc;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.VenueListing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.suhoi.adapters.mexc.MexcJson.MAPPER;


/**
 * Discovery для MEXC.
 *
 * SPOT: GET https://api.mexc.com/api/v3/exchangeInfo — поля symbol, status("1" online), baseAsset, quoteAsset, *Precision.
 * Futures: GET https://contract.mexc.com/api/v1/contract/detail — поля symbol (например, BTC_USDT), baseCoin, quoteCoin, state(0 enabled), priceScale/amountScale.
 *
 * Документация:
 *  - Spot exchangeInfo: /api-docs/spot-v3/market-data-endpoints → "Exchange Information".
 *  - Futures contract detail: /api-docs/futures/market-endpoints → "Get the contract information".
 */
public final class MexcDiscoveryClient implements DiscoveryClient {

    private static final String VENUE = "MEXC";
    private static final String KIND_SPOT = "SPOT";
    private static final String KIND_PERP = "PERP";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String spotBase;     // https://api.mexc.com
    private final String futuresBase;  // https://contract.mexc.com

    public MexcDiscoveryClient() {
        this("https://api.mexc.com", "https://contract.mexc.com");
    }

    public MexcDiscoveryClient(String spotBase, String futuresBase) {
        this.spotBase = Objects.requireNonNull(spotBase);
        this.futuresBase = Objects.requireNonNull(futuresBase);
    }

    @Override
    public List<VenueListing> listSpotUsdt() {
        String url = spotBase + "/api/v3/exchangeInfo";
        try {
            var req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(15)).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());

            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode symbols = root.get("symbols");
            if (symbols == null || !symbols.isArray()) return List.of();

            var out = new ArrayList<VenueListing>(symbols.size());
            for (JsonNode s : symbols) {
                String status = s.path("status").asText(""); // "1" online, "2" pause, "3" offline
                String base = s.path("baseAsset").asText("");
                String quote = s.path("quoteAsset").asText("");
                if (!"1".equals(status)) continue;
                if (!"USDT".equalsIgnoreCase(quote)) continue;

                String nativeSymbol = s.path("symbol").asText("");
                // Возьмем priceScale/qtyScale из precision-полей, если есть.
                int priceScale = s.path("quoteAssetPrecision").asInt(
                        s.path("quotePrecision").asInt(8));
                // qtyScale — по baseSizePrecision (string) либо baseAssetPrecision (int)
                int qtyScale = decimalsOf(s.path("baseSizePrecision").asText(null));
                if (qtyScale == 0) qtyScale = s.path("baseAssetPrecision").asInt(8);

                out.add(new VenueListing(VENUE, KIND_SPOT, nativeSymbol, base, quote, priceScale, qtyScale, status));
            }
            return out.stream()
                    .distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("MEXC spot exchangeInfo failed: " + url, e);
        }
    }

    @Override
    public List<VenueListing> listPerpUsdt() {
        String url = futuresBase + "/api/v1/contract/detail";
        try {
            var req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(15)).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());

            JsonNode root = MAPPER.readTree(resp.body());
            JsonNode data = root.get("data");
            if (data == null || !data.isArray()) return List.of();

            var out = new ArrayList<VenueListing>(data.size());
            for (JsonNode x : data) {
                int state = x.path("state").asInt(-1); // 0 enabled
                String base = x.path("baseCoin").asText("");
                String quote = x.path("quoteCoin").asText("");
                if (state != 0) continue;
                if (!"USDT".equalsIgnoreCase(quote)) continue;

                String nativeSymbol = x.path("symbol").asText(""); // "BTC_USDT"
                int priceScale = x.path("priceScale").asInt(2);
                int qtyScale = x.path("amountScale").asInt(4);

                out.add(new VenueListing(VENUE, KIND_PERP, nativeSymbol, base, quote, priceScale, qtyScale, "ENABLED"));
            }
            return out.stream()
                    .distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("MEXC futures detail failed: " + url, e);
        }
    }

    private static int decimalsOf(String str) {
        if (str == null) return 0;
        int dot = str.indexOf('.');
        if (dot < 0) return 0;
        return str.length() - dot - 1;
    }
}
