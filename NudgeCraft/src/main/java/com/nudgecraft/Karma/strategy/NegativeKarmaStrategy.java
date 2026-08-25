package com.nudgecraft.Karma.strategy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.manager.KarmaEffectManager;
import com.nudgecraft.manager.TemporaryBlockManager;

/**
 * Estratégia de Karma que aplica as penalizações, efeitos e decaimento ambiental
 * associados aos estados de Karma Negativo (SNEGATIVE, NEGATIVE e VNEGATIVE).
 */
public class NegativeKarmaStrategy implements KarmaStrategy {

    private static final long RAIN_COOLDOWN_TICKS = 18000L;
    private static long nextAllowedRainGameTime = 0L;

    /**
     * Aplica a taxa metabólica aumentada de fome, partículas de fumo,
     * controlo climático com cooldown e dessecação de culturas/flores ao redor do jogador.
     *
     * @param player O jogador sob efeito da estratégia.
     * @param level  O nível de servidor onde o jogador se encontra.
     */
    @Override
    public void applyPassiveEffects(ServerPlayer player, ServerLevel level) {
        long elapsed = System.currentTimeMillis() - KarmaEffectManager.getServerLoginTime();
        if (elapsed <= 15000) {
            if (level.getRandom().nextFloat() < 0.1f) {
                level.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        3, 0.4, 0.4, 0.4, 0.05);
            }
        }

        KarmaState current = KarmaEffectManager.getCurrentKarma();
        long gameTime = level.getGameTime();

        if (gameTime % 20 == 0) {
            if (current == KarmaState.VNEGATIVE) {
                player.causeFoodExhaustion(0.040f);
            } else if (current == KarmaState.NEGATIVE) {
                player.causeFoodExhaustion(0.025f);
            } else if (current == KarmaState.SNEGATIVE) {
                player.causeFoodExhaustion(0.015f);
            }
        }

        if (gameTime % 1200 == 0 && level.getServer() != null) {
            if (gameTime >= nextAllowedRainGameTime && !level.isRaining()) {
                if (current == KarmaState.VNEGATIVE) {
                    if (level.getRandom().nextFloat() < 0.25f) {
                        boolean thunder = level.getRandom().nextFloat() < 0.10f;
                        level.getServer().setWeatherParameters(0, 3000, true, thunder);
                        nextAllowedRainGameTime = gameTime + RAIN_COOLDOWN_TICKS;
                    }
                } else if (current == KarmaState.NEGATIVE) {
                    if (level.getRandom().nextFloat() < 0.15f) {
                        level.getServer().setWeatherParameters(0, 2400, true, false);
                        nextAllowedRainGameTime = gameTime + RAIN_COOLDOWN_TICKS;
                    }
                }
            } else if (level.isRaining() && nextAllowedRainGameTime < gameTime) {
                nextAllowedRainGameTime = gameTime + RAIN_COOLDOWN_TICKS;
            }
        }

        int radius = (current == KarmaState.VNEGATIVE) ? 2 : 1;
        BlockPos basePos = player.blockPosition();
        for (int dy = -1; dy <= 0; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dz * dz <= radius * radius) {
                        BlockPos targetPos = basePos.offset(dx, dy, dz);
                        BlockState targetState = level.getBlockState(targetPos);
                        if (targetState.is(Blocks.GRASS_BLOCK)) {
                            BlockState newState;
                            boolean isTemporary;

                            if (current == KarmaState.SNEGATIVE) {
                                newState = Blocks.DIRT.defaultBlockState();
                                isTemporary = true;
                            } else if (current == KarmaState.NEGATIVE) {
                                newState = Blocks.COARSE_DIRT.defaultBlockState();
                                isTemporary = level.getRandom().nextFloat() >= 0.15f;
                            } else {
                                if (level.getRandom().nextFloat() < 0.60f) {
                                    newState = Blocks.DIRT_PATH.defaultBlockState();
                                } else {
                                    newState = Blocks.DIRT.defaultBlockState();
                                }
                                isTemporary = level.getRandom().nextFloat() >= 0.40f;
                            }

                            level.setBlockAndUpdate(targetPos, newState);

                            if (isTemporary) {
                                TemporaryBlockManager.registerTemporaryBlock(targetPos, level);
                            }

                            if (level.getRandom().nextFloat() < 0.05f) {
                                level.sendParticles(ParticleTypes.SMOKE,
                                        targetPos.getX() + 0.5, targetPos.getY() + 1.0, targetPos.getZ() + 0.5,
                                        1, 0.1, 0.1, 0.1, 0.0);
                            }
                        }
                    }
                }
            }
        }

        // Murchamento e dessecação de culturas exclusivo de NEGATIVE e VNEGATIVE
        int attempts = switch (current) {
            case VNEGATIVE -> 3;
            case NEGATIVE -> 2;
            default -> 0;
        };

        float chancePerAttempt = switch (current) {
            case VNEGATIVE -> 0.40f;
            case NEGATIVE -> 0.25f;
            default -> 0.0f;
        };

        for (int i = 0; i < attempts; i++) {
            if (level.getRandom().nextFloat() < chancePerAttempt) {
                int rangeX = level.getRandom().nextInt(11) - 5;
                int rangeY = level.getRandom().nextInt(5) - 2;
                int rangeZ = level.getRandom().nextInt(11) - 5;

                BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
                BlockState state = level.getBlockState(pos);

                if (state.is(BlockTags.FLOWERS)) {
                    level.setBlockAndUpdate(pos, Blocks.DEAD_BUSH.defaultBlockState());

                    float pitch = 0.7f + level.getRandom().nextFloat() * 0.2f;
                    level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.75f, pitch);

                    level.sendParticles(ParticleTypes.SMOKE,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            4, 0.25, 0.25, 0.25, 0.02);
                } else if (PositiveKarmaStrategy.isPlayerCrop(state, level, pos)) {
                    Property<?> ageProp = state.getProperties().stream()
                            .filter(p -> p.getName().equals("age") && p instanceof IntegerProperty)
                            .findFirst()
                            .orElse(null);

                    if (ageProp != null) {
                        IntegerProperty intAgeProp = (IntegerProperty) ageProp;
                        int age = state.getValue(intAgeProp);
                        if (age > 0) {
                            level.setBlockAndUpdate(pos, state.setValue(intAgeProp, age - 1));

                            float pitch = 0.6f + level.getRandom().nextFloat() * 0.2f;
                            level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.75f, pitch);

                            level.sendParticles(ParticleTypes.SMOKE,
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    4, 0.25, 0.25, 0.25, 0.02);

                            KarmaEffectManager.triggerCropMessage(player, false);
                        }
                    }
                }
            }
        }
    }
}
