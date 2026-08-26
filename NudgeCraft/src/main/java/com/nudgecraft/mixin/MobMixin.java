package com.nudgecraft.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Silverfish;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void onDoHurtTarget(ServerLevel level, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            Mob self = (Mob) (Object) this;
            if (self instanceof Silverfish && self.entityTags().contains("KarmaPenaltySilverfish")) {
                self.kill(level);
            }
        }
    }
}