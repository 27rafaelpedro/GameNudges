package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.strategy.KarmaStrategy;
import com.nudgecraft.Karma.strategy.NegativeKarmaStrategy;
import com.nudgecraft.Karma.strategy.PositiveKarmaStrategy;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor global do ciclo de estratégias de Karma no servidor.
 * Controla qual a estratégia ativa de efeitos passivos aplicada a cada jogador no mundo.
 */
public final class KarmaEffectManager {

    private static final Identifier POSITIVE_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("nudgecraft", "positive_speed_boost");
    private static final Map<UUID, Integer> PLAY_TIME_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, CropMessageState> CROP_MSG_STATES = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> AIRBORNE_VALID_TICKS = new ConcurrentHashMap<>();

    private static class CropMessageState {
        int displayTicks = 0;
        int cooldownTicks = 0;
    }

    private static volatile KarmaState currentKarma = KarmaState.BASE;
    private static volatile KarmaStrategy activeStrategy = (player, level) -> {};
    private static volatile long serverLoginTime = 0;

    private KarmaEffectManager() {
    }

    /**
     * Define o registo do instante de login do jogador no servidor em milissegundos.
     * Utilizado para controlar a duração de partículas de entrada.
     *
     * @param time Instante temporal em milissegundos.
     */
    public static void setServerLoginTime(long time) {
        serverLoginTime = time;
    }

    /**
     * Obtém o instante de login registado em milissegundos.
     *
     * @return Instante temporal do login em milissegundos.
     */
    public static long getServerLoginTime() {
        return serverLoginTime;
    }

    /**
     * Atualiza o estado de Karma atual e reconstrói a estratégia de efeitos ativa.
     * Associa estados de Karma a classes especializadas de estratégia ou expressões Lambda.
     *
     * @param karma O novo {@link KarmaState} atribuído.
     */
    public static void updateStrategy(KarmaState karma) {
        currentKarma = (karma != null) ? karma : KarmaState.BASE;
        com.nudgecraft.Karma.KarmaStateHolder.set(currentKarma);

        switch (currentKarma) {
            case POSITIVE, VPOSITIVE -> activeStrategy = new PositiveKarmaStrategy();
            case NEGATIVE, VNEGATIVE -> activeStrategy = new NegativeKarmaStrategy();
            case SPOSITIVE, SNEGATIVE, BASE -> activeStrategy = (player, level) -> {};
        }
    }

    /**
     * Obtém a estratégia ativa de Karma no momento.
     *
     * @return A implementação de {@link KarmaStrategy} em vigor.
     */
    public static KarmaStrategy getActiveStrategy() {
        return activeStrategy;
    }

    /**
     * Obtém o estado de Karma atual ativo na sessão de jogo.
     *
     * @return O {@link KarmaState} atual do jogador.
     */
    public static KarmaState getCurrentKarma() {
        return currentKarma;
    }

    /**
     * Acionado a cada tick de processamento do jogador no servidor para
     * aplicar os efeitos passivos correspondentes da estratégia em vigor e gerir modificadores dinâmicos.
     *
     * @param player O jogador alvo do processamento.
     * @param level  O nível de servidor em processamento.
     */
    public static void tick(ServerPlayer player, ServerLevel level) {
        managePositiveSpeed(player, level);
        updateCropMessageState(player);
        trackPlayTime(player);
        OreVeinManager.tick(player, level);
        FireflyLightingManager.tick(player, level);
        activeStrategy.applyPassiveEffects(player, level);
    }

    /**
     * Monitoriza o tempo de jogo contínuo da sessão do jogador.
     * Envia um lembrete no chat a cada 30 minutos (36.000 ticks).
     *
     * @param player O jogador sob monitorização.
     */
    private static void trackPlayTime(ServerPlayer player) {
        UUID uuid = player.getUUID();
        int ticks = PLAY_TIME_TICKS.getOrDefault(uuid, 0) + 1;

        if (ticks >= 36000) { // 30 minutos (36.000 ticks)
            player.sendSystemMessage(
                    Component.literal("§6[Nudgecraft] Já estás a jogar há 30 minutos! Lembra-te de fazer uma pausa e manter-te ativo.")
            );
            PLAY_TIME_TICKS.put(uuid, 0);
        } else {
            PLAY_TIME_TICKS.put(uuid, ticks);
        }
    }

