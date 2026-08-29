package com.nudgecraft.util;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.nudgecraft.Karma.NudgeBlinkPayload;
import com.nudgecraft.firebase.NudgeLogger;

public class NudgeHelper {
    public static void sendNudgeMessage(ServerPlayer player, Component msg, boolean isActionBar, boolean isPositive, String featureId) {
        if (msg != null) {
            player.sendSystemMessage(msg, isActionBar);
            ServerPlayNetworking.send(player, new NudgeBlinkPayload());
        }
        if (featureId != null && !featureId.isEmpty()) {
            NudgeLogger.log(player, isPositive, featureId);
        }
    }
    
    public static void sendNudgeMessage(ServerPlayer player, Component msg, boolean isActionBar) {
        sendNudgeMessage(player, msg, isActionBar, true, null);
    }
    
    public static void sendNudgeMessage(ServerPlayer player, Component msg) {
        sendNudgeMessage(player, msg, false, true, null);
    }
}
