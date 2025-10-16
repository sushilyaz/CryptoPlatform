package com.suhoi.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Секундный снапшот справедливой цены по инструменту.
 * <p>Рассчитан по «живым» рынкам, с весами от ликвидности/качества.</p>
 *
 * <h3>Поля</h3>
 * <ul>
 *   <li><b>ts</b> — метка секунды (UTC), к которой относится расчёт.</li>
 *   <li><b>asset</b> — инструмент (BASE, напр. BTC).</li>
 *   <li><b>fair</b> — справедливая цена за секунду.</li>
 *   <li><b>sources</b> — вклад рынков в расчёт: marketId → mid → weight.</li>
 * </ul>
 *
 * <h3>Пример JSON</h3>
 * <pre>{@code
 * {
 *   "ts":"2025-10-16T18:01:24Z",
 *   "asset":"BTC",
 *   "fair":"60123.52",
 *   "sources":[
 *     {"marketId":"101", "mid":"60123.5", "weight":"0.62"},
 *     {"marketId":"202", "mid":"60123.6", "weight":"0.38"}
 *   ]
 * }
 * }</pre>
 */
public record FairSnap(
        Instant ts,
        String asset,
        BigDecimal fair,
        List<Source> sources
) {
    /** Вклад конкретного рынка в FAIR: его mid и вес. */
    public record Source(String marketId, BigDecimal mid, BigDecimal weight) {}
}
