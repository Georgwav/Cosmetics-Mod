package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.SolHooks;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.3 {
/*import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Vector4f;
*///?} elif >=26.1 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
*///?} elif >=1.21.9 {
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4f;
import org.joml.Vector4f;
//?} elif >=1.21.6 {
/*import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.joml.Matrix4f;
import org.joml.Vector4f;
*///?} elif >=1.21.4 {
/*import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
*///?} elif >=1.21.2 {
/*import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
*///?} else {
/*import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
*///?}

/**
 * The start and end of drawing the level: Sol reads the camera, the projection and the time at the start
 * ({@link io.github.georgwav.cosmetics.sol.render.FrameInfo}), and draws its particles, overlays and the lens flare
 * at the end, over the finished world and under the hand and the HUD.
 */
@Mixin(LevelRenderer.class)
abstract class SolLevelRendererMixin {
    //? if >=26.3 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void cosmeticsmod$start(GraphicsResourceAllocator allocator, boolean outline, CameraRenderState camera,
                                    GpuBufferSlice fog, Vector4f fogColor, boolean sky, boolean depth, CallbackInfo ci) {
        SolHooks.frameStart(camera.pos.x, camera.pos.y, camera.pos.z, camera.orientation, camera.projectionMatrix);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    *///?} elif >=26.2 {
    /*@Inject(method = "render", at = @At("HEAD"))
    private void cosmeticsmod$start(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline,
                                    CameraRenderState camera, Matrix4fc projection, GpuBufferSlice fog, Vector4f fogColor,
                                    boolean sky, CallbackInfo ci) {
        SolHooks.frameStart(camera.pos.x, camera.pos.y, camera.pos.z, camera.orientation, camera.projectionMatrix);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    *///?} elif >=26.1 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void cosmeticsmod$start(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline,
                                    CameraRenderState camera, Matrix4fc projection, GpuBufferSlice fog, Vector4f fogColor,
                                    boolean sky, net.minecraft.client.renderer.chunk.ChunkSectionsToRender sections,
                                    CallbackInfo ci) {
        SolHooks.frameStart(camera.pos.x, camera.pos.y, camera.pos.z, camera.orientation, camera.projectionMatrix);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    *///?} elif >=1.21.9 {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void cosmeticsmod$start(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                                    Matrix4f view, Matrix4f projection, Matrix4f cullProjection, GpuBufferSlice fog,
                                    Vector4f fogColor, boolean sky, CallbackInfo ci) {
        var pos = camera.position();
        SolHooks.frameStart(pos.x, pos.y, pos.z, camera.rotation(), projection);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    //?} elif >=1.21.6 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void cosmeticsmod$start(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                                    Matrix4f view, Matrix4f projection, GpuBufferSlice fog, Vector4f fogColor, boolean sky,
                                    CallbackInfo ci) {
        var pos = camera.getPosition();
        SolHooks.frameStart(pos.x, pos.y, pos.z, camera.rotation(), projection);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    *///?} elif >=1.21.4 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void cosmeticsmod$start(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                                    GameRenderer gameRenderer, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        var pos = camera.getPosition();
        SolHooks.frameStart(pos.x, pos.y, pos.z, camera.rotation(), projection);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    *///?} elif >=1.21.2 {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void cosmeticsmod$start(GraphicsResourceAllocator allocator, DeltaTracker delta, boolean outline, Camera camera,
                                    GameRenderer gameRenderer, LightTexture light, Matrix4f view, Matrix4f projection,
                                    CallbackInfo ci) {
        var pos = camera.getPosition();
        SolHooks.frameStart(pos.x, pos.y, pos.z, camera.rotation(), projection);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    *///?} else {
    /*@Inject(method = "renderLevel", at = @At("HEAD"))
    private void cosmeticsmod$start(DeltaTracker delta, boolean outline, Camera camera, GameRenderer gameRenderer,
                                    LightTexture light, Matrix4f view, Matrix4f projection, CallbackInfo ci) {
        var pos = camera.getPosition();
        SolHooks.frameStart(pos.x, pos.y, pos.z, camera.rotation(), projection);
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void cosmeticsmod$end(CallbackInfo ci) {
        SolHooks.frameEnd();
    }
    *///?}
}
