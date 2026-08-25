package com.nudgecraft.client.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaHudOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin cliente para intercetar o renderizador de nevoeiro (FogRenderer) do Minecraft.
 * Reduz a visibilidade do jogador dinamicamente se este possuir Karma Very Negative
 * e estiver localizado em áreas de fraca iluminação (cavernas ou noite sem tochas).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void onSetupFog(Camera camera, int fogType, DeltaTracker deltaTracker, float viewDistance, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (KarmaHudOverlay.getClientKarma() == KarmaState.VNEGATIVE) {
            int brightness = level.getMaxLocalRawBrightness(camera.blockPosition());
            if (brightness < 7) {
                FogData data = cir.getReturnValue();
                data.renderDistanceStart = 0.0f;
                data.renderDistanceEnd = 14.0f;
                data.environmentalStart = 0.0f;
                data.environmentalEnd = 14.0f;
            }
        }
    }
}
