package io.github.georgwav.cosmetics.sol.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import org.joml.Matrix4fStack;
//? if >=26.3 {
/*import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.util.Optional;
import java.util.OptionalDouble;
*///?} elif >=26.2 {
/*import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import java.util.Optional;
import java.util.OptionalDouble;
*///?} elif >=26.1 {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
*///?} elif >=1.21.11 {
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
//?} elif >=1.21.5 {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?} elif >=1.21.2 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.CoreShaders;
*///?} else {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
*///?}

/**
 * Draws Sol's textured, coloured quads in the world: in the sky pass (no depth test, nothing written to depth) and
 * after the level (tested against the world's depth, nothing written). Quads are positioned relative to the camera
 * and already turned by the view rotation (see {@link FrameInfo#view}), so drawing only needs the game's current
 * projection. One reused vertex array and byte buffer: nothing is allocated per frame. Render thread only.
 *
 * <p>Every version difference in how a draw reaches the GPU is in this class.
 */
public final class WorldDraw {
    /** Sky layer, alpha blended. */
    public static final int SKY_ALPHA = 0;
    /** Sky layer, added (the prototypes' "lighter"). */
    public static final int SKY_ADD = 1;
    /** In the world (behind terrain hides it), alpha blended. */
    public static final int WORLD_ALPHA = 2;
    /** In the world, added. */
    public static final int WORLD_ADD = 3;
    public static final int KINDS = 4;

    private static final int FLOATS = 6;
    private static float[] data = new float[FLOATS * 4 * 512];
    private static int vertices;
    private static int kind = -1;
    private static SolTexture texture;
    private static ByteBufferBuilder bytes;
    private static SolTexture white;

    private WorldDraw() {
    }

    /** A 1x1 white texture, for untextured (gradient) quads. */
    public static SolTexture white() {
        if (white == null || white.released()) {
            white = SolTexture.of("white", 1, 1, false, new int[]{0xFFFFFFFF});
        }
        return white;
    }

    /** Starts a batch of quads of one kind with one texture (null: plain colour). */
    public static void begin(int batchKind, SolTexture tex) {
        if (kind >= 0) {
            end();
        }
        kind = batchKind;
        texture = tex != null ? tex : white();
        vertices = 0;
    }

    /** One vertex: view-space position, texture coordinates, ARGB colour. Four make a quad. */
    public static void vertex(float x, float y, float z, float u, float v, int argb) {
        int i = vertices * FLOATS;
        if (i + FLOATS > data.length) {
            float[] grown = new float[data.length * 2];
            System.arraycopy(data, 0, grown, 0, data.length);
            data = grown;
        }
        data[i] = x;
        data[i + 1] = y;
        data[i + 2] = z;
        data[i + 3] = u;
        data[i + 4] = v;
        data[i + 5] = Float.intBitsToFloat(argb);
        vertices++;
    }

    public static int vertexCount() {
        return vertices;
    }

    /** Draws the batch. */
    public static void end() {
        int k = kind;
        SolTexture tex = texture;
        kind = -1;
        texture = null;
        if (k < 0 || vertices < 4 || tex == null || tex.released()) {
            vertices = 0;
            return;
        }
        int count = vertices - vertices % 4;
        vertices = 0;
        if (bytes == null) {
            bytes = new ByteBufferBuilder(1 << 16);
        }
        //? if >=26.2 {
        /*BufferBuilder builder = new BufferBuilder(bytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        *///?} else {
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        //?}
        float[] d = data;
        for (int v = 0; v < count; v++) {
            int i = v * FLOATS;
            builder.addVertex(d[i], d[i + 1], d[i + 2]).setUv(d[i + 3], d[i + 4]).setColor(Float.floatToRawIntBits(d[i + 5]));
        }
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        try {
            draw(k, tex, mesh);
        } finally {
            modelView.popMatrix();
            //? if <1.21.2 {
            /*RenderSystem.applyModelViewMatrix();
            *///?}
        }
    }

    private static boolean add(int k) {
        return k == SKY_ADD || k == WORLD_ADD;
    }

    private static boolean world(int k) {
        return k >= WORLD_ALPHA;
    }

