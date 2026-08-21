package com.nudgecraft.event;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.manager.KarmaEffectManager;
import com.nudgecraft.manager.OreVeinManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public final class BlockBreakEventHandler {

    public static void init() {
        PlayerBlockBreakEvents.AFTER.register(BlockBreakEventHandler::onAfterBlockBreak);
    }

    private static void onAfterBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        KarmaState current = KarmaEffectManager.getCurrentKarma();
        boolean isBlessed = OreVeinManager.isBlessedOre(pos);

        if (isBlessed) {
            OreVeinManager.consumeBlessedOre(pos);
        }

        // Verifica se o bloco partido é um minério
        if (OreVeinManager.isOreBlock(state.getBlock())) {
            boolean shouldDuplicate = false;

            if (isBlessed) {
                // 1. Minério abençoado brilhante da veia tem 100% de probabilidade garantida
                shouldDuplicate = true;
            } else {
                // 2. Probabilidade normal de Karma positivo
                float chance = 0.0f;
                if (current == KarmaState.VPOSITIVE) {
                    chance = 0.15f; // 15%
                } else if (current == KarmaState.POSITIVE) {
                    chance = 0.08f; // 8%
                } else if (current == KarmaState.SPOSITIVE) {
                    chance = 0.03f; // 3%
                }

                if (chance > 0.0f && level.getRandom().nextFloat() < chance) {
                    shouldDuplicate = true;
                }
            }

            if (shouldDuplicate) {
                List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, blockEntity, player, player.getMainHandItem());
                for (ItemStack drop : drops) {
                    if (!drop.isEmpty()) {
                        ItemEntity itemEntity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop.copy());
                        itemEntity.setDefaultPickUpDelay();
                        level.addFreshEntity(itemEntity);
                    }
                }

                // Efeito sonoro e partículas de recompensa
                serverLevel.sendParticles(ParticleTypes.WAX_OFF,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        16, 0.4, 0.4, 0.4, 0.1);

                serverLevel.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f);
            }
        }
    }
}
