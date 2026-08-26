package com.nudgecraft.Karma;

public final class KarmaStateHolder {
    private static volatile KarmaState currentKarma = KarmaState.BASE;
    private static volatile long lastStateChangeTime = System.currentTimeMillis();

    private KarmaStateHolder() {}

    public static KarmaState get() {
        return currentKarma;
    }

    public static void set(KarmaState state) {
        if (currentKarma != state) {
            currentKarma = (state != null) ? state : KarmaState.BASE;
            lastStateChangeTime = System.currentTimeMillis();
        }
    }

    public static long getLastStateChangeTime() {
        return lastStateChangeTime;
    }
}
