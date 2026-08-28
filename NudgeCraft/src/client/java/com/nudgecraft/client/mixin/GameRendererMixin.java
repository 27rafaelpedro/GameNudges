package com.nudgecraft.client.mixin;

import com.nudgecraft.Karma.KarmaHudOverlay;
import com.nudgecraft.Karma.KarmaState;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    private void setPostEffect(Identifier identifier) { }

    @Shadow
    public abstract void clearPostEffect();

    @Shadow
    public abstract Identifier currentPostEffect();

    private static final Identifier SATURATION_SHADER = Identifier.fromNamespaceAndPath("nudgecraft", "saturation_filter");

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        KarmaState karma = KarmaHudOverlay.getClientKarma();
        boolean isPositive = (karma == KarmaState.POSITIVE || karma == KarmaState.VPOSITIVE || karma == KarmaState.SPOSITIVE);

        if (isPositive) {
            Identifier current = currentPostEffect();
            if (current == null || !current.equals(SATURATION_SHADER)) {
                this.setPostEffect(SATURATION_SHADER);
            }
        } else {
            Identifier current = currentPostEffect();
            if (current != null && current.equals(SATURATION_SHADER)) {
                this.clearPostEffect();
            }
        }
    }
}

