package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaStateHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin para aumentar a velocidade com que o jogador parte blocos com os punhos (mãos desarmadas)
 * nos estados de Karma Positivo:
 * - VPOSITIVE: +50% (1.50x)
 * - POSITIVE:  +20% (1.20x)
 * - SPOSITIVE: +10% (1.10x)
 */
@Mixin(Player.class)
public abstract class PlayerDestroySpeedMixin {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void onGetDestroySpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;

        // Se estiver sob efeito de Fadiga de Karma Negativo, reduz a velocidade para metade
        if (com.nudgecraft.manager.FatigueManager.isFatigued(player.getUUID())) {
            cir.setReturnValue(cir.getReturnValue() * 0.50f);
            return;
        }

        if (player.getMainHandItem().isEmpty()) {
            KarmaState current = KarmaStateHolder.get();
            float multiplier = 1.0f;

            if (current == KarmaState.VPOSITIVE) {
                multiplier = 1.50f; // +50%
            } else if (current == KarmaState.POSITIVE) {
                multiplier = 1.20f; // +20%
            } else if (current == KarmaState.SPOSITIVE) {
                multiplier = 1.10f; // +10%
            }

            if (multiplier > 1.0f) {
                cir.setReturnValue(cir.getReturnValue() * multiplier);
            }
        }
    }
}
