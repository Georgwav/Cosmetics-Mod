package io.github.georgwav.cosmetics.emote;

import java.util.List;
import java.util.Map;

/**
 * An emote: how the player's body parts move over time. Each bone has up to six channels (see {@link #X} ...
 * {@link #Z_ROT}); a channel left out keeps the game's own pose for that number, so an emote that only moves
 * the right arm still lets the head look around.
 *
 * <p>Units are the player model's: offsets in pixels added to the part's rest position (+Y down, -Z the front,
 * +X the wearer's left), rotations in radians like the model's own. Bones form the Bedrock player's hierarchy:
 * {@code root} carries everything, {@code waist} the upper body, and {@code body} the head and arms.
 *
 * @param id         namespace:path
 * @param name       display name from the file, or null to use the translation or the id
 * @param author     from the file, or null
 * @param length     seconds of the emote proper
 * @param ending     what happens after {@code length}
 * @param loopStart  where a looping emote starts again, in seconds
 * @param fadeIn     seconds to blend in from the player's own pose
 * @param fadeOut    seconds to blend back out once it ends or is stopped
 * @param rootPivotY where {@code root} rotations turn around, in model pixels (24 = the feet, 12 = the hips)
 * @param bones      channels per bone: an array of six, null where the emote doesn't move that number
 * @param elytra     made for flying with an elytra: on the elytra wheel unless the player picks its emotes
 * @param props      cosmetic ids shown on the player while the emote plays (a cloud to ride)
 */
public record Emote(String id, String name, String author, float length, Ending ending, float loopStart,
                    float fadeIn, float fadeOut, float rootPivotY, Map<Bone, Channel[]> bones, boolean elytra,
                    List<String> props) {

    public Emote(String id, String name, String author, float length, Ending ending, float loopStart,
                 float fadeIn, float fadeOut, float rootPivotY, Map<Bone, Channel[]> bones) {
        this(id, name, author, length, ending, loopStart, fadeIn, fadeOut, rootPivotY, bones, false, List.of());
    }

    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;
    public static final int X_ROT = 3;
    public static final int Y_ROT = 4;
    public static final int Z_ROT = 5;
    /** The channels a part's pose has (offset and rotation). */
    public static final int CHANNELS = 6;
    /** A part's size, 1 = as the game has it; only Blockbench emotes have these (after the pose channels). */
    public static final int X_SCALE = 6;
    public static final int Y_SCALE = 7;
    public static final int Z_SCALE = 8;
    /** Every channel a part may have: pose, then size. */
    public static final int ALL_CHANNELS = 9;

    /** Waist rotations turn around the hips. */
    public static final float WAIST_PIVOT_Y = 12f;

    public enum Ending {
        /** Plays once, then blends back to the player's own pose. */
        STOP,
        /** Starts again from {@code loopStart} until stopped. */
        LOOP,
        /** Stays on its last frame until stopped. */
        HOLD
    }

    public enum Bone {
        ROOT, WAIST, BODY, HEAD, RIGHT_ARM, LEFT_ARM, RIGHT_LEG, LEFT_LEG
    }

    /** Where in the emote's timeline it is {@code elapsed} seconds after it started. */
    public float timeAt(float elapsed) {
        if (elapsed <= length) {
            return Math.max(0f, elapsed);
        }
        if (ending == Ending.LOOP && length - loopStart > 1e-3f) {
            return loopStart + (elapsed - length) % (length - loopStart);
        }
        return length;
    }

    /** How much of the emote shows {@code elapsed} seconds after it started (0..1), for the blend in and out. */
    public float weightAt(float elapsed) {
        float weight = fadeIn > 0f ? Math.min(1f, Math.max(0f, elapsed) / fadeIn) : 1f;
        if (ending == Ending.STOP && elapsed > length) {
            weight = Math.min(weight, fadeOut > 0f ? Math.max(0f, 1f - (elapsed - length) / fadeOut) : 0f);
        }
        return weight;
    }

    /** Whether it has played out and blended back ({@link Ending#STOP} only; the others play until stopped). */
    public boolean finished(float elapsed) {
        return ending == Ending.STOP && elapsed >= length + fadeOut;
    }

    /** The channels of {@code bone}, or null when the emote doesn't move it. */
    public Channel[] channels(Bone bone) {
        return bones.get(bone);
    }
}
