package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.render.HitboxLines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hitboxes: the lines' width (and from 1.21.11 the colour too). Where the game picks the lines for hitboxes: the
 * debug renderer's gizmo stroke from 1.21.11, the hitbox feature renderer on 1.21.9 and 1.21.10, the entity render
 * dispatcher before.
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer.class)
//?} elif >=1.21.9 {
/*@Mixin(net.minecraft.client.renderer.feature.HitboxFeatureRenderer.class)
*///?} else {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
*///?}
abstract class HitboxWidthMixin {
    //? if >=1.21.11 {
    @Redirect(method = "showHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/gizmos/GizmoStyle;stroke(I)Lnet/minecraft/gizmos/GizmoStyle;"),
            require = 0)
    private net.minecraft.gizmos.GizmoStyle cosmeticsmod$stroke(int argb) {
        return HitboxLines.stroke(argb);
    }
    //?} elif >=1.21.9 {
    /*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;lines()Lnet/minecraft/client/renderer/RenderType;"),
            require = 0)
    private net.minecraft.client.renderer.RenderType cosmeticsmod$lines() {
        return HitboxLines.lines();
    }
    *///?} elif >=1.21.5 {
    /*@Redirect(method = "renderHitboxes", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;lines()Lnet/minecraft/client/renderer/RenderType;"),
            require = 0)
    private net.minecraft.client.renderer.RenderType cosmeticsmod$lines() {
        return HitboxLines.lines();
    }
    *///?} else {
    /*@Redirect(method = {"render", "renderServerSideHitbox"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;lines()Lnet/minecraft/client/renderer/RenderType;"), require = 0)
    private static net.minecraft.client.renderer.RenderType cosmeticsmod$lines() {
        return HitboxLines.lines();
    }
    *///?}
}
