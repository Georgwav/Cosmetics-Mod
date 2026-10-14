package io.github.georgwav.cosmetics.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.BakedMesh;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.TextureFrames;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.Recolor;
import io.github.georgwav.cosmetics.render.RecoloredTextures;
import io.github.georgwav.cosmetics.render.SoftRaster;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.Resource;
import org.joml.Quaternionf;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The Wardrobe tiles' pictures: each cosmetic on its own, without a player, centred and scaled to fit its tile,
 * turned three quarters towards you (back items from behind). Drawn on the CPU by {@link SoftRaster} into a small
 * texture per cosmetic, so they look the same on every Minecraft version. A picture is made once and kept; the
 * one the mouse points at turns, so it is redrawn each frame. Only a few new ones are made per frame, so opening
 * a long list doesn't stall. Render thread only.
 */
final class TilePictures {
    /** Picture size in pixels. */
    static final int SIZE = 128;
    /** Pictures kept at once (each a small GPU texture). */
    private static final int MAX_KEPT = 120;
    /** New pictures made per frame at most. */
    private static final int NEW_PER_FRAME = 5;
    /** Tilted this far to look down on the cosmetic a little. */
    private static final float PITCH = 18f;
    /** Turned this far from straight on. */
    private static final float YAW = 32f;
    /** Turning speed of the picture the mouse points at, degrees per second. */
    private static final float SPIN = 60f;
    /** How much of the picture the cosmetic fills. */
    private static final float FILL = 0.88f;

    /** Pictures made since the game started (read by the in-game test). */
    static int made;

    private final SoftRaster raster = new SoftRaster(SIZE);
    private final Map<String, SoftRaster.Texture> textures = new HashMap<>();
    /** Textures recoloured as their cosmetics' settings ask, by texture and recolour. */
    private final Map<String, SoftRaster.Texture> recolored = new HashMap<>();
    private final Map<String, float[]> framing = new HashMap<>();
    private final LinkedHashMap<String, Picture> pictures = new LinkedHashMap<>(16, 0.75f, true);
    private final float[] matrix = new float[12];
    private final float[] normal = new float[9];
    private final long startNanos = System.nanoTime();
    private int madeThisFrame;
    private int generation = -1;
    private int nextSlot;

    private static final class Picture {
        final PictureTexture texture;
        float yaw = Float.NaN;
        boolean empty;

        Picture(int slot) {
            texture = new PictureTexture("tile_picture/" + slot, SIZE);
        }
    }

    /** Call once per frame before drawing tiles. */
    void beginFrame() {
        madeThisFrame = 0;
        if (generation != CosmeticRegistry.generation()) {
            // Cosmetics or resource packs were reloaded: textures and models may have changed.
            close();
            generation = CosmeticRegistry.generation();
        }
    }

    /**
     * Draws the picture of {@code cosmetic} into the square at ({@code x}, {@code y}) of {@code size} GUI pixels;
     * {@code turning}: the one the mouse points at. Returns false when it has nothing to show (a cosmetic with only
     * particles, or one whose picture isn't made yet), so the tile can show something else.
     */
    boolean draw(Canvas g, BakedCosmetic cosmetic, int x, int y, int size, boolean turning) {
        float yaw = YAW + (back(cosmetic) ? 180f : 0f);
        if (turning) {
            yaw += (System.nanoTime() - startNanos) / 1.0e9f * SPIN;
        }
        yaw = Math.round(yaw % 360f);
        Picture picture = pictures.get(cosmetic.id());
        if (picture == null) {
            if (madeThisFrame >= NEW_PER_FRAME) {
                return false;
            }
            picture = new Picture(slotFor());
            pictures.put(cosmetic.id(), picture);
        }
        if (picture.yaw != yaw && (madeThisFrame < NEW_PER_FRAME || turning)) {
            madeThisFrame++;
            picture.empty = !paint(cosmetic, yaw, picture.texture);
            picture.yaw = yaw;
            made++;
        }
        if (picture.empty || Float.isNaN(picture.yaw)) {
            return false;
        }
        picture.texture.draw(g, x, y, size);
        return true;
    }

    /** A free texture slot, closing the picture used longest ago when there are too many. */
    private int slotFor() {
        if (pictures.size() >= MAX_KEPT) {
            Iterator<Map.Entry<String, Picture>> oldest = pictures.entrySet().iterator();
            Picture dropped = oldest.next().getValue();
            oldest.remove();
            dropped.texture.release();
        }
        return nextSlot++;
    }

    /** Releases every picture's texture (when the screen closes or cosmetics reload). */
    void close() {
        for (Picture picture : pictures.values()) {
            picture.texture.release();
        }
        pictures.clear();
        textures.clear();
        recolored.clear();
        framing.clear();
    }

    private static boolean back(BakedCosmetic cosmetic) {
        return cosmetic.slot() == CosmeticSlot.CAPE || cosmetic.slot() == CosmeticSlot.BACK || cosmetic.wings() != null;
    }

    // ------------------------------------------------------------------ painting

