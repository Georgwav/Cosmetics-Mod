package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.atmosphere.FogControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.1 {
/*import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
*///?} elif >=1.21.6 {
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.objectweb.asm.Opcodes;
//?} elif >=1.21.2 {
/*import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogParameters;
import net.minecraft.client.renderer.FogRenderer;
import org.joml.Vector4f;
*///?} else {
/*import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

/**
 * The theme's fog colour and distances (see {@link FogControl}). Runs after other mods' fog hooks (priority 1100),
 * so Voxy and Distant Horizons, which read the game's fog, get the theme's fog too.
 */
//? if >=26.1 {
/*@Mixin(value = FogRenderer.class, priority = 1100)
abstract class SolFogMixin {
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void cosmeticsmod$fog(CallbackInfoReturnable<FogData> cir) {
        if (!FogControl.active()) {
            return;
        }
        FogData fog = cir.getReturnValue();
        fog.color.set(FogControl.r(), FogControl.g(), FogControl.b(), fog.color.w());
        fog.environmentalStart = FogControl.start(fog.environmentalStart);
        fog.environmentalEnd = FogControl.end(fog.environmentalEnd);
    }
}
*///?} elif >=1.21.6 {
@Mixin(value = FogRenderer.class, priority = 1100)
abstract class SolFogMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void cosmeticsmod$color(CallbackInfoReturnable<Vector4f> cir) {
        if (FogControl.active()) {
            Vector4f c = cir.getReturnValue();
            c.set(FogControl.r(), FogControl.g(), FogControl.b(), c.w());
        }
    }

    @Inject(method = "setupFog", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceStart:F", opcode = Opcodes.PUTFIELD))
    private void cosmeticsmod$distance(CallbackInfoReturnable<Vector4f> cir, @Local FogData fog) {
        if (FogControl.active()) {
            fog.environmentalStart = FogControl.start(fog.environmentalStart);
            fog.environmentalEnd = FogControl.end(fog.environmentalEnd);
        }
    }
}
//?} elif >=1.21.2 {
/*@Mixin(value = FogRenderer.class, priority = 1100)
abstract class SolFogMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private static void cosmeticsmod$color(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                           float darken, CallbackInfoReturnable<Vector4f> cir) {
        if (FogControl.active()) {
            Vector4f c = cir.getReturnValue();
            c.set(FogControl.r(), FogControl.g(), FogControl.b(), c.w());
        }
    }

    @Inject(method = "setupFog", at = @At("RETURN"), cancellable = true)
    private static void cosmeticsmod$distance(Camera camera, FogRenderer.FogMode mode, Vector4f color, float renderDistance,
                                              boolean thick, float partialTick, CallbackInfoReturnable<FogParameters> cir) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN || thick || !FogControl.active()) {
            return;
        }
        FogParameters p = cir.getReturnValue();
        cir.setReturnValue(new FogParameters(FogControl.start(p.start()), FogControl.end(p.end()), p.shape(), p.red(),
                p.green(), p.blue(), p.alpha()));
    }
}
*///?} else {
/*@Mixin(value = FogRenderer.class, priority = 1100)
abstract class SolFogMixin {
    @Shadow
    private static float fogRed;
    @Shadow
    private static float fogGreen;
    @Shadow
    private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void cosmeticsmod$color(Camera camera, float partialTick, ClientLevel level, int renderDistance,
                                           float darken, CallbackInfo ci) {
        if (FogControl.active()) {
            fogRed = FogControl.r();
            fogGreen = FogControl.g();
            fogBlue = FogControl.b();
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
        }
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void cosmeticsmod$distance(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thick,
                                              float partialTick, CallbackInfo ci) {
        if (mode != FogRenderer.FogMode.FOG_TERRAIN || thick || !FogControl.active()) {
            return;
        }
        RenderSystem.setShaderFogStart(FogControl.start(RenderSystem.getShaderFogStart()));
        RenderSystem.setShaderFogEnd(FogControl.end(RenderSystem.getShaderFogEnd()));
    }
}
*///?}
