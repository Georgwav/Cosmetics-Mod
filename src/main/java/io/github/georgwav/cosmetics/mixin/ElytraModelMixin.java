package io.github.georgwav.cosmetics.mixin;

// Before 1.21.2 the elytra model poses its wings from the entity itself: during an elytra emote they are folded
// as when standing (see ElytraUpright.foldWeight). From 1.21.2 the render state carries the pose and
// PlayerRendererMixin folds it there.
//? if <1.21.2 {
/*import io.github.georgwav.cosmetics.render.ElytraUpright;
import net.minecraft.client.model.ElytraModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
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
    private void cosmeticsmod$fold(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                                   float netHeadYaw, float headPitch, CallbackInfo ci) {
        float w = ElytraUpright.foldWeight(entity.getId());
        if (w > 0f) {
            leftWing.xRot += (ElytraUpright.FOLDED_X - leftWing.xRot) * w;
            leftWing.yRot -= leftWing.yRot * w;
            leftWing.zRot += (ElytraUpright.FOLDED_Z - leftWing.zRot) * w;
            rightWing.xRot = leftWing.xRot;
            rightWing.yRot = -leftWing.yRot;
            rightWing.zRot = -leftWing.zRot;
        }
    }
}
*///?}
