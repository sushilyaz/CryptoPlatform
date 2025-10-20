package com.suhoi;

import com.suhoi.adapter.DiscoveryClient;
import com.suhoi.adapter.VenueListing;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static com.suhoi.BinanceJson.MAPPER;

/**
 * DiscoveryClient для Binance: <br>
 *  - SPOT: GET /api/v3/exchangeInfo <br>
 *  - USDT-M PERP: GET /fapi/v1/exchangeInfo <br>
 * <br>
 * Документация Binance Spot/Futures exchangeInfo доступна в общедоступных ресурсах. :contentReference[oaicite:8]{index=8}
 * <br>
 * Примечания: <br>
 *  - В MVP поддерживаем только USDT-котируемые пары. <br>
 *  - Статус фильтруем по "TRADING". <br>
 *  - priceScale/qtyScale берём из PRICE_FILTER и LOT_SIZE. <br>
 */
public final class BinanceDiscoveryClient implements DiscoveryClient {

    private static final String VENUE = "BINANCE";
    private static final String KIND_SPOT = "SPOT";
    private static final String KIND_PERP = "PERP";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String spotRestBase;   // https://api.binance.com
    private final String futuresRestBase;// https://fapi.binance.com  (USDT-M)

    public BinanceDiscoveryClient() {
        this("https://api.binance.com", "https://fapi.binance.com");
    }

    public BinanceDiscoveryClient(String spotRestBase, String futuresRestBase) {
        this.spotRestBase = Objects.requireNonNull(spotRestBase);
        this.futuresRestBase = Objects.requireNonNull(futuresRestBase);
    }

    @Override
    public List<VenueListing> listSpotUsdt() {
        return fetchExchangeInfo(spotRestBase + "/api/v3/exchangeInfo", KIND_SPOT);
    }

    @Override
    public List<VenueListing> listPerpUsdt() {
        return fetchExchangeInfo(futuresRestBase + "/fapi/v1/exchangeInfo", KIND_PERP);
    }

    private List<VenueListing> fetchExchangeInfo(String url, String kind) {
        try {
            var req = HttpRequest.newBuilder(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(15))
                    .build();

            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new IllegalStateException("HTTP " + resp.statusCode() + " for " + url);
            }
            var root = MAPPER.readTree(resp.body());

            // Spot: symbols[]; Futures: symbols[] — формат схож.
            var symbols = root.get("symbols");
            if (symbols == null || !symbols.isArray()) return List.of();

            var result = new ArrayList<VenueListing>(symbols.size());
            for (var s : symbols) {
                var status = s.path("status").asText("");
                var base = s.path("baseAsset").asText("");
                var quote = s.path("quoteAsset").asText("");

                if (!"TRADING".equalsIgnoreCase(status)) continue; // фильтр только торгуемые
                if (!"USDT".equalsIgnoreCase(quote)) continue;     // только USDT в MVP

                var nativeSymbol = s.path("symbol").asText("");
                var priceScale = extractPriceScale(s.path("filters"));
                var qtyScale = extractQtyScale(s.path("filters"));

                result.add(new VenueListing(VENUE, kind, nativeSymbol, base, quote, priceScale, qtyScale, status));
            }
            // de-dup и сортировка
            return result.stream()
                    .distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch exchangeInfo: " + url, e);
        }
    }

    private static int extractPriceScale(com.fasterxml.jackson.databind.JsonNode filters) {
        // PRICE_FILTER: tickSize => scale = number of decimals in tickSize
        var tickSize = findFilterValue(filters, "PRICE_FILTER", "tickSize");
        return decimalsOf(tickSize);
    }

    private static int extractQtyScale(com.fasterxml.jackson.databind.JsonNode filters) {
        // LOT_SIZE: stepSize => qty scale
        var stepSize = findFilterValue(filters, "LOT_SIZE", "stepSize");
        return decimalsOf(stepSize);
    }

    private static String findFilterValue(com.fasterxml.jackson.databind.JsonNode filters, String filterType, String field) {
        if (filters == null || !filters.isArray()) return "0";
        for (var f : filters) {
            if (filterType.equalsIgnoreCase(f.path("filterType").asText(""))) {
                return f.path(field).asText("0");
            }
        }
        return "0";
    }

    private static int decimalsOf(String decimalStr) {
        if (decimalStr == null) return 0;
        var idx = decimalStr.indexOf('.');
        if (idx < 0) return 0;
        int trailing = 0;
        for (int i = idx + 1; i < decimalStr.length(); i++) {
            if (decimalStr.charAt(i) == '0') trailing++;
            else { trailing = decimalStr.length() - i; break; }
        }
        return trailing;
    }
}

