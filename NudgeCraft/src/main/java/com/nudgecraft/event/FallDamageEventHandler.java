package com.nudgecraft.event;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.manager.KarmaEffectManager;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.network.chat.Component;

/**
 * Gestor de eventos de dano de queda para o Karma Very Positive.
 * Concede anulacao de quedas pequenas (<= 4 blocos) e uma chance variavel para quedas maiores.
 */
public final class FallDamageEventHandler {

    private FallDamageEventHandler() {
    }

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            if (entity instanceof ServerPlayer player && damageSource.is(DamageTypes.FALL)) {
                if (KarmaEffectManager.getCurrentKarma() == KarmaState.VPOSITIVE) {
                    
                    // O evento já nos diz quanto dano o jogador ia levar (amount).
                    // Dano de queda no Minecraft é sempre (blocos - 3). Logo: blocos reais = dano + 3.
                    int blocks = (int) Math.ceil(amount) + 3;
                    
                    // "up to 4 blocks": 100% de chance. "5+ blocks": 30% de chance.
                    float chance = (blocks <= 4) ? 1.0f : 0.30f;

                    if (player.getRandom().nextFloat() < chance) {
                        if (player.level() instanceof ServerLevel level) {
                            double x = player.getX();
                            double y = player.getY();
                            double z = player.getZ();

                            // 1. Particulas de impacto de vento / Mace (Gust Emitter)
                            level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

                            // 2. Nuvens brancas densas ao nivel do solo
                            level.sendParticles(ParticleTypes.CLOUD, x, y + 0.1, z, 18, 0.6, 0.2, 0.6, 0.08);

                            // 3. Poof suave de fumo branco
                            level.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 12, 0.5, 0.2, 0.5, 0.03);

                            // 4. Som de explosao de vento ("Whoosh")
                            level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2f, 1.1f);

                            // 5. Mensagem para o jogador (na action bar) a verde
                            com.nudgecraft.util.NudgeHelper.sendNudgeMessage(player, Component.literal("§a§oO vento favorece a tua descida!"), true, true, "wind_fall_save");

                            // 6. Reseta a distancia
                            player.resetFallDistance();

                            // Anula o dano de queda
                            return false;
                        }
                    }
                }
            }
            return true;
        });
    }
}