    //? if >=26.2 {
    /*private static final RenderPipeline[] PIPELINES = new RenderPipeline[KINDS];
    private static GpuBuffer vertexBuffer;

    private static RenderPipeline pipeline(int k) {
        RenderPipeline p = PIPELINES[k];
        if (p == null) {
            RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET);
            //? if >=26.3 {
            b = b.withBindGroupLayout(BindGroupLayouts.PROJECTION).withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS);
            //?} else {
            b = b.withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION);
            //?}
            b = b.withLocation(Identifier.fromNamespaceAndPath("cosmeticsmod", "pipeline/sol_" + k))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                    .withColorTargetState(new ColorTargetState(add(k) ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT))
                    .withCull(false)
                    .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                    .withPrimitiveTopology(PrimitiveTopology.QUADS);
            if (world(k)) {
                // Depth is reversed here: nearer is greater.
                b = b.withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
            }
            p = b.build();
            PIPELINES[k] = p;
        }
        return p;
    }

    private static void draw(int k, SolTexture tex, MeshData mesh) {
        try (mesh) {
            var device = RenderSystem.getDevice();
            java.nio.ByteBuffer vb = mesh.vertexBuffer();
            int size = vb.remaining();
            if (vertexBuffer == null || vertexBuffer.size() < size) {
                if (vertexBuffer != null) {
                    vertexBuffer.close();
                }
                vertexBuffer = device.createBuffer(() -> "Sol vertex buffer", 40, Math.max(size, 1 << 16));
            }
            var encoder = device.createCommandEncoder();
            encoder.writeToBuffer(vertexBuffer.slice(0, size), vb);
            int indexCount = mesh.drawState().indexCount();
            RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
            GpuBuffer indexBuffer = indices.getBuffer(indexCount);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f());
            RenderTarget target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            try (RenderPass pass = encoder.createRenderPass(() -> "Sol", target.getColorTextureView(), Optional.empty(),
                    target.getDepthTextureView(), OptionalDouble.empty())) {
                //? if >=26.3 {
                pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline(k)));
                //?} else {
                pass.setPipeline(pipeline(k));
                //?}
                RenderSystem.bindDefaultUniforms(pass);
                pass.setUniform("DynamicTransforms", transforms);
                //? if >=26.3 {
                pass.setUniform("Sampler0", tex.view(), tex.sampler());
                //?} else {
                pass.bindTexture("Sampler0", tex.view(), tex.sampler());
                //?}
                pass.setIndexBuffer(indexBuffer, indices.type());
                pass.setVertexBuffer(0, vertexBuffer.slice());
                pass.drawIndexed(indexCount, 1, 0, 0, 0);
            }
        }
    }
    *///?} elif >=1.21.5 {
    private static final RenderPipeline[] PIPELINES = new RenderPipeline[KINDS];

    private static RenderPipeline pipeline(int k) {
        RenderPipeline p = PIPELINES[k];
        if (p == null) {
            //? if >=1.21.6 {
            RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET);
            //?} else {
            /*RenderPipeline.Builder b = RenderPipeline.builder(RenderPipelines.MATRICES_COLOR_SNIPPET);
            *///?}
            b = b.withLocation(id("pipeline/sol_" + k))
                    .withVertexShader("core/position_tex_color")
                    .withFragmentShader("core/position_tex_color")
                    .withSampler("Sampler0")
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
            //? if >=26.1 {
            /*b = b.withColorTargetState(new ColorTargetState(add(k) ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT));
            if (world(k)) {
                b = b.withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false));
            }
            *///?} else {
            b = b.withBlend(add(k) ? BlendFunction.LIGHTNING : BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false)
                    .withDepthTestFunction(world(k) ? DepthTestFunction.LEQUAL_DEPTH_TEST : DepthTestFunction.NO_DEPTH_TEST);
            //?}
            p = b.build();
            PIPELINES[k] = p;
        }
        return p;
    }

    private static RenderType renderType(int k, SolTexture tex) {
        Object cached = tex.renderTypes[k];
        if (cached == null) {
            //? if >=1.21.11 {
            cached = RenderType.create("cosmeticsmod_sol_" + k, RenderSetup.builder(pipeline(k))
                    .withTexture("Sampler0", tex.id, tex::sampler).createRenderSetup());
            //?} elif >=1.21.6 {
            /*cached = RenderType.create("cosmeticsmod_sol_" + k, 1536, false, false, pipeline(k),
                    RenderType.CompositeState.builder().setTextureState(new RenderStateShard.TextureStateShard(tex.id, false))
                            .createCompositeState(false));
            *///?} else {
            /*cached = RenderType.create("cosmeticsmod_sol_" + k, 1536, false, false, pipeline(k),
                    RenderType.CompositeState.builder().setTextureState(new RenderStateShard.TextureStateShard(tex.id,
                            net.minecraft.util.TriState.DEFAULT, false)).createCompositeState(false));
            *///?}
            tex.renderTypes[k] = cached;
        }
        return (RenderType) cached;
    }

    private static void draw(int k, SolTexture tex, MeshData mesh) {
        renderType(k, tex).draw(mesh);
    }

    //? if >=1.21.11 {
    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("cosmeticsmod", path);
    }
    //?} else {
    /*private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("cosmeticsmod", path);
    }
    *///?}
    //?} else {
    /*private static void draw(int k, SolTexture tex, MeshData mesh) {
        //? if >=1.21.2 {
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        //?} else {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.applyModelViewMatrix();
        //?}
        RenderSystem.setShaderTexture(0, tex.id);
        RenderSystem.enableBlend();
        if (add(k)) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        if (world(k)) {
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(515);
        } else {
            RenderSystem.disableDepthTest();
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }
    *///?}
}
