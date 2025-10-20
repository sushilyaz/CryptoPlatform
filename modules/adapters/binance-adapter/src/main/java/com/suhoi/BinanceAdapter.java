package com.suhoi;


import com.suhoi.adapter.DiscoveryClient;
import com.suhoi.adapter.ExchangeAdapter;
import com.suhoi.adapter.StreamClient;

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

