package com.suhoi.adapters.mexc;

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
 * Онлайн-дымовый тест WS MEXC (требуется интернет).
 */
class MexcStreamingSmokeTest {

    private MexcAdapter adapter;
    private StreamSubscription spotSub;
    private StreamSubscription perpSub;

    @AfterEach
    void tearDown() {
        try { if (spotSub != null) spotSub.close(); } catch (Exception ignored) {}
        try { if (perpSub != null) perpSub.close(); } catch (Exception ignored) {}
        try { if (adapter != null) adapter.close(); } catch (Exception ignored) {}
    }

    @Test
    void receivesAtLeastOneTick_SpotAndPerp_BTC() throws Exception {
        adapter = new MexcAdapter();

        var spotLatch = new CountDownLatch(1);
        var perpLatch = new CountDownLatch(1);
        var gotSpot = new AtomicBoolean(false);
        var gotPerp = new AtomicBoolean(false);

        TickHandler handler = (Tick t) -> {
            if (!"MEXC".equals(t.venue())) return;
            if (!"BTC".equalsIgnoreCase(t.asset())) return;
            if (t.mid() == null) return;

            if ("SPOT".equalsIgnoreCase(t.kind()) && gotSpot.compareAndSet(false, true)) {
                spotLatch.countDown();
            }
            if ("PERP".equalsIgnoreCase(t.kind()) && gotPerp.compareAndSet(false, true)) {
                perpLatch.countDown();
            }
            System.out.println(t);
        };

        // SPOT: BTCUSDT; PERP: BTC_USDT
        spotSub = adapter.spotStream().subscribeBookTicker(List.of("BTCUSDT"), handler);
        perpSub = adapter.perpStream().subscribeBookTicker(List.of("BTC_USDT"), handler);

        assertTrue(spotLatch.await(20, TimeUnit.SECONDS), "Should receive SPOT tick within 20s");
        assertTrue(perpLatch.await(20, TimeUnit.SECONDS), "Should receive PERP tick within 20s");
    }
}
