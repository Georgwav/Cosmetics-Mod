package io.github.georgwav.cosmetics.mixin;

// Poses your player model for the emote you're playing, right after the game posed it (see EmotePlayer).
// PlayerModel took a render state instead of the entity from 1.21.2, an AvatarRenderState from 1.21.9, and moved
// to the model.player package in 1.21.11. Before 1.21.2 its overlay parts (sleeves, pants, jacket, hat) are
// separate parts copied from the arms, legs, body and head, so they're copied again after posing.
//? if >=1.21.11 {
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(AvatarRenderState state, CallbackInfo ci) {
        EmotePlayer.pose(state.id, (HumanoidModel<?>) (Object) this);
    }
}
//?} elif >=1.21.9 {
/*import io.github.georgwav.cosmetics.emote.EmotePlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(AvatarRenderState state, CallbackInfo ci) {
        EmotePlayer.pose(state.id, (HumanoidModel<?>) (Object) this);
    }
}
*///?} elif >=1.21.2 {
/*import io.github.georgwav.cosmetics.emote.EmotePlayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(PlayerRenderState state, CallbackInfo ci) {
        EmotePlayer.pose(state.id, (HumanoidModel<?>) (Object) this);
    }
}
*///?} else {
/*import io.github.georgwav.cosmetics.emote.EmotePlayer;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo ci) {
        // The first-person hand is set up with all zeros (no age); it stays as it is.
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        if (ageInTicks != 0f && EmotePlayer.pose(entity.getId(), model)) {
            model.hat.copyFrom(model.head);
            model.jacket.copyFrom(model.body);
            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftPants.copyFrom(model.leftLeg);
            model.rightPants.copyFrom(model.rightLeg);
        }
    }
}
*///?}
