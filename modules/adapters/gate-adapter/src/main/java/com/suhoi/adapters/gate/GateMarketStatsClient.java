package com.suhoi.adapters.gate;

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

public final class GateMarketStatsClient implements VenueMarketStatsClient {
    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper om = new ObjectMapper();

    @Override public String venue() { return "GATE"; }

    @Override
    public Map<MarketRef, MarketStats> fetch(Collection<MarketRef> markets) {
        Map<MarketRef, MarketStats> out = new HashMap<>();
        Instant now = Instant.now();

        for (MarketRef m : markets) {
            if ("SPOT".equalsIgnoreCase(m.kind())) {
                // Gate spot uses BTC_USDT
                String pair = m.nativeSymbol().replace("USDT", "USDT").replace("-", "_");
                JsonNode arr = getJson("https://api.gateio.ws/api/v4/spot/tickers?currency_pair=" + pair);
                if (arr.isArray() && arr.size() > 0) {
                    BigDecimal qv = num(arr.get(0), "quote_volume");
                    if (qv != null) out.put(m, new MarketStats(qv, null, now));
                }
            } else {
                // Futures USDT settle
                String contract = m.nativeSymbol().replace("-", "_");
                JsonNode arr = getJson("https://api.gateio.ws/api/v4/futures/usdt/tickers?contract=" + contract);
                if (arr.isArray() && arr.size() > 0) {
                    BigDecimal turnover = num(arr.get(0), "volume_24h_quote"); // иногда поле так называется
                    if (turnover == null) turnover = num(arr.get(0), "volume_24h"); // фолбэк
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
        } catch (Exception e) { throw new RuntimeException("GATE GET failed: " + url, e); }
    }
    private static BigDecimal num(JsonNode n, String field) {
        String s = n.path(field).asText(null);
        return s == null ? null : new BigDecimal(s);
    }
}

