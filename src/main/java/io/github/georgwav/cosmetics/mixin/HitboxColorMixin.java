package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Hitboxes;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Hitboxes: the box's colour. From 1.21.5 each box is a HitboxRenderState (white for the entity's box, red for the
 * eyes, blue for the look): a white one takes the colour as it's made. Before, EntityRenderDispatcher draws the box
 * with the colour it's given.
 */
//? if >=1.21.5 {
@Mixin(net.minecraft.client.renderer.entity.state.HitboxRenderState.class)
abstract class HitboxColorMixin {
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Mutable
    @org.spongepowered.asm.mixin.Final
    private float red;
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Mutable
    @org.spongepowered.asm.mixin.Final
    private float green;
    @org.spongepowered.asm.mixin.Shadow
    @org.spongepowered.asm.mixin.Mutable
    @org.spongepowered.asm.mixin.Final
    private float blue;

    @org.spongepowered.asm.mixin.injection.Inject(method = "<init>(DDDDDDFFFFFF)V", at = @org.spongepowered.asm.mixin.injection.At("RETURN"), require = 0)
    private void cosmeticsmod$colour(double x0, double y0, double z0, double x1, double y1, double z1, float ox, float oy, float oz,
                                     float r, float g, float b, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        int c = Hitboxes.colour();
        if (c != 0 && r == 1f && g == 1f && b == 1f) {
            red = ((c >> 16) & 0xFF) / 255f;
            green = ((c >> 8) & 0xFF) / 255f;
            blue = (c & 0xFF) / 255f;
        }
    }
}
//?} else {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
abstract class HitboxColorMixin {
    private static void cosmeticsmod$apply(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
        int c = Hitboxes.colour();
        if (c != 0 && (float) args.get(3) == 1f && (float) args.get(4) == 1f && (float) args.get(5) == 1f) {
            args.set(3, ((c >> 16) & 0xFF) / 255f);
            args.set(4, ((c >> 8) & 0xFF) / 255f);
            args.set(5, (c & 0xFF) / 255f);
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyArgs(method = "renderHitbox", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ShapeRenderer;renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/AABB;FFFF)V",
            ordinal = 0), require = 0)
    private static void cosmeticsmod$shape(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
        cosmeticsmod$apply(args);
    }

    @org.spongepowered.asm.mixin.injection.ModifyArgs(method = "renderHitbox", at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLineBox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/phys/AABB;FFFF)V",
            ordinal = 0), require = 0)
    private static void cosmeticsmod$level(org.spongepowered.asm.mixin.injection.invoke.arg.Args args) {
        cosmeticsmod$apply(args);
    }
}
*///?}
