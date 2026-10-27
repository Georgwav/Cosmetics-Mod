package io.github.georgwav.cosmetics.mixin;

// Draws an emote's bent arms and legs at the end of the player model's drawing, into the same buffer (see BentLimbs).
// Every model draws through Model.renderToBuffer from 1.21.2; the player model through AgeableListModel's before.
//? if >=1.21.2 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.render.BentLimbs;
import net.minecraft.client.model.Model;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Model.class)
abstract class BentModelMixin {
    @Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("TAIL"))
    private void cosmeticsmod$bentLimbs(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color,
                                        CallbackInfo ci) {
        if (BentLimbs.active) {
            BentLimbs.draw(this, ((Model) (Object) this).root(), poseStack, consumer, light, overlay, color);
        }
    }
}
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.render.BentLimbs;
import net.minecraft.client.model.AgeableListModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AgeableListModel.class)
abstract class BentModelMixin {
    @Inject(method = "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",
            at = @At("TAIL"))
    private void cosmeticsmod$bentLimbs(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color,
                                        CallbackInfo ci) {
        if (BentLimbs.active) {
            BentLimbs.draw(this, null, poseStack, consumer, light, overlay, color);
        }
    }
}
*///?}
