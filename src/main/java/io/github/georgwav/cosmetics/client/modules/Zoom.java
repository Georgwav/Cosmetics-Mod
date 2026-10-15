package io.github.georgwav.cosmetics.client.modules;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * Zoom on C (OptiFine's key): hold it (or tap it, as a toggle) to narrow the view by the amount set, eased in and
 * out; the scroll wheel zooms further or back while zoomed, the mouse slows down with it, and the cinematic camera
 * can come on with it. The Sol sky's planets swap to their detailed sprites as they grow. Only the world zooms, not
 * your hand. On by default.
 */
public final class Zoom extends Module {
    private final Setting.Slider amount = add(new Setting.Slider("amount", 4.0, 2.0, 16.0, 0.5,
            v -> String.format(Locale.ROOT, v % 1 == 0 ? "%.0f×" : "%.1f×", v)));
    private final Setting.Choice mode = add(new Setting.Choice("mode", "hold", List.of("hold", "toggle")));
    private final Setting.Toggle smooth = add(new Setting.Toggle("smooth", true));
    private final Setting.Toggle scroll = add(new Setting.Toggle("scroll", true));
    private final Setting.Toggle slower = add(new Setting.Toggle("sensitivity", true));
    private final Setting.Toggle cinematic = add(new Setting.Toggle("cinematic", false));

    private boolean zooming;
    /** The scroll wheel's change to the amount while zoomed (1 = as set). */
    private double scrollMul = 1.0;
    /** The zoom shown now (1 = none), eased toward the target each frame. */
    private double factor = 1.0;
    private long lastFrame;
    private Boolean smoothBefore;

    public Zoom() {
        super("zoom", Category.VIEW, "zoom");
        addKey("zoom", InputConstants.KEY_C);
    }

    @Override
    public boolean onByDefault() {
        return true;
    }

    @Override
    protected void tick(Minecraft mc) {
        var key = key("zoom");
        if (key == null) {
            return;
        }
        boolean was = zooming;
        if (mode.get().equals("toggle")) {
            while (key.consumeClick()) {
                zooming = !zooming;
            }
        } else {
            while (key.consumeClick()) {
                // Clicks are read through isDown; drain them so they don't pile up.
            }
            zooming = key.isDown();
        }
        zooming &= mc.player != null;
        if (zooming && !was) {
            scrollMul = 1.0;
            if (cinematic.get()) {
                smoothBefore = mc.options.smoothCamera;
                mc.options.smoothCamera = true;
            }
        } else if (!zooming && was && smoothBefore != null) {
            mc.options.smoothCamera = smoothBefore;
            smoothBefore = null;
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        zooming = false;
        if (smoothBefore != null) {
            mc.options.smoothCamera = smoothBefore;
            smoothBefore = null;
        }
    }

    private double target() {
        if (held > 0) {
            return held;
        }
        return enabled() && zooming ? Math.max(1.0, Math.min(64.0, amount.get() * scrollMul)) : 1.0;
    }

    /** The zoom now, eased once per frame (the FOV is asked for once or twice a frame). */
    private double step() {
        long now = System.nanoTime();
        double dt = lastFrame == 0L ? 0.0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double t = target();
        if (!smooth.get() || io.github.georgwav.cosmetics.sol.ui.kit.SolScreen.calm()) {
            factor = t;
        } else {
            // Eased in log space, so 2x to 4x feels the same as 4x to 8x.
            double k = 1.0 - Math.exp(-dt * 14.0);
            factor = Math.exp(Math.log(factor) + (Math.log(t) - Math.log(factor)) * k);
            if (Math.abs(factor - t) < 1e-3) {
                factor = t;
            }
        }
        return factor;
    }

    /** The world's field of view with the zoom (the hand's isn't changed). */
    public static float fov(float fov) {
        Zoom z = Modules.ZOOM;
        double f = z.step();
        return f == 1.0 ? fov : (float) (fov / f);
    }

    public static double fov(double fov) {
        return fov(fov == fov ? (float) fov : 70f);
    }

    /** For the test scenario: holds a zoom of {@code amount} (0 lets go), without the key. */
    public static void hold(double amount) {
        Zoom z = Modules.ZOOM;
        held = amount;
        if (amount > 0 && !z.enabled()) {
            z.setEnabled(true);
        }
    }

    private static double held;

    /** The current zoom (1 = none), for the sky's planets and the mouse. */
    public static double factor() {
        return Modules.ZOOM.factor;
    }

    /** The scroll wheel while zoomed: zooms further or back; true when Zoom took it. */
    public static boolean scroll(double amount) {
        Zoom z = Modules.ZOOM;
        if (!z.enabled() || !z.zooming || !z.scroll.get() || amount == 0.0) {
            return false;
        }
        z.scrollMul = Math.max(0.25, Math.min(8.0, z.scrollMul * (amount > 0 ? 1.2 : 1 / 1.2)));
        return true;
    }

    /** How much the mouse turns while zoomed (1 when not). */
    public static double sensitivity() {
        Zoom z = Modules.ZOOM;
        return z.enabled() && z.slower.get() && z.factor > 1.0 ? 1.0 / z.factor : 1.0;
    }
}
