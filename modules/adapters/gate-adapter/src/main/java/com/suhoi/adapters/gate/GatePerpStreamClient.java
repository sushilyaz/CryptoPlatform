package com.suhoi.adapters.gate;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhoi.events.Tick;

import java.math.BigDecimal;
import java.time.Instant;

import static com.suhoi.adapters.gate.GateSymbols.extractBaseOrThrow;

/**
 * Futures (USDT-perp) WS клиент Gate: канал {@code futures.book_ticker}
 */
public final class GatePerpStreamClient extends AbstractGateWsClient {
    public GatePerpStreamClient() {
        super("GATE", "PERP", "wss://fx-ws.gateio.ws/v4/ws/usdt", "futures.book_ticker");
    }

    @Override
    protected Tick parseTick(JsonNode result, String venue, String kind) {
        String symbol = result.path("s").asText("").toUpperCase();
        if (!symbol.endsWith("_USDT")) return null;

        String bStr = result.path("b").asText(result.path("best_bid").asText(null));
        String aStr = result.path("a").asText(result.path("best_ask").asText(null));
        if (bStr == null || aStr == null) return null;

        var bid = new BigDecimal(bStr);
        var ask = new BigDecimal(aStr);
        var mid = bid.add(ask).divide(java.math.BigDecimal.valueOf(2));

        long t = result.path("t").asLong(System.currentTimeMillis());
        Instant ts = (String.valueOf(t).length() > 10) ? Instant.ofEpochMilli(t) : Instant.ofEpochSecond(t);

        return new com.suhoi.events.Tick(
                ts,
                extractBaseOrThrow(symbol),
                venue,
                kind,
                bid, ask, mid,
                null,
                ts,
                null,
                symbol
        );
    }
}
