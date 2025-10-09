package com.suhoi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Централизованный ObjectMapper: snake_case, JavaTime, ISO-8601.
 */
public final class ObjectMapperFactory {
    private static volatile ObjectMapper INSTANCE;

    private ObjectMapperFactory() {}

    public static ObjectMapper get() {
        if (INSTANCE == null) {
            synchronized (ObjectMapperFactory.class) {
                if (INSTANCE == null) {
                    ObjectMapper om = new ObjectMapper();
                    om.registerModule(new JavaTimeModule());
                    om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    om.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
                    INSTANCE = om;
                }
            }
        }
        return INSTANCE;
    }
}
