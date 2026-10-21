package io.github.georgwav.cosmetics.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.model.BakedMesh;
import io.github.georgwav.cosmetics.model.TextureFrames;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.MeshEmitter;
import io.github.georgwav.cosmetics.render.PlayerRenderInfo;
import io.github.georgwav.cosmetics.render.Recolor;
import io.github.georgwav.cosmetics.render.RecoloredTextures;
import io.github.georgwav.cosmetics.render.SoftRaster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Quaternionf;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * You and what you wear without a world: the game's own player model (its geometry straight from the game, so it
 * matches on every version) in your skin ({@link SkinPixels}), with your cosmetics on it as the game draws them,
 * optionally standing on a little grass island. Drawn on the CPU by {@link SoftRaster} into a texture, like the
 * Wardrobe's tile pictures: the Wardrobe's preview before you join a world, and the title screen's figure.
 * Render thread.
 */
public final class Mannequin {
    /** Repaints at most this often (cosmetics animate), seconds. */
    private static final double REPAINT = 1.0 / 30.0;

    private final SoftRaster raster;
    private final String name;
    private PictureTexture texture;
    private final int size;
    private final float[] matrix = new float[12];
    private final float[] normal = new float[9];
    private final float[] identity = {1, 0, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0};
    private final float[] identityNormal = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private final Map<String, SoftRaster.Texture> textures = new HashMap<>();
    private final Map<String, SoftRaster.Texture> recolored = new HashMap<>();
    private final PlayerRenderInfo info = new PlayerRenderInfo();
    private final Collector collector = new Collector();
    private ModelPart root, head, body;
    private ModelPart[] limbs;
    private Boolean modelSlim;
    private long lastPaint;
    private boolean painted;
    private int generation = -1;
    /** For the in-game test: frames painted. */
    public static int framesPainted;

    public Mannequin(String name, int size) {
        this.size = size;
        this.name = name;
        this.raster = new SoftRaster(size);
    }

    /**
     * Draws the figure into the square at ({@code x}, {@code y}) of {@code drawSize} GUI pixels, turned {@code yaw}
     * degrees (0: facing you) and seen from {@code pitch} degrees above; on the island when {@code island}.
     */
    public void draw(Canvas g, int x, int y, int drawSize, float yaw, float pitch, boolean island) {
        if (texture == null) {
            // First use, or used again after release (a screen opened over the title screen, then closed).
            texture = new PictureTexture("mannequin/" + name, size);
            painted = false;
        }
        if (generation != io.github.georgwav.cosmetics.model.CosmeticRegistry.generation()) {
            // Cosmetics or resource packs reloaded: their textures may have changed.
            generation = io.github.georgwav.cosmetics.model.CosmeticRegistry.generation();
            textures.clear();
            recolored.clear();
            painted = false;
        }
        long now = System.nanoTime();
        if (!painted || now - lastPaint > REPAINT * 1e9) {
            lastPaint = now;
            try {
                paint(yaw, pitch, island);
                painted = true;
                framesPainted++;
            } catch (RuntimeException | LinkageError e) {
                io.github.georgwav.cosmetics.sol.util.LogOnce.warn("mannequin", "The mannequin couldn't be drawn: {}", e.toString());
                return;
            }
        }
        texture.draw(g, x, y, drawSize);
    }

    public void release() {
        if (texture != null) {
            texture.release();
            texture = null;
        }
    }

    private void model(boolean slim) {
        if (modelSlim != null && modelSlim == slim) {
            return;
        }
        //? if >=1.21.11 {
        var mesh = net.minecraft.client.model.player.PlayerModel.createMesh(CubeDeformation.NONE, slim);
        //?} else {
        /*var mesh = net.minecraft.client.model.PlayerModel.createMesh(CubeDeformation.NONE, slim);
        *///?}
        root = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        // 1.21.1 keeps the deadmau5 ears and the cape in the player model: not the mannequin's.
        for (String extra : new String[]{"ear", "cloak"}) {
            if (root.hasChild(extra)) {
                root.getChild(extra).visible = false;
            }
        }
        head = root.getChild("head");
        body = root.getChild("body");
        limbs = new ModelPart[]{root.getChild("left_arm"), root.getChild("right_arm"), root.getChild("left_leg"),
                root.getChild("right_leg")};
        // Standing easy: the arms a little out from the sides.
        limbs[0].zRot = -0.06f;
        limbs[1].zRot = 0.06f;
        modelSlim = slim;
    }

