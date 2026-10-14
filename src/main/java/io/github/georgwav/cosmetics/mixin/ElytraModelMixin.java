package io.github.georgwav.cosmetics.mixin;

// Before 1.21.2 the elytra model poses its wings from the entity itself: a player's elytra is tucked in towards the
// back there (see ElytraLook). From 1.21.2 the render state carries the pose and
// PlayerRendererMixin tucks it there.
//? if <1.21.2 {
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
        }
    }
}
*///?}
