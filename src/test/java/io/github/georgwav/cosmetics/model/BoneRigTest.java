package io.github.georgwav.cosmetics.model;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Blockbench / GeckoLib bone animations on model cosmetics. */
class BoneRigTest {
    private static final float EPS = 1.0e-4f;
    private static final BedrockGeometry.BoneOptions NONE = new BedrockGeometry.BoneOptions(Map.of(), Map.of(), Map.of());

    /** A body with two wings: right_wing's pivot is 2 px right of the spine, 4 px below the neck. */
    private static final String WINGS = """
            {"format_version": "1.12.0", "minecraft:geometry": [{
              "description": {"identifier": "geometry.wings", "texture_width": 64, "texture_height": 64},
              "bones": [
                {"name": "right_wing", "parent": "wings", "pivot": [-2, 20, 2],
                 "cubes": [{"origin": [-12, 12, 2], "size": [10, 10, 0], "uv": [0, 0]}]},
                {"name": "wings", "pivot": [0, 24, 0]},
                {"name": "left_wing", "parent": "wings", "pivot": [2, 20, 2],
                 "cubes": [{"origin": [2, 12, 2], "size": [10, 10, 0], "uv": [0, 0]}]},
                {"name": "badge", "pivot": [0, 24, 0], "cubes": [{"origin": [-1, 20, -3], "size": [2, 2, 1], "uv": [0, 0]}]}
              ]}]}
            """;

    private static final String ANIMATIONS = """
            {"format_version": "1.8.0", "geckolib_format_version": 2, "animations": {
              "main": {"loop": true, "animation_length": 2, "bones": {
                "right_wing": {"rotation": {"0.0": [0, 0, 0], "1.0": {"vector": [0, -90, 0], "easing": "linear"},
                                            "2.0": [0, 0, 0]}},
                "left_wing": {"rotation": [0, "math.sin(q.anim_time * 90) * 30", 0]}}},
              "flight": {"loop": true, "bones": {
                "wings": {"position": [0, 2, 0]}}},
              "hidden": {"loop": "hold_on_last_frame", "bones": {"left_wing": {"scale": 0}}}
            }}
            """;

    private static BedrockGeometry.Model model() {
        return BedrockGeometry.convert(JsonParser.parseString(WINGS).getAsJsonObject(), "test", "t:t.png", NONE, List.of());
    }

    private static List<BoneRig.Clip> clips(BedrockGeometry.Model model, List<String> warnings) {
        return BoneAnimationParser.parse(JsonParser.parseString(ANIMATIONS).getAsJsonObject(), model.bones(), "test",
                warnings::add);
    }

    /** Where the correction for {@code bone} moves a point (pixels in, pixels out). */
    private static float[] moved(BoneRig rig, int bone, float x, float y, float z) {
        float[] c = rig.correction();
        int m = bone * 12;
        return new float[]{
                c[m] * x + c[m + 1] * y + c[m + 2] * z + c[m + 9] * 16f,
                c[m + 3] * x + c[m + 4] * y + c[m + 5] * z + c[m + 10] * 16f,
                c[m + 6] * x + c[m + 7] * y + c[m + 8] * z + c[m + 11] * 16f};
    }

    @Test
    void bonesComeParentsFirstAndPartsKnowTheirBone() {
        BedrockGeometry.Model model = model();
        List<BoneRig.Bone> bones = model.bones();
        assertEquals(4, bones.size());
        for (int b = 0; b < bones.size(); b++) {
            assertTrue(bones.get(b).parent() < b, bones.get(b).name() + " after its parent");
        }
        int wings = indexOf(bones, "wings");
        int right = indexOf(bones, "right_wing");
        assertEquals(wings, bones.get(right).parent());
        // The pivot in the mod's space: 4 px below the neck.
        assertEquals(4f, bones.get(right).pivot()[1], EPS);
        assertEquals(right, model.parts().get(0).bone());
    }

