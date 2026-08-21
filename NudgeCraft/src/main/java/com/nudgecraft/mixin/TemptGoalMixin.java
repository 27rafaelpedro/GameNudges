package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.manager.KarmaEffectManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EntitySelector;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TemptGoal.class)
public abstract class TemptGoalMixin {
    @Shadow @Final protected Mob mob;
    @Shadow protected Player player;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true)
    private void injectTemptByKarma(CallbackInfoReturnable<Boolean> cir) {
        KarmaState state = KarmaEffectManager.getCurrentKarma();
        if (state == KarmaState.POSITIVE || state == KarmaState.SPOSITIVE || state == KarmaState.VPOSITIVE) {
            Player nearest = this.mob.level().getNearestPlayer(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 10.0, EntitySelector.NO_SPECTATORS);
            if (nearest != null) {
                this.player = nearest;
                cir.setReturnValue(true);
            }
        }
    }
}
