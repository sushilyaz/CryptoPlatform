package com.suhoi.apiservice.echo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhoi.bus.EventBus;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * REST-эндпоинт для smoke-публикации в NATS subject {@code control.echo}.
 * <p>GET/POST /api/echo публикует сообщение и возвращает короткий ACK.</p>
 */
@RestController
@RequestMapping(path = "/api/echo", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
public class EchoController {

    private final EventBus bus;
    private final ObjectMapper om;
    private final String appName;

    public EchoController(EventBus bus,
                          ObjectMapper objectMapper,
                          @Value("${spring.application.name:api-service}") String appName) {
        this.bus = bus;
        this.om = objectMapper;
        this.appName = appName;
    }

    /**
     * Публикует текстовое сообщение в {@code control.echo}.
     *
     * @param msg произвольная строка, которую нужно «пропихнуть» по шине.
     * @return ACK с id публикации и echo subject.
     */
    @GetMapping
    public Map<String, Object> echoGet(@RequestParam("msg") @NotBlank String msg) throws Exception {
        return doPublish(msg);
    }

    /**
     * Вариант через POST (на случай длинных строк).
     */
    @PostMapping(consumes = MediaType.TEXT_PLAIN_VALUE)
    public Map<String, Object> echoPost(@RequestBody @NotBlank String body) throws Exception {
        return doPublish(body);
    }

    private Map<String, Object> doPublish(String message) throws Exception {
        String id = UUID.randomUUID().toString();
        EchoMessage payload = new EchoMessage(id, 0L, appName, message);
        bus.publish(EchoSubscriber.SUBJECT, om.writeValueAsBytes(payload));
        return Map.of(
                "status", "published",
                "subject", EchoSubscriber.SUBJECT,
                "id", id,
                "sender", appName,
                "message", message
        );
    }
}