    @Test
    void keyframesTurnABoneAroundItsPivot() {
        BedrockGeometry.Model model = model();
        BoneRig rig = new BoneRig(model.bones(), clips(model, new ArrayList<>()), List.of(), false);
        int right = rig.boneNamed("right_wing");
        BoneRig.Playback playback = rig.newPlayback();
        Molang.Context context = new Molang.Context().clear();
        rig.pose(playback, 10.0, 1f, context); // starts "main" now
        assertTrue(rig.isIdentity(right), "at the start the wing is where it was baked");
        rig.pose(playback, 11.0, 1f, context); // one second in: turned -90 degrees around Y
        float[] tip = moved(rig, right, -12f, 4f, 2f);
        // The pivot (-2, 4, 2) stays; a point 10 px to its right swings round to 10 px in front (or behind).
        float[] pivot = moved(rig, right, -2f, 4f, 2f);
        assertEquals(-2f, pivot[0], EPS);
        assertEquals(2f, pivot[2], EPS);
        assertEquals(-2f, tip[0], EPS);
        assertEquals(10f, Math.abs(tip[2] - 2f), EPS);
        rig.pose(playback, 12.0, 1f, context); // looped back to the start
        assertTrue(rig.isIdentity(right));
    }

    @Test
    void molangValuesFollowTheAnimationTime() {
        BedrockGeometry.Model model = model();
        BoneRig rig = new BoneRig(model.bones(), clips(model, new ArrayList<>()), List.of(), false);
        int left = rig.boneNamed("left_wing");
        BoneRig.Playback playback = rig.newPlayback();
        Molang.Context context = new Molang.Context().clear();
        rig.pose(playback, 0.0, 1f, context);
        rig.pose(playback, 1.0, 1f, context); // sin(90) * 30 = 30 degrees
        float[] tip = moved(rig, left, 12f, 4f, 2f);
        assertEquals(2f + 10f * (float) Math.cos(Math.toRadians(30)), tip[0], 1e-3);
        // Speed 0 holds the start.
        rig.pose(playback, 1.0, 0f, context);
        assertTrue(rig.isIdentity(left));
    }

    @Test
    void oddKeyframeValuesAreReadOrLeftOutNotFatal() {
        // NoRisk's easter bunny has a keyframe value that isn't a number; wrapped values are read through.
        String animations = """
                {"animations": {"quirk": {"animation_length": 1, "bones": {
                  "left_wing": {"rotation": {"0.0": [0, 0, 0], "0.75": [null, [45], {"vector": 30}]}}}}}}
                """;
        List<String> warnings = new ArrayList<>();
        List<BoneRig.Clip> clips = BoneAnimationParser.parse(JsonParser.parseString(animations).getAsJsonObject(),
                model().bones(), "test", warnings::add);
        assertEquals(1, clips.size());
        assertFalse(warnings.isEmpty());
        assertTrue(warnings.stream().allMatch(w -> w.contains("can't read null")), warnings.toString());
    }

    @Test
    void statesPickTheAnimationFromWhatThePlayerDoes() {
        BedrockGeometry.Model model = model();
        List<BoneRig.Clip> clips = clips(model, new ArrayList<>());
        int main = BoneAnimationParser.clipNamed(clips, "main");
        int flight = BoneAnimationParser.clipNamed(clips, "flight");
        BoneRig rig = new BoneRig(model.bones(), clips, List.of(List.of(
                new BoneRig.State(flight, Molang.compile("q.is_elytra_flying"), 0.5f),
                new BoneRig.State(main, null, 0f))), false);
        int wings = rig.boneNamed("wings");
        BoneRig.Playback playback = rig.newPlayback();
        Molang.Context context = new Molang.Context().clear();
        rig.pose(playback, 0.0, 1f, context);
        assertTrue(rig.isIdentity(wings), "main doesn't move the wings bone");
        context.set(Molang.Query.IS_ELYTRA_FLYING, true);
        rig.pose(playback, 1.0, 1f, context); // flight starts, blending in over half a second
        assertTrue(rig.isIdentity(wings));
        rig.pose(playback, 1.25, 1f, context);
        assertEquals(-1f, moved(rig, wings, 0, 0, 0)[1], EPS, "halfway: 1 of the 2 px up");
        rig.pose(playback, 2.0, 1f, context);
        assertEquals(-2f, moved(rig, wings, 0, 0, 0)[1], EPS, "2 px up (Bedrock +Y is our -Y)");
        // The wing below moves with its parent.
        int right = rig.boneNamed("right_wing");
        assertFalse(rig.isIdentity(right));
    }

