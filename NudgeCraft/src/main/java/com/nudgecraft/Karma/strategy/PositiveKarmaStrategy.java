package com.nudgecraft.Karma.strategy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
     * ou a rebentos/mudas de árvores (Saplings), excluindo vegetação selvagem.
     *
     * @param state O estado de bloco a verificar.
     * @param level O mundo do servidor.
     * @param pos   A posição do bloco no mundo.
     * @return Verdadeiro se for uma plantação válida do jogador.
     */
    public static boolean isPlayerCrop(BlockState state, ServerLevel level, BlockPos pos) {
        if (state == null || state.isAir()) {
            return false;
        }

        if (level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {
            return true;
        }

        if (state.getBlock() instanceof CropBlock || state.getBlock() instanceof StemBlock) {
            return true;
        }

        if (state.getBlock() instanceof SaplingBlock
                || state.getBlock() instanceof MangrovePropaguleBlock
                || state.getBlock() instanceof AzaleaBlock) {
            return true;
        }

        return state.is(Blocks.SWEET_BERRY_BUSH)
                || state.is(Blocks.COCOA)
                || state.is(Blocks.TORCHFLOWER_CROP)
                || state.is(Blocks.PITCHER_CROP)
                || state.is(Blocks.NETHER_WART);
    }

    /**
     * Aplica partículas verdes temporárias de login (15s), florestação espontânea
     * e aceleração de culturas do jogador exclusivamente nos estados POSITIVE e VPOSITIVE.
     *
     * @param player O jogador sob efeito da estratégia.
     * @param level  O nível de servidor onde o jogador se encontra.
     */
    @Override
    public void applyPassiveEffects(ServerPlayer player, ServerLevel level) {
        long elapsed = System.currentTimeMillis() - KarmaEffectManager.getServerLoginTime();
        // Apenas durante os primeiros 15 segundos após o login
        if (elapsed <= 15000) {
            if (level.getRandom().nextFloat() < 0.15f) {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        2, 0.4, 0.4, 0.4, 0.0);
            }
        }

        KarmaState current = KarmaEffectManager.getCurrentKarma();

        // Aceleração de colheitas exclusiva de POSITIVE e VPOSITIVE
        int attempts = switch (current) {
            case VPOSITIVE -> 3;
            case POSITIVE -> 2;
            default -> 0;
        };

        float chancePerAttempt = switch (current) {
            case VPOSITIVE -> 0.40f;
            case POSITIVE -> 0.25f;
            default -> 0.0f;
        };

        for (int i = 0; i < attempts; i++) {
            if (level.getRandom().nextFloat() < chancePerAttempt) {
                int rangeX = level.getRandom().nextInt(11) - 5;
                int rangeY = level.getRandom().nextInt(5) - 2;
                int rangeZ = level.getRandom().nextInt(11) - 5;

                BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
                BlockState state = level.getBlockState(pos);

                if (state.getBlock() instanceof BonemealableBlock bonemealable && isPlayerCrop(state, level, pos)) {
                    if (bonemealable.isValidBonemealTarget(level, pos, state) && bonemealable.isBonemealSuccess(level, level.getRandom(), pos, state)) {
                        bonemealable.performBonemeal(level, level.getRandom(), pos, state);

                        float pitch = 0.9f + level.getRandom().nextFloat() * 0.3f;
                        level.playSound(null, pos, SoundEvents.BONE_MEAL_USE, SoundSource.BLOCKS, 0.8f, pitch);

                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                4, 0.3, 0.3, 0.3, 0.0);

                        KarmaEffectManager.triggerCropMessage(player, true);
                    }
                }
            }
        }

        // Curar a Terra (Transforma Dirt em Grass)
        if (level.getGameTime() % 20 == 0) {
            int radius = (current == KarmaState.VPOSITIVE) ? 2 : 1;
            BlockPos basePos = player.blockPosition();
            boolean healedAny = false;
            
            for (int dy = -1; dy <= 0; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (dx * dx + dz * dz <= radius * radius) {
                            BlockPos targetPos = basePos.offset(dx, dy, dz);
                            BlockState targetState = level.getBlockState(targetPos);
                            
                            if (targetState.is(Blocks.DIRT) || targetState.is(Blocks.COARSE_DIRT)) {
                                // A relva precisa de luz/ar por cima para nascer
                                BlockState stateAbove = level.getBlockState(targetPos.above());
                                if (!stateAbove.isSolidRender()) {
                                    if (level.getRandom().nextFloat() < 0.25f) { // 25% probabilidade por bloco a cada seg
                                        level.setBlockAndUpdate(targetPos, Blocks.GRASS_BLOCK.defaultBlockState());
                                        
                                        // Partículas mágicas de cura
                                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                                targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5,
                                                2, 0.2, 0.1, 0.2, 0.0);
                                                
                                        healedAny = true;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (healedAny) {
                float pitch = 0.9f + level.getRandom().nextFloat() * 0.2f;
                level.playSound(null, player.blockPosition(), SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.6f, pitch);
            }
        }

        // Spawning de flores (Efeito de área - 3 a 4 flores)
        if (level.getGameTime() % 40 == 0) {
            int flowersToSpawn = 3 + level.getRandom().nextInt(2); // 3 a 4 flores
            boolean spawnedAny = false;

            for (int i = 0; i < flowersToSpawn; i++) {
                int rangeX = level.getRandom().nextInt(11) - 5;
                int rangeY = level.getRandom().nextInt(5) - 2;
                int rangeZ = level.getRandom().nextInt(11) - 5;

                BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
                BlockState state = level.getBlockState(pos);

                if (state.is(Blocks.GRASS_BLOCK)) {
                    BlockPos posAbove = pos.above();
                    if (level.isEmptyBlock(posAbove)) {
                        BlockState randomFlower = FLOWERS[level.getRandom().nextInt(FLOWERS.length)];
                        level.setBlockAndUpdate(posAbove, randomFlower);
                        
                        // Sistema de permanência condicional
                        boolean isPermanent = false;
                        if (current == KarmaState.VPOSITIVE) {
                            isPermanent = level.getRandom().nextFloat() < 0.80f; // 80% mantém-se
                        } else if (current == KarmaState.POSITIVE) {
                            isPermanent = level.getRandom().nextFloat() < 0.40f; // 40% mantém-se
                        }

                        if (!isPermanent) {
                            com.nudgecraft.manager.TemporaryBlockManager.registerTemporaryBlock(posAbove, level);
                        }

                        // Partículas e Som individual para cada flor
                        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                posAbove.getX() + 0.5, posAbove.getY() + 0.5, posAbove.getZ() + 0.5,
                                5, 0.3, 0.3, 0.3, 0.0);
                                
                        float pitch = 0.8f + level.getRandom().nextFloat() * 0.4f;
                        level.playSound(null, posAbove, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 0.5f, pitch);
                        
                        spawnedAny = true;
                    }
                }
            }

            if (spawnedAny) {
                KarmaEffectManager.triggerFlowerMessage(player);
            }
        }
    }
}
