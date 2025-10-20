package com.suhoi.adapters.bitget;

/** USDT-FUTURES WS клиент для канала "ticker". */
public final class BitgetPerpTickerStreamClient extends AbstractBitgetTickerWsClient {
    public BitgetPerpTickerStreamClient() {
        super("BITGET", "PERP", "USDT-FUTURES", "wss://ws.bitget.com/v2/ws/public");
    }
}

