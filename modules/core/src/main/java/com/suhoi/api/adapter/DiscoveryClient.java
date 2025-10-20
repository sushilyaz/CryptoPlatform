package com.suhoi.api.adapter;


import java.util.List;

/**
 * REST discovery поверх площадки: сбор USDT-спотов и USDT-M перпетуалов.
 * Клиент обязан нормализовать символы к BASE/USDT.
 */
public interface DiscoveryClient {
    /**
     * @return листинги SPOT/USDT в статусе TRADING (или эквивалент).
     */
    List<VenueListing> listSpotUsdt();

    /**
     * @return листинги USDT-M perpetual (перпетуалы/фьючи в статусе TRADING).
     */
    List<VenueListing> listPerpUsdt();
}

