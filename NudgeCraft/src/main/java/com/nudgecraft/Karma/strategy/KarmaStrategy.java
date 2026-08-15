package com.nudgecraft.Karma.strategy;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public interface KarmaStrategy {
    void applyPassiveEffects(ServerPlayer player, ServerLevel level);
}
