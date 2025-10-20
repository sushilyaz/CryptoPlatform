package com.suhoi.adapter;


/**
 * Жизненный цикл конкретной подписки на поток (может включать несколько символов).
 * Важно уметь закрыть подписку независимо от клиента.
 */
public interface StreamSubscription extends AutoCloseable {
    /**
     * Закрывает WS/пуллинг и освобождает ресурсы.
     * Должен быть идемпотентным.
     */
    @Override
    void close();
}

