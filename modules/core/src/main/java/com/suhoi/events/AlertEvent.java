package com.suhoi.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Событие алерта на раскор — открытие/закрытие.
 * <p>Алгоритм: dev = (price - fair - bias) / fair; порог по абсолютному значению.</p>
 *
 * <h3>Поля</h3>
 * <ul>
 *   <li><b>ts</b> — момент генерации события (UTC).</li>
 *   <li><b>asset</b> — инструмент (BASE, напр. BTC).</li>
 *   <li><b>marketId</b> — рынок, на котором обнаружен раскор (FK в markets).</li>
 *   <li><b>devPct</b> — отклонение в долях (0.03 = 3%).</li>
 *   <li><b>price</b> — текущая цена рынка в момент алерта.</li>
 *   <li><b>fair</b> — справедливая цена для инструмента в этот момент.</li>
 *   <li><b>bias</b> — текущий устойчивый сдвиг рынка относительно fair (EWMA).</li>
 *   <li><b>thresholdPct</b> — порог срабатывания (в долях), записанный для прозрачности.</li>
 *   <li><b>state</b> — "OPEN" или "CLOSE".</li>
 * </ul>
 *
 * <h3>Пример JSON</h3>
 * <pre>{@code
 * {
 *   "ts":"2025-10-16T18:01:24.123Z",
 *   "asset":"BTC",
 *   "marketId":"101",
 *   "devPct":"0.0342",
 *   "price":"60020.0",
 *   "fair":"62020.0",
 *   "bias":"-50.0",
 *   "thresholdPct":"0.03",
 *   "state":"OPEN"
 * }
 * }</pre>
 */
public record AlertEvent(
        Instant ts,
        String asset,
        String marketId,
        BigDecimal devPct,
        BigDecimal price,
        BigDecimal fair,
        BigDecimal bias,
        BigDecimal thresholdPct,
        String state // OPEN | CLOSE
) {}