    /** Paints the cosmetic turned {@code yaw} degrees into the texture; false when it has no geometry. */
    private boolean paint(BakedCosmetic cosmetic, float yaw, PictureTexture texture) {
        float[] frame = framing.computeIfAbsent(cosmetic.id(), id -> frame(cosmetic));
        if (frame == null) {
            return false;
        }
        raster.clear();
        PoseStack poseStack = new PoseStack();
        float half = SIZE / 2f;
        float scale = half * FILL / frame[3];
        poseStack.translate(half, half, 0f);
        poseStack.scale(scale, scale, scale);
        CosmeticRenderer.rotate(poseStack, new Quaternionf().rotationX((float) Math.toRadians(PITCH)));
        CosmeticRenderer.rotate(poseStack, new Quaternionf().rotationY((float) Math.toRadians(yaw)));
        poseStack.translate(-frame[0], -frame[1], -frame[2]);
        CosmeticRenderer.drawAlone(poseStack, cosmetic, (pose, c, layer, light, color, textureFrame, clip, clipTime, cloth) -> {
            load(pose);
            TextureFrames frames = layer.textureFrames();
            String textureId = frames != null && !frames.strip() ? frames.texture(textureFrame) : layer.texture();
            float vOffset = frames != null ? frames.vOffset(textureFrame) : 0f;
            // Recoloured and painted as in the game (the part being drawn's recolour, see RecoloredTextures).
            Recolor recolor = RecoloredTextures.current();
            SoftRaster.Texture image = recolor == null ? texture(textureId) : recolored(textureId, recolor);
            SoftRaster.Blend blend = switch (layer.mode()) {
                case CUTOUT -> SoftRaster.Blend.CUTOUT;
                case TRANSLUCENT -> SoftRaster.Blend.TRANSLUCENT;
                case EMISSIVE -> SoftRaster.Blend.EMISSIVE;
            };
            for (BakedMesh mesh : meshes(layer, clip, clipTime)) {
                raster.draw(mesh, matrix, normal, vOffset, image, color, blend);
            }
        });
        texture.upload(raster.pixels());
        return true;
    }

    /**
     * Where the cosmetic is and how big, unturned: the middle of its bounds and a radius that keeps it inside the
     * picture however it turns (around the vertical, and the tilt). Null when it has no geometry.
     */
    private float[] frame(BakedCosmetic cosmetic) {
        float[] bounds = SoftRaster.emptyBounds();
        CosmeticRenderer.drawAlone(new PoseStack(), cosmetic, (pose, c, layer, light, color, textureFrame, clip, clipTime, cloth) -> {
            load(pose);
            for (BakedMesh mesh : meshes(layer, clip, clipTime)) {
                SoftRaster.bounds(mesh, matrix, bounds);
            }
        });
        if (!(bounds[0] <= bounds[3])) {
            return null;
        }
        float hx = (bounds[3] - bounds[0]) / 2f;
        float hy = (bounds[4] - bounds[1]) / 2f;
        float hz = (bounds[5] - bounds[2]) / 2f;
        float tilt = (float) Math.sin(Math.toRadians(PITCH));
        float radius = Math.max((float) Math.sqrt(hx * hx + hz * hz), hy + hz * tilt);
        return new float[]{(bounds[0] + bounds[3]) / 2f, (bounds[1] + bounds[4]) / 2f, (bounds[2] + bounds[5]) / 2f,
                Math.max(radius, 1.0e-3f)};
    }

    private static BakedMesh[] meshes(BakedCosmetic.Layer layer, int clip, float clipTime) {
        if (layer.skin() != null) {
            return new BakedMesh[]{layer.skin().update(clip, clipTime)};
        }
        return layer.smoothMesh() != null ? new BakedMesh[]{layer.mesh(), layer.smoothMesh()} : new BakedMesh[]{layer.mesh()};
    }

    private void load(PoseStack poseStack) {
        PictureTexture.load(poseStack, matrix, normal);
    }

    /** A texture's pixels recoloured, made once per recolour. */
    private SoftRaster.Texture recolored(String id, Recolor recolor) {
        return recolored.computeIfAbsent(id + "\n" + recolor, k -> {
            SoftRaster.Texture base = texture(id);
            int[] out = new int[base.argb().length];
            recolor.applyAll(base.argb(), out);
            return new SoftRaster.Texture(base.width(), base.height(), out);
        });
    }

    /** A texture's pixels, read once from the resource packs; plain white when it can't be read. */
    private SoftRaster.Texture texture(String id) {
        return textures.computeIfAbsent(id, TilePictures::read);
    }

    private static SoftRaster.Texture read(String id) {
        try {
            //? if >=1.21.11 {
            Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(Identifier.parse(id));
            //?} else {
            /*Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(ResourceLocation.parse(id));
            *///?}
            if (resource.isEmpty()) {
                return SoftRaster.Texture.WHITE;
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                return new SoftRaster.Texture(image.getWidth(), image.getHeight(), image.makePixelArray());
            }
        } catch (Exception e) {
            CosmeticsMod.LOGGER.debug("Wardrobe picture: couldn't read texture {}", id, e);
            return SoftRaster.Texture.WHITE;
        }
    }
}
