package com.suhoi.adapters.bybit;

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

public final class BybitMarketStatsClient implements VenueMarketStatsClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    @Override public String venue() { return "BYBIT"; }

    @Override
    public Map<MarketRef, MarketStats> fetch(Collection<MarketRef> markets) {
        Map<MarketRef, MarketStats> out = new HashMap<>();
        Instant now = Instant.now();

        Map<String, MarketRef> spot = new HashMap<>();
        Map<String, MarketRef> perp = new HashMap<>();
        for (MarketRef m : markets) {
            if ("SPOT".equalsIgnoreCase(m.kind())) spot.put(m.nativeSymbol(), m);
            else perp.put(m.nativeSymbol(), m);
        }

        if (!spot.isEmpty()) {
            JsonNode root = getJson("https://api.bybit.com/v5/market/tickers?category=spot");
            JsonNode list = root.path("result").path("list");
            for (JsonNode n : list) {
                String symbol = n.path("symbol").asText(); // напр. BTCUSDT
                MarketRef ref = spot.get(symbol);
                if (ref != null) {
                    BigDecimal turnoverUsd = num(n, "turnover24h"); // для spot Bybit тоже отдаёт
                    if (turnoverUsd == null) turnoverUsd = num(n, "usdIndexPrice").multiply(num(n, "volume24h", "0"), java.math.MathContext.DECIMAL64);
                    if (turnoverUsd != null) out.put(ref, new MarketStats(turnoverUsd, null, now));
                }
            }
        }

        if (!perp.isEmpty()) {
            JsonNode root = getJson("https://api.bybit.com/v5/market/tickers?category=linear");
            JsonNode list = root.path("result").path("list");
            for (JsonNode n : list) {
                String symbol = n.path("symbol").asText();
                MarketRef ref = perp.get(symbol);
                if (ref != null) {
                    BigDecimal turnoverUsd = num(n, "turnover24h");
                    if (turnoverUsd != null) out.put(ref, new MarketStats(turnoverUsd, null, now));
                }
            }
        }
        return out;
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return om.readTree(resp.body());
        } catch (Exception e) { throw new RuntimeException("BYBIT GET failed: " + url, e); }
    }

    private static BigDecimal num(JsonNode n, String field) {
        String s = n.path(field).asText(null);
        return s == null ? null : new BigDecimal(s);
    }
    private static BigDecimal num(JsonNode n, String field, String def) {
        String s = n.path(field).asText(def);
        return new BigDecimal(s);
    }
}

