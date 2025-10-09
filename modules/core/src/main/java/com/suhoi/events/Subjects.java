package com.suhoi.events;

/**
 * Хелперы для NATS subjects.
 */
public final class Subjects {
    private Subjects() {}

    public static String ticks(String asset)    { return "ticks." + asset; }
    public static String fairSnap(String asset) { return "fair.snap." + asset; }
    public static String alerts(String asset)   { return "alerts." + asset; }

    public static final String CONTROL_ECHO = "control.echo";
}
