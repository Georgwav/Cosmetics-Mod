package io.github.georgwav.cosmetics.mixin;

// 1.21.9's Fabric API has no block outline event: there the game's own outline takes Block Outline's colour (the
// first int of renderHitOutline), its width (a copy of the game's lines with that width) and its fill, after the
// outline's pass. The same jar runs on 1.21.10, where the event does it and this stays out of the way (fallback()).
//? if >=1.21.9 && <1.21.11 {
/*import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.OutlineFill;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.modules.BlockOutline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class OutlineFallbackMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$ready(CallbackInfo ci) {
        OutlineHook.fallbackReady();
    }

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int cosmeticsmod$colour(int color) {
        return OutlineHook.color(color);
    }

    @Redirect(method = "renderBlockOutline", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/RenderType;lines()Lnet/minecraft/client/renderer/RenderType;"), require = 0)
    private RenderType cosmeticsmod$width() {
        return OutlineHook.fallback() ? io.github.georgwav.cosmetics.render.HitboxLines.lines(BlockOutline.widthScale()) : RenderType.lines();
    }

    @Inject(method = "renderBlockOutline", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$fill(MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucentPass, LevelRenderState state,
                                   CallbackInfo ci) {
        int fill = OutlineHook.fallback() ? Modules.OUTLINE.fillArgb() : 0;
        BlockOutlineRenderState outline = state.blockOutlineRenderState;
        if (fill == 0 || outline == null || outline.isTranslucent() != translucentPass) {
            return;
        }
        Vec3 camera = state.cameraRenderState.pos;
        OutlineFill.faces(pose.last(), buffers.getBuffer(RenderType.debugQuads()), outline.shape(), outline.pos().getX() - camera.x,
                outline.pos().getY() - camera.y, outline.pos().getZ() - camera.z, fill);
        buffers.endLastBatch();
    }
}
*///?}
