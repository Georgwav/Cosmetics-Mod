package io.github.georgwav.cosmetics.emote;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
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
 *
 * <p>An elytra emote (played from the wheel while gliding) is different: it plays until you stop gliding,
 * moving doesn't stop it, the camera stays where it is, and you are drawn upright to the ground rather than
 * lying along your flight ({@link #upright}, used by {@code PlayerRendererMixin}).
 */
public final class EmotePlayer {
    /** An emote stopped early blends back over at least this long. */
    private static final float MIN_STOP_FADE = 0.15f;
    /** Moving in the first moments of an emote doesn't stop it (you may still be sliding from a step). */
    private static final float GRACE_SECONDS = 0.3f;
    /** Clicks in the first moment of an emote don't stop it (the click that picked it on the wheel). */
    private static final float ACTION_GRACE_SECONDS = 0.15f;

    private static Emote current;
    /** Played while gliding with an elytra. */
    private static boolean elytra;
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
    private static final float[][] SIZE = new float[EmotePose.PARTS][3];
    /** Models whose parts an emote sized this frame (to size back next time). */
    private static final java.util.Set<HumanoidModel<?>> SIZED =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private EmotePlayer() {
    }

    /** Plays {@code emote} on your player, in place of any playing now. */
    public static void play(Emote emote) {
        play(emote, false);
    }

    /** Plays {@code emote}; {@code whileGliding} makes it an elytra emote (see the class description). */
    public static void play(Emote emote, boolean whileGliding) {
        Minecraft client = Minecraft.getInstance();
        if (emote == null || client.player == null || !CosmeticsConfig.get().emotes) {
            return;
        }
        current = emote;
        elytra = whileGliding;
        startNanos = System.nanoTime();
        stopNanos = 0L;
        EquippedCosmetics.emoteProps(emote.props());
        CosmeticsConfig.EmoteCamera camera = CosmeticsConfig.get().emoteCamera;
        // Gliding, you need to see where you're going.
        if (camera != CosmeticsConfig.EmoteCamera.UNCHANGED && !elytra) {
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
        if (stopNanos == 0L && (acted(client, player) || (elytra ? !player.isFallFlying()
                : CosmeticsConfig.get().emoteStopOnMove && interrupted(player)))) {
            stop();
        }
    }

    /**
     * Doing something stops an emote: attacking (or punching), using an item (eating, blocking) or sneaking. During
     * an elytra emote using an item doesn't, when "Rockets keep elytra emotes" is on (the default): you fly on with
     * rockets while it plays.
     */
    private static boolean acted(Minecraft client, LocalPlayer player) {
        if (seconds(startNanos) < ACTION_GRACE_SECONDS) {
            return false;
        }
        var options = client.options;
        boolean using = options.keyUse.isDown() || player.isUsingItem();
        if (using && elytra && CosmeticsConfig.get().elytraEmoteRockets) {
            using = false;
        }
        return options.keyAttack.isDown() || options.keyShift.isDown() || using;
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
        elytra = false;
        stopNanos = 0L;
        EquippedCosmetics.emoteProps(java.util.List.of());
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
        // Sizes an emote gave the parts last time go back first: the game keeps them otherwise (before 1.21.2),
        // and one model draws every player of its kind.
        if (SIZED.remove(model)) {
            for (ModelPart part : new ModelPart[]{model.head, model.body, model.rightArm, model.leftArm, model.rightLeg,
                    model.leftLeg}) {
                part.xScale = 1f;
                part.yScale = 1f;
                part.zScale = 1f;
            }
        }
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
        float time = emote.timeAt(elapsed);
        EmotePose.apply(emote, time, weight, POSE, REST);
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
        // Body parts an emote makes bigger or smaller (Blockbench "scale").
        if (EmotePose.scales(emote, time, weight, SIZE)) {
            for (int p = 0; p < EmotePose.PARTS; p++) {
                parts[p].xScale = SIZE[p][0];
                parts[p].yScale = SIZE[p][1];
                parts[p].zScale = SIZE[p][2];
            }
            SIZED.add(model);
        }
        posedFrames++;
        return true;
    }

    /**
     * How far your player {@code entityId} is drawn upright to the ground while gliding, 0 (lying along the flight,
     * as normal) to 1 (upright): the weight of the elytra emote playing, 0 when there is none.
     */
    public static float upright(int entityId) {
        Emote emote = current;
        if (emote == null || !elytra) {
            return 0f;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.getId() != entityId) {
            return 0f;
        }
        // An emote that played out blends back down with its own fade; while it holds or loops, stay upright.
        return Math.max(0f, Math.min(1f, emote.weightAt(seconds(startNanos)) * stopWeight()));
    }

    /** Whether your player {@code entityId} is posed by an emote now ({@link #pose} changes its model). */
    public static boolean posing(int entityId) {
        Emote emote = current;
        if (emote == null) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.getId() == entityId
                && emote.weightAt(seconds(startNanos)) * stopWeight() > 0f;
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
