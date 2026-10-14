package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Draws your player upright to the ground while an elytra emote plays, instead of lying along the flight.
 *
 * <p>The player renderer turns a gliding player in {@code setupRotations}: first towards where the body faces
 * (as for everyone), then down along the flight and around the flight direction. {@code PlayerRendererMixin}
 * calls {@link #begin} before and {@link #end} after it; {@link #end} keeps the turn towards the body's facing
 * and takes the flight part back out, blended by the emote's weight so it eases in and out. Everything drawn on
 * the player (its elytra, cape, armour, held items, cosmetics) follows, since it is all drawn in that frame.
 *
 * <p>Render thread only.
 */
public final class ElytraUpright {
    private static final Matrix4f POSE_BEFORE = new Matrix4f();
    private static final Matrix3f NORMAL_BEFORE = new Matrix3f();
    private static float weight;
    /** Frames a player was drawn upright (read by the in-game test). */
    public static long frames;

    /**
     * The elytra's wings as when standing (vanilla's resting pose, the left wing; the right one mirrors it), radians:
     * during an elytra emote the elytra is drawn folded like this instead of spread for flight, so it doesn't
     * stretch away from your upright back.
     */
    public static final float FOLDED_X = 0.2617994f;
    public static final float FOLDED_Z = -0.2617994f;

    private ElytraUpright() {
    }

    /** How far to fold the elytra of the player {@code entityId} (0: as the game has it, 1: folded). */
    public static float foldWeight(int entityId) {
        return EmotePlayer.upright(entityId);
    }

    /** Before the renderer turns the player {@code entityId}. */
    public static void begin(int entityId, PoseStack poseStack) {
        weight = EmotePlayer.upright(entityId);
        if (weight > 0f) {
            PoseStack.Pose last = poseStack.last();
            POSE_BEFORE.set(last.pose());
            NORMAL_BEFORE.set(last.normal());
        }
    }

    /** After it, with the body yaw the renderer turned to (degrees, the game's). */
    public static void end(PoseStack poseStack, float bodyYaw) {
        if (weight <= 0f) {
            return;
        }
        float w = weight;
        weight = 0f;
        float yaw = (float) Math.toRadians(180f - bodyYaw);
        PoseStack.Pose last = poseStack.last();
        // The frame turned towards the body only, and the flight turn the renderer added on top of it.
        Matrix4f facing = new Matrix4f(POSE_BEFORE).rotateY(yaw);
        Matrix4f flight = new Matrix4f(facing).invert().mul(last.pose());
        Quaternionf turn = flight.getNormalizedRotation(new Quaternionf());
        // w = 0 keeps the whole flight turn, w = 1 takes it all out.
        Quaternionf kept = new Quaternionf(turn).slerp(new Quaternionf(), w);
        last.pose().set(facing).rotate(kept);
        last.normal().set(NORMAL_BEFORE).rotateY(yaw).rotate(kept);
        frames++;
    }
}
