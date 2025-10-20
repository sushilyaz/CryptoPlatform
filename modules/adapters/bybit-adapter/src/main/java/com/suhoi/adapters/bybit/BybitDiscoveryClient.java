package com.suhoi.adapters.bybit;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.VenueListing;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.suhoi.adapters.bybit.BybitJson.MAPPER;

/**
 * REST discovery Bybit v5:
 *  - Spot:   GET /v5/market/instruments-info?category=spot
 *  - Linear: GET /v5/market/instruments-info?category=linear
 * Фильтр: status=Trading, quote=USDT. scale извлекаем из tickSize/qtyStep.
 */
public final class BybitDiscoveryClient implements DiscoveryClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String restBase;

    public BybitDiscoveryClient() { this("https://api.bybit.com"); }
    public BybitDiscoveryClient(String restBase) { this.restBase = Objects.requireNonNull(restBase); }

    @Override public List<VenueListing> listSpotUsdt()  { return fetch("spot",   "SPOT"); }
    @Override public List<VenueListing> listPerpUsdt()  { return fetch("linear", "PERP"); }

    private List<VenueListing> fetch(String category, String kind) {
        String url = restBase + "/v5/market/instruments-info?category=" + category;
        try {
            var resp = http.send(HttpRequest.newBuilder(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());

            JsonNode list = MAPPER.readTree(resp.body()).path("result").path("list");
            if (!list.isArray()) return List.of();

            var out = new ArrayList<VenueListing>(list.size());
            for (JsonNode n : list) {
                String status = n.path("status").asText("Trading");
                if (!"Trading".equalsIgnoreCase(status)) continue;

                String base  = n.path("baseCoin").asText(n.path("baseCurrency").asText(""));
                String quote = n.path("quoteCoin").asText(n.path("quoteCurrency").asText(""));
                if (!"USDT".equalsIgnoreCase(quote)) continue;

                String symbol = n.path("symbol").asText("");

                int priceScale = decimalsOf(n.path("priceFilter").path("tickSize").asText("0"));
                int qtyScale   = "spot".equalsIgnoreCase(category)
                        ? parseIntSafe(n.path("lotSizeFilter").path("basePrecision").asText("0"), 0)
                        : decimalsOf(n.path("lotSizeFilter").path("qtyStep").asText("0"));

                out.add(new VenueListing("BYBIT", kind, symbol, base, quote, priceScale, qtyScale, status));
            }
            return out.stream().distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol)).collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Bybit instruments fetch failed: " + url, e);
        }
    }

    private static int parseIntSafe(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ignore) { return def; }
    }
    private static int decimalsOf(String s) {
        if (s == null || s.isEmpty()) return 0;
        int dot = s.indexOf('.'); if (dot < 0) return 0;
        int lastNonZero = -1;
        for (int i = s.length() - 1; i > dot; i--) if (s.charAt(i) != '0') { lastNonZero = i; break; }
        return lastNonZero < 0 ? 0 : (lastNonZero - dot);
    }
}
