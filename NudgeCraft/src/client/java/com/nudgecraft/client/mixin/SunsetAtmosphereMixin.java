package com.nudgecraft.client.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.KarmaHudOverlay;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin cliente dedicado exclusivamente a modificar o tom do céu e da atmosfera (Fog Color).
 * 
 * Aplica de forma progressiva o tom exato do pôr-do-sol (laranja/âmbar) aos estados 
 * de Karma Positivo durante o dia. Interceta a assinatura nativa do Minecraft 1.21
 * de forma 100% segura sem conflitos.
 */
@Mixin(FogRenderer.class)
public abstract class SunsetAtmosphereMixin {

    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void onComputeFogColor(Camera camera, float tickDelta, ClientLevel level, int renderDistance, float bossColorModifier, Vector4f colorOut, CallbackInfo ci) {
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

                // O tom clássico de pôr-do-sol do Minecraft:
                // Red: 1.0 (máximo), Green: 0.52 (mistura para laranja), Blue: 0.15 (âmbar profundo)
                float r = Math.min(1.0f, colorOut.x * (1.0f - warmFactor) + 1.00f * warmFactor);
                float g = Math.min(1.0f, colorOut.y * (1.0f - warmFactor) + 0.52f * warmFactor);
                float b = Math.max(0.0f, colorOut.z * (1.0f - warmFactor) + 0.15f * warmFactor);

                // Modifica o vetor de cor de forma segura sem substituir o objeto de retorno
                colorOut.set(r, g, b, colorOut.w);
            }
        }
    }
}
