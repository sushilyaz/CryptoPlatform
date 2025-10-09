package com.suhoi.bus;

/**
 * Минимальная абстракция pub/sub.
 * Реализацию (NATS) добавим на этапе A4.
 */
public interface EventBus extends AutoCloseable {

    void publish(String subject, byte[] payload);

    Subscription subscribe(String subject, MessageHandler handler);

    default <T> void publishJson(String subject, T payload, JsonCodec codec) {
        publish(subject, codec.toBytes(payload));
    }

    interface JsonCodec {
        byte[] toBytes(Object value);
        <T> T fromBytes(byte[] bytes, Class<T> type);
    }

    @Override
    void close();
}
