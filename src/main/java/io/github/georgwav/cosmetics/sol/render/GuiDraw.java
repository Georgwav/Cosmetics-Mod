package io.github.georgwav.cosmetics.sol.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.ui.Canvas;
//? if >=26.3 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
*///?} elif >=1.21.6 {
import com.mojang.blaze3d.pipeline.RenderPipeline;
//?}
//? if >=1.21.6 {
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
//?} else {
/*import org.joml.Matrix4f;
*///?}
//? if >=26.1 {
/*import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
*///?} elif >=1.21.6 {
import net.minecraft.client.gui.render.state.GuiElementRenderState;
//?}
//? if >=1.21.5 && <1.21.6 {
/*import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
*///?} elif <1.21.5 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
*///?}
//? if >=1.21.2 && <1.21.5 {
/*import net.minecraft.client.renderer.CoreShaders;
*///?} elif <1.21.2 {
/*import net.minecraft.client.renderer.GameRenderer;
*///?}

/**
 * Sol's GUI geometry: textured, vertex-coloured quads (a triangle is a quad with two corners the same) in the GUI's
 * coordinates, through the current transform. Alpha blended, or added for glows (a premultiplied texture, see
 * {@link SolTexture#premultiplied}). From 1.21.6 a batch becomes one of the GUI's own render states, so it is ordered
 * and clipped like everything else on screen; before that it is drawn there and then. Every version difference in
 * drawing GUI geometry is in this class.
 */
public final class GuiDraw {
    private static final int FLOATS = 5;
    private static float[] data = new float[FLOATS * 4 * 256];
    private static int vertices;
    private static boolean open;
    private static boolean additive;
    private static SolTexture texture;
    private static Canvas canvas;

    //? if >=1.21.6 {
    private static final Matrix3x2f pose = new Matrix3x2f();
    //?} else {
    /*private static final Matrix4f pose = new Matrix4f();
    *///?}

    private GuiDraw() {
    }

    /** Starts a batch on {@code g} with a texture (null: plain colour), added or blended. */
    public static void begin(Canvas g, SolTexture tex, boolean add) {
        if (open) {
            end();
        }
        open = true;
        canvas = g;
        texture = tex != null ? tex : white(add);
        additive = add;
        vertices = 0;
        //? if >=1.21.6 {
        pose.set(g.raw.pose());
        //?} else {
        /*pose.set(g.raw.pose().last().pose());
        *///?}
    }

    private static SolTexture whiteStraight;
    private static SolTexture whitePremultiplied;

    /** A 1x1 white texture (straight, or premultiplied for added drawing). */
    public static SolTexture white(boolean premultiplied) {
        if (premultiplied) {
            if (whitePremultiplied == null || whitePremultiplied.released()) {
                whitePremultiplied = SolTexture.premultiplied("gui_white_pm", 1, 1, false, new int[]{0xFFFFFFFF});
            }
            return whitePremultiplied;
        }
        if (whiteStraight == null || whiteStraight.released()) {
            whiteStraight = SolTexture.of("gui_white", 1, 1, false, new int[]{0xFFFFFFFF});
        }
        return whiteStraight;
    }

    /** One vertex at (x, y) in the transform's units, texture (u, v), ARGB colour. Four make a quad. */
    public static void vertex(float x, float y, float u, float v, int argb) {
        int i = vertices * FLOATS;
        if (i + FLOATS > data.length) {
            float[] grown = new float[data.length * 2];
            System.arraycopy(data, 0, grown, 0, data.length);
            data = grown;
        }
        //? if >=1.21.6 {
        float px = pose.m00() * x + pose.m10() * y + pose.m20();
        float py = pose.m01() * x + pose.m11() * y + pose.m21();
        //?} else {
        /*float px = pose.m00() * x + pose.m10() * y + pose.m30();
        float py = pose.m01() * x + pose.m11() * y + pose.m31();
        *///?}
        data[i] = px;
        data[i + 1] = py;
        data[i + 2] = u;
        data[i + 3] = v;
        if (additive) {
            // Premultiplied colour with (almost) no alpha: the destination stays, the colour is added.
            int a = argb >>> 24;
            int r = ((argb >> 16) & 0xFF) * a / 255, g = ((argb >> 8) & 0xFF) * a / 255, b = (argb & 0xFF) * a / 255;
            argb = (1 << 24) | (r << 16) | (g << 8) | b;
        }
        data[i + 4] = Float.intBitsToFloat(argb);
        vertices++;
        if ((vertices & 3) == 0) {
            faceTheViewer(i - 3 * FLOATS);
        }
    }

