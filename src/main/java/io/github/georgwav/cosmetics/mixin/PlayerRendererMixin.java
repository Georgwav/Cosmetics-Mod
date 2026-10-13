package io.github.georgwav.cosmetics.mixin;

// Lets an elytra emote draw your player upright to the ground while gliding (see ElytraUpright): marks the frame
// before the player renderer turns the player and adjusts it after. The renderer is AvatarRenderer from 1.21.9
// (PlayerRenderer before), and takes a render state from 1.21.2 (the entity before).
//? if >=1.21.9 {
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
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
}
//?} elif >=1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
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
}
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
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
}
*///?}
