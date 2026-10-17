package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Nametags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Nametags: the name in front drawn with the game's text shadow. Where the shadow is chosen differs by era: the
 * second drawInBatch of EntityRenderer.renderNameTag up to 1.21.8, of the name tag renderer from 1.21.9, its
 * prepareText on 26.2, and the text content of each name tag part on 26.3.
 */
//? if >=26.3 {
/*@Mixin(net.minecraft.client.renderer.SubmitNodeCollection.class)
*///?} elif >=1.21.9 {
@Mixin(net.minecraft.client.renderer.feature.NameTagFeatureRenderer.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
*///?}
abstract class NametagShadowMixin {
    //? if >=26.3 {
    /*@ModifyArg(method = "submitNameTag", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollection;submitNameTagPart(Lnet/minecraft/client/renderer/feature/TextFeatureRenderer$Submit;)V"),
            require = 0)
    private net.minecraft.client.renderer.feature.TextFeatureRenderer.Submit cosmeticsmod$shadow(
            net.minecraft.client.renderer.feature.TextFeatureRenderer.Submit part) {
        if (!Nametags.shadow() || !(part.content() instanceof net.minecraft.client.renderer.feature.TextFeatureRenderer.Content.Text t)
                || t.dropShadow()) {
            return part;
        }
        return new net.minecraft.client.renderer.feature.TextFeatureRenderer.Submit(part.pose(), part.displayMode(), part.lightCoords(),
                new net.minecraft.client.renderer.feature.TextFeatureRenderer.Content.Text(t.x(), t.y(), t.string(), true, t.color(),
                        t.backgroundColor(), t.outlineColor()));
    }
    *///?} elif >=26.2 {
    /*@ModifyArg(method = "prepareText", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;prepareText(Lnet/minecraft/util/FormattedCharSequence;FFIZZI)Lnet/minecraft/client/gui/Font$PreparedText;"),
            index = 4, require = 0)
    private static boolean cosmeticsmod$shadow(boolean shadow) {
        return shadow || Nametags.shadow();
    }
    *///?} elif >=26.1 {
    /*@ModifyArg(method = "renderTranslucent", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4fc;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            ordinal = 1), index = 4, require = 0)
    private boolean cosmeticsmod$shadow(boolean shadow) {
        return shadow || Nametags.shadow();
    }
    *///?} elif >=1.21.9 {
    @ModifyArg(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            ordinal = 1), index = 4, require = 0)
    private boolean cosmeticsmod$shadow(boolean shadow) {
        return shadow || Nametags.shadow();
    }
    //?} elif >=1.21.6 {
    /*@ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)V",
            ordinal = 1), index = 4, require = 0)
    private boolean cosmeticsmod$shadow(boolean shadow) {
        return shadow || Nametags.shadow();
    }
    *///?} else {
    /*@ModifyArg(method = "renderNameTag", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/gui/Font;drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
            ordinal = 1), index = 4, require = 0)
    private boolean cosmeticsmod$shadow(boolean shadow) {
        return shadow || Nametags.shadow();
    }
    *///?}
}
