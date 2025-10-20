package com.suhoi.adapters.binance;


import com.suhoi.api.adapter.DiscoveryClient;
import com.suhoi.api.adapter.ExchangeAdapter;
import com.suhoi.api.adapter.StreamClient;

/**
 * Композит для Binance: discovery + два потоковых клиента (SPOT и PERP).
 * Использование:
 *  var adapter = new BinanceAdapter();
 *  var spot = adapter.spotStream().subscribeBookTicker(List.of("BTCUSDT", "ETHUSDT"), handler);
 *  ...
 *  spot.close(); adapter.close();
 */
public final class BinanceAdapter implements ExchangeAdapter, AutoCloseable {
    private final BinanceDiscoveryClient discovery = new BinanceDiscoveryClient();
    private final BinanceSpotStreamClient spot = new BinanceSpotStreamClient();
    private final BinanceFuturesStreamClient perp = new BinanceFuturesStreamClient();

    @Override public String venue() { return "BINANCE"; }
    @Override public DiscoveryClient discovery() { return discovery; }
    @Override public StreamClient spotStream() { return spot; }
    @Override public StreamClient perpStream() { return perp; }

    @Override public void close() {
        spot.close();
        perp.close();
    }
}

