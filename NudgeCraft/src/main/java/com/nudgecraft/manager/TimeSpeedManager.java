package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TimeSpeedManager {
    
    private static final Map<UUID, Integer> NIGHT_MSG_TICKS = new ConcurrentHashMap<>();
    private static long lastGameTime = -1;
    private static boolean wasDayBefore = false;

    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID uuid = player.getUUID();
        NIGHT_MSG_TICKS.remove(uuid);
    }

    public static void tick(ServerLevel level) {
        if (level == null || level.players().isEmpty()) {
            return;
        }

        long currentTick = level.getServer().getTickCount();
        if (currentTick == lastGameTime) {
            return;
        }
        lastGameTime = currentTick;

        // Limpar o tickrate global caso tenha sido alterado numa tentativa anterior
        if (level.getServer().tickRateManager().tickrate() != 20.0f) {
            level.getServer().tickRateManager().setTickRate(20.0f);
        }

        KarmaState current = KarmaEffectManager.getCurrentKarma();
        
        Holder<WorldClock> clock = level.dimensionTypeRegistration().value().defaultClock().orElse(null);
        if (clock == null) {
            return;
        }

        long timeOfDay = level.getServer().clockManager().getTotalTicks(clock) % 24000;
        // O dia no Minecraft vai do tick 0 ao 12000 (anoitecer comea a partir daqui)
        boolean isDay = (timeOfDay >= 0 && timeOfDay < 12500); 

        float targetRate = 1.0f;
        if (isDay) {
            if (current == KarmaState.SNEGATIVE) {
                targetRate = 1.25f; // ~8 minutos de dia (10/8 = 1.25)
            } else if (current == KarmaState.NEGATIVE) {
                targetRate = 1.67f; // ~6 minutos de dia (10/6 = 1.67)
            } else if (current == KarmaState.VNEGATIVE) {
                targetRate = 2.50f; // 4 minutos de dia (10/4 = 2.50)
            }
            level.getServer().clockManager().setRate(clock, targetRate);
            
            wasDayBefore = true;
        } 
        else {
            // Repor o ritmo  noite
            level.getServer().clockManager().setRate(clock, 1.0f);
            
            // Momento exato em que anoiteceu apos um dia acelerado
            if (wasDayBefore) {
                wasDayBefore = false;
                
                // Exibir a mensagem caso o karma seja negativo
                if (current == KarmaState.SNEGATIVE || current == KarmaState.NEGATIVE || current == KarmaState.VNEGATIVE) {
                    for (ServerPlayer player : level.players()) {
                        NIGHT_MSG_TICKS.put(player.getUUID(), 100);
                    }
                }
            }
        }

        // Processar mensagens
        for (ServerPlayer player : level.players()) {
            UUID uuid = player.getUUID();
            Integer ticks = NIGHT_MSG_TICKS.get(uuid);
            if (ticks != null && ticks > 0) {
                NIGHT_MSG_TICKS.put(uuid, ticks - 1);
                if (ticks % 20 == 0 || ticks == 100) {
                    com.nudgecraft.util.NudgeHelper.sendNudgeMessage(player, 
                            Component.literal("O dia passa a correr..")
                                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC),
                            true
                    );
                }
            }
        }
    }
}