    /**
     * Remove o registo de tempo de jogo do jogador quando ele se desconecta.
     *
     * @param player O jogador que se desconectou.
     */
    public static void onPlayerDisconnect(ServerPlayer player) {
        PLAY_TIME_TICKS.remove(player.getUUID());
        CROP_MSG_STATES.remove(player.getUUID());
        AIRBORNE_VALID_TICKS.remove(player.getUUID());
        OreVeinManager.onPlayerDisconnect(player);
        FireflyLightingManager.onPlayerDisconnect(player);
    }

    /**
     * Aciona a exibição da mensagem de plantação no Action Bar.
     * Mostra a mensagem por 5 segundos (100 ticks) caso o cooldown de 10 segundos não esteja ativo.
     *
     * @param player O jogador que ativou o crescimento/regressão.
     * @param isGrowth true se a plantação cresceu, false se regrediu.
     */
    public static void triggerCropMessage(ServerPlayer player, boolean isGrowth) {
        UUID uuid = player.getUUID();
        CropMessageState state = CROP_MSG_STATES.computeIfAbsent(uuid, k -> new CropMessageState());

        if (state.cooldownTicks == 0 && state.displayTicks == 0) {
            state.displayTicks = 100;
            sendCropActionBarMessage(player);
        }
    }

    /**
     * Atualiza o estado de tempo da mensagem a cada tick do jogador.
     */
    private static void updateCropMessageState(ServerPlayer player) {
        UUID uuid = player.getUUID();
        CropMessageState state = CROP_MSG_STATES.get(uuid);
        if (state == null) {
            return;
        }

        if (state.cooldownTicks > 0) {
            state.cooldownTicks--;
        } else if (state.displayTicks > 0) {
            state.displayTicks--;
            // Envia a cada 20 ticks (1 segundo) para mantê-la estável no ecrã
            if (state.displayTicks % 20 == 0 && state.displayTicks > 0) {
                sendCropActionBarMessage(player);
            }
            if (state.displayTicks == 0) {
                state.cooldownTicks = 200; // 10 segundos de cooldown
            }
        }
    }

