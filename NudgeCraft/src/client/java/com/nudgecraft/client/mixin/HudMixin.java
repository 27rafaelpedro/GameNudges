package com.nudgecraft.client.mixin;

import com.nudgecraft.Karma.KarmaHudOverlay;
import com.nudgecraft.Karma.KarmaState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin cliente para intercetar o ecrã de jogo principal (Hud).
 * Renderiza a barra de Karma no ecrã de jogo e normaliza o aspeto da barra de comida.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Shadow public abstract boolean isHidden();

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void renderKarmaHud(GuiGraphicsExtractor graphicsExtractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!isHidden()) {
            KarmaHudOverlay.render(graphicsExtractor, deltaTracker);
        }
    }

    /**
     * Redireciona a verificação de efeitos de poção na barra de comida (extractFood).
     * Retorna false se o efeito a ser verificado for HUNGER e o jogador tiver Karma
     * NEGATIVE ou VNEGATIVE, ocultando o efeito visual verde de fome nos ícones da barra de comida.
     */
    @Redirect(
            method = "extractFood",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;hasEffect(Lnet/minecraft/core/Holder;)Z")
    )
    private boolean onHasEffectInFoodBar(Player player, Holder<MobEffect> effect) {
        if (effect.is(MobEffects.HUNGER)) {
            KarmaState clientKarma = KarmaHudOverlay.getClientKarma();
            if (clientKarma == KarmaState.NEGATIVE || clientKarma == KarmaState.VNEGATIVE) {
                return false; // Mantém a barra de comida com o visual normal (castanho)
            }
        }
        return player.hasEffect(effect);
    }
}
