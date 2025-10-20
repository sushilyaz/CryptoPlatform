package com.suhoi.adapters.binance;

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
 * Smoke для WS-стриминга Binance (SPOT и PERP).
 * Тест онлайн, требует интернет.
 */
class BinanceStreamingSmokeTest {

    private BinanceAdapter adapter;
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
        adapter = new BinanceAdapter();

        var spotLatch = new CountDownLatch(1);
        var perpLatch = new CountDownLatch(1);
        var gotSpot = new AtomicBoolean(false);
        var gotPerp = new AtomicBoolean(false);

        var handler = (TickHandler) (Tick t) -> {
            // адаптер выдаёт Tick согласно твоему core: asset=BASE, venue/kind, BigDecimal/Instant
            if (!"BINANCE".equals(t.venue())) return;
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

        perpSub = adapter.perpStream().subscribeBookTicker(List.of("BTCUSDT"), handler);
        spotSub = adapter.spotStream().subscribeBookTicker(List.of("BTCUSDT"), handler);

        boolean spotOk = spotLatch.await(15, TimeUnit.SECONDS);
        boolean perpOk = perpLatch.await(15, TimeUnit.SECONDS);

        assertTrue(spotOk, "Should receive SPOT tick within 15s");
        assertTrue(perpOk, "Should receive PERP tick within 15s");
    }
}
