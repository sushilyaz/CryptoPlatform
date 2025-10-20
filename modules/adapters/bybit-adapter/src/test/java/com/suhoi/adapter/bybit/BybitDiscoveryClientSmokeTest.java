package com.suhoi.adapter.bybit;


import com.suhoi.adapters.bybit.BybitDiscoveryClient;
import com.suhoi.api.adapter.VenueListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Smoke для REST discovery Bybit v5. */

class BybitDiscoveryClientSmokeTest {
    @Test
    void spotContainsBTCUSDT() {
        var d = new BybitDiscoveryClient();
        List<VenueListing> list = d.listSpotUsdt();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(v -> "BTCUSDT".equalsIgnoreCase(v.nativeSymbol)));
    }
    @Test
    void perpContainsBTCUSDT() {
        var d = new BybitDiscoveryClient();
        List<VenueListing> list = d.listPerpUsdt();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(v -> "BTCUSDT".equalsIgnoreCase(v.nativeSymbol)));
    }
}

