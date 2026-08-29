package com.nudgecraft.Karma.strategy;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaStateHolder;
import com.nudgecraft.manager.KarmaEffectManager;
import com.nudgecraft.manager.TemporaryBlockManager;
import com.nudgecraft.mixin.LevelAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.saveddata.WeatherData;

public class NegativeKarmaStrategy implements KarmaStrategy {

    private boolean thunderCheckedForCurrentStorm = false;

    @Override
    public void applyPassiveEffects(ServerPlayer player, ServerLevel level) {
        long loginElapsed = System.currentTimeMillis() - KarmaEffectManager.getServerLoginTime();
        if (loginElapsed <= 15000) {
            if (level.getRandom().nextFloat() < 0.1f) {
                level.sendParticles(ParticleTypes.SMOKE,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        3, 0.4, 0.4, 0.4, 0.05);
            }
        }

        KarmaState current = KarmaStateHolder.get();
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

        long karmaElapsed = System.currentTimeMillis() - KarmaStateHolder.getLastStateChangeTime();
        
        if (karmaElapsed < 5000) {
            float realRain = ((LevelAccessor) level).getRealRainLevel();
            if (realRain > 0.0f) {
                level.getServer().setWeatherParameters(3600, 0, false, false);
            }
        }

        if (level.getWeatherData() != null) {
            WeatherData wData = level.getWeatherData();
            
            if (karmaElapsed >= 180000) {
                int clearTime = wData.getClearWeatherTime();
                if (clearTime > 0) {
                    int extraDecrease = 0;
                    if (current == KarmaState.SNEGATIVE) {
                        if (level.getRandom().nextFloat() < 0.20f) extraDecrease = 1;
                    } else if (current == KarmaState.NEGATIVE) {
                        if (level.getRandom().nextFloat() < 0.50f) extraDecrease = 1;
                    } else if (current == KarmaState.VNEGATIVE) {
                        extraDecrease = 1;
                    }
                    if (extraDecrease > 0) {
                        wData.setClearWeatherTime(Math.max(0, clearTime - extraDecrease));
                    }
                }
            }

            // Logica da probabilidade de Trovoada
            boolean isRaining = wData.isRaining();
            if (!isRaining) {
                thunderCheckedForCurrentStorm = false;
            } else if (!thunderCheckedForCurrentStorm) {
                thunderCheckedForCurrentStorm = true;
                
                float thunderChance = 0.0f;
                if (current == KarmaState.NEGATIVE) thunderChance = 0.25f;
                else if (current == KarmaState.VNEGATIVE) thunderChance = 0.70f;
                
                if (level.getRandom().nextFloat() < thunderChance) {
                    wData.setThundering(true);
                    wData.setThunderTime(wData.getRainTime());
                    
                    for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                        com.nudgecraft.util.NudgeHelper.sendNudgeMessage(p, 
                                net.minecraft.network.chat.Component.literal("§c§oO clima torna-se mais intenso.."),
                                true
                        );
                    }
                } else {
                    for (net.minecraft.server.level.ServerPlayer p : level.players()) {
                        com.nudgecraft.util.NudgeHelper.sendNudgeMessage(p, 
                                net.minecraft.network.chat.Component.literal("§8§oAs nuvens abrem a chuva.."),
                                true
                        );
                    }
                }
            }
        }

        int radius = (current == KarmaState.VNEGATIVE) ? 2 : 1;
        int vegRadius = (current == KarmaState.VNEGATIVE) ? 3 : (current == KarmaState.NEGATIVE) ? 2 : 1;
        
        float destroyChance = 0.0f;
        if (current == KarmaState.SNEGATIVE) destroyChance = 0.05f;
        else if (current == KarmaState.NEGATIVE) destroyChance = 0.20f;
        else if (current == KarmaState.VNEGATIVE) destroyChance = 0.40f;

        BlockPos basePos = player.blockPosition();
        for (int dy = -1; dy <= 6; dy++) {
            for (int dx = -Math.max(radius, vegRadius); dx <= Math.max(radius, vegRadius); dx++) {
                for (int dz = -Math.max(radius, vegRadius); dz <= Math.max(radius, vegRadius); dz++) {
                    double distSq = dx * dx + dz * dz;
                    double sphereSq = distSq + (dy > 0 ? dy * dy : 0); // Cilindro em baixo, semi-esfera em cima
                    
                    BlockPos targetPos = basePos.offset(dx, dy, dz);
                    BlockState targetState = level.getBlockState(targetPos);

                    if (dy <= 1 && distSq <= radius * radius) {
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

                    if (sphereSq <= vegRadius * vegRadius) {
                        if (targetState.is(BlockTags.FLOWERS) || 
                            targetState.is(BlockTags.LEAVES) || 
                            targetState.is(Blocks.SHORT_GRASS) || 
                            targetState.is(Blocks.TALL_GRASS) || 
                            targetState.is(Blocks.FERN) || 
                            targetState.is(Blocks.LARGE_FERN)) {
                            
                            if (level.getRandom().nextFloat() < destroyChance) {
                                level.destroyBlock(targetPos, false);
                            }
                        }
                    }
                }
            }
        }

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
