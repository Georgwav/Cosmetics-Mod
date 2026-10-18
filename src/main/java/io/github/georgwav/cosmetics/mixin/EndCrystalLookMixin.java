package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.CrystalPvp;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
//?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}

/**
 * Crystal PvP's crystals: their size (round the middle of their box, so they stay where they are), how fast they
 * turn and bob, and whether they bob at all. From 1.21.2 the render state's age is scaled; before, each rotation by
 * the age in the render and the bob's height.
 */
@Mixin(EndCrystalRenderer.class)
abstract class EndCrystalLookMixin {
    /** The bob's middle height: the game's getY at its halfway point. */
    private static final float STILL_Y = -1.1f;

    //? if >=1.21.2 {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;F)V",
            at = @At("TAIL"), require = 0)
    private void cosmeticsmod$spin(EndCrystal crystal, EndCrystalRenderState state, float partialTick, CallbackInfo ci) {
        io.github.georgwav.cosmetics.sol.Hooks.CRYSTAL_SPIN.hit();
        state.ageInTicks *= CrystalPvp.crystalSpin();
    }

    @Inject(method = "getY(F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$still(float age, CallbackInfoReturnable<Float> cir) {
        if (!CrystalPvp.crystalBounce()) {
            cir.setReturnValue(STILL_Y);
        }
    }
    //?} else {
    /*@Inject(method = "getY(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;F)F", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$still(EndCrystal crystal, float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!CrystalPvp.crystalBounce()) {
            cir.setReturnValue(STILL_Y);
            return;
        }
        float spin = CrystalPvp.crystalSpin();
        if (spin != 1f) {
            // The game's bob, at the crystals' speed.
            float t = (crystal.time + partialTick) * spin;
            float g = net.minecraft.util.Mth.sin(t * 0.2f) / 2f + 0.5f;
            g = (g * g + g) * 0.4f;
            cir.setReturnValue(g - 1.4f);
        }
    }

    /^* The turn: every rotation by the crystal's age, scaled (smooth at any speed, still at 0%). ^/
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotationDegrees(F)Lorg/joml/Quaternionf;"), require = 0)
    private float cosmeticsmod$spin(float degrees) {
        io.github.georgwav.cosmetics.sol.Hooks.CRYSTAL_SPIN.hit();
        return degrees * CrystalPvp.crystalSpin();
    }
    *///?}

    //? if >=26.1 {
    /*@Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), require = 0)
    private void cosmeticsmod$size(EndCrystalRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                   net.minecraft.client.renderer.state.level.CameraRenderState camera, CallbackInfo ci) {
        size(pose);
    }
    *///?} elif >=1.21.9 {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"), require = 0)
    private void cosmeticsmod$size(EndCrystalRenderState state, PoseStack pose, SubmitNodeCollector collector,
                                   net.minecraft.client.renderer.state.CameraRenderState camera, CallbackInfo ci) {
        size(pose);
    }
    //?} elif >=1.21.2 {
    /*@Inject(method = "render(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), require = 0)
    private void cosmeticsmod$size(EndCrystalRenderState state, PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo ci) {
        size(pose);
    }
    *///?} else {
    /*@Inject(method = "render(Lnet/minecraft/world/entity/boss/enderdragon/EndCrystal;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"), require = 0)
    private void cosmeticsmod$size(EndCrystal crystal, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers,
                                   int light, CallbackInfo ci) {
        size(pose);
    }
    *///?}

    /** Scales round the middle of the crystal's box, a block up. */
    private static void size(PoseStack pose) {
        io.github.georgwav.cosmetics.sol.Hooks.CRYSTAL_SIZE.hit();
        float s = CrystalPvp.crystalScale();
        if (s != 1f) {
            pose.translate(0f, 1f, 0f);
            pose.scale(s, s, s);
            pose.translate(0f, -1f, 0f);
        }
    }
}
