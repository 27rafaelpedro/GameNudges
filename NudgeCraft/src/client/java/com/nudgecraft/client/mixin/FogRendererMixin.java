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

    private static boolean hasWarnedVision = false;
    private static boolean wasInFog = false;

    @Inject(method = "setupFog", at = @At("RETURN"))
    private void onSetupFog(Camera camera, int fogType, DeltaTracker deltaTracker, float viewDistance, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        KarmaState karma = KarmaHudOverlay.getClientKarma();

        if (karma == KarmaState.SNEGATIVE || karma == KarmaState.NEGATIVE || karma == KarmaState.VNEGATIVE) {
            int brightness = level.getMaxLocalRawBrightness(camera.blockPosition());
            if (brightness < 7) {
                float fogEnd = switch(karma) {
                    case SNEGATIVE -> 32.0f;
                    case NEGATIVE -> 20.0f;
                    case VNEGATIVE -> 14.0f;
                    default -> 32.0f;
                };

                FogData data = cir.getReturnValue();
                data.renderDistanceStart = 0.0f;
                data.renderDistanceEnd = fogEnd;
                data.environmentalStart = 0.0f;
                data.environmentalEnd = fogEnd;

                if (!wasInFog) {
                    wasInFog = true;
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    if (client.player != null) {
                        // Reproduzir som ambiente de caverna (som muffle/susto) ao entrar na escuridao
                        client.player.playSound(net.minecraft.sounds.SoundEvents.AMBIENT_CAVE.value(), 1.0f, 0.8f);
                        
                        // Enviar mensagem apenas a primeira vez na sessao para a action bar
                        if (!hasWarnedVision) {
                            hasWarnedVision = true;
                            client.gui.hud.setOverlayMessage(net.minecraft.network.chat.Component.literal("§c§oA tua visao esta cansada..."), false);
                        }
                    }
                }
            } else {
                wasInFog = false;
            }
        } else {
            wasInFog = false;
        }
    }
}
