package io.github.georgwav.cosmetics.emote;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmoteTest {
    private static final float EPS = 1e-4f;
    private static final float DEG = (float) Math.PI / 180f;

    private static List<Emote> parse(String json) {
        return EmoteParser.parse("test:emote", new StringReader(json), warning -> { });
    }

    /** The game's rest pose of the player model. */
    private static float[][] rest() {
        return new float[][]{{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
    }

    private static float[][] restPose() {
        float[][] parts = new float[EmotePose.PARTS][];
        float[][] rest = rest();
        for (int p = 0; p < EmotePose.PARTS; p++) {
            parts[p] = new float[]{rest[p][0], rest[p][1], rest[p][2], 0, 0, 0};
        }
        return parts;
    }

    // ---------------------------------------------------------------- pictures

    @Test
    void theBestMomentIsTheBiggestPose() {
        // The right arm goes up over a second and back down over the next: the picture shows it up.
        Emote wave = parse("""
                {"format_version": "1.8.0", "animations": {"animation.wave": {"animation_length": 2,
                  "bones": {"right_arm": {"rotation": {"0.0": [0, 0, 0], "1.0": [0, 0, 150], "2.0": [0, 0, 0]}}}}}}
                """).get(0);
        assertEquals(1f, EmotePose.bestTime(wave), 0.05f);
        float[][] posed = EmotePose.posed(wave, 1f);
        assertTrue(Math.abs(posed[EmotePose.RIGHT_ARM][5]) > 2f, "the arm is raised at the best moment");
        assertEquals(EmotePose.REST[EmotePose.LEFT_LEG][0], posed[EmotePose.LEFT_LEG][0], EPS, "legs stay put");
    }

    @Test
    void noRisksBracketedKeyframesPlay() {
        // NoRisk's emotes write each keyframe in brackets once more, and some as [arriving, leaving].
        Emote wave = parse("""
                {"format_version": "1.8.0", "animations": {"animation.emote.wave": {"animation_length": 2,
                  "bones": {"right_arm": {"rotation": {"0.0": [[0, 0, 0]], "1.0": [[0, 0, 150], [0, 0, 150]],
                                                       "2.0": {"post": [[0, 0, 0]]}}}}}}}
                """).get(0);
        assertTrue(Math.abs(EmotePose.posed(wave, 1f)[EmotePose.RIGHT_ARM][5]) > 2f, "the arm is raised");
    }

    // ---------------------------------------------------------------- easing and channels

    @Test
    void easesStartAtZeroAndEndAtOne() {
        for (Ease ease : Ease.values()) {
            if (ease == Ease.STEP) {
                continue;
            }
            assertEquals(0f, ease.apply(0f), 1e-3f, ease.name());
            assertEquals(1f, ease.apply(1f), 1e-3f, ease.name());
        }
        assertEquals(0f, Ease.STEP.apply(0.99f));
        assertEquals(1f, Ease.STEP.apply(1f));
    }

    @Test
    void easeNamesInEveryUsualSpelling() {
        assertEquals(Ease.IN_OUT_SINE, Ease.byName("easeInOutSine"));
        assertEquals(Ease.IN_OUT_SINE, Ease.byName("INOUTSINE"));
        assertEquals(Ease.OUT_BOUNCE, Ease.byName("out_bounce"));
        assertEquals(Ease.STEP, Ease.byName("CONSTANT"));
        assertEquals(Ease.CATMULLROM, Ease.byName("catmullrom"));
        assertNull(Ease.byName("wobbly"));
    }

    @Test
    void channelInterpolatesBetweenKeyframes() {
        Channel c = new Channel(new float[]{0, 1, 2}, new float[]{0, 10, 0},
                new Ease[]{Ease.LINEAR, Ease.LINEAR, Ease.STEP}, false);
        assertEquals(0f, c.sample(-1f, 99f), EPS);
        assertEquals(5f, c.sample(0.5f, 99f), EPS);
        assertEquals(10f, c.sample(1f, 99f), EPS);
        // A step holds the value until the keyframe.
        assertEquals(10f, c.sample(1.9f, 99f), EPS);
        assertEquals(0f, c.sample(2f, 99f), EPS);
        assertEquals(0f, c.sample(5f, 99f), EPS);
    }

    @Test
    void leadInMovesFromTheOwnPose() {
        Channel c = new Channel(new float[]{1}, new float[]{10}, new Ease[]{Ease.LINEAR}, true);
        assertEquals(4f, c.sample(0f, 4f), EPS);
        assertEquals(7f, c.sample(0.5f, 4f), EPS);
        assertEquals(10f, c.sample(1f, 4f), EPS);
    }

    @Test
    void catmullRomPassesThroughItsKeyframes() {
        Channel c = new Channel(new float[]{0, 1, 2, 3}, new float[]{0, 5, 2, 8},
                new Ease[]{Ease.CATMULLROM, Ease.CATMULLROM, Ease.CATMULLROM, Ease.CATMULLROM}, false);
        assertEquals(5f, c.sample(1f, 0f), EPS);
        assertEquals(2f, c.sample(2f, 0f), EPS);
        float mid = c.sample(1.5f, 0f);
        assertTrue(mid > 2f && mid < 5f, "smooth in between: " + mid);
    }

    @Test
    void sameTimeKeyframesJump() {
        Channel c = new Channel(new float[]{0, 1, 1, 2}, new float[]{0, 10, -10, 0},
                new Ease[]{Ease.LINEAR, Ease.LINEAR, Ease.STEP, Ease.LINEAR}, false);
        assertEquals(9f, c.sample(0.9f, 0f), EPS);
        assertEquals(-10f, c.sample(1f, 0f), EPS);
        assertEquals(-5f, c.sample(1.5f, 0f), EPS);
    }

    // ---------------------------------------------------------------- timing

    @Test
    void timingOfEachEnding() {
        Map<Emote.Bone, Channel[]> none = Map.of();
        Emote once = new Emote("a:b", null, null, 2f, Emote.Ending.STOP, 0f, 0.5f, 0.5f, 24f, none);
        assertEquals(0.5f, once.weightAt(0.25f), EPS);
        assertEquals(1f, once.weightAt(1f), EPS);
        assertEquals(0.5f, once.weightAt(2.25f), EPS);
        assertEquals(2f, once.timeAt(2.25f), EPS);
        assertTrue(once.finished(2.5f));

        Emote loop = new Emote("a:b", null, null, 2f, Emote.Ending.LOOP, 0.5f, 0f, 0.5f, 24f, none);
        assertEquals(0.5f, loop.timeAt(2f + 1e-6f), 1e-3f);
        assertEquals(1f, loop.timeAt(2.5f), EPS);
        assertEquals(1f, loop.weightAt(100f), EPS);
        assertTrue(!loop.finished(100f));

        Emote hold = new Emote("a:b", null, null, 2f, Emote.Ending.HOLD, 0f, 0f, 0.5f, 24f, none);
        assertEquals(2f, hold.timeAt(50f), EPS);
        assertEquals(1f, hold.weightAt(50f), EPS);
    }

    // ---------------------------------------------------------------- Bedrock files

    private static final String WAVE = """
            {
              "format_version": "1.8.0",
              "animations": {
                "animation.emote.wave": {
                  "loop": true,
                  "animation_length": 1.0,
                  "bones": {
                    "rightArm": {
                      "rotation": {
                        "0.0": [0, 0, 150],
                        "0.5": {"post": [0, 0, 120], "lerp_mode": "catmullrom"},
                        "1.0": [0, 0, 150]
                      }
                    },
                    "body": {"position": [0, 2, 0]},
                    "tail": {"rotation": [10, 0, 0]}
                  }
                }
              }
            }
            """;

    @Test
    void readsBlockbenchAnimations() {
        List<String> warnings = new ArrayList<>();
        List<Emote> emotes = EmoteParser.parse("test:wave", new StringReader(WAVE), warnings::add);
        assertEquals(1, emotes.size());
        Emote wave = emotes.get(0);
        assertEquals("test:wave", wave.id());
        assertEquals(Emote.Ending.LOOP, wave.ending());
        assertEquals(1f, wave.length(), EPS);
        Channel[] arm = wave.channels(Emote.Bone.RIGHT_ARM);
        assertNotNull(arm);
        // Degrees become radians with the same sign.
        assertEquals(150 * DEG, arm[Emote.Z_ROT].sample(0f, 0f), EPS);
        assertEquals(120 * DEG, arm[Emote.Z_ROT].sample(0.5f, 0f), EPS);
        assertNull(arm[Emote.X]);
        // Bedrock's +Y is up; the model's is down.
        assertEquals(-2f, wave.channels(Emote.Bone.BODY)[Emote.Y].sample(0f, 0f), EPS);
        assertEquals(1, warnings.size(), warnings.toString());
        assertTrue(warnings.get(0).contains("tail"));
    }

    @Test
    void severalAnimationsGiveSeveralEmotes() {
        List<Emote> emotes = parse("""
                {"animations": {
                  "animation.player.nod": {"animation_length": 1, "bones": {"head": {"rotation": {"0": [0,0,0], "0.5": [20,0,0]}}}},
                  "animation.player.still": {"bones": {"head": {"rotation": [0, 30, 0]}}}
                }}
                """);
        assertEquals(2, emotes.size());
        assertEquals("test:emote/nod", emotes.get(0).id());
        assertEquals("test:emote/still", emotes.get(1).id());
        // A still pose has no length: it holds until the player moves.
        assertEquals(Emote.Ending.HOLD, emotes.get(1).ending());
    }

    @Test
    void rejectsBrokenMolangAndBrokenFiles() {
        JsonParseException molang = assertThrows(JsonParseException.class, () -> parse("""
                {"animations": {"a": {"bones": {"head": {"rotation": ["math.sin(query.anim_time * ", 0, 0]}}}}}
                """));
        assertTrue(molang.getMessage().contains("Molang"));
        assertThrows(JsonParseException.class, () -> parse("{\"cubes\": []}"));
        assertThrows(JsonParseException.class, () -> parse("""
                {"animations": {"a": {"animation_length": 99999, "bones": {}}}}
                """));
    }

    @Test
    void molangMovesAsTheEmotePlays() {
        // NoRisk's clap: the arm swings by a formula of the emote's time.
        Emote clap = parse("""
                {"animations": {"a": {"loop": true, "animation_length": 2,
                  "bones": {"bipedRightArm": {"rotation": ["math.sin(query.anim_time * 90) * 10", 0, "-5"]}}}}}
                """).get(0);
        Channel[] arm = clap.channels(Emote.Bone.RIGHT_ARM);
        assertEquals(0f, arm[Emote.X_ROT].sample(0f, 0f), EPS);
        assertEquals(10f * DEG, arm[Emote.X_ROT].sample(1f, 0f), EPS);
        assertEquals(0f, arm[Emote.X_ROT].sample(2f, 0f), 1e-3f);
        assertEquals(-5f * DEG, arm[Emote.Z_ROT].sample(1.5f, 0f), EPS);
        assertEquals(2f, clap.length(), EPS);

        // Molang in keyframes, between plain ones; with no length, as long as its keyframes.
        Emote keyed = parse("""
                {"animations": {"a": {"bones": {"head": {"position": {
                  "0": [0, 0, 0], "1": ["q.anim_time * 2", 0, 0], "2": [0, 0, 0]}}}}}}
                """).get(0);
        Channel x = keyed.channels(Emote.Bone.HEAD)[Emote.X];
        assertEquals(2f, x.sample(1f, 0f), 1e-3f);
        assertEquals(0f, x.sample(2f, 0f), 1e-3f);
        assertEquals(2f, keyed.length(), EPS);

        // Molang that doesn't change is worked out once.
        Emote still = parse("""
                {"animations": {"a": {"bones": {"head": {"rotation": ["math.pi * 0 + 45", "10 * 2", 0]}}}}}
                """).get(0);
        assertEquals(45f * DEG, still.channels(Emote.Bone.HEAD)[Emote.X_ROT].sample(5f, 0f), EPS);
        assertEquals(20f * DEG, still.channels(Emote.Bone.HEAD)[Emote.Y_ROT].sample(5f, 0f), EPS);
    }

    @Test
    void norisksWholeBodyBoneAndItsPivot() {
        Emote spin = parse("""
                {"animations": {"a": {"animation_length": 1, "root_pivot": [0, 12, 0],
                  "bones": {"bipedRig": {"rotation": {"0": [0, 0, 0], "1": [0, 360, 0]}}, "bipedHead": {"rotation": [5, 0, 0]}}}}}
                """).get(0);
        assertNotNull(spin.channels(Emote.Bone.ROOT));
        assertNotNull(spin.channels(Emote.Bone.HEAD));
        // 12 px up from the feet: the hips, as Emotecraft's whole body turns.
        assertEquals(12f, spin.rootPivotY(), EPS);
        assertEquals(24f, parse("{\"animations\": {\"a\": {\"bones\": {}}}}").get(0).rootPivotY(), EPS);
    }

    @Test
    void emotesMakeBodyPartsBiggerOrSmaller() {
        // NoRisk's iron golem: big arms; its headless emote: no head; the whole player a little bigger.
        Emote golem = parse("""
                {"animations": {"a": {"animation_length": 1, "bones": {
                  "bipedRightArm": {"scale": {"0": [1, 1, 1], "1": [2, 3, 2]}},
                  "bipedHead": {"scale": 0},
                  "bipedRig": {"scale": [1.5, 1.5, 1.5]}}}}}
                """).get(0);
        float[][] size = new float[EmotePose.PARTS][3];
        assertTrue(EmotePose.scales(golem, 1f, 1f, size));
        assertEquals(0f, size[0][1], EPS);
        assertEquals(1.5f, size[EmotePose.BODY][0], EPS);
        assertEquals(4.5f, size[2][1], EPS);
        assertEquals(1.5f, size[2][0] / 2f, EPS);
        // Fading in, half way.
        assertTrue(EmotePose.scales(golem, 1f, 0.5f, size));
        assertEquals(2.75f, size[2][1], EPS);
        // No sizes: the game's are left alone.
        assertTrue(!EmotePose.scales(parse("{\"animations\": {\"a\": {\"bones\": {\"head\": {\"rotation\": [5, 0, 0]}}}}}")
                .get(0), 0f, 1f, size));
    }

    @Test
    void readsElytraFlagAndProps() {
        Emote emote = parse("""
                {"animations": {"a": {"elytra": true, "props": ["cosmeticsmod:flying_cloud"],
                  "bones": {"head": {"rotation": [0, 10, 0]}}}}}
                """).get(0);
        assertTrue(emote.elytra());
        assertEquals(List.of("cosmeticsmod:flying_cloud"), emote.props());
        Emote plain = parse("{\"animations\": {\"a\": {\"bones\": {\"head\": {\"rotation\": [0, 10, 0]}}}}}").get(0);
        assertTrue(!plain.elytra());
        assertEquals(List.of(), plain.props());
        // One prop may be given without a list; a prop must be a cosmetic id.
        assertEquals(List.of("a:cloud"), parse("""
                {"animations": {"a": {"props": "a:cloud", "bones": {"head": {"rotation": [0, 10, 0]}}}}}
                """).get(0).props());
        assertThrows(JsonParseException.class, () -> parse("""
                {"animations": {"a": {"props": ["Not An Id"], "bones": {"head": {"rotation": [0, 10, 0]}}}}}
                """));
    }

    @Test
    void readsSound() {
        Emote emote = parse("""
                {"animations": {"a": {"sound": "emote_music:brazilian_dance", "sound_volume": 0.35,
                  "bones": {"head": {"rotation": [0, 10, 0]}}}}}
                """).get(0);
        assertEquals("emote_music:brazilian_dance", emote.sound());
        assertEquals(0.35f, emote.soundVolume(), 1e-6f);
        Emote plain = parse("{\"animations\": {\"a\": {\"bones\": {\"head\": {\"rotation\": [0, 10, 0]}}}}}").get(0);
        assertEquals(null, plain.sound());
        assertEquals(1f, plain.soundVolume(), 0f, "full volume when not given");
        assertEquals(1f, parse("""
                {"animations": {"a": {"sound": "a:song", "bones": {"head": {"rotation": [0, 10, 0]}}}}}
                """).get(0).soundVolume(), 0f);
        assertThrows(JsonParseException.class, () -> parse("""
                {"animations": {"a": {"sound": "Not An Id", "bones": {"head": {"rotation": [0, 10, 0]}}}}}
                """));
        assertThrows(JsonParseException.class, () -> parse("""
                {"animations": {"a": {"sound": "a:song", "sound_volume": 2, "bones": {"head": {"rotation": [0, 10, 0]}}}}}
                """));
    }

    // ---------------------------------------------------------------- Emotecraft files

    @Test
    void readsEmotecraftEmotes() {
        Emote emote = parse("""
                {
                  "name": "Clap",
                  "author": {"text": "Someone"},
                  "emote": {
                    "beginTick": 0, "endTick": 20, "stopTick": 30, "isLoop": "true", "returnTick": 10,
                    "degrees": false,
                    "moves": [
                      {"tick": 10, "easing": "INOUTSINE", "rightArm": {"pitch": -1.5, "bend": 0.4}},
                      {"tick": 20, "turn": 1, "body": {"yaw": 0}},
                      {"tick": 20, "easing": "linear", "torso": {"y": 1}}
                    ]
                  }
                }
                """).get(0);
        assertEquals("Clap", emote.name());
        assertEquals("Someone", emote.author());
        assertEquals(1f, emote.length(), EPS);
        assertEquals(Emote.Ending.LOOP, emote.ending());
        assertEquals(0.5f, emote.loopStart(), EPS);
        assertEquals(0.5f, emote.fadeOut(), EPS);
        Channel pitch = emote.channels(Emote.Bone.RIGHT_ARM)[Emote.X_ROT];
        // Leads in from the player's own pose, eased.
        assertEquals(0.2f, pitch.sample(0f, 0.2f), EPS);
        assertEquals(-1.5f, pitch.sample(0.5f, 0.2f), EPS);
        // "turn" adds whole turns; Emotecraft's "body" is the whole player.
        assertEquals((float) (2 * Math.PI), emote.channels(Emote.Bone.ROOT)[Emote.Y_ROT].sample(1f, 0f), EPS);
        assertEquals(1f, emote.channels(Emote.Bone.BODY)[Emote.Y].sample(1f, 0f), EPS);
    }

    // ---------------------------------------------------------------- posing

    private static Emote pose(Map<Emote.Bone, Channel[]> bones) {
        return new Emote("a:b", null, null, 1f, Emote.Ending.HOLD, 0f, 0f, 0f, 24f, bones);
    }

    private static Channel[] rotation(float x, float y, float z) {
        Channel[] channels = new Channel[Emote.CHANNELS];
        channels[Emote.X_ROT] = Channel.constant(x);
        channels[Emote.Y_ROT] = Channel.constant(y);
        channels[Emote.Z_ROT] = Channel.constant(z);
        return channels;
    }

    @Test
    void untouchedPartsKeepTheGamesPose() {
        float[][] parts = restPose();
        parts[EmotePose.HEAD][Emote.Y_ROT] = 0.7f;
        parts[EmotePose.LEFT_LEG][Emote.X_ROT] = 0.3f;
        EmotePose.apply(pose(Map.of(Emote.Bone.RIGHT_ARM, rotation(0, 0, 2.5f))), 0f, 1f, parts, rest());
        assertEquals(0.7f, parts[EmotePose.HEAD][Emote.Y_ROT], EPS);
        assertEquals(0.3f, parts[EmotePose.LEFT_LEG][Emote.X_ROT], EPS);
        assertEquals(2.5f, parts[EmotePose.RIGHT_ARM][Emote.Z_ROT], EPS);
        assertArrayEquals(new float[]{-5, 2, 0}, java.util.Arrays.copyOf(parts[EmotePose.RIGHT_ARM], 3), EPS);
    }

    @Test
    void weightBlendsWithTheGamesPose() {
        float[][] parts = restPose();
        parts[EmotePose.RIGHT_ARM][Emote.X_ROT] = 1f;
        EmotePose.apply(pose(Map.of(Emote.Bone.RIGHT_ARM, rotation(-1f, 0, 0))), 0f, 0.25f, parts, rest());
        assertEquals(0.5f, parts[EmotePose.RIGHT_ARM][Emote.X_ROT], EPS);
    }

    @Test
    void turningTheBodyCarriesTheArmsAndHead() {
        float[][] parts = restPose();
        float quarter = (float) Math.PI / 2;
        EmotePose.apply(pose(Map.of(Emote.Bone.BODY, rotation(0, quarter, 0))), 0f, 1f, parts, rest());
        // Body turned a quarter left/right around the neck: the right arm swings from x=-5 to the front or back.
        float[] arm = parts[EmotePose.RIGHT_ARM];
        assertEquals(0f, arm[0], 1e-3f);
        assertEquals(2f, arm[1], 1e-3f);
        assertEquals(5f, Math.abs(arm[2]), 1e-3f);
        assertEquals(quarter, arm[Emote.Y_ROT], 1e-3f);
        assertEquals(quarter, parts[EmotePose.HEAD][Emote.Y_ROT], 1e-3f);
        // Legs don't hang off the body.
        assertEquals(0f, parts[EmotePose.LEFT_LEG][Emote.Y_ROT], EPS);
        assertEquals(1.9f, parts[EmotePose.LEFT_LEG][0], EPS);
    }

    @Test
    void movingTheRootMovesEverything() {
        float[][] parts = restPose();
        Channel[] root = new Channel[Emote.CHANNELS];
        root[Emote.Y] = Channel.constant(-4f);
        EmotePose.apply(pose(Map.of(Emote.Bone.ROOT, root)), 0f, 1f, parts, rest());
        float[][] rest = rest();
        for (int p = 0; p < EmotePose.PARTS; p++) {
            assertEquals(rest[p][1] - 4f, parts[p][1], 1e-3f);
            assertEquals(rest[p][0], parts[p][0], 1e-3f);
        }
    }

    @Test
    void spinningTheRootTurnsAroundTheFeet() {
        float[][] parts = restPose();
        EmotePose.apply(pose(Map.of(Emote.Bone.ROOT, rotation(0, (float) Math.PI, 0))), 0f, 1f, parts, rest());
        // Half a turn around the vertical axis through the feet: left and right swap, heights stay.
        assertEquals(5f, parts[EmotePose.RIGHT_ARM][0], 1e-3f);
        assertEquals(2f, parts[EmotePose.RIGHT_ARM][1], 1e-3f);
        assertEquals(-1.9f, parts[EmotePose.LEFT_LEG][0], 1e-3f);
    }

    @Test
    void decomposeUndoesMatrix() {
        float[][] poses = {{1, 2, 3, 0.3f, -0.4f, 1.1f}, {0, 0, 0, -2.5f, 1.2f, 0.1f}, {5, -1, 0, 0.1f, 0.2f, -3f}};
        for (float[] pose : poses) {
            float[] out = new float[6];
            EmotePose.decompose(EmotePose.matrix(pose), out);
            float[] again = EmotePose.matrix(out);
            float[] expected = EmotePose.matrix(pose);
            assertArrayEquals(expected, again, 1e-4f);
        }
    }
}
