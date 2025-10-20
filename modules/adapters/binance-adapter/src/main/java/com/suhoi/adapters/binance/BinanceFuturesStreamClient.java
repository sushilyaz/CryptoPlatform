package com.suhoi.adapters.binance;

/**
 * Futures (USDT-M) WS streams клиент.
 * Базовый эндпоинт комбинированных потоков: wss://fstream.binance.com/stream :contentReference[oaicite:12]{index=12}
 */
public final class BinanceFuturesStreamClient extends AbstractBinanceWsClient {
    public BinanceFuturesStreamClient() {
        super("BINANCE", "PERP", "wss://fstream.binance.com/stream");
    }
}

