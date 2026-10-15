package io.github.georgwav.cosmetics.devtools;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.render.SolTiming;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * The benchmark's Sol phases, after the cosmetics ones: the same view with Atmosphere off, with Atmosphere on, and
 * with everything of Sol on (Atmosphere at High, Sol UI, every HUD module, Glint, Sol Chat). Each phase records the
 * frame time percentiles and Sol's CPU time per part, and the budgets are checked on them.
 */
final class SolBench {
    /** CPU milliseconds per frame Sol may spend: Atmosphere's world parts, the sun and flare, Sol UI. */
    static final double ATMOSPHERE_BUDGET = 0.3;
    static final double SUN_FLARE_BUDGET = 0.03;
    static final double SOL_UI_BUDGET = 0.2;

    private static final List<Module> TURNED_ON = new ArrayList<>();

    private SolBench() {
    }

    /** Ember at sunset, facing the sun, everything of Sol off: the start of the Sol phases. */
    static void setUp(Minecraft mc) {
        ThemeState.setTheme("ember_solstice", false);
        Atmosphere at = Atmosphere.INSTANCE;
        if (at != null) {
            at.quality.set("high");
            at.lockTime.set(true);
            at.time.set(12250.0);
            at.lockWeather.set(true);
            at.weather.set("clear");
            at.setEnabled(false);
        }
        Modules.SOL_UI.setEnabled(false);
        if (mc.player != null) {
            mc.player.setYRot(90f);
            mc.player.setXRot(-8f);
        }
        SolTiming.reset();
    }

    static void atmosphereOn() {
        if (Atmosphere.INSTANCE != null) {
            Atmosphere.INSTANCE.setEnabled(true);
        }
        SolTiming.reset();
    }

    static void everythingOn() {
        Modules.SOL_UI.setEnabled(true);
        for (Module m : Modules.hud()) {
            on(m);
        }
        on(Modules.GLINT);
        on(Modules.SOL_CHAT);
        SolTiming.reset();
    }

    private static void on(Module m) {
        if (!m.enabled()) {
            m.setEnabled(true);
            TURNED_ON.add(m);
        }
    }

    /** Puts back what the Sol phases turned on (Atmosphere stays as the player had it: on, after first launch). */
    static void tearDown() {
        TURNED_ON.forEach(m -> m.setEnabled(false));
        TURNED_ON.clear();
        Atmosphere at = Atmosphere.INSTANCE;
        if (at != null) {
            at.lockTime.set(false);
            at.lockWeather.set(false);
            at.quality.set("auto");
        }
    }

    /** Adds the phase's frame times and Sol's CPU time per part. */
    static void record(JsonObject p) {
        p.addProperty("frameMsP50", SolTiming.frameMs(0.5));
        p.addProperty("frameMsP99", SolTiming.frameMs(0.99));
        JsonObject parts = new JsonObject();
        for (SolTiming.Part part : SolTiming.Part.values()) {
            parts.addProperty(part.name().toLowerCase(java.util.Locale.ROOT), SolTiming.msPerFrame(part));
        }
        p.add("solMsPerFrame", parts);
        p.addProperty("atmosphereMsPerFrame", atmosphere());
        p.addProperty("atmosphereOn", Atmosphere.active());
        p.addProperty("solUiOn", Modules.SOL_UI.enabled());
    }

    /** Atmosphere's world parts: the sky pass (the sun is inside it), the particles and the flare. */
    private static double atmosphere() {
        return SolTiming.msPerFrame(SolTiming.Part.SKY) + SolTiming.msPerFrame(SolTiming.Part.PARTICLES)
                + SolTiming.msPerFrame(SolTiming.Part.FLARE);
    }

    /**
     * The budgets, on the phase with everything on. Not on a software renderer (CI's llvmpipe): there every draw
     * call also does the graphics card's work on the processor, so the times are the renderer's, not Sol's; the
     * numbers are still recorded.
     */
    static void check(JsonObject phase, List<String> failures) {
        if (phase == null || !phase.has("solMsPerFrame")) {
            failures.add("the Sol benchmark phases didn't run");
            return;
        }
        if (!phase.get("atmosphereOn").getAsBoolean()) {
            failures.add("Atmosphere wasn't drawing in the sol_all phase");
            return;
        }
        boolean software = "1".equals(System.getenv("LIBGL_ALWAYS_SOFTWARE"));
        phase.addProperty("budgetsChecked", !software);
        if (software) {
            return;
        }
        JsonObject parts = phase.getAsJsonObject("solMsPerFrame");
        double atmosphere = phase.get("atmosphereMsPerFrame").getAsDouble();
        double sunFlare = parts.get("sun").getAsDouble() + parts.get("flare").getAsDouble();
        double ui = parts.get("sol_ui").getAsDouble();
        if (atmosphere > ATMOSPHERE_BUDGET) {
            failures.add(String.format(java.util.Locale.ROOT, "Atmosphere took %.3f ms/frame (budget %.2f)", atmosphere, ATMOSPHERE_BUDGET));
        }
        if (sunFlare > SUN_FLARE_BUDGET) {
            failures.add(String.format(java.util.Locale.ROOT, "the sun and flare took %.3f ms/frame (budget %.2f)", sunFlare, SUN_FLARE_BUDGET));
        }
        if (ui > SOL_UI_BUDGET) {
            failures.add(String.format(java.util.Locale.ROOT, "Sol UI took %.3f ms/frame (budget %.2f)", ui, SOL_UI_BUDGET));
        }
    }
}
