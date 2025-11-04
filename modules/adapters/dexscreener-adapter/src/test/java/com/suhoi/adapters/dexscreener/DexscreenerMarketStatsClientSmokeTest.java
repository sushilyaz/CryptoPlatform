package com.suhoi.adapters.dexscreener;

import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.VenueListing;
import com.suhoi.discovery.MarketRef;
import com.suhoi.discovery.MarketStats;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("smoke")
class DexscreenerMarketStatsClientSmokeTest {

    @Test
    void fetch_statsForRealUsdtPool_foundViaDiscovery_hasPositiveVolAndLiquidity() {
        assumeOnline();

        DiscoveryClient discovery = new DexscreenerDiscoveryClient();
        // Пытаемся найти ликвидный USDT-пул для одного из популярных активов:
        List<String> candidates = List.of("BTC", "ETH", "SOL", "BNB", "XRP", "DOGE");

        Optional<VenueListing> maybe = candidates.stream()
                .flatMap(sym -> safeSearch((DexscreenerDiscoveryClient) discovery, sym).stream())
                .findFirst();

        VenueListing picked = maybe.orElseThrow(() ->
                new AssertionError("Dexscreener discovery returned no suitable USDT pools for "
                        + candidates));

        assertEquals("DEX", picked.kind, "kind must be DEX");
        assertEquals("DEXSCREENER", picked.venue, "venue must be DEXSCREENER");
        assertEquals("USDT", picked.quote.toUpperCase(Locale.ROOT), "quote must be USDT");
        assertNotNull(picked.nativeSymbol, "nativeSymbol should be 'chainId:pairAddress'");

        var statsClient = new DexscreenerMarketStatsClient();
        var ref = new MarketRef(picked.base, picked.venue, picked.kind, picked.nativeSymbol);

        Map<MarketRef, MarketStats> got = statsClient.fetch(List.of(ref));
        assertTrue(got.containsKey(ref), "stats not returned for " + ref);

        MarketStats st = got.get(ref);
        assertPositiveUsd(st.vol24hUsd(), "vol24hUsd");
        assertPositiveUsd(st.liquidityUsd(), "liquidityUsd");
    }

    // -------- helpers --------

    /** В офлайне/на CI можно отключить реальный вызов: CI_OFFLINE=true */
    private static void assumeOnline() {
        if ("true".equalsIgnoreCase(System.getenv("CI_OFFLINE"))) {
            org.junit.jupiter.api.Assumptions.abort("offline mode");
        }
    }

    private static List<VenueListing> safeSearch(DexscreenerDiscoveryClient c, String baseUpper) {
        try {
            // Возвращает уже отфильтрованные по ликвидности/объёму/возрасту пулы
            return c.searchBestUsdtPools(baseUpper);
        } catch (Exception e) {
            return List.of();
        }
    }

    private static void assertPositiveUsd(BigDecimal v, String name) {
        assertNotNull(v, name + " is null");
        assertTrue(v.compareTo(BigDecimal.ZERO) > 0, name + " must be > 0 but was " + v);
    }
}
