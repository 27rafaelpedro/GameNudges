package com.nudgecraft.client.mixin;

import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Collection;

/**
 * Mixin cliente para filtrar os efeitos visíveis no inventário do jogador.
 * Impede que efeitos com showIcon = false (como a Visão Noturna condicional do mod)
 * apareçam no ecrã de inventário.
 */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {

    /**
     * Modifica o parâmetro de efeitos recebido no método extractEffects para filtrar
     * e reter apenas os efeitos que devem mostrar ícone (showIcon() == true).
     */
    @ModifyVariable(method = "extractEffects", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Collection<MobEffectInstance> filterVisibleEffectsInInventory(Collection<MobEffectInstance> effects) {
        if (effects == null || effects.isEmpty()) {
            return effects;
        }
        return effects.stream()
                .filter(MobEffectInstance::showIcon)
                .toList();
    }
}