    @Test
    void scaleToNothingHidesABone() {
        BedrockGeometry.Model model = model();
        List<BoneRig.Clip> clips = clips(model, new ArrayList<>());
        BoneRig rig = new BoneRig(model.bones(), clips, List.of(List.of(
                new BoneRig.State(BoneAnimationParser.clipNamed(clips, "hidden"), Molang.compile("q.is_first_person"), 0f))),
                false);
        int left = rig.boneNamed("left_wing");
        Molang.Context context = new Molang.Context().clear();
        BoneRig.Playback playback = rig.newPlayback();
        rig.pose(playback, 0.0, 1f, context);
        assertFalse(rig.isHidden(left), "no state holds: rest");
        rig.pose(playback, 0.1, 1f, context.set(Molang.Query.IS_FIRST_PERSON, true));
        assertTrue(rig.isHidden(left));
    }

    @Test
    void driversGroupStillBonesWithTheMovingOneAbove() {
        BedrockGeometry.Model model = model();
        BoneRig rig = new BoneRig(model.bones(), clips(model, new ArrayList<>()), List.of(), false);
        assertEquals(rig.boneNamed("right_wing"), rig.driver(rig.boneNamed("right_wing")));
        assertEquals(rig.boneNamed("wings"), rig.driver(rig.boneNamed("wings")), "flight moves it");
        assertEquals(-1, rig.driver(rig.boneNamed("badge")), "never moves");
    }

    @Test
    void aPlayerSkeletonFollowsTheBodyParts() {
        String geometry = """
                {"format_version": "1.12.0", "minecraft:geometry": [{
                  "description": {"identifier": "geometry.armor", "texture_width": 64, "texture_height": 64},
                  "bones": [
                    {"name": "bipedHead", "pivot": [0, 24, 0]},
                    {"name": "armorHead", "parent": "bipedHead", "pivot": [0, 24, 0],
                     "cubes": [{"origin": [-4, 32, -4], "size": [8, 2, 8], "uv": [0, 0]}]},
                    {"name": "bipedRightArm", "pivot": [-5, 22, 0]},
                    {"name": "armorRightArm", "parent": "bipedRightArm", "pivot": [-5, 22, 0]},
                    {"name": "sword", "parent": "armorRightArm", "pivot": [-6, 12, 0],
                     "cubes": [{"origin": [-7, 2, -1], "size": [2, 10, 2], "uv": [0, 0]}]}
                  ]}]}
                """;
        BedrockGeometry.Model model = BedrockGeometry.convert(JsonParser.parseString(geometry).getAsJsonObject(), "t",
                "t:t.png", NONE, List.of());
        BoneRig rig = new BoneRig(model.bones(), List.of(), List.of(), true);
        int head = rig.boneNamed("bipedHead");
        assertEquals(head, rig.driver(rig.boneNamed("armorHead")));
        assertEquals(BoneRig.Follow.HEAD, rig.follow(head));
        int arm = rig.boneNamed("bipedRightArm");
        assertEquals(arm, rig.driver(rig.boneNamed("sword")));
        assertEquals(BoneRig.Follow.RIGHT_ARM, rig.follow(arm));
        assertEquals(-5f, rig.follow(arm).pivotX, EPS);
        assertTrue(rig.followsParts());
        assertEquals(BoneRig.Follow.LEFT_LEG, BoneRig.Follow.byBoneName("armor_left_leg"));
    }

