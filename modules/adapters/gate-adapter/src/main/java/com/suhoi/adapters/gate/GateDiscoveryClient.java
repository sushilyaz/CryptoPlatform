package com.suhoi.adapters.gate;

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

import static com.suhoi.adapters.gate.GateJson.MAPPER;
import static com.suhoi.adapters.gate.GateSymbols.decimalsOf;

/**
 * DiscoveryClient для Gate:<br>
 *  - Spot: GET /api/v4/spot/currency_pairs (берём только *_USDT, trade_status=tradable).<br>
 *  - Futures USDT-perp: GET /api/v4/futures/usdt/contracts (берём все контракты *_USDT).<br>
 *<br>
 * Примечания:<br>
 *  - В поли полей spot используем: id, base, quote, precision (price), amount_precision (qty), trade_status.<br>
 *  - Для futures шкалы оцениваем эвристически: priceScale из order_price_round (если есть), qtyScale из order_size_round / order_size_min.<br>
 */
public final class GateDiscoveryClient implements DiscoveryClient {

    private static final String VENUE = "GATE";
    private static final String KIND_SPOT = "SPOT";
    private static final String KIND_PERP = "PERP";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final String restBase; // https://api.gateio.ws

    public GateDiscoveryClient() { this("https://api.gateio.ws"); }

    public GateDiscoveryClient(String restBase) {
        this.restBase = Objects.requireNonNull(restBase);
    }

    @Override
    public List<VenueListing> listSpotUsdt() {
        String url = restBase + "/api/v4/spot/currency_pairs";
        try {
            var req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(15)).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());

            JsonNode arr = MAPPER.readTree(resp.body());
            if (arr == null || !arr.isArray()) return List.of();

            var out = new ArrayList<VenueListing>(arr.size());
            for (var n : arr) {
                String id = n.path("id").asText("");
                String base = n.path("base").asText("");
                String quote = n.path("quote").asText("");
                String tradeStatus = n.path("trade_status").asText("");

                if (!id.endsWith("_USDT")) continue;
                if (!"tradable".equalsIgnoreCase(tradeStatus)) continue;

                int priceScale = n.path("precision").asInt(0);
                int qtyScale = n.path("amount_precision").asInt(0);

                out.add(new VenueListing(VENUE, KIND_SPOT, id, base, quote, priceScale, qtyScale, tradeStatus));
            }
            return out.stream().distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Gate spot discovery failed: " + url, e);
        }
    }

    @Override
    public List<VenueListing> listPerpUsdt() {
        // futures usdt contracts
        String url = restBase + "/api/v4/futures/usdt/contracts";
        try {
            var req = HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofSeconds(15)).build();
            var resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) throw new IllegalStateException("HTTP " + resp.statusCode());

            JsonNode arr = MAPPER.readTree(resp.body());
            if (arr == null || !arr.isArray()) return List.of();

            var out = new ArrayList<VenueListing>(arr.size());
            for (var n : arr) {
                String name = n.path("name").asText(n.path("contract").asText("")); // разные схемы в доках/реальных ответах
                if (name == null || name.isEmpty()) continue;
                String id = name.toUpperCase(); // формат BTC_USDT

                if (!id.endsWith("_USDT")) continue;

                // Эвристика шкал
                String orderPriceRound = n.path("order_price_round").asText(null);
                String orderSizeRound = n.path("order_size_round").asText(null);
                String orderSizeMin   = n.path("order_size_min").asText(null);

                int priceScale = decimalsOf(orderPriceRound);
                int qtyScale = (orderSizeRound != null) ? decimalsOf(orderSizeRound) : decimalsOf(orderSizeMin);

                // Статус: полей «tradable» нет, берём in_delisting=false как признак активности (если поле есть)
                String status = n.has("in_delisting") && n.get("in_delisting").asBoolean() ? "UNTRADABLE" : "TRADING";

                // base/quote
                String[] parts = id.split("_", 2);
                String base = parts[0];
                String quote = parts.length > 1 ? parts[1] : "USDT";

                out.add(new VenueListing(VENUE, KIND_PERP, id, base, quote, priceScale, qtyScale, status));
            }
            return out.stream().distinct()
                    .sorted(Comparator.comparing(v -> v.nativeSymbol))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new RuntimeException("Gate futures discovery failed: " + url, e);
        }
    }
}
