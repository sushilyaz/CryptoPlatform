package com.suhoi.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Нормализованный рыночный тик для одного инструмента и конкретного рынка.
 * <p>Содержит «среднюю» цену {@code mid}, глубину до $50 {@code depthUsd50} и служебные поля.</p>
 *
 * <h3>Поля</h3>
 * <ul>
 *   <li><b>ts</b> — момент получения данных (UTC). Используется как «время тика».</li>
 *   <li><b>asset</b> — каноническая база инструмента (например, {@code BTC}). Всегда против {@code USDT} в MVP.</li>
 *   <li><b>venue</b> — код площадки (BINANCE/BYBIT/...). См. таблицу {@code venues}.</li>
 *   <li><b>kind</b> — тип рынка: SPOT | PERP | FUTURES | DEX.</li>
 *   <li><b>bid</b>/<b>ask</b> — лучшие цены стакана, если доступны из источника.</li>
 *   <li><b>mid</b> — средняя цена {@code (bid+ask)/2}. Если один из спредов недоступен, может быть равна лучшей доступной котировке.</li>
 *   <li><b>depthUsd50</b> — оценка глубины стакана в долларах до $50 нотионала (для весов FAIR). Может быть null, если источник не дал depth.</li>
 *   <li><b>heartbeatTs</b> — последний «пульс» источника по этому рынку (для детекта живости).</li>
 *   <li><b>marketId</b> — FK в таблицу {@code markets} (внутренний идентификатор рынка).</li>
 *   <li><b>nativeSymbol</b> — нативный символ биржи (например, {@code BTCUSDT}, {@code BTCUSDT_PERP}).</li>
 * </ul>
 *
 * <h3>Пример JSON</h3>
 * <pre>{@code
 * {
 *   "ts":"2025-10-16T18:01:23.456Z",
 *   "asset":"BTC",
 *   "venue":"BINANCE",
 *   "kind":"PERP",
 *   "bid":"60123.4",
 *   "ask":"60123.6",
 *   "mid":"60123.5",
 *   "depth_usd50":"48.7",
 *   "heartbeat_ts":"2025-10-16T18:01:23.400Z",
 *   "market_id":"123",
 *   "native_symbol":"BTCUSDT"
 * }
 * }</pre>
 */
public record Tick(
        Instant ts,
        String asset,
        String venue,
        String kind,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal mid,
        BigDecimal depthUsd50,
        Instant heartbeatTs,
        String marketId,
        String nativeSymbol
) {}
