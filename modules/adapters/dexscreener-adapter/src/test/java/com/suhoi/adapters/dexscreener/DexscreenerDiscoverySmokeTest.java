package com.suhoi.adapters.dexscreener;

import com.suhoi.api.adapter.VenueListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Онлайн smoke: ищем валидные пулы SOL/USDT на DEX (через DexScreener Search).
 */
class DexscreenerDiscoverySmokeTest {

    @Test
    void findBestPools_SOL_USDT() {
        var d = new DexscreenerDiscoveryClient();
        List<VenueListing> list = d.searchBestUsdtPools("SOL");
        assertNotNull(list);
        assertFalse(list.isEmpty(), "expected at least one SOL/USDT pool on DEX");
        // Проверим форматы
        var v = list.get(0);
        assertEquals("DEXSCREENER", v.venue);
        assertEquals("DEX", v.kind);
        assertTrue(v.nativeSymbol.contains(":"), "nativeSymbol must be chainId:pairAddress");
    }
}
