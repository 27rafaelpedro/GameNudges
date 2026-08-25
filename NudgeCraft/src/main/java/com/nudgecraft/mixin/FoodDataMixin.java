package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.manager.KarmaEffectManager;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(FoodData.class)
public abstract class FoodDataMixin {

    @ModifyVariable(method = "add(IF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int modifyFoodLevel(int originalNutrition) {
        KarmaState current = com.nudgecraft.Karma.KarmaStateHolder.get();
        int penalty = 0;
        
        if (current == KarmaState.SNEGATIVE) {
            penalty = 1; // 0.5 icon
        } else if (current == KarmaState.NEGATIVE) {
            penalty = 2; // 1.0 icon
        } else if (current == KarmaState.VNEGATIVE) {
            penalty = 4; // 2.0 icons
        }

        if (penalty > 0) {
            // Aplica a penalidade fixa, mas garante que a comida nunca perde mais de 50% do seu valor (e no mínimo 1)
            int minAllowed = Math.max(1, (int)(originalNutrition * 0.5f));
            return Math.max(minAllowed, originalNutrition - penalty);
        }
        
        return originalNutrition;
    }

    @ModifyVariable(method = "add(IF)V", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float modifySaturationLevel(float originalSaturation) {
        // Para simplificar e não mexer com números decimais arbitrários, 
        // escalamos a saturação na mesma proporção que a nutrição foi reduzida!
        // No entanto, o Mixin não passa os dois argumentos ao mesmo tempo no ModifyVariable.
        // Assim, usamos a mesma lógica de penalidade:
        KarmaState current = com.nudgecraft.Karma.KarmaStateHolder.get();
        float penalty = 0;
        
        if (current == KarmaState.SNEGATIVE) {
            penalty = 1.0f;
        } else if (current == KarmaState.NEGATIVE) {
            penalty = 2.0f;
        } else if (current == KarmaState.VNEGATIVE) {
            penalty = 4.0f;
        }

        if (penalty > 0.0f) {
            float minAllowed = originalSaturation * 0.5f;
            return Math.max(minAllowed, originalSaturation - penalty);
        }
        
        return originalSaturation;
    }
}
