package io.github.georgwav.cosmetics.client.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.render.CameraGlide;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Camera: freelook (Left Alt, held or as a toggle) turns the camera round you without turning you, in third person;
 * freecam (its own key; in singleplayer, or where you may use operator commands) flies the camera away from you with
 * the movement keys while you stand still. F5 glide: switching to third person glides the camera out from your eyes,
 * switching back glides it in, and leaving the freecam glides it home, each over a quarter of a second.
 */
public final class CameraTweaks extends Module {
    private final Setting.Choice freelookMode = add(new Setting.Choice("freelook_mode", "hold", List.of("hold", "toggle")).group("freelook"));
    private final Setting.Toggle freelookThirdPerson = add(new Setting.Toggle("freelook_third_person", true).group("freelook"));
    private final Setting.Toggle glide = add(new Setting.Toggle("glide", true).group("glide"));

    private static final int OUT = 1, IN = 2, HOME = 3;
    /** The glide under way (0 for none), when it started, and where from (IN: off your eyes; HOME: the freecam's place). */
    private int glideKind;
    private float glideStart;
    private double gx, gy, gz;
    /** The camera type and the camera's offset from your eyes the frame before (render thread). */
    private CameraType seenType;
    private double ox, oy, oz;
    /** For the in-game test: glides started. */
    public static int glides;

    private boolean freelooking, freecam;
    private float yaw, pitch;
    private CameraType typeBefore;
    /** The freecam's place this tick and last tick (the camera moves between them over the frames). */
    private double fx, fy, fz, lastX, lastY, lastZ;
    /** The movement keys as the player pressed them, held back from the player while the freecam flies. */
    private final boolean[] held = new boolean[8];

    public CameraTweaks() {
        super("camera", Category.VIEW, "eye");
        addKey("freelook", InputConstants.KEY_LALT);
        addKey("freecam");
    }

