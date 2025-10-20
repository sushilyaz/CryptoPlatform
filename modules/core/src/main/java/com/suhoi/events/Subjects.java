package com.suhoi.events;

/**
 * Хелперы для NATS subjects.
 */
public final class Subjects {
    private Subjects() {
    }

    public static String ticks(String asset) {
        return "ticks." + asset;
    }

    public static String fairSnap(String asset) {
        return "fair.snap." + asset;
    }

    public static String alerts(String asset) {
        return "alerts." + asset;
    }

    public static String echo(String asset) {
        return "echo." + asset;
    }

    public static final String CONTROL_ECHO = "control.echo";

    public static final String TICKS_ALL = "ticks.>";
    public static final String FAIR_ALL  = "fair.snap.>";
    public static final String ALERTS_ALL= "alerts.>";

}
