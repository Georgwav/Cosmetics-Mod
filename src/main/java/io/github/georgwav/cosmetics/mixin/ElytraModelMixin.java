package io.github.georgwav.cosmetics.mixin;

// Lays a player's elytra on the back, as the wings are posed (see ElytraLook): half as deep, half a pixel in. Before
// 1.21.2 the model also poses its wings from the entity, so it tucks them in here too; from 1.21.2 the render state
// carries the pose and PlayerRendererMixin tucks it there. The model takes a render state from 1.21.2 (a
// PlayerRenderState for players, an AvatarRenderState from 1.21.9) and moved to model.object.equipment in 1.21.11.
//? if >=1.21.11 {
import io.github.georgwav.cosmetics.render.ElytraLook;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraModel.class)
abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$fit(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState) {
            ElytraLook.fit(leftWing, rightWing);
        }
    }
}
//?} elif >=1.21.9 {
/*import io.github.georgwav.cosmetics.render.ElytraLook;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraModel.class)
abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$fit(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof AvatarRenderState) {
            ElytraLook.fit(leftWing, rightWing);
        }
    }
}
*///?} elif >=1.21.2 {
/*import io.github.georgwav.cosmetics.render.ElytraLook;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraModel.class)
abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void cosmeticsmod$fit(HumanoidRenderState state, CallbackInfo ci) {
        if (state instanceof PlayerRenderState) {
            ElytraLook.fit(leftWing, rightWing);
        }
    }
}
*///?} else {
/*import io.github.georgwav.cosmetics.render.ElytraLook;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraModel.class)
abstract class ElytraModelMixin {
    @Shadow @Final private ModelPart leftWing;
    @Shadow @Final private ModelPart rightWing;

    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void cosmeticsmod$tuck(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player) {
            leftWing.xRot = ElytraLook.tucked(leftWing.xRot, leftWing.zRot);
            rightWing.xRot = leftWing.xRot;
            ElytraLook.fit(leftWing, rightWing);
        }
    }
}
*///?}
