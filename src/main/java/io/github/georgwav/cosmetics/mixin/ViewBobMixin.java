package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.CameraTweaks;
import io.github.georgwav.cosmetics.client.modules.PvpTweaks;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.level.CameraRenderState;
*///?}

/**
 * The world's view bobbing (Camera: off while the hand keeps it) and the hurt tilt (PvP Tweaks: scaled from none to
 * the game's), around the game's own bobView and bobHurt calls.
 */
@Mixin(GameRenderer.class)
abstract class ViewBobMixin {
    //? if >=26.1 {
    /*@Shadow
    private void bobView(CameraRenderState camera, PoseStack pose) {
        throw new AssertionError();
    }

    @Shadow
    private void bobHurt(CameraRenderState camera, PoseStack pose) {
        throw new AssertionError();
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"),
            require = 0)
    private void cosmeticsmod$worldBob(GameRenderer self, CameraRenderState camera, PoseStack pose) {
        io.github.georgwav.cosmetics.sol.Hooks.WORLD_BOB.hit();
        if (CameraTweaks.worldBobs()) {
            bobView(camera, pose);
        }
    }

    @Redirect(method = {"renderLevel", "renderItemInHand"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V"),
            require = 0)
    private void cosmeticsmod$hurt(GameRenderer self, CameraRenderState camera, PoseStack pose) {
        io.github.georgwav.cosmetics.sol.Hooks.HURT_BOB.hit();
        float k = PvpTweaks.hurtCam();
        if (k >= 0.999f) {
            bobHurt(camera, pose);
        } else if (k > 0.001f) {
            PoseStack alone = new PoseStack();
            bobHurt(camera, alone);
            pose.rotateAround(scaled(alone, k), 0f, 0f, 0f);
        }
    }
    *///?} else {
    @Shadow
    private void bobView(PoseStack pose, float partialTick) {
        throw new AssertionError();
    }

    @Shadow
    private void bobHurt(PoseStack pose, float partialTick) {
        throw new AssertionError();
    }

    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;bobView(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"), require = 0)
    private void cosmeticsmod$worldBob(GameRenderer self, PoseStack pose, float partialTick) {
        io.github.georgwav.cosmetics.sol.Hooks.WORLD_BOB.hit();
        if (CameraTweaks.worldBobs()) {
            bobView(pose, partialTick);
        }
    }

    @Redirect(method = {"renderLevel", "renderItemInHand"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/GameRenderer;bobHurt(Lcom/mojang/blaze3d/vertex/PoseStack;F)V"), require = 0)
    private void cosmeticsmod$hurt(GameRenderer self, PoseStack pose, float partialTick) {
        io.github.georgwav.cosmetics.sol.Hooks.HURT_BOB.hit();
        float k = PvpTweaks.hurtCam();
        if (k >= 0.999f) {
            bobHurt(pose, partialTick);
        } else if (k > 0.001f) {
            PoseStack alone = new PoseStack();
            bobHurt(alone, partialTick);
            pose.rotateAround(scaled(alone, k), 0f, 0f, 0f);
        }
    }
    //?}

    /** The rotation the hurt tilt made, scaled toward none by {@code k}. */
    private static Quaternionf scaled(PoseStack alone, float k) {
        Quaternionf full = alone.last().pose().getNormalizedRotation(new Quaternionf());
        return new Quaternionf().slerp(full, k);
    }
}
