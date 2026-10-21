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

/**
 * Camera: the freelook camera's own direction (and its place behind you in third person), the freecam's place, and the
 * F5 glide's. Up to 1.21.11 after Camera.setup; from 26.1 after alignWithEntity.
 */
@Mixin(Camera.class)
abstract class CameraTweaksMixin {
    @Shadow
    private boolean detached;
    @Shadow
    private Entity entity;
    @Shadow
    private Vec3 position;
    @Unique
    private final double[] cosmeticsmod$glide = new double[3];

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
    private void cosmeticsmod$freelook(float partialTick) {
        io.github.georgwav.cosmetics.sol.Hooks.CAMERA_SETUP.hit();
        cosmeticsmod$turn(partialTick);
        if (entity == null) {
            return;
        }
        // F5 glide, from where the game (and freelook) put the camera.
        double ex = net.minecraft.util.Mth.lerp(partialTick, entity.xo, entity.getX());
        double ey = net.minecraft.util.Mth.lerp(partialTick, entity.yo, entity.getY()) + entity.getEyeHeight();
        double ez = net.minecraft.util.Mth.lerp(partialTick, entity.zo, entity.getZ());
        double[] g = cosmeticsmod$glide;
        if (CameraTweaks.glide(ex, ey, ez, position.x, position.y, position.z, g)) {
            setPosition(new Vec3(g[0], g[1], g[2]));
            if (CameraTweaks.handsHidden()) {
                detached = true;
            }
        }
    }

    @Unique
    private void cosmeticsmod$turn(float partialTick) {
        if (!CameraTweaks.freelooking() || entity == null) {
            return;
        }
        setRotation(CameraTweaks.yaw(), CameraTweaks.pitch());
        if (CameraTweaks.freecam()) {
            setPosition(CameraTweaks.freecamPosition(partialTick));
        } else if (detached) {
            setPosition(entity.getEyePosition(partialTick));
            float scale = entity instanceof LivingEntity living ? living.getScale() : 1f;
            move(-getMaxZoom(4f * scale), 0f, 0f);
        }
    }

    //? if >=26.1 {
    /*@Inject(method = "alignWithEntity(F)V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$align(float partialTick, CallbackInfo ci) {
        cosmeticsmod$freelook(partialTick);
    }
    *///?} elif >=1.21.11 {
    @Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$setup(net.minecraft.world.level.Level level, Entity e, boolean detached, boolean mirrored,
                                    float partialTick, CallbackInfo ci) {
        cosmeticsmod$freelook(partialTick);
    }
    //?} else {
    /*@Inject(method = "setup(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$setup(net.minecraft.world.level.BlockGetter level, Entity e, boolean detached, boolean mirrored,
                                    float partialTick, CallbackInfo ci) {
        cosmeticsmod$freelook(partialTick);
    }
    *///?}
}
