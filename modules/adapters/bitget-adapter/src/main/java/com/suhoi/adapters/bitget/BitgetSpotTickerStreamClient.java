package com.suhoi.adapters.bitget;

/** SPOT WS клиент для канала "ticker". */
public final class BitgetSpotTickerStreamClient extends AbstractBitgetTickerWsClient {
    public BitgetSpotTickerStreamClient() {
        super("BITGET", "SPOT", "SPOT", "wss://ws.bitget.com/v2/ws/public");
    }
}

