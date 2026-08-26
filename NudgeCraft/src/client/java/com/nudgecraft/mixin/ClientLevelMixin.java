package com.nudgecraft.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.nudgecraft.mixin.LevelAccessor;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {
    @Inject(method = "playLocalSound(DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FFZ)V", at = @At("HEAD"), cancellable = true)
    private void onPlayLocalSound(double x, double y, double z, SoundEvent sound, SoundSource category, float volume, float pitch, boolean distanceDelay, CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        float realRainLevel = ((LevelAccessor) level).getRealRainLevel();
        if (realRainLevel <= 0.2f && (sound == SoundEvents.WEATHER_RAIN || sound == SoundEvents.WEATHER_RAIN_ABOVE)) {
            ci.cancel();
        }
    }

    @Inject(method = "tickWeatherEffects", at = @At("HEAD"), cancellable = true)
    private void onTickWeatherEffects(CallbackInfo ci) {
        ClientLevel level = (ClientLevel) (Object) this;
        float realRainLevel = ((LevelAccessor) level).getRealRainLevel();
        if (realRainLevel <= 0.0f) {
            ci.cancel();
        }
    }
}
