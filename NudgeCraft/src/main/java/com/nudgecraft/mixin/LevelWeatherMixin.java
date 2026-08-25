package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaStateHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin que ajusta dinamicamente a atmosfera visual, escurecimento e cinzento do céu
 * durante o dia de acordo com o nível progressivo de Karma Negativo.
 */
@Mixin(Level.class)
public abstract class LevelWeatherMixin {

    @Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
    private void injectThunderLevelByKarma(float delta, CallbackInfoReturnable<Float> cir) {
        KarmaState current = KarmaStateHolder.get();
        if (current == KarmaState.SNEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.35f));
        } else if (current == KarmaState.NEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.65f));
        } else if (current == KarmaState.VNEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.90f));
        }
    }

    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void injectRainLevelByKarma(float delta, CallbackInfoReturnable<Float> cir) {
        KarmaState current = KarmaStateHolder.get();
        if (current == KarmaState.SNEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.30f));
        } else if (current == KarmaState.NEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.55f));
        } else if (current == KarmaState.VNEGATIVE) {
            cir.setReturnValue(Math.max(cir.getReturnValue(), 0.80f));
        }
    }
}
