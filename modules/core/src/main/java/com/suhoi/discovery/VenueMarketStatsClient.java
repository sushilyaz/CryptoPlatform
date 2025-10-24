package com.suhoi.discovery;

import java.util.Collection;
import java.util.Map;

/** Клиент 24h-метрик для конкретной площадки (venue). */
public interface VenueMarketStatsClient extends AutoCloseable {
    String venue();
    Map<MarketRef, MarketStats> fetch(Collection<MarketRef> markets);
    @Override default void close() {}
}

