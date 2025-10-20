package com.suhoi.adapters.mexc;


import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import com.suhoi.api.adapter.StreamClient;

/**
 * Композитный адаптер MEXC: discovery + потоковые клиенты (SPOT и PERP).
 */
public final class MexcAdapter implements ExchangeAdapter, AutoCloseable {
    private final MexcDiscoveryClient discovery = new MexcDiscoveryClient();
    private final MexcSpotPbStreamClient spot = new MexcSpotPbStreamClient();
    private final MexcFuturesStreamClient perp = new MexcFuturesStreamClient();

    @Override public String venue() { return "MEXC"; }
    @Override public DiscoveryClient discovery() { return discovery; }
    @Override public StreamClient spotStream() { return spot; }
    @Override public StreamClient perpStream() { return perp; }

    @Override public void close() {
        try { spot.close(); } catch (Exception ignored) {}
        try { perp.close(); } catch (Exception ignored) {}
    }
}
