package com.nudgecraft.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.nudgecraft.Karma.NudgeBlinkPayload;

public class NudgeHelper {
    public static void sendNudgeMessage(ServerPlayer player, Component msg, boolean isActionBar) {
        player.sendSystemMessage(msg, isActionBar);
        ServerPlayNetworking.send(player, new NudgeBlinkPayload());
    }
    
    public static void sendNudgeMessage(ServerPlayer player, Component msg) {
        player.sendSystemMessage(msg);
        ServerPlayNetworking.send(player, new NudgeBlinkPayload());
    }
}
