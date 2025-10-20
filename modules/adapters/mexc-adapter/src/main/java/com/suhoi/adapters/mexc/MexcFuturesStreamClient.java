package com.suhoi.adapters.mexc;

/** PERP (USDT-M) WS клиент для MEXC. */
public final class MexcFuturesStreamClient extends AbstractMexcFuturesWsClient {
    public MexcFuturesStreamClient() {
        super("wss://contract.mexc.com/edge");
    }
}
