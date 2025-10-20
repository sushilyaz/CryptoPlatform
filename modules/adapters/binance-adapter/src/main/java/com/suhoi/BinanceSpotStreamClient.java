package com.suhoi;

/**
 * Spot WS streams клиент. <br>
 * Базовый эндпоинт комбинированных потоков: wss://stream.binance.com/stream
 */
public final class BinanceSpotStreamClient extends AbstractBinanceWsClient {
    public BinanceSpotStreamClient() {
        super("BINANCE", "SPOT", "wss://stream.binance.com/stream");
    }
}