    @Test
    void aCosmeticFileBringsItAllTogether() {
        Map<String, String> files = Map.of("t:wings", WINGS, "t:wings.animation", ANIMATIONS);
        CosmeticParser.ModelSource models = id -> {
            String text = files.get(id);
            if (text == null) {
                throw new IOException("no " + id);
            }
            return new StringReader(text);
        };
        List<String> warnings = new ArrayList<>();
        CosmeticDefinition definition = CosmeticParser.parse("t:wings", new StringReader("""
                {"slot": "back", "texture": "t:wings.png", "model": "t:wings", "model_animations": "t:wings.animation",
                 "model_states": [[{"animation": "flight", "when": "q.is_elytra_flying", "blend": 0.2},
                                   {"animation": "main"}],
                                  [{"animation": "no_such_animation"}]]}
                """), new CosmeticParser.Context(models, CosmeticParser.NO_FILES, warnings::add, 1000));
        BoneRig rig = definition.bones();
        assertNotNull(rig);
        assertEquals(2, rig.controllerCount());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no_such_animation")), warnings.toString());
        BakedCosmetic baked = CosmeticBaker.bake(definition);
        assertTrue(baked.hasAnimation());
        // One layer per moving wing, and the badge that never moves on its own.
        assertEquals(3, baked.layers().length);
        assertTrue(java.util.Arrays.stream(baked.layers()).anyMatch(l -> l.bone() == -1));
        assertTrue(java.util.Arrays.stream(baked.layers()).anyMatch(l -> l.bone() == rig.boneNamed("left_wing")));

        // Without animations a model stays one piece, as before.
        CosmeticDefinition still = CosmeticParser.parse("t:still", new StringReader("""
                {"slot": "back", "texture": "t:wings.png", "model": "t:wings"}
                """), models);
        assertNull(still.bones());
        assertEquals(1, CosmeticBaker.bake(still).layers().length);
    }

    @Test
    void aPlacedModelStillTurnsAroundItsPivots() {
        Map<String, String> files = Map.of("t:wings", WINGS, "t:wings.animation", ANIMATIONS);
        CosmeticParser.ModelSource models = id -> new StringReader(files.get(id));
        CosmeticDefinition definition = CosmeticParser.parse("t:big", new StringReader("""
                {"slot": "back", "texture": "t:wings.png", "model": "t:wings", "model_animations": "t:wings.animation",
                 "model_placement": {"scale": 2, "offset": [0, 0, 3]}}
                """), models);
        BoneRig rig = definition.bones();
        int right = rig.boneNamed("right_wing");
        // Baked twice the size and 3 px back: the wing's tip at x -24, its pivot at x -4.
        CosmeticDefinition.Part part = definition.parts().stream().filter(p -> p.bone() == right).findFirst().orElseThrow();
        float[] t = part.cubes().get(0).transform();
        assertEquals(2f, t[0], EPS);
        assertEquals(3f, t[11], EPS);
        BoneRig.Playback playback = rig.newPlayback();
        Molang.Context context = new Molang.Context().clear();
        rig.pose(playback, 0.0, 1f, context);
        assertTrue(rig.isIdentity(right), "at rest nothing moves");
        rig.pose(playback, 1.0, 1f, context);
        float[] pivot = moved(rig, right, -4f, 8f, 7f);
        assertEquals(-4f, pivot[0], EPS);
        assertEquals(8f, pivot[1], EPS);
        assertEquals(7f, pivot[2], EPS);
        float[] tip = moved(rig, right, -24f, 8f, 7f);
        assertEquals(-4f, tip[0], EPS);
        assertEquals(20f, Math.abs(tip[2] - 7f), EPS);
    }

    private static int indexOf(List<BoneRig.Bone> bones, String name) {
        for (int i = 0; i < bones.size(); i++) {
            if (bones.get(i).name().equals(name)) {
                return i;
            }
        }
        return -1;
    }
}
