package com.suhoi.adapters.dexscreener;

import com.suhoi.api.adapter.StreamSubscription;
import com.suhoi.api.adapter.TickHandler;
import com.suhoi.events.Tick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Онлайн smoke: поллим первый найденный SOL/USDT и ждём хотя бы один Tick.
 */
class DexscreenerPollingSmokeTest {

    private DexscreenerAdapter adapter;
    private StreamSubscription sub;

    @AfterEach
    void tearDown() {
        try { if (sub != null) sub.close(); } catch (Exception ignored) {}
        try { if (adapter != null) adapter.close(); } catch (Exception ignored) {}
    }

    @Test
    void receivesAtLeastOneTick_SOL_USDT() throws Exception {
        adapter = new DexscreenerAdapter();
        var discovery = (DexscreenerDiscoveryClient) adapter.discovery();
        var pools = discovery.searchBestUsdtPools("SOL");
        assertFalse(pools.isEmpty(), "need at least one SOL/USDT pool");

        var first = pools.get(0).nativeSymbol; // chainId:pairAddress
        var latch = new CountDownLatch(1);

        TickHandler handler = (Tick t) -> {
            if (!"DEXSCREENER".equals(t.venue())) return;
            if (!"DEX".equals(t.kind())) return;
            if (!"SOL".equalsIgnoreCase(t.asset())) return;
            if (t.mid() == null) return;
            latch.countDown();
            System.out.println(t);
        };

        sub = adapter.spotStream().subscribeBookTicker(List.of(first), handler);

        assertTrue(latch.await(20, TimeUnit.SECONDS), "Should receive at least one mid within 20s");
    }
}
