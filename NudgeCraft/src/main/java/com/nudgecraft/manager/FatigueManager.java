package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de Fadiga e Exaustão Temporária para o estado Karma Muito Negativo:
 * - VNEGATIVE: 12% de probabilidade em ações (minerar, atacar, correr, saltar)
 *
 * Ao sofrer fadiga, o jogador tem a velocidade de mineração, ataque, movimento e salto
 * reduzidas em 50% durante 4 segundos, com a mensagem:
 * "O cansaço consome temporariamente as tuas forças..."
 */
public final class FatigueManager {

    private static final Identifier FATIGUE_SPEED_ID = Identifier.fromNamespaceAndPath("nudgecraft", "fatigue_movement_speed");
    private static final Identifier FATIGUE_ATTACK_SPEED_ID = Identifier.fromNamespaceAndPath("nudgecraft", "fatigue_attack_speed");
    private static final Identifier FATIGUE_BREAK_SPEED_ID = Identifier.fromNamespaceAndPath("nudgecraft", "fatigue_block_break_speed");
    private static final Identifier FATIGUE_JUMP_STRENGTH_ID = Identifier.fromNamespaceAndPath("nudgecraft", "fatigue_jump_strength");

    /** Período de carência inicial ao entrar no jogo: 60 segundos (1200 ticks) sem fadiga. */
    public static final int INITIAL_LOGIN_GRACE_TICKS = 1200;

    private static final Map<UUID, Integer> FATIGUE_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> COOLDOWN_TICKS = new ConcurrentHashMap<>();

    private FatigueManager() {
    }

    /**
     * Acionado quando o jogador entra no servidor/mundo.
     * Define um delay inicial de segurança para não sofrer fadiga logo ao entrar.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        COOLDOWN_TICKS.put(uuid, INITIAL_LOGIN_GRACE_TICKS);
        FATIGUE_TICKS.remove(uuid);
        removeFatigueModifiers(player);
    }

    /**
     * Limpa o estado de fadiga do jogador ao desconectar.
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        if (player == null) return;
        UUID uuid = player.getUUID();
        COOLDOWN_TICKS.remove(uuid);
        FATIGUE_TICKS.remove(uuid);
        removeFatigueModifiers(player);
    }

    public static boolean isFatigued(UUID uuid) {
        return FATIGUE_TICKS.getOrDefault(uuid, 0) > 0;
    }

    public static void tryTriggerFatigue(ServerPlayer player, ServerLevel level) {
        if (player == null || level == null) return;
        UUID uuid = player.getUUID();

        // Se já estiver fatigado ou em cooldown de ativação recente / grace period de login, não dispara
        if (isFatigued(uuid) || COOLDOWN_TICKS.getOrDefault(uuid, 0) > 0) {
            return;
        }

        // Delay adicional de segurança baseado no timestamp de login (mínimo 60s)
        long elapsedSinceLogin = System.currentTimeMillis() - KarmaEffectManager.getServerLoginTime();
        if (elapsedSinceLogin < 60_000) {
            return;
        }

        KarmaState current = KarmaEffectManager.getCurrentKarma();
        float chance = 0.0f;
        if (current == KarmaState.VNEGATIVE) {
            chance = 0.12f; // 12% exclusivo para Very Negative
        }

        if (chance > 0.0f && level.getRandom().nextFloat() < chance) {
            triggerFatigue(player, level, uuid);
        }
    }

    private static void triggerFatigue(ServerPlayer player, ServerLevel level, UUID uuid) {
        // Duração: 4 segundos = 80 ticks
        FATIGUE_TICKS.put(uuid, 80);
        // Cooldown: 3 minutos = 3600 ticks antes de poder disparar novamente
        COOLDOWN_TICKS.put(uuid, 3600);

        // Mensagem enviada na Action Bar
        com.nudgecraft.util.NudgeHelper.sendNudgeMessage(player, 
                Component.literal("§cSentes-te pesado.."),
                true, false, "fatigue"
        );

        // Efeitos sonoros e visuais de exaustão súbita
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_BREATH, SoundSource.PLAYERS, 1.0f, 0.7f);

        level.sendParticles(ParticleTypes.SMOKE,
                player.getX(), player.getY() + 1.2, player.getZ(),
                8, 0.3, 0.3, 0.3, 0.02);

        applyFatigueModifiers(player);
    }

    public static void tick(ServerPlayer player, ServerLevel level) {
        if (player == null || level == null) return;
        UUID uuid = player.getUUID();

        int cooldown = COOLDOWN_TICKS.getOrDefault(uuid, 0);
        if (cooldown > 0) {
            COOLDOWN_TICKS.put(uuid, cooldown - 1);
        }

        int fatigue = FATIGUE_TICKS.getOrDefault(uuid, 0);
        if (fatigue > 0) {
            FATIGUE_TICKS.put(uuid, fatigue - 1);
            applyFatigueModifiers(player);

            // Pequenas partículas ocasionais de fumo/suor enquanto fatigado
            if (fatigue % 10 == 0) {
                level.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 0.8, player.getZ(),
                        2, 0.2, 0.2, 0.2, 0.01);
            }

            if (fatigue - 1 == 0) {
                removeFatigueModifiers(player);
            }
        } else {
            removeFatigueModifiers(player);
        }

        // Teste de fadiga ao correr (sprint) a cada segundo
        if (player.isSprinting() && player.tickCount % 20 == 0) {
            tryTriggerFatigue(player, level);
        }
    }

    private static void applyFatigueModifiers(ServerPlayer player) {
        applyModifier(player, Attributes.MOVEMENT_SPEED, FATIGUE_SPEED_ID, -0.50);
        applyModifier(player, Attributes.ATTACK_SPEED, FATIGUE_ATTACK_SPEED_ID, -0.50);
        applyModifier(player, Attributes.BLOCK_BREAK_SPEED, FATIGUE_BREAK_SPEED_ID, -0.50);
        applyModifier(player, Attributes.JUMP_STRENGTH, FATIGUE_JUMP_STRENGTH_ID, -0.50);
    }

    public static void removeFatigueModifiers(ServerPlayer player) {
        removeModifier(player, Attributes.MOVEMENT_SPEED, FATIGUE_SPEED_ID);
        removeModifier(player, Attributes.ATTACK_SPEED, FATIGUE_ATTACK_SPEED_ID);
        removeModifier(player, Attributes.BLOCK_BREAK_SPEED, FATIGUE_BREAK_SPEED_ID);
        removeModifier(player, Attributes.JUMP_STRENGTH, FATIGUE_JUMP_STRENGTH_ID);
    }

    private static void applyModifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id, double amount) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            AttributeModifier existing = instance.getModifier(id);
            if (existing == null || existing.amount() != amount) {
                if (existing != null) {
                    instance.removeModifier(id);
                }
                instance.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    private static void removeModifier(ServerPlayer player, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute, Identifier id) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.hasModifier(id)) {
            instance.removeModifier(id);
        }
    }
}
