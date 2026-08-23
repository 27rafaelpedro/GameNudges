package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaStateHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin que simula a atmosfera e o céu escuro de trovoada (Thunderstorm)
 * para os estados de Karma Negativo, de forma suave e nativa do motor do Minecraft.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void injectThunderLevelByKarma(float delta, CallbackInfoReturnable<Float> cir) {
        KarmaState current = KarmaStateHolder.get();
        if (current == KarmaState.SNEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.65f));
        } else if (current == KarmaState.NEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.85f));
        } else if (current == KarmaState.VNEGATIVE) {
            cir.setReturnValue(1.0f);
        }
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void injectRainLevelByKarma(float delta, CallbackInfoReturnable<Float> cir) {
        KarmaState current = KarmaStateHolder.get();
        if (current == KarmaState.SNEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.40f));
        } else if (current == KarmaState.NEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.60f));
        } else if (current == KarmaState.VNEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.80f));
        }
    }
}
