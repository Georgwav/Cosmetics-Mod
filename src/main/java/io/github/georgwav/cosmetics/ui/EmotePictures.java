package io.github.georgwav.cosmetics.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePose;
import io.github.georgwav.cosmetics.model.BakedMesh;
import io.github.georgwav.cosmetics.model.CosmeticBaker;
import io.github.georgwav.cosmetics.model.CosmeticParser;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.SoftRaster;
import org.joml.Quaternionf;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Pictures of emotes for the emote wheel: a plain figure (head, body, arms and legs in the screen's colours) in the
 * emote's most telling pose ({@link EmotePose#bestTime}), turned a little towards you. Drawn on the CPU by
 * {@link SoftRaster} like the Wardrobe's tile pictures; one small texture per emote, made once. Render thread only.
 */
final class EmotePictures {
    static final int SIZE = 96;
    /** Model pixels that fit across the picture. */
    private static final float VIEW = 46f;
    /** The height (model pixels, +Y down from the neck) the picture centres on. */
    private static final float CENTRE_Y = 5f;
    private static final float PITCH = 8f;
    private static final float YAW = 28f;
    /** Head, body, right arm, left arm, right leg, left leg, as {@link EmotePose} orders the parts. */
    private static final String[] BOXES = {
            "[-4,-8,-4],\"to\":[4,0,4]", "[-4,0,-2],\"to\":[4,12,2]", "[-3,-2,-2],\"to\":[1,10,2]",
            "[-1,-2,-2],\"to\":[3,10,2]", "[-2,0,-2],\"to\":[2,12,2]", "[-2,0,-2],\"to\":[2,12,2]"};
    private static final int[] COLORS = {0xFFEAD7C3, Theme.ACCENT, 0xFF8FA4FF, 0xFF8FA4FF, 0xFF414B7A, 0xFF414B7A};

    /** Pictures made since the game started (read by the in-game test). */
    static int made;

    private static BakedMesh[] parts;

    private final SoftRaster raster = new SoftRaster(SIZE);
    private final Map<String, PictureTexture> pictures = new HashMap<>();
    private final float[] matrix = new float[12];
    private final float[] normal = new float[9];

    /** Draws the picture of {@code emote} into the square at ({@code x}, {@code y}) of {@code size} GUI pixels. */
    void draw(Canvas g, Emote emote, int x, int y, int size) {
        PictureTexture picture = pictures.get(emote.id());
        if (picture == null) {
            picture = new PictureTexture("emote_picture/" + pictures.size(), SIZE);
            paint(emote, picture);
            pictures.put(emote.id(), picture);
            made++;
        }
        picture.draw(g, x, y, size);
    }

    /** Releases every picture's texture (when the screen closes). */
    void close() {
        pictures.values().forEach(PictureTexture::release);
        pictures.clear();
    }

    private void paint(Emote emote, PictureTexture picture) {
        float[][] posed = EmotePose.posed(emote, EmotePose.bestTime(emote));
        raster.clear();
        PoseStack poseStack = new PoseStack();
        float scale = SIZE * 16f / VIEW;
        poseStack.translate(SIZE / 2f, SIZE / 2f, 0f);
        poseStack.scale(scale, scale, scale);
        CosmeticRenderer.rotate(poseStack, new Quaternionf().rotationX((float) Math.toRadians(PITCH)));
        CosmeticRenderer.rotate(poseStack, new Quaternionf().rotationY((float) Math.toRadians(YAW)));
        poseStack.translate(0f, -CENTRE_Y / 16f, 0f);
        BakedMesh[] meshes = parts();
        for (int p = 0; p < EmotePose.PARTS; p++) {
            float[] part = posed[p];
            poseStack.pushPose();
            poseStack.translate(part[0] / 16f, part[1] / 16f, part[2] / 16f);
            CosmeticRenderer.rotate(poseStack, new Quaternionf().rotationZYX(part[5], part[4], part[3]));
            PictureTexture.load(poseStack, matrix, normal);
            raster.draw(meshes[p], matrix, normal, 0f, SoftRaster.Texture.WHITE, COLORS[p], SoftRaster.Blend.CUTOUT);
            poseStack.popPose();
        }
        picture.upload(raster.pixels());
    }

    /** The figure's six boxes, baked once from cosmetic-style cubes (positions in blocks). */
    private static BakedMesh[] parts() {
        if (parts == null) {
            BakedMesh[] baked = new BakedMesh[BOXES.length];
            for (int i = 0; i < BOXES.length; i++) {
                String json = "{\"slot\":\"head\",\"texture\":\"cosmeticsmod:none.png\",\"texture_size\":[16,16],"
                        + "\"parts\":[{\"cubes\":[{\"from\":" + BOXES[i] + ",\"uv\":[0,0]}]}]}";
                baked[i] = CosmeticBaker.bake(CosmeticParser.parse("cosmeticsmod:figure_" + i, new StringReader(json)))
                        .layers()[0].mesh();
            }
            parts = baked;
        }
        return parts;
    }
}
