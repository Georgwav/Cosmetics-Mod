package io.github.georgwav.cosmetics.mixin;

// World FX › Item physics (ItemPhysics): a dropped item's lift and turn, as the game draws it, through ItemPhysics: the
// game's bob lift (the first translate) and its spin (the turn round the vertical). Up to 1.21.1 from the item entity;
// later from its render state (whether it rests on a block is looked up there).
//? if >=26.3 {
/*import com.llamalad7.mixinextras.sugar.Local;
import io.github.georgwav.cosmetics.render.ItemPhysics;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
abstract class ItemPhysicsMixin {
    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private float cosmeticsmod$lift(float y, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.lift(y, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset);
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotate(Lcom/mojang/math/Axis;F)V"))
    private void cosmeticsmod$turn(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.math.Axis axis, float spin,
                                   @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        pose.rotate(ItemPhysics.turn(spin, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset));
    }
}
*///?} elif >=26.1 {
/*import com.llamalad7.mixinextras.sugar.Local;
import io.github.georgwav.cosmetics.render.ItemPhysics;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
abstract class ItemPhysicsMixin {
    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private float cosmeticsmod$lift(float y, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.lift(y, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset);
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"))
    private Quaternionf cosmeticsmod$turn(com.mojang.math.Axis axis, float spin, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.turn(spin, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset);
    }
}
*///?} elif >=1.21.9 {
import com.llamalad7.mixinextras.sugar.Local;
import io.github.georgwav.cosmetics.render.ItemPhysics;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
abstract class ItemPhysicsMixin {
    @ModifyArg(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private float cosmeticsmod$lift(float y, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.lift(y, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset);
    }

    @Redirect(method = "submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/CameraRenderState;)V", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"))
    private Quaternionf cosmeticsmod$turn(com.mojang.math.Axis axis, float spin, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.turn(spin, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset);
    }
}
//?} elif >=1.21.4 {
/*import com.llamalad7.mixinextras.sugar.Local;
import io.github.georgwav.cosmetics.render.ItemPhysics;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
abstract class ItemPhysicsMixin {
    @ModifyArg(method = "render(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private float cosmeticsmod$lift(float y, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.lift(y, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset);
    }

    @Redirect(method = "render(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"))
    private Quaternionf cosmeticsmod$turn(com.mojang.math.Axis axis, float spin, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.turn(spin, ItemPhysics.resting(state.x, state.y, state.z), !state.item.usesBlockLight(), state.ageInTicks, state.bobOffset);
    }
}
*///?} elif >=1.21.2 {
/*import com.llamalad7.mixinextras.sugar.Local;
import io.github.georgwav.cosmetics.render.ItemPhysics;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
abstract class ItemPhysicsMixin {
    @ModifyArg(method = "render(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", require = 0, index = 1, at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private float cosmeticsmod$lift(float y, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.lift(y, ItemPhysics.resting(state.x, state.y, state.z), !(state.item.getItem() instanceof net.minecraft.world.item.BlockItem), state.ageInTicks, state.bobOffset);
    }

    @Redirect(method = "render(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", require = 0, at = @At(value = "INVOKE",
            target = "Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"))
    private Quaternionf cosmeticsmod$turn(com.mojang.math.Axis axis, float spin, @Local(argsOnly = true) net.minecraft.client.renderer.entity.state.ItemEntityRenderState state) {
        return ItemPhysics.turn(spin, ItemPhysics.resting(state.x, state.y, state.z), !(state.item.getItem() instanceof net.minecraft.world.item.BlockItem), state.ageInTicks, state.bobOffset);
    }
}
*///?} else {
/*import com.llamalad7.mixinextras.sugar.Local;
import io.github.georgwav.cosmetics.render.ItemPhysics;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemEntityRenderer.class)
abstract class ItemPhysicsMixin {
    @ModifyArg(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            require = 0, index = 1, at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", ordinal = 0))
    private float cosmeticsmod$lift(float y, @Local(argsOnly = true) net.minecraft.world.entity.item.ItemEntity item,
                                    @Local(argsOnly = true, ordinal = 1) float partialTick) {
        return ItemPhysics.lift(y, item.onGround(), !(item.getItem().getItem() instanceof net.minecraft.world.item.BlockItem),
                item.getAge() + partialTick, item.bobOffs);
    }

    @Redirect(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            require = 0, at = @At(value = "INVOKE", target = "Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"))
    private Quaternionf cosmeticsmod$turn(com.mojang.math.Axis axis, float spin, @Local(argsOnly = true) net.minecraft.world.entity.item.ItemEntity item,
                                         @Local(argsOnly = true, ordinal = 1) float partialTick) {
        return ItemPhysics.turn(spin, item.onGround(), !(item.getItem().getItem() instanceof net.minecraft.world.item.BlockItem),
                item.getAge() + partialTick, item.bobOffs);
    }
}
*///?}