    private void paint(float yaw, float pitch, boolean island) {
        SkinPixels.Skin skin = SkinPixels.current();
        model(skin.slim());
        raster.clear();
        PoseStack pose = new PoseStack();
        float half = size / 2f;
        // Model space has y down from the neck: the head's top at -0.5, the feet at 1.5 (the player is two blocks
        // tall); the island reaches down to 4.5. Room is left round it for wings, halos and pets.
        float tall = island ? 5.8f : 2.6f;
        float middle = island ? 2.0f : 0.5f;
        float scale = size / tall;
        pose.translate(half, half, 0f);
        pose.scale(scale, scale, scale);
        CosmeticRenderer.rotate(pose, new Quaternionf().rotationX((float) Math.toRadians(pitch)));
        CosmeticRenderer.rotate(pose, new Quaternionf().rotationY((float) Math.toRadians(yaw)));
        pose.translate(0f, -middle, 0f);
        if (island) {
            island(pose);
        }
        // The body, from the game's own model.
        collector.reset();
        root.render(pose, collector, MeshEmitter.FULL_BRIGHT, 0);
        raster.draw(collector.mesh(), identity, identityNormal, 0f, skin.texture(), 0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        // What you wear, as the game puts it on you.
        info.uuid = Minecraft.getInstance().getUser().getProfileId();
        info.local = true;
        info.onGround = true;
        info.gui = true;
        CosmeticRenderer.render(pose, MeshEmitter.FULL_BRIGHT, info, null, head, body, limbs,
                (p, c, layer, light, color, textureFrame, clip, clipTime, cloth) -> {
                    PictureTexture.load(p, matrix, normal);
                    TextureFrames frames = layer.textureFrames();
                    String textureId = frames != null && !frames.strip() ? frames.texture(textureFrame) : layer.texture();
                    float vOffset = frames != null ? frames.vOffset(textureFrame) : 0f;
                    Recolor recolor = RecoloredTextures.current();
                    SoftRaster.Texture image = recolor == null ? texture(textureId) : recolored(textureId, recolor);
                    SoftRaster.Blend blend = switch (layer.mode()) {
                        case CUTOUT -> SoftRaster.Blend.CUTOUT;
                        case TRANSLUCENT -> SoftRaster.Blend.TRANSLUCENT;
                        case EMISSIVE -> SoftRaster.Blend.EMISSIVE;
                    };
                    for (BakedMesh mesh : TilePictures.meshes(layer, clip, clipTime)) {
                        raster.draw(mesh, matrix, normal, vOffset, image, color, blend);
                    }
                });
        texture.upload(raster.pixels());
    }

    // ------------------------------------------------------------------ the island

    private static final int GRASS = 0xFF7CBD6B;

    /** Three by three grass blocks under the feet, dirt under the middle row, a stone tip below. */
    private void island(PoseStack pose) {
        PictureTexture.load(pose, matrix, normal);
        // Model space: y down, the feet at 1.5 (24 pixels below the neck), a block 1 wide.
        float top = 1.5f;
        SoftRaster.Texture grassTop = texture("minecraft:textures/block/grass_block_top.png");
        SoftRaster.Texture grassSide = texture("minecraft:textures/block/grass_block_side.png");
        SoftRaster.Texture overlay = texture("minecraft:textures/block/grass_block_side_overlay.png");
        SoftRaster.Texture dirt = texture("minecraft:textures/block/dirt.png");
        SoftRaster.Texture stone = texture("minecraft:textures/block/stone.png");
        for (int bx = -1; bx <= 1; bx++) {
            for (int bz = -1; bz <= 1; bz++) {
                float x0 = bx - 0.5f, z0 = bz - 0.5f;
                raster.draw(face(x0, top, z0, 1f, 1f, 1f, TOP), matrix, normal, 0f, grassTop, GRASS, SoftRaster.Blend.CUTOUT);
                BakedMesh sides = face(x0, top, z0, 1f, 1f, 1f, SIDES);
                raster.draw(sides, matrix, normal, 0f, grassSide, 0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
                raster.draw(sides, matrix, normal, 0f, overlay, GRASS, SoftRaster.Blend.TRANSLUCENT);
            }
        }
        for (int bx = -1; bx <= 1; bx++) {
            BakedMesh d = face(bx - 0.5f, top + 1f, -0.5f, 1f, 1f, 1f, SIDES | BOTTOM);
            raster.draw(d, matrix, normal, 0f, dirt, 0xFFFFFFFF, SoftRaster.Blend.CUTOUT);
        }
        raster.draw(face(-0.5f, top + 2f, -0.5f, 1f, 1f, 1f, SIDES | BOTTOM), matrix, normal, 0f, stone, 0xFFFFFFFF,
                SoftRaster.Blend.CUTOUT);
    }

    private static final int TOP = 1, BOTTOM = 2, SIDES = 4;

    /** A block's faces as a mesh (y down: {@code y} is its top), each face the whole texture, sides upright. */
    private static BakedMesh face(float x, float y, float z, float w, float h, float d, int which) {
        float[] out = new float[6 * BakedMesh.FLOATS_PER_QUAD];
        int[] at = {0};
        float x1 = x + w, y1 = y + h, z1 = z + d;
        if ((which & TOP) != 0) {
            quad(out, at, x, y, z, x1, y, z, x1, y, z1, x, y, z1, 0, -1, 0);
        }
        if ((which & BOTTOM) != 0) {
            quad(out, at, x, y1, z1, x1, y1, z1, x1, y1, z, x, y1, z, 0, 1, 0);
        }
        if ((which & SIDES) != 0) {
            quad(out, at, x, y, z, x1, y, z, x1, y1, z, x, y1, z, 0, 0, -1);
            quad(out, at, x1, y, z1, x, y, z1, x, y1, z1, x1, y1, z1, 0, 0, 1);
            quad(out, at, x, y, z1, x, y, z, x, y1, z, x, y1, z1, -1, 0, 0);
            quad(out, at, x1, y, z, x1, y, z1, x1, y1, z1, x1, y1, z, 1, 0, 0);
        }
        return BakedMesh.of(Arrays.copyOf(out, at[0]));
    }

    /** One face: four corners, top left first, clockwise; the texture's top on the first two. */
    private static void quad(float[] out, int[] at, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, float nx, float ny, float nz) {
        float[][] v = {{ax, ay, az, 0, 0}, {bx, by, bz, 1, 0}, {cx, cy, cz, 1, 1}, {dx, dy, dz, 0, 1}};
        for (float[] p : v) {
            int i = at[0];
            out[i] = p[0];
            out[i + 1] = p[1];
            out[i + 2] = p[2];
            out[i + 3] = p[3];
            out[i + 4] = p[4];
            out[i + 5] = nx;
            out[i + 6] = ny;
            out[i + 7] = nz;
            at[0] += BakedMesh.FLOATS_PER_VERTEX;
        }
    }

    // ------------------------------------------------------------------ textures

    private SoftRaster.Texture texture(String id) {
        return textures.computeIfAbsent(id, TilePictures::read);
    }

    private SoftRaster.Texture recolored(String id, Recolor recolor) {
        if (recolored.size() > 64) {
            // A colour being dragged makes a copy per step: keep only the latest few.
            recolored.clear();
        }
        return recolored.computeIfAbsent(id + "\n" + recolor, k -> {
            SoftRaster.Texture base = texture(id);
            int[] out = new int[base.argb().length];
            recolor.applyAll(base.argb(), out);
            return new SoftRaster.Texture(base.width(), base.height(), out);
        });
    }

    /**
     * Collects what the game's model draws (already moved by the pose) as quads in the mesh layout. Only position,
     * texture and normal are kept; a vertex is complete with its normal, which the game sets last.
     */
    private static final class Collector implements VertexConsumer {
        private float[] data = new float[2048];
        private int used;
        private float x, y, z, u, v;

        void reset() {
            used = 0;
        }

        BakedMesh mesh() {
            int whole = used - used % BakedMesh.FLOATS_PER_QUAD;
            return BakedMesh.of(Arrays.copyOf(data, whole));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        // Not on every version's interface (so no @Override): a colour as one int, a third texture
        // coordinate, a line width.
        public VertexConsumer setColor(int argb) {
            return this;
        }

        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        public VertexConsumer setLineWidth(float width) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            this.u = u;
            this.v = v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float nx, float ny, float nz) {
            if (used + BakedMesh.FLOATS_PER_VERTEX > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[used] = x;
            data[used + 1] = y;
            data[used + 2] = z;
            data[used + 3] = u;
            data[used + 4] = v;
            data[used + 5] = nx;
            data[used + 6] = ny;
            data[used + 7] = nz;
            used += BakedMesh.FLOATS_PER_VERTEX;
            return this;
        }
    }
}
