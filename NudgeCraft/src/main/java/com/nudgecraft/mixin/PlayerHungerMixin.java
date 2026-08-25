package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaStateHolder;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Player.class)
public abstract class PlayerHungerMixin {

    @ModifyVariable(method = "causeFoodExhaustion", at = @At("HEAD"), argsOnly = true)
    private float modifyFoodExhaustionByKarma(float exhaustion) {
        KarmaState current = KarmaStateHolder.get();
        if (current == KarmaState.SNEGATIVE) {
            return exhaustion * 1.20f; // +20%
        } else if (current == KarmaState.NEGATIVE) {
            return exhaustion * 1.50f; // +50%
        } else if (current == KarmaState.VNEGATIVE) {
            return exhaustion * 2.00f; // +100%
        }
        return exhaustion;
    }
}
