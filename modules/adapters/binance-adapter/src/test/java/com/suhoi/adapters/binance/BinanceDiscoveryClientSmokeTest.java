package com.suhoi.adapters.binance;

import com.suhoi.api.adapter.VenueListing;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke для REST discovery Binance.
 * Тест онлайн, требует интернет.
 */
class BinanceDiscoveryClientSmokeTest {

    @Test
    void spotAndPerpContainBTCUSDT() {
        var client = new BinanceDiscoveryClient();

        List<VenueListing> spot = client.listSpotUsdt();
        List<VenueListing> perp = client.listPerpUsdt();

        assertFalse(spot.isEmpty(), "SPOT listings should not be empty");
        assertFalse(perp.isEmpty(), "PERP listings should not be empty");

        assertTrue(spot.stream().anyMatch(v -> "BTCUSDT".equalsIgnoreCase(v.nativeSymbol)),
                "SPOT should contain BTCUSDT");
        assertTrue(perp.stream().anyMatch(v -> "BTCUSDT".equalsIgnoreCase(v.nativeSymbol)),
                "PERP should contain BTCUSDT");

        // базовые инварианты MVP
        assertTrue(spot.stream().allMatch(v -> "USDT".equalsIgnoreCase(v.quote)));
        assertTrue(perp.stream().allMatch(v -> "USDT".equalsIgnoreCase(v.quote)));
    }
}
