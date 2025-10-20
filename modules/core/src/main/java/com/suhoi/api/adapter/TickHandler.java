package com.suhoi.api.adapter;

import com.suhoi.events.Tick;

/**
 * Callback единообразной обработки нормализованных тикков книжки.
 * Реализация не должна блокировать долго (иначе backpressure).
 */
@FunctionalInterface
public interface TickHandler {
    /**
     * Обработка одного Tick (top-of-book mid и/или bid/ask).
     * @param tick нормализованное событие из адаптера
     */
    void onTick(Tick tick);
}

