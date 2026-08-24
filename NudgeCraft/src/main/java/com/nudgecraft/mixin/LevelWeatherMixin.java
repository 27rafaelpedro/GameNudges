package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaStateHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin que ajusta de forma subtil a iluminação e atmosfera sombria
 * nos estados de Karma Negativo apenas durante tempestades reais.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void injectThunderLevelByKarma(float delta, CallbackInfoReturnable<Float> cir) {
        KarmaState current = KarmaStateHolder.get();
        // Apenas intensifica se o mundo já estiver em trovoada real
        if (cir.getReturnValue() > 0.0f) {
            if (current == KarmaState.NEGATIVE) {
                cir.setReturnValue(Math.max(cir.getReturnValue(), 0.50f));
            } else if (current == KarmaState.VNEGATIVE) {
                cir.setReturnValue(Math.max(cir.getReturnValue(), 0.80f));
            }
        }
    }
}
