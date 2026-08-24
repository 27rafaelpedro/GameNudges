package com.nudgecraft.Karma.strategy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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

    /** Delay mínimo de 15 minutos (18.000 ticks) entre episódios de chuva forçada. */
    private static final long RAIN_COOLDOWN_TICKS = 18000L;
    private static long nextAllowedRainGameTime = 0L;

    /**
     * Aplica a taxa metabólica aumentada de fome (sem poção nem ícones),
     * partículas de login de fumo, controlo do clima hostil com cooldown e murchamento/seca de plantas
     * ao redor do jogador afetado.
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

        // Aumento metabólico passivo de exaustão a cada segundo (20 ticks) sem poção nem alteração visual da barra
        if (gameTime % 20 == 0) {
            if (current == KarmaState.VNEGATIVE) {
                player.causeFoodExhaustion(0.040f); // +20% taxa acelerada
            } else if (current == KarmaState.NEGATIVE) {
                player.causeFoodExhaustion(0.025f); // +15% taxa acelerada
            } else if (current == KarmaState.SNEGATIVE) {
                player.causeFoodExhaustion(0.015f); // +10% taxa acelerada
            }
        }

        // Controlo meteorológico com cooldown rígido de 15 minutos (testa apenas a cada 60 segundos / 1200 ticks)
        if (gameTime % 1200 == 0 && level.getServer() != null) {
            if (gameTime >= nextAllowedRainGameTime && !level.isRaining()) {
                if (current == KarmaState.VNEGATIVE) {
                    // VNEGATIVE: 25% de probabilidade de chuva curta (2.5 minutos = 3000 ticks) com 10% de trovoada
                    if (level.getRandom().nextFloat() < 0.25f) {
                        boolean thunder = level.getRandom().nextFloat() < 0.10f;
                        level.getServer().setWeatherParameters(0, 3000, true, thunder);
                        nextAllowedRainGameTime = gameTime + RAIN_COOLDOWN_TICKS;
                    }
                } else if (current == KarmaState.NEGATIVE) {
                    // NEGATIVE: 15% de probabilidade de chuva curta (2 minutos = 2400 ticks) sem trovoada
                    if (level.getRandom().nextFloat() < 0.15f) {
                        level.getServer().setWeatherParameters(0, 2400, true, false);
                        nextAllowedRainGameTime = gameTime + RAIN_COOLDOWN_TICKS;
                    }
                }
            } else if (level.isRaining() && nextAllowedRainGameTime < gameTime) {
                // Se já estiver a chover por evento natural, garante que o cooldown de 15 min só conta a partir do fim
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
                            } else { // VNEGATIVE
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

        float tickChance = (current == KarmaState.VNEGATIVE) ? 0.8f : 0.4f;
        if (level.getRandom().nextFloat() < tickChance) {
            int rangeX = level.getRandom().nextInt(11) - 5;
            int rangeY = level.getRandom().nextInt(5) - 2;
            int rangeZ = level.getRandom().nextInt(11) - 5;

            BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
            BlockState state = level.getBlockState(pos);

            if (state.is(BlockTags.FLOWERS)) {
                float flowerDecayChance = (current == KarmaState.VNEGATIVE) ? 0.75f : 0.40f;
                if (level.getRandom().nextFloat() < flowerDecayChance) {
                    level.setBlockAndUpdate(pos, Blocks.DEAD_BUSH.defaultBlockState());
                    level.sendParticles(ParticleTypes.SMOKE,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.0);
                }
            } else if (PositiveKarmaStrategy.isPlayerCrop(state, level, pos)) {
                // Apenas regride se for exclusivamente uma plantação agrícola do jogador
                Property<?> ageProp = state.getProperties().stream()
                        .filter(p -> p.getName().equals("age") && p instanceof IntegerProperty)
                        .findFirst()
                        .orElse(null);

                if (ageProp != null) {
                    IntegerProperty intAgeProp = (IntegerProperty) ageProp;
                    int age = state.getValue(intAgeProp);
                    if (age > 0) {
                        float cropDecayChance = (current == KarmaState.VNEGATIVE) ? 0.75f : 0.40f;
                        if (level.getRandom().nextFloat() < cropDecayChance) {
                            level.setBlockAndUpdate(pos, state.setValue(intAgeProp, age - 1));
                            level.sendParticles(ParticleTypes.SMOKE,
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    3, 0.2, 0.2, 0.2, 0.0);

                            KarmaEffectManager.triggerCropMessage(player, false);
                        }
                    }
                }
            }
        }
    }
}
