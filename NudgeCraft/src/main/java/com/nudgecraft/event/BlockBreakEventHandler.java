package com.nudgecraft.event;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.manager.KarmaEffectManager;
import com.nudgecraft.manager.OreVeinManager;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
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
        PlayerBlockBreakEvents.BEFORE.register(BlockBreakEventHandler::onBeforeBlockBreak);
        PlayerBlockBreakEvents.AFTER.register(BlockBreakEventHandler::onAfterBlockBreak);
    }

    /**
     * Acionado antes do bloco ser quebrado.
     * No Karma VNEGATIVE, aplica 4% de probabilidade de invocar um Silverfish em vez de dropar o item minerado.
     */
    private static boolean onBeforeBlockBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return true;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            com.nudgecraft.manager.FatigueManager.tryTriggerFatigue(serverPlayer, serverLevel);
        }

        // Apenas para Very Negative
        if (KarmaEffectManager.getCurrentKarma() == KarmaState.VNEGATIVE) {
            // Verifica se o bloco partido é de mineração (picareta ou minério)
            boolean isMiningBlock = state.is(BlockTags.MINEABLE_WITH_PICKAXE) || OreVeinManager.isOreBlock(state.getBlock());

            if (isMiningBlock && level.getRandom().nextFloat() < 0.04f) { // 4% de probabilidade
                // Remove o bloco primeiro para nao asfixiar nem fundir o silverfish
                serverLevel.destroyBlock(pos, false);
                
                Entity silverfish = EntityTypes.SILVERFISH.spawn(serverLevel, pos, EntitySpawnReason.EVENT);
                if (silverfish != null) {
                    silverfish.addTag("KarmaPenaltySilverfish");
                    if (silverfish instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setTarget(player);
                        mob.setPersistenceRequired(); // Impede despawn imediato
                        mob.setInvulnerable(true); // Impede que morra acidentalmente (só morre ao morder)
                    }
                }

                // Efeitos sonoros e visuais de infestação de silverfish
                serverLevel.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        10, 0.25, 0.25, 0.25, 0.03);
                serverLevel.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        6, 0.2, 0.2, 0.2, 0.02);

                serverLevel.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.SILVERFISH_AMBIENT, SoundSource.HOSTILE, 1.0f, 1.0f);
                net.minecraft.network.chat.Component msg = com.nudgecraft.util.NudgeMessages.getMiningCurseMessage();
                    if (msg != null) com.nudgecraft.util.NudgeHelper.sendNudgeMessage((ServerPlayer) player, msg, true, false, "silverfish_spawn");

                // Cancela o drop e o evento normal de quebra
                return false;
            }
        }

        return true;
    }

    /**
     * Acionado após a quebra do bloco para gerir double drops nos Karmas Positivos.
     */
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
