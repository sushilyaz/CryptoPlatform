package com.suhoi.bus;

/**
 * Хэндл подписки.
 */
public interface Subscription extends AutoCloseable {
    @Override
    void close();
}
