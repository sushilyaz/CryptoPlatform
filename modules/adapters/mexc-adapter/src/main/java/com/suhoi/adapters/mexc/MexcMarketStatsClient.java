package com.suhoi.adapters.mexc;

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

public final class MexcMarketStatsClient implements VenueMarketStatsClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    @Override public String venue() { return "MEXC"; }

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
            JsonNode root = getJson("https://api.mexc.com/api/v3/ticker/24hr");
            for (JsonNode n : root) {
                String symbol = n.path("symbol").asText();
                MarketRef ref = spot.get(symbol);
                if (ref != null) {
                    BigDecimal quoteVol = num(n, "quoteVolume");
                    if (quoteVol != null) out.put(ref, new MarketStats(quoteVol, null, now));
                }
            }
        }
        if (!perp.isEmpty()) {
            JsonNode root = getJson("https://contract.mexc.com/api/v1/contract/ticker");
            JsonNode data = root.get("data");
            for (JsonNode n : data) {
                String symbol = n.path("symbol").asText();
                MarketRef ref = perp.get(symbol);
                if (ref != null) {
                    BigDecimal turnover = num(n, "volume24");
                    if (turnover != null) out.put(ref, new MarketStats(turnover, null, now));
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
        } catch (Exception e) { throw new RuntimeException("MEXC GET failed: " + url, e); }
    }
    private static BigDecimal num(JsonNode n, String field) {
        String s = n.path(field).asText(null);
        return s == null ? null : new BigDecimal(s);
    }
}

