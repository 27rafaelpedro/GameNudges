package com.nudgecraft.manager;

import com.nudgecraft.Karma.KarmaState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gestor de blocos temporários de terra seca que simula o efeito de "Ice Walker" (Frost Walker).
 * Regista blocos de relva convertidos e restaura-os após algum tempo caso nenhum jogador negativo esteja por perto.
 */
public final class TemporaryBlockManager {

    private static final Map<BlockPos, Long> tempBlocks = new ConcurrentHashMap<>();
    private static final int DECAY_DELAY_TICKS = 80;

    private TemporaryBlockManager() {
    }

    /**
     * Regista um bloco convertido como temporário se ainda não estiver registado.
     *
     * @param pos   A posição do bloco a registar.
     * @param level O nível de servidor em processamento.
     */
    public static void registerTemporaryBlock(BlockPos pos, ServerLevel level) {
        tempBlocks.putIfAbsent(pos.immutable(), level.getGameTime());
    }

    /**
     * Executa a verificação periódica e o decaimento de blocos temporários que já expiraram.
     * Restaura os blocos para relva caso nenhum jogador com Karma negativo esteja no raio de 4 blocos.
     *
     * @param level O nível de servidor.
     */
    public static void tick(ServerLevel level) {
        if (level.getGameTime() % 10 != 0 || tempBlocks.isEmpty()) {
            return;
        }

        long gameTime = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Long>> iterator = tempBlocks.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            BlockPos pos = entry.getKey();
            long registeredTime = entry.getValue();

            if (gameTime - registeredTime >= DECAY_DELAY_TICKS) {
                boolean playerNear = level.players().stream().anyMatch(player -> {
                    KarmaState currentKarma = KarmaEffectManager.getCurrentKarma();
                    boolean hasNegKarma = currentKarma == KarmaState.SNEGATIVE
                            || currentKarma == KarmaState.NEGATIVE
                            || currentKarma == KarmaState.VNEGATIVE;
                    return hasNegKarma && player.blockPosition().distSqr(pos) <= 16;
                });

                if (!playerNear) {
                    BlockState currentState = level.getBlockState(pos);
                    if (currentState.is(Blocks.DIRT) || currentState.is(Blocks.COARSE_DIRT) || currentState.is(Blocks.DIRT_PATH)) {
                        level.setBlockAndUpdate(pos, Blocks.GRASS_BLOCK.defaultBlockState());
                    }
                    iterator.remove();
                }
            }
        }
    }
}
