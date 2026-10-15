package io.github.georgwav.cosmetics.client.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Locale;

/**
 * Camera: freelook (Left Alt, held or as a toggle) turns the camera round you without turning you, in third person;
 * snaplook (its own key) shows you from the front while held; the third-person camera's distance, and whether it
 * stops at walls; and whether the world bobs as you walk (your hand still does).
 */
public final class CameraTweaks extends Module {
    private final Setting.Choice freelookMode = add(new Setting.Choice("freelook_mode", "hold", List.of("hold", "toggle")));
    private final Setting.Toggle freelookThirdPerson = add(new Setting.Toggle("freelook_third_person", true));
    private final Setting.Slider distance = add(new Setting.Slider("distance", 4.0, 1.0, 16.0, 0.5,
            v -> String.format(Locale.ROOT, "%.1f", v)));
    private final Setting.Toggle clip = add(new Setting.Toggle("clip", true));
    /** Third person over a shoulder: the camera this far to the right (left when negative), in blocks. */
    private final Setting.Slider shoulder = add(new Setting.Slider("shoulder", 0.0, -1.5, 1.5, 0.05,
            v -> v == 0 ? "\u2014" : String.format(java.util.Locale.ROOT, "%+.2f", v)));
    private final Setting.Toggle worldBob = add(new Setting.Toggle("world_bob", true));

    private boolean freelooking, snapping;
    private float yaw, pitch;
    private CameraType typeBefore;

    public CameraTweaks() {
        super("camera", Category.VIEW, "eye");
        addKey("freelook", InputConstants.KEY_LALT);
        addKey("snaplook");
    }

    @Override
    protected void tick(Minecraft mc) {
        var free = key("freelook");
        var snap = key("snaplook");
        boolean wantFree = freelooking;
        if (!io.github.georgwav.cosmetics.sol.compat.Overlaps.solFreelooks()) {
            // Freelook does it (the Mods page): Sol's stays still.
            free = null;
            wantFree = false;
        }
        if (free != null) {
            if (freelookMode.get().equals("toggle")) {
                while (free.consumeClick()) {
                    wantFree = !wantFree;
                }
            } else {
                while (free.consumeClick()) {
                    // Held, read below.
                }
                wantFree = free.isDown();
            }
        }
        boolean wantSnap = snap != null && snap.isDown();
        while (snap != null && snap.consumeClick()) {
            // Held, read above.
        }
        if (mc.player == null) {
            wantFree = wantSnap = false;
        }
        if (wantFree != freelooking) {
            freelooking = wantFree;
            if (freelooking) {
                yaw = mc.player.getYRot();
                pitch = mc.player.getXRot();
                if (freelookThirdPerson.get() && mc.options.getCameraType().isFirstPerson()) {
                    typeBefore = mc.options.getCameraType();
                    mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                }
            } else {
                restore(mc);
            }
        }
        if (wantSnap != snapping) {
            snapping = wantSnap;
            if (snapping) {
                if (typeBefore == null) {
                    typeBefore = mc.options.getCameraType();
                }
                mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            } else {
                restore(mc);
            }
        }
    }

    private void restore(Minecraft mc) {
        if (typeBefore != null && !freelooking && !snapping) {
            mc.options.setCameraType(typeBefore);
            typeBefore = null;
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        freelooking = snapping = false;
        restore(mc);
    }

    /** Whether the camera turns on its own now. */
    /** The shoulder offset now (0 with the module off). */
    public static float shoulder() {
        CameraTweaks c = io.github.georgwav.cosmetics.client.Modules.CAMERA;
        return c.enabled() ? c.shoulder.get().floatValue() : 0f;
    }

    public static boolean freelooking() {
        CameraTweaks c = Modules.CAMERA;
        return c.enabled() && c.freelooking;
    }

    public static float yaw() {
        return Modules.CAMERA.yaw;
    }

    public static float pitch() {
        return Modules.CAMERA.pitch;
    }

    /** The mouse while freelooking: it turns the camera instead of you (the game's own 0.15 per unit). */
    public static void turn(double dYaw, double dPitch) {
        CameraTweaks c = Modules.CAMERA;
        c.yaw += (float) dYaw * 0.15f;
        c.pitch = Mth.clamp(c.pitch + (float) dPitch * 0.15f, -90f, 90f);
    }

    /** The third-person camera's distance for the game's {@code base} (4 blocks at normal size). */
    public static float distance(float base) {
        CameraTweaks c = Modules.CAMERA;
        return c.enabled() ? base * (float) (c.distance.get() / 4.0) : base;
    }

    /** Whether the third-person camera goes through walls instead of stopping at them. */
    public static boolean noClip() {
        CameraTweaks c = Modules.CAMERA;
        return c.enabled() && !c.clip.get();
    }

    /** Whether the world bobs as you walk (the game's View Bobbing still decides for the hand). */
    public static boolean worldBobs() {
        CameraTweaks c = Modules.CAMERA;
        return !c.enabled() || c.worldBob.get();
    }
}
