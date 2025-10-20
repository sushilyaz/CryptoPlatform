package com.suhoi.adapters.bitget;


import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.VenueListing;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.suhoi.adapters.bitget.BitgetJson.MAPPER;

/**
 * REST discovery для Bitget.
 * <ul>
 *   <li>SPOT: GET /api/v2/spot/public/symbols → status=online, quoteCoin=USDT</li>
 *   <li>USDT-FUTURES (UTA): GET /api/v3/market/instruments?category=USDT-FUTURES → status=online, quoteCoin=USDT</li>
 * </ul>
 * Поля масштаба берём из pricePrecision/quantityPrecision. См. оф. документацию.
 */
public final class BitgetDiscoveryClient implements DiscoveryClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private final String restBase;

    public BitgetDiscoveryClient() { this("https://api.bitget.com"); }
    public BitgetDiscoveryClient(String restBase) { this.restBase = Objects.requireNonNull(restBase); }

    @Override public List<VenueListing> listSpotUsdt() { return fetchSpot(); }
    @Override public List<VenueListing> listPerpUsdt() { return fetchPerp(); }

    private List<VenueListing> fetchSpot() {
        String url = restBase + "/api/v2/spot/public/symbols";
        try {
            var resp = http.send(HttpRequest.newBuilder(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());
            JsonNode data = MAPPER.readTree(resp.body()).path("data");
            if (!data.isArray()) return List.of();

            var out = new ArrayList<VenueListing>(data.size());
            for (JsonNode n : data) {
                String status = n.path("status").asText("");
                String base   = n.path("baseCoin").asText("");
                String quote  = n.path("quoteCoin").asText("");
                if (!"online".equalsIgnoreCase(status)) continue;
                if (!"USDT".equalsIgnoreCase(quote)) continue;

                String symbol = n.path("symbol").asText("");
                int priceScale = parseInt(n.path("pricePrecision").asText("0"), 0);
                int qtyScale   = parseInt(n.path("quantityPrecision").asText("0"), 0);

                out.add(new VenueListing("BITGET", "SPOT", symbol, base, quote, priceScale, qtyScale, status));
            }
            return out.stream().distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Bitget spot symbols fetch failed: " + url, e);
        }
    }

    private List<VenueListing> fetchPerp() {
        String url = restBase + "/api/v3/market/instruments?category=USDT-FUTURES";
        try {
            var resp = http.send(HttpRequest.newBuilder(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(15)).build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());
            JsonNode data = MAPPER.readTree(resp.body()).path("data");
            if (!data.isArray()) return List.of();

            var out = new ArrayList<VenueListing>(data.size());
            for (JsonNode n : data) {
                String status = n.path("status").asText("");
                String base   = n.path("baseCoin").asText("");
                String quote  = n.path("quoteCoin").asText("");
                if (!"online".equalsIgnoreCase(status)) continue;
                if (!"USDT".equalsIgnoreCase(quote)) continue;

                String symbol = n.path("symbol").asText("");
                int priceScale = parseInt(n.path("pricePrecision").asText("0"), 0);
                int qtyScale   = parseInt(n.path("quantityPrecision").asText("0"), 0);

                out.add(new VenueListing("BITGET", "PERP", symbol, base, quote, priceScale, qtyScale, status));
            }
            return out.stream().distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Bitget futures instruments fetch failed: " + url, e);
        }
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception ignore) { return def; }
    }
}

