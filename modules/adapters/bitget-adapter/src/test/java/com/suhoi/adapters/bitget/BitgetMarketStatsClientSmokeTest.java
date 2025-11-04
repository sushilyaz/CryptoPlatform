package com.suhoi.adapters.bitget;

import com.suhoi.discovery.MarketRef;
import com.suhoi.discovery.MarketStats;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("smoke")
class BitgetMarketStatsClientSmokeTest {

    @Test
    void fetch_spot_and_perp_BTCUSDT_havePositiveVolume() {
        assumeOnline();

        var client = new BitgetMarketStatsClient();

        var spot = new MarketRef("BTC", "BITGET", "SPOT", "BTCUSDT");
        var perp = new MarketRef("BTC", "BITGET", "PERP", "BTCUSDT");

        Map<MarketRef, MarketStats> got = client.fetch(List.of(spot, perp));

        assertTrue(got.containsKey(spot), "missing SPOT");
        assertTrue(got.containsKey(perp), "missing PERP");

        assertPositiveUsd(got.get(spot).vol24hUsd(), "spot.vol24hUsd");
        assertPositiveUsd(got.get(perp).vol24hUsd(), "perp.vol24hUsd");
    }

    private static void assumeOnline() {
        if ("true".equalsIgnoreCase(System.getenv("CI_OFFLINE"))) {
            org.junit.jupiter.api.Assumptions.abort("offline mode");
        }
    }

    private static void assertPositiveUsd(BigDecimal v, String name) {
        assertNotNull(v, name + " is null");
        assertTrue(v.compareTo(BigDecimal.ZERO) > 0, name + " must be > 0 but was " + v);
    }
}

