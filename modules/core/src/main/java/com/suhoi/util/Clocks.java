package com.suhoi.util;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/**
 * Утилиты для работы со временем (удобно стабилизировать в тестах).
 */
public final class Clocks {
    private static volatile Clock clock = Clock.systemUTC();

    private Clocks() {}

    public static Clock get() { return clock; }

    public static void set(Clock c) { clock = c; }

    public static Instant now() { return clock.instant(); }

    public static ZoneId zone() { return clock.getZone(); }
}
