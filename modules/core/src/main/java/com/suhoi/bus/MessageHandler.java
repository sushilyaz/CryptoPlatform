package com.suhoi.bus;

/**
 * Колбек обработки сообщения.
 */
@FunctionalInterface
public interface MessageHandler {
    void onMessage(String subject, byte[] payload);
}

