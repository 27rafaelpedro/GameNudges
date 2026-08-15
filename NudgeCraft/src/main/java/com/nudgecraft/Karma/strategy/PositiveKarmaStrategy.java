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

public class PositiveKarmaStrategy implements KarmaStrategy {

    @Override
    public void applyPassiveEffects(ServerPlayer player, ServerLevel level) {
        // Spawn positive/happy particles around the player occasionally (yellow glow)
        // Apenas ativo durante os primeiros 15 segundos após o jogador entrar no mundo
        long elapsed = System.currentTimeMillis() - KarmaEffectManager.getServerLoginTime();
        if (elapsed <= 15000) {
            if (level.getRandom().nextFloat() < 0.1f) {
                level.sendParticles(ParticleTypes.GLOW,
                        player.getX(), player.getY() + 1.0, player.getZ(),
                        3, 0.4, 0.4, 0.4, 0.1);
            }
        }

        // Se o Karma for VPOSITIVE, aplica o efeito de visão noturna (Night Vision) à noite
        if (KarmaEffectManager.getCurrentKarma() == KarmaState.VPOSITIVE) {
            if (level.isDarkOutside()) {
                player.addEffect(new MobEffectInstance(
                        MobEffects.NIGHT_VISION,
                        260,   // Duração de 13 segundos (evita o efeito de cintilação ao re-aplicar)
                        0,     // Amplificador
                        false, // Ambient
                        false, // Sem partículas no jogador
                        true   // Mostrar o ícone no HUD
                ));
            }
        }

        // Randomly affect vegetation around the player
        if (level.getRandom().nextFloat() < 0.2f) { // 20% chance per tick
            // Select random coordinate in radius of 5 blocks around player
            int rangeX = level.getRandom().nextInt(11) - 5;
            int rangeY = level.getRandom().nextInt(5) - 2;
            int rangeZ = level.getRandom().nextInt(11) - 5;

            BlockPos pos = player.blockPosition().offset(rangeX, rangeY, rangeZ);
            BlockState state = level.getBlockState(pos);

            // 1. Spawning flowers on empty grass blocks
            if (state.is(Blocks.GRASS_BLOCK)) {
                BlockPos posAbove = pos.above();
                if (level.isEmptyBlock(posAbove)) {
                    // 5% chance to spawn a flower
                    if (level.getRandom().nextFloat() < 0.05f) {
                        BlockState flower = level.getRandom().nextBoolean() ? 
                                Blocks.DANDELION.defaultBlockState() : 
                                Blocks.POPPY.defaultBlockState();
                        level.setBlockAndUpdate(posAbove, flower);
                    }
                }
            }

            // 2. Bonemeal crops / saplings (excluding GRASS_BLOCK to prevent tall grass spawn)
            if (state.getBlock() instanceof BonemealableBlock bonemealable && !state.is(Blocks.GRASS_BLOCK)) {
                if (bonemealable.isValidBonemealTarget(level, pos, state) && bonemealable.isBonemealSuccess(level, level.getRandom(), pos, state)) {
                    bonemealable.performBonemeal(level, level.getRandom(), pos, state);
                    // Spawn happy particle at the crop (yellow glow)
                    level.sendParticles(ParticleTypes.GLOW,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            3, 0.2, 0.2, 0.2, 0.0);
                }
            }
        }
    }
}
