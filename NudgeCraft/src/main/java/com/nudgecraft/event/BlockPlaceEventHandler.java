package com.nudgecraft.event;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.strategy.KarmaEffectManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;

/**
 * Gestor de eventos acionados após a colocação bem-sucedida de blocos no mundo.
 * Controla os efeitos visuais associados ao Karma do jogador no momento da construção.
 */
public final class BlockPlaceEventHandler {

    private BlockPlaceEventHandler() {
    }

    /**
     * Processa a colocação de um bloco, aplicando partículas decorativas ao redor dele
     * caso o jogador possua o nível de Karma necessário.
     *
     * @param context O contexto físico do item de bloco que foi utilizado.
     * @param result  O resultado da tentativa de interação/colocação.
     */
    public static void onBlockPlaced(BlockPlaceContext context, InteractionResult result) {
        if (result == InteractionResult.SUCCESS || result == InteractionResult.CONSUME) {
            Level level = context.getLevel();
            if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
                KarmaState karma = KarmaEffectManager.getCurrentKarma();
                BlockPos pos = context.getClickedPos();

                if (karma == KarmaState.VPOSITIVE) {
                    for (int i = 0; i < 12; i++) {
                        double theta = level.getRandom().nextDouble() * 2 * Math.PI;
                        double phi = Math.acos(2 * level.getRandom().nextDouble() - 1);
                        double speedVal = 0.08 + level.getRandom().nextDouble() * 0.08;

                        double dx = Math.sin(phi) * Math.cos(theta);
                        double dy = Math.sin(phi) * Math.sin(theta);
                        double dz = Math.cos(phi);

                        double vx = speedVal * dx;
                        double vy = speedVal * dy;
                        double vz = speedVal * dz;

                        double spawnX = pos.getX() + 0.5 + dx * 0.65;
                        double spawnY = pos.getY() + 0.5 + dy * 0.65;
                        double spawnZ = pos.getZ() + 0.5 + dz * 0.65;

                        serverLevel.sendParticles(
                                new DustColorTransitionOptions(0xFFFF00, 0xFFFF00, 1.3f),
                                spawnX, spawnY, spawnZ,
                                0,
                                vx, vy, vz,
                                1.0
                        );
                    }
                }
            }
        }
    }
}
