package com.nudgecraft.Karma.strategy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.AzaleaBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.MangrovePropaguleBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.manager.KarmaEffectManager;

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
     * Verifica se o bloco corresponde exclusivamente a uma plantação agrícola do jogador
     * ou a rebentos/mudas de árvores (Saplings).
     * Exclui relva (grass/tall grass), fetos e vegetação selvagem.
     */
    public static boolean isPlayerCrop(BlockState state, ServerLevel level, BlockPos pos) {
        if (state == null || state.isAir()) {
            return false;
        }

        // 1. Qualquer cultura em terra arada (Farmland)
        if (level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
            return true;
        }

        // 2. Culturas agrícolas padrão do Minecraft (Trigo, Cenouras, Batatas, Beterrabas, Melancia/Abóbora)
        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) {
            return true;
        }

        // 3. Rebentos / Mudas de árvores (Saplings de Carvalho, Bétula, Pinheiro, Selva, Acácia, Manguezal, Azáleas, etc.)
        if (state.getBlock() instanceof SaplingBlock
                || state.getBlock() instanceof MangrovePropaguleBlock
                || state.getBlock() instanceof AzaleaBlock) {
            return true;
        }

        // 4. Outras culturas agrícolas intencionais
        return state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COCOA)
                || state.is(Blocks.TORCHFLOWER_CROP)
                || state.is(Blocks.PITCHER_CROP)
                || state.is(Blocks.NETHER_WART);
    }

    /**
     * Aplica partículas de login brilhantes,
     * florestação espontânea de solo e aceleração de colheitas/árvores do jogador (efeito Bonemeal).
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

        // Crescimento exclusivo de plantações e árvores do jogador a cada tick com chance
        if (level.getRandom().nextFloat() < 0.15f) {
            int rangeX = level.getRandom().nextInt(11) - 5;
            int rangeY = level.getRandom().nextInt(5) - 2;
            int rangeZ = level.getRandom().nextInt(11) - 5;

            BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
            BlockState state = level.getBlockState(pos);

            // Garante que é um bloco fértil E que é exclusivamente plantação ou muda de árvore
            if (state.getBlock() instanceof BonemealableBlock bonemealable && isPlayerCrop(state, level, pos)) {
                if (bonemealable.isValidBonemealTarget(level, pos, state) && bonemealable.isBonemealSuccess(level, level.getRandom(), pos, state)) {
                    bonemealable.performBonemeal(level, level.getRandom(), pos, state);
                    level.sendParticles(ParticleTypes.GLOW,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.0);

                    KarmaEffectManager.triggerCropMessage(player, true);
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
