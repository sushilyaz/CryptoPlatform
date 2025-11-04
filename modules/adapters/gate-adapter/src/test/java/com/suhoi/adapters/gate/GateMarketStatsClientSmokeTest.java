package com.suhoi.adapters.gate;

import com.suhoi.discovery.MarketRef;
import com.suhoi.discovery.MarketStats;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@Tag("smoke")
class GateMarketStatsClientSmokeTest {

    @Test
    void fetch_spot_and_perp_BTCUSDT_havePositiveVolume() {
        assumeOnline();

        var client = new GateMarketStatsClient();

        var spotA = new MarketRef("BTC", "GATE", "SPOT", "BTC_USDT");
        var spotB = new MarketRef("BTC", "GATE", "SPOT", "BTCUSDT");
        var perpA = new MarketRef("BTC", "GATE", "PERP", "BTC_USDT");
        var perpB = new MarketRef("BTC", "GATE", "PERP", "BTCUSDT");

        Map<MarketRef, MarketStats> got = client.fetch(List.of(spotA, spotB, perpA, perpB));

        MarketStats spot = firstPresent(got, spotA, spotB);
        MarketStats perp = firstPresent(got, perpA, perpB);

        assertNotNull(spot, "missing SPOT stat");
        assertNotNull(perp, "missing PERP stat");

        assertPositiveUsd(spot.vol24hUsd(), "spot.vol24hUsd");
        assertPositiveUsd(perp.vol24hUsd(), "perp.vol24hUsd");
    }

    private static MarketStats firstPresent(Map<MarketRef, MarketStats> m, MarketRef... keys) {
        for (MarketRef k : keys) {
            MarketStats v = m.get(k);
            if (v != null) return v;
        }
        return null;
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

