package com.nudgecraft.Karma.strategy;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.network.chat.Component;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor global do ciclo de estratégias de Karma no servidor.
 * Controla qual a estratégia ativa de efeitos passivos aplicada a cada jogador no mundo.
 */
public final class KarmaEffectManager {

    private static final Identifier GRASS_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("nudgecraft", "grass_speed_boost");
    private static final Map<UUID, Integer> PLAY_TIME_TICKS = new ConcurrentHashMap<>();
    private static final Map<UUID, CropMessageState> CROP_MSG_STATES = new ConcurrentHashMap<>();

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
     * @param state O novo estado de Karma do jogador.
     */
    public static void updateStrategy(KarmaState state) {
        currentKarma = (state != null) ? state : KarmaState.BASE;
        activeStrategy = switch (currentKarma) {
            case VPOSITIVE, POSITIVE, SPOSITIVE -> new PositiveKarmaStrategy();
            case VNEGATIVE, NEGATIVE, SNEGATIVE -> new NegativeKarmaStrategy();
            default -> (player, level) -> {};
        };
    }

    /**
     * Obtém a estratégia ativa atualmente instanciada.
     *
     * @return A instância de {@link KarmaStrategy} ativa.
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
        manageGrassSpeed(player);
        updateCropMessageState(player);
        trackPlayTime(player);
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
                    Component.literal("Lembra-te de incluir algum movimento no dia de hoje!")
                            .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD)
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
        net.minecraft.network.chat.Component msg = null;

        if (karma == KarmaState.VPOSITIVE || karma == KarmaState.POSITIVE) {
            msg = net.minecraft.network.chat.Component.literal("As tuas sementes crescem com a tua energia positiva!")
                    .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC);
        } else if (karma == KarmaState.VNEGATIVE || karma == KarmaState.NEGATIVE) {
            msg = net.minecraft.network.chat.Component.literal("As sementes sofrem com a tua energia..")
                    .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC);
        }

        if (msg != null) {
            player.sendSystemMessage(msg, true);
        }
    }

    /**
     * Controla e atualiza o modificador de velocidade na relva do jogador
     * conforme o nível atual de Karma positivo e se o bloco sob os pés é relva.
     *
     * @param player O jogador sob verificação de velocidade.
     */
    private static void manageGrassSpeed(ServerPlayer player) {
        AttributeInstance attributeInstance = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (attributeInstance == null) {
            return;
        }

        double boostValue = 0.0;
        KarmaState current = getCurrentKarma();
        if (current == KarmaState.SPOSITIVE) {
            boostValue = 0.03;
        } else if (current == KarmaState.POSITIVE) {
            boostValue = 0.06;
        } else if (current == KarmaState.VPOSITIVE) {
            boostValue = 0.10;
        }

        boolean onGrass = player.getBlockStateOn().is(Blocks.GRASS_BLOCK);

        if (boostValue > 0.0 && onGrass) {
            AttributeModifier existing = attributeInstance.getModifier(GRASS_SPEED_MODIFIER_ID);
            if (existing == null || existing.amount() != boostValue) {
                if (existing != null) {
                    attributeInstance.removeModifier(GRASS_SPEED_MODIFIER_ID);
                }
                attributeInstance.addTransientModifier(new AttributeModifier(
                        GRASS_SPEED_MODIFIER_ID,
                        boostValue,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                ));
            }
        } else {
            if (attributeInstance.hasModifier(GRASS_SPEED_MODIFIER_ID)) {
                attributeInstance.removeModifier(GRASS_SPEED_MODIFIER_ID);
            }
        }
    }
}
