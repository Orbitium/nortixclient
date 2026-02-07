package me.orbitium.nortix.client.util;

public class IdleTracker {
    private static long lastActivityTime = System.currentTimeMillis();
    private static final long IDLE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

    public static void recordActivity() {
        lastActivityTime = System.currentTimeMillis();
    }

    public static boolean isIdle() {
        return (System.currentTimeMillis() - lastActivityTime) > IDLE_THRESHOLD_MS;
    }

    public static long getLastActivityTime() {
        return lastActivityTime;
    }
}
