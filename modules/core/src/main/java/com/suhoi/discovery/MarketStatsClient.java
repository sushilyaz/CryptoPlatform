package com.suhoi.discovery;

import java.util.Collection;
import java.util.Map;

/**
 * Унифицированный клиент получения 24h-метрик (объёмов) по наборам рынков. <br>
 * Внутри может быть композит, который маршрутизирует по venue к конкретной реализации.<br>
 */
public interface MarketStatsClient {
    Map<MarketRef, MarketStats> fetch24hStats(Collection<MarketRef> markets);
}
