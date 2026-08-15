package com.nudgecraft.mixin;

import com.nudgecraft.Karma.KarmaState;
import com.nudgecraft.Karma.strategy.KarmaEffectManager;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Animal.class)
public abstract class AnimalMixin extends PathfinderMob {

    protected AnimalMixin(net.minecraft.world.entity.EntityType<? extends PathfinderMob> entityType, net.minecraft.world.level.Level level) {
        super(entityType, level);
    }

    @Inject(method = "aiStep", at = @At("TAIL"))
    private void avoidPlayerByKarma(CallbackInfo ci) {
        if (this.level().isClientSide()) {
            return;
        }

        KarmaState state = KarmaEffectManager.getCurrentKarma();
        if (state == KarmaState.NEGATIVE || state == KarmaState.SNEGATIVE || state == KarmaState.VNEGATIVE) {
            Player player = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 8.0, false);
            if (player != null) {
                // If close, panic-move in the opposite direction
                if (this.tickCount % 20 == 0 && !this.getNavigation().isInProgress()) {
                    Vec3 playerPos = player.position();
                    Vec3 animalPos = this.position();
                    Vec3 awayDir = animalPos.subtract(playerPos).normalize();
                    Vec3 targetPos = animalPos.add(awayDir.scale(8.0));
                    this.getNavigation().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.25);
                }
            }
        }
    }
}
