package com.nudgecraft.client.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaHudOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin para modificar o estado de renderização de todo o céu.
 * Interceta a extração de estados e tinge a cúpula do céu (skyColor) com 
 * tons de pôr-do-sol progressivos no Karma Positivo.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(ClientLevel level, float tickDelta, Camera camera, SkyRenderState state, CallbackInfo ci) {
        KarmaState karma = KarmaHudOverlay.getClientKarma();

        if (karma == KarmaState.SPOSITIVE || karma == KarmaState.POSITIVE || karma == KarmaState.VPOSITIVE) {
            // Apenas aplicável durante o dia (quando a luz do sol está presente)
            if (!level.isDarkOutside()) {
                float warmFactor = switch (karma) {
                    case VPOSITIVE -> 0.75f;
                    case POSITIVE  -> 0.40f;
                    case SPOSITIVE -> 0.22f;
                    default -> 0.0f;
                };

                int originalColor = state.skyColor;
                int a = (originalColor >> 24) & 0xFF;
                int r = (originalColor >> 16) & 0xFF;
                int g = (originalColor >> 8) & 0xFF;
                int b = originalColor & 0xFF;

                // Tom de Pôr-do-Sol: RGB(255, 132, 38)
                r = Math.min(255, (int) (r * (1.0f - warmFactor) + 255 * warmFactor));
                g = Math.min(255, (int) (g * (1.0f - warmFactor) + 132 * warmFactor));
                b = Math.max(0,   (int) (b * (1.0f - warmFactor) + 38 * warmFactor));

                state.skyColor = (a << 24) | (r << 16) | (g << 8) | b;
                
                // Aplicar também ao disco brilhante do sol nascente/poente
                int originalSunrise = state.sunriseAndSunsetColor;
                if (originalSunrise != 0) {
                    int sa = (originalSunrise >> 24) & 0xFF;
                    int sr = (originalSunrise >> 16) & 0xFF;
                    int sg = (originalSunrise >> 8) & 0xFF;
                    int sb = originalSunrise & 0xFF;

                    sr = Math.min(255, (int) (sr * (1.0f - warmFactor) + 255 * warmFactor));
                    sg = Math.min(255, (int) (sg * (1.0f - warmFactor) + 132 * warmFactor));
                    sb = Math.max(0,   (int) (sb * (1.0f - warmFactor) + 38 * warmFactor));

                    state.sunriseAndSunsetColor = (sa << 24) | (sr << 16) | (sg << 8) | sb;
                }
            }
        }
    }
}
