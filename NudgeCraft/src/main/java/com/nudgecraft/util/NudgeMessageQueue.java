package com.nudgecraft.util;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.nudgecraft.Karma.NudgeBlinkPayload;
import com.nudgecraft.firebase.NudgeLogger;

public class NudgeMessageQueue {

    private static class QueuedMessage {
        final Component msg;
        final boolean isActionBar;
        final boolean isPositive;
        final String featureId;

        QueuedMessage(Component msg, boolean isActionBar, boolean isPositive, String featureId) {
            this.msg = msg;
            this.isActionBar = isActionBar;
            this.isPositive = isPositive;
            this.featureId = featureId;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof QueuedMessage)) return false;
            QueuedMessage other = (QueuedMessage) obj;
            return this.msg.getString().equals(other.msg.getString());
        }
    }

    private static final ConcurrentHashMap<UUID, Queue<QueuedMessage>> QUEUES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> DISPLAY_TIMERS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, QueuedMessage> CURRENT_MSG = new ConcurrentHashMap<>();

    public static void queueMessage(ServerPlayer player, Component msg, boolean isActionBar, boolean isPositive, String featureId) {
        if (msg == null) return;
        
        UUID uuid = player.getUUID();
        QueuedMessage qm = new QueuedMessage(msg, isActionBar, isPositive, featureId);
        
        Queue<QueuedMessage> queue = QUEUES.computeIfAbsent(uuid, k -> new LinkedList<>());
        QueuedMessage active = CURRENT_MSG.get(uuid);
        
        // Anti-spam: Evita adicionar  fila se j l estiver uma idntica ou se for exatamente a mesma que est a passar no ecr
        if (!queue.contains(qm) && (active == null || !active.equals(qm))) {
            queue.add(qm);
        }
    }

    public static void tick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int currentTimer = DISPLAY_TIMERS.getOrDefault(uuid, 0);

        if (currentTimer > 0) {
            DISPLAY_TIMERS.put(uuid, currentTimer - 1);
            if (currentTimer - 1 == 0) {
                CURRENT_MSG.remove(uuid); // Terminou o tempo da mensagem atual
            }
        } else {
            Queue<QueuedMessage> queue = QUEUES.get(uuid);
            if (queue != null && !queue.isEmpty()) {
                QueuedMessage nextMsg = queue.poll();
                CURRENT_MSG.put(uuid, nextMsg);
                
                // Dispara a mensagem
                player.sendSystemMessage(nextMsg.msg, nextMsg.isActionBar);
                ServerPlayNetworking.send(player, new NudgeBlinkPayload());
                
                if (nextMsg.featureId != null && !nextMsg.featureId.isEmpty()) {
                    NudgeLogger.log(player, nextMsg.isPositive, nextMsg.featureId);
                }
                
                // 3.5 segundos de bloqueio (70 ticks)
                DISPLAY_TIMERS.put(uuid, 70);
            }
        }
    }
}
