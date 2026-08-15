package com.nudgecraft.Karma.strategy;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public final class KarmaEffectManager {

    private static volatile KarmaState currentKarma = KarmaState.BASE;
    private static volatile KarmaStrategy activeStrategy = (player, level) -> {};
    private static volatile long serverLoginTime = 0;

    private KarmaEffectManager() {
    }

    public static void setServerLoginTime(long time) {
        serverLoginTime = time;
    }

    public static long getServerLoginTime() {
        return serverLoginTime;
    }

    public static void updateStrategy(KarmaState state) {
        currentKarma = (state != null) ? state : KarmaState.BASE;
        activeStrategy = switch (currentKarma) {
            case VPOSITIVE, POSITIVE -> new PositiveKarmaStrategy();
            default -> (player, level) -> {};
        };
    }

    public static KarmaStrategy getActiveStrategy() {
        return activeStrategy;
    }

    public static KarmaState getCurrentKarma() {
        return currentKarma;
    }

    public static void tick(ServerPlayer player, ServerLevel level) {
        activeStrategy.applyPassiveEffects(player, level);
    }
}
