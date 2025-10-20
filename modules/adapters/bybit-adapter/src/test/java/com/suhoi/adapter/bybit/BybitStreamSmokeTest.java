package com.suhoi.adapter.bybit;

import com.suhoi.adapters.bybit.BybitAdapter;
import com.suhoi.api.adapter.StreamSubscription;
import com.suhoi.api.adapter.TickHandler;
import com.suhoi.events.Tick;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke для WS-стриминга Bybit v5 (SPOT и PERP=linear).
 * Онлайн-тест, нужен интернет.
 */
class BybitStreamSmokeTest {

    private BybitAdapter adapter;
    private StreamSubscription spotSub;
    private StreamSubscription perpSub;

    @AfterEach
    void tearDown() {
        try { if (spotSub != null) spotSub.close(); } catch (Exception ignored) {}
        try { if (perpSub != null) perpSub.close(); } catch (Exception ignored) {}
        try { if (adapter != null) adapter.close(); } catch (Exception ignored) {}
    }

    @Test
    void receivesAtLeastOneTickForSpotAndPerp_BTCUSDT() throws Exception {
        adapter = new BybitAdapter();

        var spotLatch = new CountDownLatch(1);
        var perpLatch = new CountDownLatch(1);
        var gotSpot = new AtomicBoolean(false);
        var gotPerp = new AtomicBoolean(false);

        TickHandler handler = (Tick t) -> {
            if (!"BYBIT".equals(t.venue())) return;
            if (!"BTC".equalsIgnoreCase(t.asset())) return;
            if (t.mid() == null) return;

            if ("SPOT".equalsIgnoreCase(t.kind()) && gotSpot.compareAndSet(false, true)) spotLatch.countDown();
            if ("PERP".equalsIgnoreCase(t.kind()) && gotPerp.compareAndSet(false, true)) perpLatch.countDown();
            System.out.println(t);
        };

        // SPOT → orderbook.1.*, PERP → tickers.*
        spotSub = adapter.spotStream().subscribeBookTicker(List.of("BTCUSDT"), handler);
        perpSub = adapter.perpStream().subscribeBookTicker(List.of("BTCUSDT"), handler);

        boolean spotOk = spotLatch.await(20, TimeUnit.SECONDS);
        boolean perpOk = perpLatch.await(20, TimeUnit.SECONDS);

        assertTrue(spotOk, "Should receive SPOT tick within 20s");
        assertTrue(perpOk, "Should receive PERP tick within 20s");
    }
}


