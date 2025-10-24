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
        for (MarketRef m : markets) {
            if ("SPOT".equalsIgnoreCase(m.kind())) {
                JsonNode n = getJson("https://api.mexc.com/api/v3/ticker/24hr?symbol=" + m.nativeSymbol());
                BigDecimal qv = num(n, "quoteVolume");
                if (qv != null) out.put(m, new MarketStats(qv, null, now));
            } else { // PERP/FUTURES
                String sym = m.nativeSymbol().replace("-", "_");
                JsonNode root = getJson("https://contract.mexc.com/api/v1/contract/ticker?symbol=" + sym);
                JsonNode data = root.path("data");
                if (data.isArray() && data.size() > 0) {
                    BigDecimal turnover = num(data.get(0), "turnover24h");
                    if (turnover == null) turnover = num(data.get(0), "turnover");
                    if (turnover != null) out.put(m, new MarketStats(turnover, null, now));
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

