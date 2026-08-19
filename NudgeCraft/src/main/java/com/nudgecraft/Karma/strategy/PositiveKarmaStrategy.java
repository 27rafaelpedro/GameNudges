package com.nudgecraft.Karma.strategy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.nudgecraft.Karma.KarmaState;

/**
 * Estratégia de Karma que aplica as recompensas, efeitos e melhorias ambientais
 * associadas aos estados de Karma Positivo (POSITIVE e VPOSITIVE).
 */
public class PositiveKarmaStrategy implements KarmaStrategy {

    /**
     * Catálogo estático das 12 espécies de flores de 1 bloco de altura
     * utilizadas para a florestação passiva ao redor do jogador.
     */
    private static final BlockState[] FLOWERS = {
            Blocks.DANDELION.defaultBlockState(),
            Blocks.POPPY.defaultBlockState(),
            Blocks.BLUE_ORCHID.defaultBlockState(),
            Blocks.ALLIUM.defaultBlockState(),
            Blocks.AZURE_BLUET.defaultBlockState(),
            Blocks.RED_TULIP.defaultBlockState(),
            Blocks.ORANGE_TULIP.defaultBlockState(),
            Blocks.WHITE_TULIP.defaultBlockState(),
            Blocks.PINK_TULIP.defaultBlockState(),
            Blocks.OXEYE_DAISY.defaultBlockState(),
            Blocks.CORNFLOWER.defaultBlockState(),
            Blocks.LILY_OF_THE_VALLEY.defaultBlockState()
    };

    /**
     * Aplica partículas de login brilhantes, visão noturna no VPOSITIVE à noite,
     * florestação espontânea de solo e aceleração de colheitas próximas (efeito Bonemeal).
     *
     * @param player O jogador sob efeito da estratégia.
     * @param level  O nível de servidor onde o jogador se encontra.
     */
    @Override
    public void applyPassiveEffects(ServerPlayer player, ServerLevel level) {
        long elapsed = System.currentTimeMillis() - KarmaEffectManager.getServerLoginTime();
        if (elapsed <= 15000) {
            if (level.getRandom().nextFloat() < 0.1f) {
                level.sendParticles(ParticleTypes.GLOW,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        3, 0.4, 0.4, 0.4, 0.1);
            }
        }

        if (KarmaEffectManager.getCurrentKarma() == KarmaState.VPOSITIVE) {
            int brightness = level.getMaxLocalRawBrightness(player.blockPosition());
            if (brightness < 7) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION,
                        260,   // Duração de 13 segundos para aplicação suave
                        0,     // Nível 0
                        false, // Ambient
                        false, // Sem partículas
                        false  // Ocultar ícones no HUD e Inventário!
                ));
            } else {
                if (player.hasEffect(MobEffects.NIGHT_VISION)) {
                    player.removeEffect(MobEffects.NIGHT_VISION);
                }
            }
        }

        // Crescimento de plantas/culturas (bonemeal) a cada tick com chance
        if (level.getRandom().nextFloat() < 0.15f) {
            int rangeX = level.getRandom().nextInt(11) - 5;
            int rangeY = level.getRandom().nextInt(5) - 2;
            int rangeZ = level.getRandom().nextInt(11) - 5;

            BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof BonemealableBlock bonemealable && !state.is(Blocks.GRASS_BLOCK)) {
                if (bonemealable.isValidBonemealTarget(level, pos, state) && bonemealable.isBonemealSuccess(level, level.getRandom(), pos, state)) {
                    bonemealable.performBonemeal(level, level.getRandom(), pos, state);
                    level.sendParticles(ParticleTypes.GLOW,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.0);
                }
            }
        }

        // Spawn de flores controlado (apenas a cada 100 ticks, aprox. 5 segundos)
        if (level.getGameTime() % 100 == 0) {
            int rangeX = level.getRandom().nextInt(9) - 4;
            int rangeY = level.getRandom().nextInt(3) - 1;
            int rangeZ = level.getRandom().nextInt(9) - 4;

            BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
            BlockState state = level.getBlockState(pos);

            if (state.is(Blocks.GRASS_BLOCK)) {
                BlockPos posAbove = pos.above();
                if (level.isEmptyBlock(posAbove)) {
                    // 50% de chance a cada 5 segundos de gerar 1 flor
                    if (level.getRandom().nextFloat() < 0.50f) {
                        BlockState randomFlower = FLOWERS[level.getRandom().nextInt(FLOWERS.length)];
                        level.setBlockAndUpdate(posAbove, randomFlower);
                    }
                }
            }
        }
    }
}
