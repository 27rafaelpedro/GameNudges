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
 * - VPOSITIVE: +200% (3.00x - 3x mais rápido)
 * - POSITIVE:  +100% (2.00x - 2x mais rápido)
 * - SPOSITIVE: +50%  (1.50x - 1.5x mais rápido)
 */
@Mixin(Player.class)
public abstract class PlayerDestroySpeedMixin {

    @Inject(method = "getDestroySpeed", at = @At("RETURN"), cancellable = true)
    private void onGetDestroySpeed(BlockState state, CallbackInfoReturnable<Float> cir) {
        Player player = (Player) (Object) this;

        if (com.nudgecraft.manager.FatigueManager.isFatigued(player.getUUID())) {
            cir.setReturnValue(cir.getReturnValue() * 0.50f);
            return;
        }

        if (player.getMainHandItem().isEmpty()) {
            KarmaState current = KarmaStateHolder.get();
            float multiplier = 1.0f;

            if (current == KarmaState.VPOSITIVE) {
                multiplier = 3.00f;
            } else if (current == KarmaState.POSITIVE) {
                multiplier = 2.00f;
            } else if (current == KarmaState.SPOSITIVE) {
                multiplier = 1.50f;
            }

            if (multiplier > 1.0f) {
                cir.setReturnValue(cir.getReturnValue() * multiplier);
            }
        }
    }
}
