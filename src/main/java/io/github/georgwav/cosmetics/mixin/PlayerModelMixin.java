package io.github.georgwav.cosmetics.mixin;

// Poses your player model for the emote you're playing, right after the game posed it (see EmotePlayer).
// PlayerModel took a render state instead of the entity from 1.21.2, an AvatarRenderState from 1.21.9, and moved
// to the model.player package in 1.21.11. Before 1.21.2 its overlay parts (sleeves, pants, jacket, hat) are
// separate parts copied from the arms, legs, body and head, so they're copied again after posing. Limbs the emote
// bends are drawn bent (see BentLimbs), and a held item stays in a bent arm's hand.
//? if >=1.21.11 {
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.render.BentLimbs;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        // An emote's pose wins; else the Naruto run's lean while you sprint.
        if (!EmotePlayer.pose(state.id, model)) {
            io.github.georgwav.cosmetics.sol.fx.effects.You.pose(state.id, model.head, model.body, model.leftArm, model.rightArm,
                    model.leftLeg, model.rightLeg);
        }
        BentLimbs.hide(state.id, model, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants);
    }

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void cosmeticsmod$bentHand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        BentLimbs.toHand(this, arm == HumanoidArm.RIGHT, poseStack);
    }
}
//?} elif >=1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.render.BentLimbs;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(AvatarRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        // An emote's pose wins; else the Naruto run's lean while you sprint.
        if (!EmotePlayer.pose(state.id, model)) {
            io.github.georgwav.cosmetics.sol.fx.effects.You.pose(state.id, model.head, model.body, model.leftArm, model.rightArm,
                    model.leftLeg, model.rightLeg);
        }
        BentLimbs.hide(state.id, model, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants);
    }

    @Inject(method = "translateToHand(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void cosmeticsmod$bentHand(AvatarRenderState state, HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        BentLimbs.toHand(this, arm == HumanoidArm.RIGHT, poseStack);
    }
}
*///?} elif >=1.21.2 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.render.BentLimbs;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/PlayerRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(PlayerRenderState state, CallbackInfo ci) {
        PlayerModel model = (PlayerModel) (Object) this;
        // An emote's pose wins; else the Naruto run's lean while you sprint.
        if (!EmotePlayer.pose(state.id, model)) {
            io.github.georgwav.cosmetics.sol.fx.effects.You.pose(state.id, model.head, model.body, model.leftArm, model.rightArm,
                    model.leftLeg, model.rightLeg);
        }
        BentLimbs.hide(state.id, model, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants);
    }

    @Inject(method = "translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void cosmeticsmod$bentHand(HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        BentLimbs.toHand(this, arm == HumanoidArm.RIGHT, poseStack);
    }
}
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.render.BentLimbs;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void cosmeticsmod$emote(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                    float netHeadYaw, float headPitch, CallbackInfo ci) {
        PlayerModel<?> model = (PlayerModel<?>) (Object) this;
        if (ageInTicks == 0f) {
            // The first-person hand is set up with all zeros (no age); it stays as it is, and straight.
            BentLimbs.restore();
            return;
        }
        if (EmotePlayer.pose(entity.getId(), model)
                || io.github.georgwav.cosmetics.sol.fx.effects.You.pose(entity.getId(), model.head, model.body, model.leftArm,
                model.rightArm, model.leftLeg, model.rightLeg)) {
            model.hat.copyFrom(model.head);
            model.jacket.copyFrom(model.body);
            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftPants.copyFrom(model.leftLeg);
            model.rightPants.copyFrom(model.rightLeg);
        }
        BentLimbs.hide(entity.getId(), model, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants);
    }

    @Inject(method = "translateToHand(Lnet/minecraft/world/entity/HumanoidArm;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    private void cosmeticsmod$bentHand(HumanoidArm arm, PoseStack poseStack, CallbackInfo ci) {
        BentLimbs.toHand(this, arm == HumanoidArm.RIGHT, poseStack);
    }
}
*///?}