    /**
     * Envia a mensagem de Action Bar apropriada com base no Karma.
     */
    private static void sendCropActionBarMessage(ServerPlayer player) {
        KarmaState karma = getCurrentKarma();
        Component msg = null;

        if (karma == KarmaState.VPOSITIVE || karma == KarmaState.POSITIVE) {
            msg = Component.literal("As tuas sementes crescem com a tua energia positiva!")
                    .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC);
        } else if (karma == KarmaState.VNEGATIVE || karma == KarmaState.NEGATIVE) {
            msg = Component.literal("As sementes sofrem com a tua energia..")
                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC);
        }

        if (msg != null) {
            player.sendSystemMessage(msg, true);
        }
    }

    /**
     * Verifica se o bloco corresponde a superfícies naturais de construção/habitação
     * (Relva, Terra, Areia, Neve, Terracota).
     */
    private static boolean isNaturalBuildingSurface(BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        // 1. Relva, Terra e Solo
        if (state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.DIRT) || state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.FARMLAND) || state.is(Blocks.MUD) || state.is(Blocks.PODZOL)
                || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.ROOTED_DIRT) || state.is(Blocks.MYCELIUM)) {
            return true;
        }
        // 2. Areia e Arenito
        if (state.is(BlockTags.SAND) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
                || state.is(Blocks.SMOOTH_SANDSTONE) || state.is(Blocks.SMOOTH_RED_SANDSTONE)
                || state.is(Blocks.CUT_SANDSTONE) || state.is(Blocks.CUT_RED_SANDSTONE)) {
            return true;
        }
        // 3. Neve
        if (state.is(BlockTags.SNOW) || state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)) {
            return true;
        }
        // 4. Terracota (todas as cores e variantes)
        if (state.is(BlockTags.TERRACOTTA) || state.is(Blocks.TERRACOTTA)) {
            return true;
        }
        return false;
    }

    /**
     * Controla e atualiza o modificador de velocidade do jogador durante o dia
     * em superfícies comuns de habitação (Relva, Terra, Areia, Neve, Terracota) para os estados de Karma Positivo:
     * - VPOSITIVE: +50% (+0.50)
     * - POSITIVE:  +20% (+0.20)
     * - SPOSITIVE: +10% (+0.10)
     *
     * Preserva a velocidade durante o salto aéreo sem soluços/stutter.
     *
     * @param player O jogador sob verificação de velocidade.
     * @param level  O nível do servidor para verificação de luz do dia.
     */
    private static void managePositiveSpeed(ServerPlayer player, ServerLevel level) {
        AttributeInstance attributeInstance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attributeInstance == null) {
            return;
        }

        double boostValue = 0.0;
        KarmaState current = getCurrentKarma();
        if (current == KarmaState.SPOSITIVE) {
            boostValue = 0.10; // +10%
        } else if (current == KarmaState.POSITIVE) {
            boostValue = 0.20; // +20%
        } else if (current == KarmaState.VPOSITIVE) {
            boostValue = 0.50; // +50%
        }

        boolean isDay = !level.isDarkOutside();
        UUID uuid = player.getUUID();
        boolean isOnGround = player.onGround();

        // 1. Bloco onde o jogador está apoiado diretamente
        BlockState stateOn = player.getBlockStateOn();
        boolean onValidSurface = isNaturalBuildingSurface(stateOn);

        // 2. Se for ar/folhagem, verifica o bloco imediatamente abaixo dos pés (Y - 1)
        if (!onValidSurface) {
            BlockState stateBelow = level.getBlockState(player.blockPosition().below());
            onValidSurface = isNaturalBuildingSurface(stateBelow);
        }

        // 3. Verifica a posição de movimento de apoio
        if (!onValidSurface) {
            BlockState stateMovement = level.getBlockState(player.getOnPos());
            onValidSurface = isNaturalBuildingSurface(stateMovement);
        }

        // Preservação fluida da velocidade durante o salto aéreo
        boolean effectiveValidSurface = false;
        if (onValidSurface) {
            AIRBORNE_VALID_TICKS.put(uuid, 0);
            effectiveValidSurface = true;
        } else if (!isOnGround) {
            // Em pleno ar após descolagem de solo válido (Sprint-jump contínuo)
            int airTicks = AIRBORNE_VALID_TICKS.getOrDefault(uuid, 999) + 1;
            if (airTicks <= 25) { // Mantém a velocidade durante todo o arco do salto (~1.25 segundos)
                AIRBORNE_VALID_TICKS.put(uuid, airTicks);
                effectiveValidSurface = true;
            } else {
                AIRBORNE_VALID_TICKS.put(uuid, airTicks);
            }
        } else {
            // Aterrou em bloco não válido (ex: pedra, madeira) -> cancela o boost imediatamente
            AIRBORNE_VALID_TICKS.put(uuid, 999);
        }

        if (boostValue > 0.0 && isDay && effectiveValidSurface) {
            AttributeModifier existing = attributeInstance.getModifier(POSITIVE_SPEED_MODIFIER_ID);
            if (existing == null || existing.amount() != boostValue) {
                if (existing != null) {
                    attributeInstance.removeModifier(POSITIVE_SPEED_MODIFIER_ID);
                }
                attributeInstance.addTransientModifier(new AttributeModifier(
                        POSITIVE_SPEED_MODIFIER_ID,
                        boostValue,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        } else {
            if (attributeInstance.hasModifier(POSITIVE_SPEED_MODIFIER_ID)) {
                attributeInstance.removeModifier(POSITIVE_SPEED_MODIFIER_ID);
            }
        }
    }
}
