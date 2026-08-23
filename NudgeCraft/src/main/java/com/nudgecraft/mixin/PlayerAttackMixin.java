package com.nudgecraft.mixin;

import com.nudgecraft.manager.FatigueManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerAttackMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void onPlayerAttack(Entity target, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (!player.level().isClientSide() && player instanceof ServerPlayer serverPlayer && player.level() instanceof ServerLevel serverLevel) {
            FatigueManager.tryTriggerFatigue(serverPlayer, serverLevel);
        }
    }
}
