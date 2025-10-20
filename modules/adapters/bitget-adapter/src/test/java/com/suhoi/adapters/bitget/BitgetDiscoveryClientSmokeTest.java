package com.suhoi.adapters.bitget;

import com.suhoi.api.adapter.VenueListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BitgetDiscoveryClientSmokeTest {

    @Test
    void spotContainsBTCUSDT() {
        var d = new BitgetDiscoveryClient();
        List<VenueListing> list = d.listSpotUsdt();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(v -> "BTCUSDT".equalsIgnoreCase(v.nativeSymbol)));
    }

    @Test
    void perpContainsBTCUSDT() {
        var d = new BitgetDiscoveryClient();
        List<VenueListing> list = d.listPerpUsdt();
        assertFalse(list.isEmpty());
        assertTrue(list.stream().anyMatch(v -> "BTCUSDT".equalsIgnoreCase(v.nativeSymbol)));
    }
}

