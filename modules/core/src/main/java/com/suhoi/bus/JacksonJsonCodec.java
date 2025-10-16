package com.suhoi.bus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhoi.config.ObjectMapperFactory;

/** Jackson-обёртка под EventBus.JsonCodec. */
public final class JacksonJsonCodec implements EventBus.JsonCodec {
    private final ObjectMapper om = ObjectMapperFactory.get();

    @Override public byte[] toBytes(Object value) {
        try { return om.writeValueAsBytes(value); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
    @Override public <T> T fromBytes(byte[] bytes, Class<T> type) {
        try { return om.readValue(bytes, type); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
}
