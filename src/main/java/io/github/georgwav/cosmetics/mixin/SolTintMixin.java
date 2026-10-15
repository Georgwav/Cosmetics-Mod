package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.atmosphere.WorldTint;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=26.3 {
/*import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.joml.Vector4fc;
*///?} elif >=1.21.11 {
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
//?} elif >=1.21.2 {
/*import net.minecraft.client.multiplayer.ClientLevel;
*///?} else {
/*import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
*///?}

/**
 * The theme's cloud tint, and from 1.21.11 the light tint too: the colours the game asks its environment for. Up to
 * 1.21.10 the cloud colour comes from the level (the light tint is in {@code SolLightMixin}).
 */
//? if >=1.21.11 {
@Mixin(EnvironmentAttributeProbe.class)
abstract class SolTintMixin {
    @Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$tint(EnvironmentAttribute<?> attribute, float partialTick, CallbackInfoReturnable<Object> cir) {
        //? if >=26.3 {
        /*if (attribute == EnvironmentAttributes.CLOUD_COLOR && cir.getReturnValue() instanceof Vector4fc c) {
            int argb = io.github.georgwav.cosmetics.sol.util.Colors.argbF(c.w(), c.x(), c.y(), c.z());
            int out = WorldTint.cloud(argb);
            if (out != argb) {
                cir.setReturnValue(new Vector4f(((out >> 16) & 0xFF) / 255f, ((out >> 8) & 0xFF) / 255f, (out & 0xFF) / 255f, c.w()));
            }
        } else if (attribute == EnvironmentAttributes.SKY_LIGHT_COLOR && cir.getReturnValue() instanceof Vector3fc c) {
            Vector3f v = new Vector3f(c);
            WorldTint.light(v);
            cir.setReturnValue(v);
        }
        *///?} else {
        if (attribute == EnvironmentAttributes.CLOUD_COLOR && cir.getReturnValue() instanceof Integer c) {
            int out = WorldTint.cloud(c);
            if (out != c) {
                cir.setReturnValue(out);
            }
        } else if (attribute == EnvironmentAttributes.SKY_LIGHT_COLOR && cir.getReturnValue() instanceof Integer c) {
            int out = WorldTint.lightRgb(c);
            if (out != c) {
                cir.setReturnValue(out);
            }
        }
        //?}
    }
}
//?} elif >=1.21.2 {
/*@Mixin(ClientLevel.class)
abstract class SolTintMixin {
    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$clouds(float partialTick, CallbackInfoReturnable<Integer> cir) {
        int c = cir.getReturnValue();
        int out = WorldTint.cloud(c);
        if (out != c) {
            cir.setReturnValue(out);
        }
    }
}
*///?} else {
/*@Mixin(ClientLevel.class)
abstract class SolTintMixin {
    @Inject(method = "getCloudColor", at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$clouds(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Vec3 v = cir.getReturnValue();
        int c = 0xFF000000 | ((int) (v.x * 255) << 16) | ((int) (v.y * 255) << 8) | (int) (v.z * 255);
        int out = WorldTint.cloud(c);
        if (out != c) {
            cir.setReturnValue(new Vec3(((out >> 16) & 0xFF) / 255.0, ((out >> 8) & 0xFF) / 255.0, (out & 0xFF) / 255.0));
        }
    }
}
*///?}
