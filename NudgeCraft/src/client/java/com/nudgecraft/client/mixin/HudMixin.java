package com.nudgecraft.client.mixin;

import com.nudgecraft.Karma.KarmaHudOverlay;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow public abstract boolean isHidden();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderKarmaHud(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!isHidden()) {
            KarmaHudOverlay.render(graphicsExtractor, deltaTracker);
        }
    }
}
