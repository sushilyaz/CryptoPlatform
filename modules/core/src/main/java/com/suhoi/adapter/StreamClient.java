package com.suhoi.adapter;


import java.util.Collection;

/**
 * Потоковый клиент площадки для bookTicker (лучшие bid/ask ~реалтайм).
 * Имплементации могут использовать комбинированные WS-потоки для множества символов.
 */
public interface StreamClient extends AutoCloseable {
    /**
     * Подписка на множество нативных символов (lowercase/uppercase зависит от venue).
     * Возвращает дескриптор для управления жизненным циклом подписки.
     */
    StreamSubscription subscribeBookTicker(Collection<String> nativeSymbols, TickHandler handler);

    /**
     * Закрывает все активные подписки и ресурсы клиента (если ещё не закрыты).
     */
    @Override
    void close();
}

