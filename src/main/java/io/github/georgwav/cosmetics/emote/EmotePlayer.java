package io.github.georgwav.cosmetics.emote;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.LocalPlayer;

/**
 * Plays emotes on your own player. Only you see them: like the rest of the mod, nothing is sent to the server.
 *
 * <p>Right after the game poses a player model, {@code PlayerModelMixin} calls {@link #pose}, which moves the
 * parts of your model as the emote says. While an emote plays the camera can move in front of or behind you,
 * and moving stops it (both can be set in Display). Render and client thread only.
 */
public final class EmotePlayer {
    /** An emote stopped early blends back over at least this long. */
    private static final float MIN_STOP_FADE = 0.15f;
    /** Moving in the first moments of an emote doesn't stop it (you may still be sliding from a step). */
    private static final float GRACE_SECONDS = 0.3f;

    private static Emote current;
    private static long startNanos;
    /** When it was asked to stop; 0 while it plays on. */
    private static long stopNanos;
    /** The camera from before the emote moved it, and where it moved it; null when it didn't. */
    private static CameraType cameraBefore;
    private static CameraType cameraSet;
    /** Frames your model was posed for an emote (read by the in-game test). */
    public static long posedFrames;

    private static final float[][] POSE = new float[EmotePose.PARTS][6];
    private static final float[][] REST = new float[EmotePose.PARTS][3];

    private EmotePlayer() {
    }

    /** Plays {@code emote} on your player, in place of any playing now. */
    public static void play(Emote emote) {
        Minecraft client = Minecraft.getInstance();
        if (emote == null || client.player == null || !CosmeticsConfig.get().emotes) {
            return;
        }
        current = emote;
        startNanos = System.nanoTime();
        stopNanos = 0L;
        CosmeticsConfig.EmoteCamera camera = CosmeticsConfig.get().emoteCamera;
        if (camera != CosmeticsConfig.EmoteCamera.UNCHANGED) {
            CameraType wanted = camera == CosmeticsConfig.EmoteCamera.FRONT ? CameraType.THIRD_PERSON_FRONT
                    : CameraType.THIRD_PERSON_BACK;
            CameraType now = client.options.getCameraType();
            if (now != wanted) {
                if (cameraBefore == null) {
                    cameraBefore = now;
                }
                client.options.setCameraType(wanted);
                cameraSet = wanted;
            }
        }
    }

    /** Stops the emote, blending back to the normal pose. */
    public static void stop() {
        if (current != null && stopNanos == 0L) {
            stopNanos = System.nanoTime();
        }
    }

    /** The emote playing now (and not stopping), or null. */
    public static Emote playing() {
        return stopNanos == 0L ? current : null;
    }

    /** Called at the end of every client tick: stops the emote when it is over or you move. */
    public static void tick(Minecraft client) {
        if (current == null) {
            return;
        }
        LocalPlayer player = client.player;
        if (player == null || client.level == null || !CosmeticsConfig.get().emotes) {
            end(client);
            return;
        }
        if (current.finished(seconds(startNanos)) || stopWeight() <= 0f) {
            end(client);
            return;
        }
        if (stopNanos == 0L && CosmeticsConfig.get().emoteStopOnMove && interrupted(player)) {
            stop();
        }
    }

    private static boolean interrupted(LocalPlayer player) {
        if (seconds(startNanos) < GRACE_SECONDS) {
            return false;
        }
        double dx = player.getX() - player.xo;
        double dy = player.getY() - player.yo;
        double dz = player.getZ() - player.zo;
        return dx * dx + dz * dz > 1.0e-4 || Math.abs(dy) > 0.01 || player.isCrouching() || player.hurtTime > 0
                || player.isPassenger() || player.isSwimming() || player.isFallFlying() || player.isSleeping();
    }

    private static void end(Minecraft client) {
        current = null;
        stopNanos = 0L;
        if (cameraBefore != null) {
            // Put the camera back, unless you've moved it yourself since.
            if (client.options.getCameraType() == cameraSet) {
                client.options.setCameraType(cameraBefore);
            }
            cameraBefore = null;
            cameraSet = null;
        }
    }

    /**
     * Poses {@code model} for the playing emote if it is your player's ({@code entityId}); called right after the
     * game posed it. Returns whether it changed anything.
     */
    public static boolean pose(int entityId, HumanoidModel<?> model) {
        Emote emote = current;
        if (emote == null) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getId() != entityId) {
            return false;
        }
        float elapsed = seconds(startNanos);
        float weight = emote.weightAt(elapsed) * stopWeight();
        if (weight <= 0f) {
            return false;
        }
        ModelPart[] parts = {model.head, model.body, model.rightArm, model.leftArm, model.rightLeg, model.leftLeg};
        for (int p = 0; p < EmotePose.PARTS; p++) {
            ModelPart part = parts[p];
            float[] pose = POSE[p];
            pose[0] = part.x;
            pose[1] = part.y;
            pose[2] = part.z;
            pose[3] = part.xRot;
            pose[4] = part.yRot;
            pose[5] = part.zRot;
            PartPose rest = part.getInitialPose();
            //? if >=1.21.2 {
            REST[p][0] = rest.x();
            REST[p][1] = rest.y();
            REST[p][2] = rest.z();
            //?} else {
            /*REST[p][0] = rest.x;
            REST[p][1] = rest.y;
            REST[p][2] = rest.z;
            *///?}
        }
        EmotePose.apply(emote, emote.timeAt(elapsed), weight, POSE, REST);
        for (int p = 0; p < EmotePose.PARTS; p++) {
            ModelPart part = parts[p];
            float[] pose = POSE[p];
            part.x = pose[0];
            part.y = pose[1];
            part.z = pose[2];
            part.xRot = pose[3];
            part.yRot = pose[4];
            part.zRot = pose[5];
        }
        posedFrames++;
        return true;
    }

    /** How much is left of an emote that was stopped early (1 while it plays on). */
    private static float stopWeight() {
        if (stopNanos == 0L || current == null) {
            return 1f;
        }
        float fade = Math.max(MIN_STOP_FADE, current.fadeOut());
        return Math.max(0f, 1f - seconds(stopNanos) / fade);
    }

    private static float seconds(long since) {
        return (System.nanoTime() - since) / 1.0e9f;
    }
}
