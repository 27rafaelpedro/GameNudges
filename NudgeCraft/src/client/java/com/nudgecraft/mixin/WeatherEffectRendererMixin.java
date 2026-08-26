package com.nudgecraft.mixin;

import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.nudgecraft.mixin.LevelAccessor;

@Mixin(WeatherEffectRenderer.class)
public class WeatherEffectRendererMixin {
    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(ClientLevel level, float delta, Vec3 cameraPos, WeatherRenderState state, CallbackInfo ci) {
        float realRainLevel = ((LevelAccessor) level).getRealRainLevel();
        if (realRainLevel <= 0.2f) {
            state.rainColumns.clear();
            state.snowColumns.clear();
            state.intensity = 0.0f;
        }
    }
}
