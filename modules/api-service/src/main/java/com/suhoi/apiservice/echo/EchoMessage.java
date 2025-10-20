package com.suhoi.apiservice.echo;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Objects;

/**
 * Сообщение для smoke-канала control.echo.
 * <ul>
 *   <li>{@code id} — произвольный идентификатор запроса (UUID/nonce) для корреляции в логах.</li>
 *   <li>{@code ts} — момент формирования сообщения (UTC, epoch millis).</li>
 *   <li>{@code sender} — имя приложения-отправителя (например, spring.application.name).</li>
 *   <li>{@code message} — полезная нагрузка (строка) для эхо.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class EchoMessage {
    private final String id;
    private final long ts;
    private final String sender;
    private final String message;

    public EchoMessage(String id, long ts, String sender, String message) {
        this.id = Objects.requireNonNull(id, "id");
        this.ts = ts == 0 ? Instant.now().toEpochMilli() : ts;
        this.sender = Objects.requireNonNull(sender, "sender");
        this.message = Objects.requireNonNull(message, "message");
    }

    public String getId() { return id; }
    public long getTs() { return ts; }
    public String getSender() { return sender; }
    public String getMessage() { return message; }

    @Override public String toString() {
        return "EchoMessage{id='%s', ts=%d, sender='%s', message='%s'}"
                .formatted(id, ts, sender, message);
    }
}

