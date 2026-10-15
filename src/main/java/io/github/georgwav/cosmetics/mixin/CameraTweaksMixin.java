package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.CameraTweaks;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Camera: the freelook camera's own direction (and its place behind you in third person), the third-person
 * distance, and the camera going through walls. Up to 1.21.11 after Camera.setup; from 26.1 after alignWithEntity.
 */
@Mixin(Camera.class)
abstract class CameraTweaksMixin {
    @Shadow
    private boolean detached;
    @Shadow
    private Entity entity;
    @Unique
    private boolean cosmeticsmod$zooming;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    protected abstract void setPosition(Vec3 position);

    @Shadow
    protected abstract void move(float forward, float up, float left);

    @Shadow
    private float getMaxZoom(float distance) {
        throw new AssertionError();
    }

    @Unique
    private void cosmeticsmod$freelook(float partialTick, boolean mirrored) {
        io.github.georgwav.cosmetics.sol.Hooks.CAMERA_SETUP.hit();
        // Over the shoulder in third person: the camera moved to one side.
        float shoulder = CameraTweaks.shoulder();
        if (detached && shoulder != 0f && entity != null && !CameraTweaks.freelooking()) {
            move(0f, 0f, mirrored ? shoulder : -shoulder);
        }
        if (!CameraTweaks.freelooking() || entity == null) {
            return;
        }
        setRotation(CameraTweaks.yaw(), CameraTweaks.pitch());
        if (detached) {
            setPosition(entity.getEyePosition(partialTick));
            float scale = entity instanceof LivingEntity living ? living.getScale() : 1f;
            move(-getMaxZoom(4f * scale), 0f, 0f);
        }
    }

    //? if >=26.1 {
    /*@Inject(method = "alignWithEntity(F)V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$align(float partialTick, CallbackInfo ci) {
        cosmeticsmod$freelook(partialTick, false);
    }
    *///?} elif >=1.21.11 {
    @Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$setup(net.minecraft.world.level.Level level, Entity e, boolean detached, boolean mirrored,
                                    float partialTick, CallbackInfo ci) {
        cosmeticsmod$freelook(partialTick, mirrored);
    }
    //?} else {
    /*@Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$setup(net.minecraft.world.level.BlockGetter level, Entity e, boolean detached, boolean mirrored,
                                    float partialTick, CallbackInfo ci) {
        cosmeticsmod$freelook(partialTick, mirrored);
    }
    *///?}

    /** The third-person distance, and no stopping at walls when asked. */
    @Inject(method = "getMaxZoom(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$distance(float distance, CallbackInfoReturnable<Float> cir) {
        io.github.georgwav.cosmetics.sol.Hooks.MAX_ZOOM.hit();
        if (cosmeticsmod$zooming) {
            return;
        }
        float want = CameraTweaks.distance(distance);
        if (CameraTweaks.noClip()) {
            cir.setReturnValue(want);
        } else if (want != distance) {
            cosmeticsmod$zooming = true;
            try {
                cir.setReturnValue(getMaxZoom(want));
            } finally {
                cosmeticsmod$zooming = false;
            }
        }
    }
}
