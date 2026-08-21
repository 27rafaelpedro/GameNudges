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

/**
 * Gestor de eventos de dano de queda para o Karma Very Positive.
 * Concede uma chance (30%) de amortecer e anular o dano de queda com efeito de vento e nuvens estilo Mace.
 */
public final class FallDamageEventHandler {

    private static final float CHANCE_ANULACAO = 0.30f; // 30% de probabilidade

    private FallDamageEventHandler() {
    }

    public static void init() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, damageSource, amount) -> {
            if (entity instanceof ServerPlayer player && damageSource.is(DamageTypes.FALL)) {
                if (KarmaEffectManager.getCurrentKarma() == KarmaState.VPOSITIVE) {
                    if (player.getRandom().nextFloat() < CHANCE_ANULACAO) {
                        if (player.level() instanceof ServerLevel level) {
                            double x = player.getX();
                            double y = player.getY();
                            double z = player.getZ();

                            // 1. Partículas de impacto de vento / Mace (Gust Emitter)
                            level.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, x, y, z, 1, 0.0, 0.0, 0.0, 0.0);

                            // 2. Nuvens brancas densas ao nível do solo
                            level.sendParticles(ParticleTypes.CLOUD, x, y + 0.1, z, 18, 0.6, 0.2, 0.6, 0.08);

                            // 3. Poof suave de fumo branco
                            level.sendParticles(ParticleTypes.POOF, x, y + 0.1, z, 12, 0.5, 0.2, 0.5, 0.03);

                            // 4. Som de explosão de vento (Wind Charge / Mace)
                            level.playSound(null, x, y, z, SoundEvents.WIND_CHARGE_BURST, SoundSource.PLAYERS, 1.2f, 1.1f);

                            // 5. Reset da distância de queda do jogador
                            player.resetFallDistance();

                            // Anula 100% do dano de queda
                            return false;
                        }
                    }
                }
            }
            return true;
        });
    }
}
