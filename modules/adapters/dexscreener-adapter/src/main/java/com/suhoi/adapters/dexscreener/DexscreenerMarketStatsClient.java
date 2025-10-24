package com.suhoi.adapters.dexscreener;

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

public final class DexscreenerMarketStatsClient implements VenueMarketStatsClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    @Override public String venue() { return "DEXSCREENER"; }

    @Override
    public Map<MarketRef, MarketStats> fetch(Collection<MarketRef> markets) {
        Map<MarketRef, MarketStats> out = new HashMap<>();
        Instant now = Instant.now();

        for (MarketRef m : markets) {
            // nativeSymbol формата "chainId:pairAddress"
            String[] parts = m.nativeSymbol().split(":", 2);
            if (parts.length != 2) continue;
            String chain = parts[0], pair = parts[1];
            JsonNode root = getJson("https://api.dexscreener.com/latest/dex/pairs/" + chain + "/" + pair);
            JsonNode pairs = root.path("pairs");
            if (pairs.isArray() && pairs.size() > 0) {
                JsonNode p = pairs.get(0);
                BigDecimal vol24 = num(p.path("volume"), "h24");
                BigDecimal liq   = num(p.path("liquidity"), "usd");
                if (vol24 != null) out.put(m, new MarketStats(vol24, liq, now));
            }
        }
        return out;
    }

    private JsonNode getJson(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            return om.readTree(resp.body());
        } catch (Exception e) { throw new RuntimeException("DexScreener GET failed: " + url, e); }
    }
    private static BigDecimal num(JsonNode node, String field) {
        String s = node.path(field).asText(null);
        return s == null ? null : new BigDecimal(s);
    }
}

