package io.github.georgwav.cosmetics.mixin;

// Lets an elytra emote draw your player upright to the ground while gliding (see ElytraUpright): marks the frame
// before the player renderer turns the player and adjusts it after. And raises the name above head cosmetics
// (see NameTags) around where the renderer draws it: renderNameTag before 1.21.9, submitNameTag after, renamed
// submitNameDisplay in 26.1. The renderer is AvatarRenderer from 1.21.9 (PlayerRenderer before), and takes a
// render state from 1.21.2 (the entity before).
//? if >=26.1 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import io.github.georgwav.cosmetics.render.NameTags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeTurn(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale,
                                         CallbackInfo ci) {
        ElytraUpright.begin(state.id, poseStack);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterTurn(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale,
                                        CallbackInfo ci) {
        ElytraUpright.end(poseStack, bodyRot);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeName(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                         CameraRenderState camera, CallbackInfo ci) {
        NameTags.begin(state.id, poseStack);
    }

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterName(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                        CameraRenderState camera, CallbackInfo ci) {
        NameTags.end(poseStack);
    }
}
*///?} elif >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import io.github.georgwav.cosmetics.render.NameTags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeTurn(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale,
                                         CallbackInfo ci) {
        ElytraUpright.begin(state.id, poseStack);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterTurn(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale,
                                        CallbackInfo ci) {
        ElytraUpright.end(poseStack, bodyRot);
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeName(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                         CameraRenderState camera, CallbackInfo ci) {
        NameTags.begin(state.id, poseStack);
    }

    @Inject(method = "submitNameTag(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterName(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                        CameraRenderState camera, CallbackInfo ci) {
        NameTags.end(poseStack);
    }
}
//?} elif >=1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import io.github.georgwav.cosmetics.render.NameTags;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeTurn(PlayerRenderState state, PoseStack poseStack, float bodyRot, float scale,
                                         CallbackInfo ci) {
        ElytraUpright.begin(state.id, poseStack);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterTurn(PlayerRenderState state, PoseStack poseStack, float bodyRot, float scale,
                                        CallbackInfo ci) {
        ElytraUpright.end(poseStack, bodyRot);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeName(PlayerRenderState state, Component name, PoseStack poseStack,
                                         MultiBufferSource buffers, int light, CallbackInfo ci) {
        NameTags.begin(state.id, poseStack);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterName(PlayerRenderState state, Component name, PoseStack poseStack,
                                        MultiBufferSource buffers, int light, CallbackInfo ci) {
        NameTags.end(poseStack);
    }
}
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import io.github.georgwav.cosmetics.render.NameTags;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerRenderer.class)
abstract class PlayerRendererMixin {
    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeTurn(AbstractClientPlayer player, PoseStack poseStack, float bob, float bodyRot,
                                         float partialTick, float scale, CallbackInfo ci) {
        ElytraUpright.begin(player.getId(), poseStack);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/player/AbstractClientPlayer;Lcom/mojang/blaze3d/vertex/PoseStack;FFFF)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterTurn(AbstractClientPlayer player, PoseStack poseStack, float bob, float bodyRot,
                                        float partialTick, float scale, CallbackInfo ci) {
        ElytraUpright.end(poseStack, bodyRot);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("HEAD"))
    private void cosmeticsmod$beforeName(AbstractClientPlayer player, Component name, PoseStack poseStack,
                                         MultiBufferSource buffers, int light, float partialTick, CallbackInfo ci) {
        NameTags.begin(player, poseStack);
    }

    @Inject(method = "renderNameTag(Lnet/minecraft/client/player/AbstractClientPlayer;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            at = @At("RETURN"))
    private void cosmeticsmod$afterName(AbstractClientPlayer player, Component name, PoseStack poseStack,
                                        MultiBufferSource buffers, int light, float partialTick, CallbackInfo ci) {
        NameTags.end(poseStack);
    }
}
*///?}
