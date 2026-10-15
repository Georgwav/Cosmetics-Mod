package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.Nametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Nametags: their size and how dark their backing is. The game scales a tag and asks the options for the backing's
 * opacity in one place per era: EntityRenderer up to 1.21.8, the name tag renderer's storage from 1.21.9, the submit
 * collection from 26.2.
 */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.renderer.SubmitNodeCollection.class)
*///?} elif >=1.21.9 {
@Mixin(targets = "net.minecraft.client.renderer.feature.NameTagFeatureRenderer$Storage")
//?} else {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
*///?}
abstract class NametagLookMixin {
    //? if >=26.2 {
    /*@Redirect(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0), require = 0)
    *///?} elif >=1.21.9 {
    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0), require = 0)
    //?} else {
    /*@Redirect(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", ordinal = 0), require = 0)
    *///?}
    private void cosmeticsmod$size(PoseStack pose, float x, float y, float z) {
        float k = Nametags.scale();
        pose.scale(x * k, y * k, z * k);
    }

    //? if >=26.2 {
    /*@Redirect(method = "submitNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;getBackgroundOpacity(F)F"), require = 0)
    private float cosmeticsmod$backing(net.minecraft.client.renderer.state.OptionsRenderState options, float fallback) {
        return Nametags.backing(options.getBackgroundOpacity(fallback));
    }
    *///?} elif >=1.21.9 {
    @Redirect(method = "add", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundOpacity(F)F"), require = 0)
    private float cosmeticsmod$backing(net.minecraft.client.Options options, float fallback) {
        return Nametags.backing(options.getBackgroundOpacity(fallback));
    }
    //?} else {
    /*@Redirect(method = "renderNameTag", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getBackgroundOpacity(F)F"), require = 0)
    private float cosmeticsmod$backing(net.minecraft.client.Options options, float fallback) {
        return Nametags.backing(options.getBackgroundOpacity(fallback));
    }
    *///?}
}