    /**
     * The game culls GUI triangles that face away, so a quad given in the other turning direction (a ring segment
     * traced outwards then round, a fan in a mirrored order) would vanish. Every quad is turned to face the viewer:
     * with y pointing down, a quad facing the viewer has a negative shoelace sum; one with a positive sum gets its
     * second and fourth corners swapped, which flips both of its triangles and keeps its shape.
     */
    private static void faceTheViewer(int q) {
        float sum = 0f;
        for (int k = 0; k < 4; k++) {
            int a = q + k * FLOATS, b = q + ((k + 1) & 3) * FLOATS;
            sum += data[a] * data[b + 1] - data[b] * data[a + 1];
        }
        if (sum > 0f) {
            int one = q + FLOATS, three = q + 3 * FLOATS;
            for (int f = 0; f < FLOATS; f++) {
                float t = data[one + f];
                data[one + f] = data[three + f];
                data[three + f] = t;
            }
        }
    }

    /** A quad with four corners and colours, texture corners (0,0)-(1,1). */
    public static void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
                            int c0, int c1, int c2, int c3) {
        vertex(x0, y0, 0f, 0f, c0);
        vertex(x1, y1, 0f, 1f, c1);
        vertex(x2, y2, 1f, 1f, c2);
        vertex(x3, y3, 1f, 0f, c3);
    }

    /** An axis-aligned rectangle with the texture region (u0, v0)-(u1, v1). */
    public static void rect(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int argb) {
        vertex(x0, y0, u0, v0, argb);
        vertex(x0, y1, u0, v1, argb);
        vertex(x1, y1, u1, v1, argb);
        vertex(x1, y0, u1, v0, argb);
    }

    public static void rect(float x0, float y0, float x1, float y1, int argb) {
        rect(x0, y0, x1, y1, 0f, 0f, 1f, 1f, argb);
    }

    /** A rectangle with a vertical gradient. */
    public static void gradient(float x0, float y0, float x1, float y1, int top, int bottom) {
        vertex(x0, y0, 0f, 0f, top);
        vertex(x0, y1, 0f, 1f, bottom);
        vertex(x1, y1, 1f, 1f, bottom);
        vertex(x1, y0, 1f, 0f, top);
    }

    /** A rectangle with a horizontal gradient. */
    public static void hGradient(float x0, float y0, float x1, float y1, int left, int right) {
        vertex(x0, y0, 0f, 0f, left);
        vertex(x0, y1, 0f, 1f, left);
        vertex(x1, y1, 1f, 1f, right);
        vertex(x1, y0, 1f, 0f, right);
    }

    /** A triangle (a quad with its last corner doubled). */
    public static void triangle(float x0, float y0, float x1, float y1, float x2, float y2, int c0, int c1, int c2) {
        vertex(x0, y0, 0f, 0f, c0);
        vertex(x1, y1, 0f, 1f, c1);
        vertex(x2, y2, 1f, 1f, c2);
        vertex(x2, y2, 1f, 1f, c2);
    }

    /** Ends the batch: queued for the GUI (1.21.6+) or drawn now. */
    public static void end() {
        if (!open) {
            return;
        }
        open = false;
        int count = vertices - vertices % 4;
        vertices = 0;
        SolTexture tex = texture;
        Canvas g = canvas;
        texture = null;
        canvas = null;
        if (count < 4 || tex == null || tex.released() || g == null) {
            return;
        }
        submit(g, tex, additive, count);
    }

    //? if >=1.21.6 {
    /** One batch as a GUI element: its own copy of the vertices, reused round-robin from a pool. */
    private static final class Element implements GuiElementRenderState {
        float[] verts = new float[FLOATS * 64];
        int count;
        RenderPipeline pipeline;
        TextureSetup setup;
        ScreenRectangle scissor;
        ScreenRectangle bounds;

        //? if >=1.21.9 {
        @Override
        public void buildVertices(VertexConsumer consumer) {
        //?} else {
        /*@Override
        public void buildVertices(VertexConsumer consumer, float z) {
        *///?}
            float[] d = verts;
            for (int v = 0; v < count; v++) {
                int i = v * FLOATS;
                consumer.addVertex(d[i], d[i + 1], 0f).setUv(d[i + 2], d[i + 3]).setColor(Float.floatToRawIntBits(d[i + 4]));
            }
        }

        @Override
        public RenderPipeline pipeline() {
            return pipeline;
        }

        @Override
        public TextureSetup textureSetup() {
            return setup;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return scissor;
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }
    }

    private static final Element[] POOL = new Element[4096];
    private static int next;

    private static void submit(Canvas g, SolTexture tex, boolean add, int count) {
        Element e = POOL[next];
        if (e == null) {
            e = new Element();
            POOL[next] = e;
        }
        next = (next + 1) % POOL.length;
        if (e.verts.length < count * FLOATS) {
            e.verts = new float[count * FLOATS * 2];
        }
        System.arraycopy(data, 0, e.verts, 0, count * FLOATS);
        e.count = count;
        e.pipeline = add ? RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA : RenderPipelines.GUI_TEXTURED;
        //? if >=1.21.11 {
        e.setup = TextureSetup.singleTexture(tex.view(), tex.sampler());
        //?} else {
        /*e.setup = TextureSetup.singleTexture(tex.view());
        *///?}
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int v = 0; v < count; v++) {
            float x = data[v * FLOATS], y = data[v * FLOATS + 1];
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }
        e.scissor = g.raw.scissorStack.peek();
        int bx = (int) Math.floor(minX), by = (int) Math.floor(minY);
        ScreenRectangle box = new ScreenRectangle(bx, by, Math.max(1, (int) Math.ceil(maxX) - bx), Math.max(1, (int) Math.ceil(maxY) - by));
        e.bounds = e.scissor != null ? e.scissor.intersection(box) : box;
        if (e.bounds == null) {
            return;
        }
        //? if >=26.1 {
        /*g.raw.guiRenderState.addGuiElement(e);
        *///?} else {
        g.raw.guiRenderState.submitGuiElement(e);
        //?}
    }
    //?} elif >=1.21.5 {
    /*private static RenderType additiveType(SolTexture tex) {
        Object cached = tex.renderTypes[WorldDraw.KINDS * 2];
        if (cached == null) {
            RenderPipeline p = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(ResourceLocation.fromNamespaceAndPath("cosmeticsmod", "pipeline/sol_gui_add"))
                    .withBlend(BlendFunction.ADDITIVE)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .build();
            cached = RenderType.create("cosmeticsmod_sol_gui_add", 256, false, false, p,
                    RenderType.CompositeState.builder().setTextureState(new RenderStateShard.TextureStateShard(tex.id,
                            net.minecraft.util.TriState.DEFAULT, false)).createCompositeState(false));
            tex.renderTypes[WorldDraw.KINDS * 2] = cached;
        }
        return (RenderType) cached;
    }

    private static void submit(Canvas g, SolTexture tex, boolean add, int count) {
        RenderType type = add ? additiveType(tex) : RenderType.guiTextured(tex.id);
        float[] d = data;
        g.raw.drawSpecial(source -> {
            VertexConsumer consumer = source.getBuffer(type);
            for (int v = 0; v < count; v++) {
                int i = v * FLOATS;
                int c = Float.floatToRawIntBits(d[i + 4]);
                if (add) {
                    // Added one to one: the colour is already premultiplied.
                    c = 0xFF000000 | (c & 0xFFFFFF);
                }
                consumer.addVertex(d[i], d[i + 1], 0f).setUv(d[i + 2], d[i + 3]).setColor(c);
            }
        });
    }
    *///?} else {
    /*private static void submit(Canvas g, SolTexture tex, boolean add, int count) {
        g.raw.flush();
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        float[] d = data;
        for (int v = 0; v < count; v++) {
            int i = v * FLOATS;
            int c = Float.floatToRawIntBits(d[i + 4]);
            if (add) {
                c = 0xFF000000 | (c & 0xFFFFFF);
            }
            builder.addVertex(d[i], d[i + 1], 0f).setUv(d[i + 2], d[i + 3]).setColor(c);
        }
        MeshData mesh = builder.build();
        if (mesh == null) {
            return;
        }
        //? if >=1.21.2 {
        RenderSystem.setShader(CoreShaders.POSITION_TEX_COLOR);
        //?} else {
        RenderSystem.setShader(SolShaders::texColor);
        //?}
        RenderSystem.setShaderTexture(0, tex.id);
        RenderSystem.enableBlend();
        if (add) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.defaultBlendFunc();
        }
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }
    *///?}
}
