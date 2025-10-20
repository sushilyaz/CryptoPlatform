package com.suhoi.api.adapter;

/**
 * Композит для конкретной биржи.
 * Позволяет получить discovery и два потоковых клиента: SPOT и PERP.
 */
public interface ExchangeAdapter {
    String venue(); // например, "BINANCE"

    DiscoveryClient discovery();

    StreamClient spotStream();

    StreamClient perpStream();
}

