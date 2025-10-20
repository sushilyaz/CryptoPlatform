package com.suhoi.adapters.mexc;

import com.suhoi.api.adapter.VenueListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MexcDiscoveryClientTest {

    @Test
    void spot_exchangeInfo_containsUsdtPairs() {
        var dc = new MexcDiscoveryClient();
        List<VenueListing> spot = dc.listSpotUsdt();

        assertNotNull(spot);
        assertTrue(spot.stream().anyMatch(v -> "BTCUSDT".equalsIgnoreCase(v.nativeSymbol)),
                "MEXC SPOT should include BTCUSDT");
        assertTrue(spot.stream().allMatch(v -> "USDT".equalsIgnoreCase(v.quote)));
    }

    @Test
    void perp_contractDetail_containsUsdtPerps() {
        var dc = new MexcDiscoveryClient();
        List<VenueListing> perps = dc.listPerpUsdt();

        assertNotNull(perps);
        assertTrue(perps.stream().anyMatch(v -> "BTC_USDT".equalsIgnoreCase(v.nativeSymbol)),
                "MEXC PERP should include BTC_USDT");
        assertTrue(perps.stream().allMatch(v -> "USDT".equalsIgnoreCase(v.quote)));
    }
}
