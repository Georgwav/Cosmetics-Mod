package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonParser;

/**
 * Your arms as the packs' animators posed them: the arm template VCT Lock In's geometry still has under its
 * {@code *_pos} bones (the player model's arm with your skin's box UVs, turned half round so the fist is at the top),
 * read by the same face table as the knives. In a {@code *_pos} bone's frame (knife space) the right arm spans x -8 to
 * -4, y 0 to 12 with the fist at the top, z -2 to 2; the left one is mirrored; slim arms are 3 px wide on the same
 * centre line. Each arm comes with its sleeve (the skin's second layer, a quarter pixel bigger). Pure Java.
 */
public final class ArmGeo {
    /** The fist's middle in a {@code *_pos} bone's frame: x for the right arm (the left is mirrored), y. */
    public static final float FIST_X = -6f, FIST_Y = 12f;

    private static final KnifeGeo WIDE = geo(false), SLIM = geo(true);

    private ArmGeo() {
    }

    /** The faces of an arm and its sleeve ({@link KnifeGeo#QUAD} floats each), in its {@code *_pos} bone's frame. */
    public static float[] quads(boolean right, boolean slim) {
        KnifeGeo g = slim ? SLIM : WIDE;
        return g.bones[g.bone(right ? "arm_right" : "arm_left")].quads;
    }

    private static KnifeGeo geo(boolean slim) {
        // Bedrock coordinates relative to the pos bone's pivot: the arm hangs below y 48, turned round about (0, 24, 0).
        float w = slim ? 3f : 4f;
        float rightX = -6f - w / 2f, leftX = 6f - w / 2f;
        String json = "{\"minecraft:geometry\":[{\"description\":{\"texture_width\":64,\"texture_height\":64},\"bones\":["
                + bone("arm_right", rightX, w, 40, 16, 40, 32) + "," + bone("arm_left", leftX, w, 32, 48, 48, 48) + "]}]}";
        return KnifeGeo.parse(JsonParser.parseString(json).getAsJsonObject(), "arm template");
    }

    private static String bone(String name, float x, float w, int u, int v, int su, int sv) {
        String place = "\"pivot\":[0,24,0],\"rotation\":[0,0,-180]";
        return "{\"name\":\"" + name + "\",\"pivot\":[0,0,0],\"cubes\":["
                + "{\"origin\":[" + x + ",36,-2],\"size\":[" + w + ",12,4],\"uv\":[" + u + "," + v + "]," + place + "},"
                + "{\"origin\":[" + x + ",36,-2],\"size\":[" + w + ",12,4],\"uv\":[" + su + "," + sv + "],\"inflate\":0.25,"
                + place + "}]}";
    }
}
