package com.nudgecraft.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class NudgeHelper {
    public static void sendNudgeMessage(ServerPlayer player, Component msg, boolean isActionBar, boolean isPositive, String featureId) {
        NudgeMessageQueue.queueMessage(player, msg, isActionBar, isPositive, featureId);
    }
    
    public static void sendNudgeMessage(ServerPlayer player, Component msg, boolean isActionBar) {
        sendNudgeMessage(player, msg, isActionBar, true, null);
    }
    
    public static void sendNudgeMessage(ServerPlayer player, Component msg) {
        sendNudgeMessage(player, msg, false, true, null);
    }
}
