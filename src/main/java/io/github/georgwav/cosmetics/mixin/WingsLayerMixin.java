package io.github.georgwav.cosmetics.mixin;

// The game's elytra on your player (see ElytraLook): not drawn during your elytra emote, and drawn from your posed
// body during any other emote (the body's pose is put on the pose stack just before the layer moves the elytra
// behind the back, as the cape layer does with the body), except while crouching: the elytra copies the crouch's
// lean itself, and the body's would lean it twice. With "Show on elytra" on, your cape's design is on it (CapeElytra):
// the game picks the texture in getPlayerElytraTexture from 1.21.2, in the middle of render before. The layer is
// ElytraLayer before 1.21.2, WingsLayer after, taking a render state from 1.21.2 and submitting instead of rendering
// from 1.21.9.
//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.CapeElytra;
import io.github.georgwav.cosmetics.render.ElytraLook;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WingsLayer.class)
abstract class WingsLayerMixin {
    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$hide(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                   HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar && ElytraLook.hidden(avatar.id)) {
            ElytraLook.hiddenFrames++;
            ci.cancel();
        }
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void cosmeticsmod$followBody(PoseStack poseStack, SubmitNodeCollector collector, int light,
                                         HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof AvatarRenderState avatar && !state.isCrouching && ElytraLook.followsBody(avatar.id)
                && ((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model) {
            model.body.translateAndRotate(poseStack);
            ElytraLook.followedFrames++;
        }
    }

    // Identifier from 1.21.11, ResourceLocation before: one handler for both (the type is erased).
    @Inject(method = "getPlayerElytraTexture", at = @At("HEAD"), cancellable = true)
    private static void cosmeticsmod$capeTexture(HumanoidRenderState state, CallbackInfoReturnable<Object> cir) {
        if (state instanceof AvatarRenderState avatar) {
            Object texture = CapeElytra.texture(avatar.id);
            if (texture != null) {
                cir.setReturnValue(texture);
            }
        }
    }
}
//?} elif >=1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.CapeElytra;
import io.github.georgwav.cosmetics.render.ElytraLook;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.WingsLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WingsLayer.class)
abstract class WingsLayerMixin {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$hide(PoseStack poseStack, MultiBufferSource buffers, int light,
                                   HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof PlayerRenderState player && ElytraLook.hidden(player.id)) {
            ElytraLook.hiddenFrames++;
            ci.cancel();
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/state/HumanoidRenderState;FF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void cosmeticsmod$followBody(PoseStack poseStack, MultiBufferSource buffers, int light,
                                         HumanoidRenderState state, float yRot, float xRot, CallbackInfo ci) {
        if (state instanceof PlayerRenderState player && !state.isCrouching && ElytraLook.followsBody(player.id)
                && ((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model) {
            model.body.translateAndRotate(poseStack);
            ElytraLook.followedFrames++;
        }
    }

    @Inject(method = "getPlayerElytraTexture", at = @At("HEAD"), cancellable = true)
    private static void cosmeticsmod$capeTexture(HumanoidRenderState state, CallbackInfoReturnable<Object> cir) {
        if (state instanceof PlayerRenderState player) {
            Object texture = CapeElytra.texture(player.id);
            if (texture != null) {
                cir.setReturnValue(texture);
            }
        }
    }
}
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.CapeElytra;
import io.github.georgwav.cosmetics.render.ElytraLook;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraLayer.class)
abstract class WingsLayerMixin {
    /** The entity being drawn, for the texture (render only takes the texture there). */
    @Unique
    private static int cosmeticsmod$wearer;

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$hide(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntity entity,
                                   float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        cosmeticsmod$wearer = entity.getId();
        if (ElytraLook.hidden(entity.getId())) {
            ElytraLook.hiddenFrames++;
            ci.cancel();
        }
    }

    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void cosmeticsmod$followBody(PoseStack poseStack, MultiBufferSource buffers, int light, LivingEntity entity,
                                         float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                                         float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (!entity.isCrouching() && ElytraLook.followsBody(entity.getId())
                && ((RenderLayer<?, ?>) (Object) this).getParentModel() instanceof HumanoidModel<?> model) {
            model.body.translateAndRotate(poseStack);
            ElytraLook.followedFrames++;
        }
    }

    @ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;armorCutoutNoCull(Lnet/minecraft/resources/ResourceLocation;)Lnet/minecraft/client/renderer/RenderType;"))
    private ResourceLocation cosmeticsmod$capeTexture(ResourceLocation texture) {
        Object cape = CapeElytra.texture(cosmeticsmod$wearer);
        return cape != null ? (ResourceLocation) cape : texture;
    }
}
*///?}
