package com.nudgecraft.Karma.strategy;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

/**
 * Gestor global do ciclo de estratégias de Karma no servidor.
 * Controla qual a estratégia ativa de efeitos passivos aplicada a cada jogador no mundo.
 */
public final class KarmaEffectManager {

    private static final Identifier GRASS_SPEED_MODIFIER_ID = Identifier.fromNamespaceAndPath("nudgecraft", "grass_speed_boost");

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
        checkLookingAtCrops(player, level);
        activeStrategy.applyPassiveEffects(player, level);
    }

    /**
     * Verifica se o jogador está a olhar para plantações e envia-lhe uma mensagem
     * de Action Bar de forma passiva e subtil baseada no seu nível de Karma.
     */
    private static void checkLookingAtCrops(ServerPlayer player, ServerLevel level) {
        if (level.getGameTime() % 30 != 0) {
            return;
        }

        net.minecraft.world.phys.HitResult hit = player.pick(5.0D, 1.0F, false);
        if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            net.minecraft.world.phys.BlockHitResult blockHit = (net.minecraft.world.phys.BlockHitResult) hit;
            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(blockHit.getBlockPos());

            if (isCrop(state)) {
                KarmaState karma = getCurrentKarma();
                net.minecraft.network.chat.Component msg = null;

                if (karma == KarmaState.VPOSITIVE || karma == KarmaState.POSITIVE) {
                    msg = net.minecraft.network.chat.Component.literal("As tuas sementes crescem com a tua energia positiva!")
                            .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.ITALIC);
                } else if (karma == KarmaState.VNEGATIVE || karma == KarmaState.NEGATIVE) {
                    msg = net.minecraft.network.chat.Component.literal("As tuas plantações parecem murchar com a tua presença...")
                            .withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.ITALIC);
                }

                if (msg != null) {
                    player.sendSystemMessage(msg, true); // Envia como Action Bar
                }
            }
        }
    }

    /**
     * Determina se o estado do bloco corresponde a uma plantação.
     */
    private static boolean isCrop(net.minecraft.world.level.block.state.BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.CROPS)
                || state.getBlock() instanceof net.minecraft.world.level.block.CropBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.StemBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.CocoaBlock
                || state.getBlock() instanceof net.minecraft.world.level.block.SweetBerryBushBlock;
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
