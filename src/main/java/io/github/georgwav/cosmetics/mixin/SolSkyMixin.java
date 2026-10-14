package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.atmosphere.SkyPass;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
*///?} elif >=26.1 {
/*import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.level.SkyRenderState;
*///?} elif >=1.21.11 {
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
//?} elif >=1.21.9 {
/*import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.world.phys.Vec3;
*///?} elif >=1.21.2 {
/*import net.minecraft.client.renderer.SkyRenderer;
*///?} else {
/*import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
*///?}

/**
 * Puts the Sol sky in the game's sky pass. Where Atmosphere is on, the Sol sky is drawn at the start of the
 * overworld sky and the game's sky disc, sunrise fan, sun, moon, stars and dark disc are skipped; with it off,
 * nothing here changes anything. From 1.21.9 the sky's state (sun angle, moon phase, rain) is read where the game
 * extracts it.
 */
//? if >=26.3 {
/*@Mixin(SkyRenderer.class)
abstract class SolSkyMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cosmeticsmod$state(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        FrameInfo.sunAngle = state.sunAngle;
        FrameInfo.moonPhase = state.moonPhase.ordinal();
        FrameInfo.rainBrightness = state.rainBrightness;
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sky(GpuBufferSlice fog, SkyRenderState state, CallbackInfo ci) {
        if (state.skybox == DimensionType.Skybox.OVERWORLD
                && SkyPass.render(state.sunAngle, state.moonPhase.ordinal(), state.rainBrightness)) {
            ci.cancel();
        }
    }
}
*///?} elif >=1.21.9 {
@Mixin(SkyRenderer.class)
abstract class SolSkyMixin {
    //? if >=1.21.11 {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cosmeticsmod$state(ClientLevel level, float partialTicks, Camera camera, SkyRenderState state, CallbackInfo ci) {
        FrameInfo.sunAngle = state.sunAngle;
        FrameInfo.moonPhase = state.moonPhase.ordinal();
        FrameInfo.rainBrightness = state.rainBrightness;
    }
    //?} else {
    /*@Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cosmeticsmod$state(ClientLevel level, float partialTicks, Vec3 camera, SkyRenderState state, CallbackInfo ci) {
        FrameInfo.sunAngle = state.sunAngle;
        FrameInfo.moonPhase = state.moonPhase;
        FrameInfo.rainBrightness = state.rainBrightness;
    }
    *///?}

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sky(CallbackInfo ci) {
        if (SkyPass.render(FrameInfo.sunAngle, FrameInfo.moonPhase, FrameInfo.rainBrightness)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sunrise(CallbackInfo ci) {
        if (SkyPass.drewThisFrame) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sunMoonStars(CallbackInfo ci) {
        if (SkyPass.drewThisFrame) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$darkDisc(CallbackInfo ci) {
        if (SkyPass.drewThisFrame) {
            ci.cancel();
        }
    }
}
//?} elif >=1.21.2 {
/*@Mixin(SkyRenderer.class)
abstract class SolSkyMixin {
    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sky(CallbackInfo ci) {
        if (SkyPass.render(FrameInfo.sunAngle, FrameInfo.moonPhase, FrameInfo.rainBrightness)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sunrise(CallbackInfo ci) {
        if (SkyPass.drewThisFrame) {
            ci.cancel();
        }
    }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sunMoonStars(CallbackInfo ci) {
        if (SkyPass.drewThisFrame) {
            ci.cancel();
        }
    }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$darkDisc(CallbackInfo ci) {
        if (SkyPass.drewThisFrame) {
            ci.cancel();
        }
    }
}
*///?} else {
/*@Mixin(LevelRenderer.class)
abstract class SolSkyMixin {
    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$sky(Matrix4f frustum, Matrix4f projection, float partialTick, Camera camera, boolean foggy,
                                  Runnable fogSetup, CallbackInfo ci) {
        var level = Minecraft.getInstance().level;
        if (foggy || level == null || level.effects().skyType() != DimensionSpecialEffects.SkyType.NORMAL) {
            return;
        }
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA || FrameInfo.skyHidden) {
            return;
        }
        if (SkyPass.render(FrameInfo.sunAngle, FrameInfo.moonPhase, FrameInfo.rainBrightness)) {
            ci.cancel();
        }
    }
}
*///?}