    private static net.minecraft.client.KeyMapping[] flyKeys(Minecraft mc) {
        var o = mc.options;
        return new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift,
                o.keySprint, o.keyAttack};
    }

    /**
     * Before the game's tick: while the freecam flies, the movement keys move it, and the player sees them released
     * (they're given back after the tick, see {@link #tick}); clicks don't reach the world.
     */
    public static void beforeTick(Minecraft mc) {
        CameraTweaks c = Modules.CAMERA;
        if (!c.freecam) {
            return;
        }
        var keys = flyKeys(mc);
        for (int i = 0; i < keys.length; i++) {
            c.held[i] = keys[i].isDown();
            keys[i].setDown(false);
        }
        while (mc.options.keyAttack.consumeClick() || mc.options.keyUse.consumeClick()) {
            // Dropped: the player stays where they are.
        }
        c.lastX = c.fx;
        c.lastY = c.fy;
        c.lastZ = c.fz;
        float forward = (c.held[0] ? 1f : 0f) - (c.held[1] ? 1f : 0f);
        float strafe = (c.held[2] ? 1f : 0f) - (c.held[3] ? 1f : 0f);
        float up = (c.held[4] ? 1f : 0f) - (c.held[5] ? 1f : 0f);
        double speed = c.held[6] ? 1.2 : 0.5;
        double rad = Math.toRadians(c.yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        c.fx += (-sin * forward + cos * strafe) * speed;
        c.fz += (cos * forward + sin * strafe) * speed;
        c.fy += up * speed;
    }

    /** Whether this player may fly the freecam: in singleplayer, or with operator commands (game master). */
    /** Whether you may use operator commands here (or play singleplayer), as the freecam needs. */
    public static boolean mayFreecam(Minecraft mc) {
        if (mc.player == null) {
            return false;
        }
        if (mc.hasSingleplayerServer()) {
            return true;
        }
        //? if >=1.21.11 {
        return mc.player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        //?} else {
        /*return mc.player.hasPermissions(2);
        *///?}
    }

    /** Starts or stops the freecam; true when it's flying after. */
    public static boolean setFreecam(Minecraft mc, boolean on) {
        CameraTweaks c = Modules.CAMERA;
        if (on == c.freecam) {
            return on;
        }
        if (on) {
            if (!c.enabled() || !mayFreecam(mc)) {
                return false;
            }
            var eye = mc.player.getEyePosition(1f);
            c.fx = c.lastX = eye.x;
            c.fy = c.lastY = eye.y;
            c.fz = c.lastZ = eye.z;
            c.yaw = mc.player.getYRot();
            c.pitch = mc.player.getXRot();
            c.freecam = true;
            if (c.typeBefore == null) {
                c.typeBefore = mc.options.getCameraType();
            }
            // The camera away from you shows you (and not your hand).
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else {
            c.freecam = false;
            c.giveKeysBack(mc);
            c.restore(mc);
            if (c.glides()) {
                // Home from where the freecam was.
                c.startGlide(HOME, Mth.lerp(io.github.georgwav.cosmetics.sol.render.FrameInfo.partialTick, c.lastX, c.fx),
                        Mth.lerp(io.github.georgwav.cosmetics.sol.render.FrameInfo.partialTick, c.lastY, c.fy),
                        Mth.lerp(io.github.georgwav.cosmetics.sol.render.FrameInfo.partialTick, c.lastZ, c.fz));
            }
        }
        return c.freecam;
    }

    private void giveKeysBack(Minecraft mc) {
        var keys = flyKeys(mc);
        for (int i = 0; i < keys.length; i++) {
            if (held[i]) {
                keys[i].setDown(true);
                held[i] = false;
            }
        }
    }

    /** Whether the freecam flies now. */
    public static boolean freecam() {
        CameraTweaks c = Modules.CAMERA;
        return c.enabled() && c.freecam;
    }

    /** The freecam's place between its last tick and this one. */
    public static net.minecraft.world.phys.Vec3 freecamPosition(float partialTick) {
        CameraTweaks c = Modules.CAMERA;
        return new net.minecraft.world.phys.Vec3(Mth.lerp(partialTick, c.lastX, c.fx), Mth.lerp(partialTick, c.lastY, c.fy),
                Mth.lerp(partialTick, c.lastZ, c.fz));
    }

    @Override
    protected void tick(Minecraft mc) {
        if (freecam) {
            // The keys back as the player holds them, for the next tick's reading.
            var keys = flyKeys(mc);
            for (int i = 0; i < keys.length; i++) {
                keys[i].setDown(held[i]);
            }
            if (mc.player == null || !mayFreecam(mc)) {
                setFreecam(mc, false);
            }
        }
        var fc = key("freecam");
        while (fc != null && fc.consumeClick()) {
            setFreecam(mc, !freecam);
        }
        var free = key("freelook");
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
        if (mc.player == null) {
            wantFree = false;
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
    }

    private void restore(Minecraft mc) {
        if (typeBefore != null && !freelooking && !freecam) {
            mc.options.setCameraType(typeBefore);
            typeBefore = null;
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        freelooking = false;
        if (freecam) {
            freecam = false;
            giveKeysBack(mc);
        }
        restore(mc);
    }

    private boolean glides() {
        return enabled() && glide.get() && !io.github.georgwav.cosmetics.sol.fx.FxWorld.calm();
    }

    private void startGlide(int kind, double x, double y, double z) {
        glideKind = kind;
        glideStart = io.github.georgwav.cosmetics.sol.render.FrameInfo.time;
        gx = x;
        gy = y;
        gz = z;
        glides++;
    }

    /**
     * The glide, as the camera is set up (render thread): given your eyes and the camera's place as the game (and
     * freelook) set it, writes the camera's place into {@code out} and returns true while a glide moves it. Out to third
     * person it starts just behind your head and slows into place; back to first person it speeds in from where the
     * camera was; home from the freecam it travels from the freecam's place. While it glides into first person the
     * camera stays detached (you're drawn, your hands aren't: {@link #handsHidden}).
     */
    public static boolean glide(double ex, double ey, double ez, double px, double py, double pz, double[] out) {
        CameraTweaks c = Modules.CAMERA;
        CameraType type = Minecraft.getInstance().options.getCameraType();
        CameraType before = c.seenType;
        c.seenType = type;
        double lastX = c.ox, lastY = c.oy, lastZ = c.oz;
        c.ox = px - ex;
        c.oy = py - ey;
        c.oz = pz - ez;
        if (!c.glides()) {
            c.glideKind = 0;
            return false;
        }
        if (before != null && before != type && c.glideKind != HOME && !c.freecam) {
            if (before.isFirstPerson() && !type.isFirstPerson()) {
                c.startGlide(OUT, 0, 0, 0);
            } else if (!before.isFirstPerson() && type.isFirstPerson()) {
                c.startGlide(IN, lastX, lastY, lastZ);
            }
        }
        if (c.glideKind == 0) {
            return false;
        }
        float k = (io.github.georgwav.cosmetics.sol.render.FrameInfo.time - c.glideStart) / CameraGlide.SECONDS;
        // Done, or the switch went back the other way part-way (that glide starts above).
        if (k >= 1f || k < 0f || c.glideKind == OUT && type.isFirstPerson() || c.glideKind == IN && !type.isFirstPerson()) {
            c.glideKind = 0;
            return false;
        }
        // The glide's offset from your eyes, then the place.
        if (c.glideKind == OUT) {
            if (!CameraGlide.out(k, c.ox, c.oy, c.oz, out)) {
                return false;
            }
        } else if (c.glideKind == IN) {
            if (!CameraGlide.in(k, c.gx, c.gy, c.gz, out)) {
                c.glideKind = 0;
                return false;
            }
        } else {
            // Home to the game's own place (or just behind your head on the way into first person).
            CameraGlide.home(k, c.gx - ex, c.gy - ey, c.gz - ez, px - ex, py - ey, pz - ez, type.isFirstPerson(), out);
        }
        c.ox = out[0];
        c.oy = out[1];
        c.oz = out[2];
        out[0] += ex;
        out[1] += ey;
        out[2] += ez;
        return true;
    }

    /** Whether a glide carries the camera into first person now: it stays detached, and the hands wait. */
    public static boolean handsHidden() {
        CameraTweaks c = Modules.CAMERA;
        return c.glideKind != 0 && c.glideKind != OUT && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    /** Whether the camera turns on its own (freelook, or the freecam): the mouse turns it, not you. */
    public static boolean freelooking() {
        CameraTweaks c = Modules.CAMERA;
        return c.enabled() && (c.freelooking || c.freecam);
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
}
