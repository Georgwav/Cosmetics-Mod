package io.github.georgwav.cosmetics.mixin;

// 26.x has no Fabric block outline event: the game's own outline takes BlockOutline's colour (the first int) and
// width (the first float) where it's drawn, renderHitOutline on 26.1 and submitHitOutline from 26.2, and the fill is
// added after the outline, into the outline's buffers on 26.1 and as custom geometry from 26.2.
//? if >=26.2 {
/*import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.OutlineFill;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.modules.BlockOutline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererOutlineMixin {
    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int cosmeticsmod$outlineColor(int color) {
        return OutlineHook.color(color);
    }

    @ModifyVariable(method = "submitHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float cosmeticsmod$outlineWidth(float width) {
        return width * BlockOutline.widthScale();
    }

    @Inject(method = "submitBlockOutline", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$fill(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        int fill = Modules.OUTLINE.fillArgb();
        BlockOutlineRenderState outline = state.blockOutlineRenderState;
        if (fill == 0 || outline == null) {
            return;
        }
        Vec3 camera = state.cameraRenderState.pos;
        double x = outline.pos().getX() - camera.x, y = outline.pos().getY() - camera.y, z = outline.pos().getZ() - camera.z;
        collector.submitCustomGeometry(pose, RenderTypes.debugQuads(), (p, c) -> OutlineFill.faces(p, c, outline.shape(), x, y, z, fill));
    }
}
*///?} elif >=26.1 {
/*import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.OutlineFill;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.modules.BlockOutline;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class LevelRendererOutlineMixin {
    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int cosmeticsmod$outlineColor(int color) {
        return OutlineHook.color(color);
    }

    @ModifyVariable(method = "renderHitOutline", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private float cosmeticsmod$outlineWidth(float width) {
        return width * BlockOutline.widthScale();
    }

    /^* After the outline's pass (the solid one or the see-through one, as the block is): its fill. ^/
    @Inject(method = "renderBlockOutline", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$fill(MultiBufferSource.BufferSource buffers, PoseStack pose, boolean translucentPass, LevelRenderState state,
                                   CallbackInfo ci) {
        int fill = Modules.OUTLINE.fillArgb();
        BlockOutlineRenderState outline = state.blockOutlineRenderState;
        if (fill == 0 || outline == null || outline.isTranslucent() != translucentPass) {
            return;
        }
        Vec3 camera = state.cameraRenderState.pos;
        OutlineFill.faces(pose.last(), buffers.getBuffer(RenderTypes.debugQuads()), outline.shape(), outline.pos().getX() - camera.x,
                outline.pos().getY() - camera.y, outline.pos().getZ() - camera.z, fill);
        buffers.endLastBatch();
    }
}
*///?}
