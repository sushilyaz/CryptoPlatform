package com.suhoi.adapters.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhoi.discovery.MarketRef;
import com.suhoi.discovery.MarketStats;
import com.suhoi.discovery.VenueMarketStatsClient;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;

public final class BinanceMarketStatsClient implements VenueMarketStatsClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    @Override public String venue() { return "BINANCE"; }

    @Override
    public Map<MarketRef, MarketStats> fetch(Collection<MarketRef> markets) {
        List<MarketRef> spot  = markets.stream().filter(m -> "SPOT".equalsIgnoreCase(m.kind())).toList();
        List<MarketRef> perp  = markets.stream().filter(m -> "PERP".equalsIgnoreCase(m.kind()) || "FUTURES".equalsIgnoreCase(m.kind())).toList();
        Map<String, MarketRef> bySymbolSpot = indexByNative(spot);
        Map<String, MarketRef> bySymbolPerp = indexByNative(perp);

        Map<MarketRef, MarketStats> out = new HashMap<>();
        Instant now = Instant.now();

        // SPOT
        if (!bySymbolSpot.isEmpty()) {
            JsonNode arr = getJson("https://api.binance.com/api/v3/ticker/24hr");
            for (JsonNode n : arr) {
                String symbol = n.path("symbol").asText();
                MarketRef ref = bySymbolSpot.get(symbol);
                if (ref != null) {
                    BigDecimal qv = readBig(n, "quoteVolume");
                    if (qv != null) out.put(ref, new MarketStats(qv, null, now));
                }
            }
        }
        // PERP (USDT-M)
        if (!bySymbolPerp.isEmpty()) {
            JsonNode arr = getJson("https://fapi.binance.com/fapi/v1/ticker/24hr");
            for (JsonNode n : arr) {
                String symbol = n.path("symbol").asText();
                MarketRef ref = bySymbolPerp.get(symbol);
                if (ref != null) {
                    BigDecimal qv = readBig(n, "quoteVolume");
                    if (qv != null) out.put(ref, new MarketStats(qv, null, now));
                }
            }
        }
        return out;
    }

    private Map<String, MarketRef> indexByNative(List<MarketRef> list) {
        Map<String, MarketRef> m = new HashMap<>();
        for (MarketRef r : list) m.put(r.nativeSymbol(), r);
        return m;
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return om.readTree(resp.body());
        } catch (Exception e) { throw new RuntimeException("BINANCE GET failed: " + url, e); }
    }

    private static BigDecimal readBig(JsonNode n, String field) {
        String s = n.path(field).asText(null);
        return s == null ? null : new BigDecimal(s);
    }
}

