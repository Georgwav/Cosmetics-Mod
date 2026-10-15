package io.github.georgwav.cosmetics.devtools;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.atmosphere.Extras;
import io.github.georgwav.cosmetics.sol.atmosphere.SkyPass;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sol's in-game scenario ({@code -Dcosmeticsmod.solScenario=full|reduced|look}): builds a showcase scene in the CI
 * world (a sand beach with sugar cane at the water's edge, water to the west, a low far shore, dunes and trees to
 * the sides), then shows each theme at its showpiece moment, facing the way its showpiece frame faces, at FOV 70
 * with only the crosshair on screen, and writes a marker ({@code cosmeticsmod-phase-sol_<theme>_show}) so the CI
 * script screenshots it. Ember Solstice is shot like for like with the reference frames: tick 12,300, facing west
 * into the sun. Checks that the Sol sky drew and that the game reports the field of view it was given; the result
 * goes to the log and {@code cosmeticsmod-sol-results.json}.
 */
public final class SolScenario {
    private static final String MODE = System.getProperty("cosmeticsmod.solScenario", "");
    /** Which shots: "world" (the themes), "sheet" (every theme's showpiece, day and night), "ui" (the menus) or "all". */
    private static final String SHOTS = System.getProperty("cosmeticsmod.solShots", "all");
    /** The menus' shots, in the prototype's reference order: the quick menu's states, then the full menu's. */
    private static final String[] UI_SHOTS = {"quick_hud", "quick_modules", "quick_world", "quick_music", "quick_settings",
            "quick_mods", "quick_eclipse", "quick_presets", "quick_emotes", "quick_back", "menu_modules", "menu_hud",
            "menu_world", "menu_wardrobe", "menu_wardrobe_outfits", "menu_music", "menu_mods", "menu_settings",
            "menu_drawer_atmosphere", "menu_cmdbar", "menu_profiles", "toast", "wardrobe_grid", "wardrobe_drawer",
            "wardrobe_drawer_cape", "wardrobe_drawer_pet", "wardrobe_outfits", "wardrobe_outfit_page", "wardrobe_emotes",
            "wardrobe_skins", "cosmetics_settings", "cosmetics_settings_performance", "cosmetics_settings_compatibility",
            "cosmetics_settings_maintenance", "hud_editor", "emote_wheel", "emote_wheel_elytra", "emote_picker", "quick_wheel"};
    private static final int SETTLE = 70;
    private static final int HOLD = 60;
    /** How long each benchmark preset is measured (ticks). */
    private static final int BENCH_HOLD = 400;

    /** One frame: a theme at a sun height (or a fixed day time, when {@code time} isn't -1), facing yaw/pitch. */
    private record Shot(String name, String theme, float altitude, boolean morning, float yaw, float pitch, long time,
                        java.util.Map<String, Float> tune) {
        Shot(String name, String theme, float altitude, boolean morning, float yaw, float pitch, long time) {
            this(name, theme, altitude, morning, yaw, pitch, time, java.util.Map.of());
        }

        Shot(String name, String theme, float altitude, boolean morning, float yaw, float pitch) {
            this(name, theme, altitude, morning, yaw, pitch, -1L);
        }
    }

    /** The reference frames' moment: the sun just above the western horizon. */
    private static final long EMBER_TICK = 12300L;

    private final List<Shot> shots = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private int tick;
    private int index = -1;
    private int phaseTick;
    private boolean built;
    private boolean finished;

    /** The time the scenario holds (0 is 06:00), or -1. Read by the time lock. */
    public static volatile long lockedTime = -1L;
    /** Whether the scenario is showing a frame: the HUD is hidden and only the crosshair is drawn. */
    public static volatile boolean crosshairOnly;

    // The benchmark: frame times and the light pass's own time, while a bench shot is held.
    private static final float[] frameMs = new float[20000];
    private static int frames;
    private static double passMs;
    private static volatile boolean recording;
    private final com.google.gson.JsonObject bench = new com.google.gson.JsonObject();

    /** Every rendered frame (from the frame hook): its length and what the light pass took. */
    public static void frame(float seconds, long passNanos) {
        if (recording && frames < frameMs.length && seconds > 0f) {
            frameMs[frames++] = seconds * 1000f;
            passMs += passNanos / 1e6;
        }
    }

    public static boolean enabled() {
        return !MODE.isEmpty();
    }

    public static void init() {
        if (!enabled()) {
            return;
        }
        SolScenario s = new SolScenario();
        ClientTickEvents.END_CLIENT_TICK.register(s::tick);
        CosmeticsMod.LOGGER.info("[Sol] Scenario '{}' armed", MODE);
    }

    /** Only these themes' shots (comma-separated ids; empty for all), for quick looks while tuning. */
    private static final String THEMES = System.getProperty("cosmeticsmod.solThemes", "");

    private void plan() {
        planAll();
        if (!THEMES.isBlank()) {
            java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(THEMES.split(",")));
            shots.removeIf(s -> !keep.contains(s.theme()) && !s.name().equals("sol_ember_sunlab"));
        }
    }

    private void planAll() {
        if ("bench".equals(MODE)) {
            // The light pass's cost on the Ember frame: Low (no pass), Medium, High.
            String[] q = {"low", "medium", "high"};
            for (int i = 0; i < q.length; i++) {
                shots.add(new Shot("sol_bench_" + q[i], "ember_solstice", 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK,
                        java.util.Map.of("quality", (float) i)));
            }
            return;
        }
        if (!"world".equals(SHOTS) && !"sheet".equals(SHOTS)) {
            // The menus over the game, as the prototype shows them: Deep Field's night, facing its galaxy.
            ThemeRegistry.Entry deep = ThemeRegistry.find("deep_field");
            float yaw = deep == null ? 180f : deep.base().show().azimuth();
            for (String ui : UI_SHOTS) {
                shots.add(new Shot("ui_" + ui, "deep_field", -60f, false, yaw, -Extras.SHOW_PITCH));
            }
        }
        if ("ui".equals(SHOTS)) {
            return;
        }
        for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
            ThemeSpec s = e.base();
            if (!e.builtIn() || "empty".equals(e.id())) {
                continue;
            }
            String phase = s.show().phase();
            float yaw = s.show().azimuth();
            if ("silver_night".equals(e.id())) {
                // Facing the huge moon as it sets in the west over the water, the sun 25° below the eastern horizon.
                shots.add(new Shot("sol_" + e.id() + "_show", e.id(), -25f, true, 90f, -Extras.SHOW_PITCH));
                continue;
            }
            switch (phase) {
                case "night" -> shots.add(new Shot("sol_" + e.id() + "_show", e.id(), -60f, false, yaw, -Extras.SHOW_PITCH));
                case "dusk" -> shots.add("ember_solstice".equals(e.id())
                        ? new Shot("sol_" + e.id() + "_show", e.id(), 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK)
                        : new Shot("sol_" + e.id() + "_show", e.id(), showAltitude(e.id()), false, 90f, -Extras.SHOW_PITCH));
                case "dawn" -> shots.add(new Shot("sol_" + e.id() + "_show", e.id(), showAltitude(e.id()), true, -90f,
                        -Extras.SHOW_PITCH));
                default -> {
                    float alt = showAltitude(e.id());
                    // A day showpiece with a sun faces the sun.
                    float y = s.sun() == null ? yaw : (alt < 0f ? yaw : (morning(e.id()) ? -90f : 90f));
                    shots.add(new Shot("sol_" + e.id() + "_show", e.id(), alt, morning(e.id()), y, -Extras.SHOW_PITCH));
                }
            }
        }
        // The Sun lab's framing: Ember with the sun just above the horizon (tick 12,300 puts it at about 7.7°,
        // e ≈ .2), every layer on, facing west into it.
        shots.add(0, new Shot("sol_ember_sunlab", "ember_solstice", 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK));
        // Tuning (-Dcosmeticsmod.solTune=name:bloom=0.5,scatter=0.8;name2:...): the Sun lab frame again for each
        // set of light-pass strengths, to compare them side by side.
        String tuneSpec = System.getProperty("cosmeticsmod.solTune", "");
        int at = 1;
        for (String variant : tuneSpec.split(";")) {
            int colon = variant.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            java.util.Map<String, Float> values = new java.util.HashMap<>();
            for (String kv : variant.substring(colon + 1).split(",")) {
                String[] p = kv.split("=");
                if (p.length == 2) {
                    values.put(p[0].trim(), Float.parseFloat(p[1].trim()));
                }
            }
            // "name@theme:..." tunes that theme's showpiece instead of the Sun lab.
            String name = variant.substring(0, colon).trim();
            int amp = name.indexOf('@');
            Shot base = shots.get(0);
            if (amp > 0) {
                String theme = name.substring(amp + 1);
                name = name.substring(0, amp);
                for (Shot s : shots) {
                    if (s.name().equals("sol_" + theme + "_show")) {
                        base = s;
                    }
                }
            }
            shots.add(at++, new Shot("sol_tune_" + name, base.theme(), base.altitude(), base.morning(), base.yaw(),
                    base.pitch(), base.time(), values));
        }
        if ("sheet".equals(SHOTS)) {
            // The Atmosphere intensity slider toning the Sun lab frame down: half, and none (the theme's sky only).
            for (int pct : new int[]{50, 0}) {
                shots.add(1, new Shot("sol_ember_intensity" + pct, "ember_solstice", 0f, false, 90f, -Extras.SHOW_PITCH,
                        EMBER_TICK, java.util.Map.of("intensity", (float) pct)));
            }
            // The contact sheet: every theme by day (the sun 40° up, facing it) and by night (facing its showpiece
            // way), beside its showpiece.
            for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
                if (!e.builtIn()) {
                    continue;
                }
                shots.add(new Shot("sol_" + e.id() + "_day", e.id(), 40f, false, 90f, -Extras.SHOW_PITCH));
                shots.add(new Shot("sol_" + e.id() + "_night", e.id(), -60f, false, e.base().show().azimuth(), -Extras.SHOW_PITCH));
            }
        } else if (!"reduced".equals(MODE)) {
            for (String id : new String[]{"ember_solstice", "aurora", "frost_morning"}) {
                shots.add(new Shot("sol_" + id + "_day", id, 40f, false, 90f, -Extras.SHOW_PITCH));
                shots.add(new Shot("sol_" + id + "_night", id, -60f, false, 180f, -Extras.SHOW_PITCH));
            }
        }
    }

    /** The sun's height for each theme's showpiece (plan §9.4). */
    private static float showAltitude(String id) {
        return switch (id) {
            case "ember_solstice" -> 0.15f * 38f;
            case "sakura_dusk" -> 4f;
            case "maple_afternoon" -> 6f;
            case "firefly_meadow" -> 3.5f;
            case "frost_morning" -> 24f;
            case "eclipse" -> 23.7f;
            case "monsoon" -> 35f;
            default -> 45f;
        };
    }

    private static boolean morning(String id) {
        return "frost_morning".equals(id);
    }

    private void tick(Minecraft mc) {
        if (finished) {
            return;
        }
        if (mc.level == null || mc.player == null) {
            tick = 0;
            return;
        }
        tick++;
        if (tick < 60) {
            return;
        }
        if (!built) {
            built = true;
            buildScene(mc);
            if (Atmosphere.INSTANCE != null && !Atmosphere.INSTANCE.enabled()) {
                Atmosphere.INSTANCE.setEnabled(true);
            }
            // The reference frames are judged at High (the software renderer CI uses would make Auto pick Low).
            if (Atmosphere.INSTANCE != null) {
                Atmosphere.INSTANCE.quality.set(System.getProperty("cosmeticsmod.solQuality", "high"));
            }
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.fov().set(70);
            io.github.georgwav.cosmetics.sol.render.Mc.hideHud(true);
            crosshairOnly = true;
            plan();
            phaseTick = 0;
            index = -1;
            return;
        }
        phaseTick++;
        if (index >= 0 && index < shots.size()) {
            Shot s = shots.get(index);
            mc.player.setYRot(s.yaw());
            mc.player.setXRot(s.pitch());
            mc.player.yRotO = s.yaw();
            mc.player.xRotO = s.pitch();
            if (phaseTick == SETTLE) {
                // Empty is the game's own sky: nothing of Sol's draws there.
                if (!SkyPass.drewThisFrame && !"sol_ember_sunlab".equals(s.name()) && !"empty".equals(s.theme())
                        && Guard.on(io.github.georgwav.cosmetics.sol.Feature.SKY)) {
                    problems.add(s.name() + ": the Sol sky didn't draw");
                }
                float fov = io.github.georgwav.cosmetics.sol.render.FrameInfo.fovY;
                if (Math.abs(fov - 70f) > 4f) {
                    problems.add(s.name() + ": the field of view read from the game is " + fov + "°, not 70°");
                }
                if ((s.name().equals("sol_ember_sunlab") || s.name().equals("sol_ember_solstice_show")) && SkyPass.sunVisible < 0.5f) {
                    problems.add(s.name() + ": the sun isn't in the frame (visible " + SkyPass.sunVisible + ")");
                }
                marker(mc, s.name());
                CosmeticsMod.LOGGER.info("[Sol] {}: light factor {} (world light {}, boost {}), key {}", s.name(),
                        io.github.georgwav.cosmetics.sol.atmosphere.WorldTint.lightFactor(1f),
                        io.github.georgwav.cosmetics.sol.atmosphere.SkyTime.worldLight, ThemeState.tokens().lightBoost,
                        io.github.georgwav.cosmetics.sol.atmosphere.KeyLight.describe());
            }
        }
        boolean benching = index >= 0 && index < shots.size() && shots.get(index).name().startsWith("sol_bench");
        if (benching) {
            recording = phaseTick > SETTLE && phaseTick < SETTLE + BENCH_HOLD;
        }
        if (index < 0 || phaseTick >= SETTLE + (benching ? BENCH_HOLD : HOLD)) {
            if (benching) {
                benchResult(shots.get(index).name().substring("sol_bench_".length()));
            }
            index++;
            phaseTick = 0;
            if (index >= shots.size()) {
                finish(mc);
                return;
            }
            Shot s = shots.get(index);
            stageUi(s.name());
            ThemeState.setTheme(s.theme(), false);
            // The Sun lab shows every layer; the showpieces use the hero frames' layers (no flare or glare).
            // Glare is a setting for playing into the sun, not part of the reference frames.
            boolean lab = s.name().endsWith("sunlab");
            Atmosphere at = Atmosphere.INSTANCE;
            if (at != null) {
                at.flare.set(lab);
                at.glare.set(false);
                // Monsoon's showpiece is a thunderstorm, its frame taken in a lightning flash; every other frame
                // has a clear sky.
                boolean storm = "monsoon".equals(s.theme());
                at.lockWeather.set(true);
                at.weather.set(storm ? "thunder" : "clear");
                io.github.georgwav.cosmetics.sol.atmosphere.Extras.holdFlash = storm ? 0.32f : Float.NaN;
            }
            lockedTime = s.time() >= 0 ? s.time() : dayTimeFor(s.altitude(), s.morning());
            io.github.georgwav.cosmetics.sol.render.LightPass.tune.clear();
            io.github.georgwav.cosmetics.sol.render.LightPass.tune.putAll(s.tune());
            Float quality = s.tune().get("quality");
            if (quality != null && at != null) {
                at.quality.set(new String[]{"low", "medium", "high"}[Math.round(quality)]);
            }
            Float intensity = s.tune().get("intensity");
            if (at != null) {
                at.intensity.set(intensity != null ? (double) intensity : 100.0);
            }
            frames = 0;
            passMs = 0;
        }
    }

    /** Opens the menu a "ui_" shot shows, in its state; any other shot closes it. */
    private static void stageUi(String name) {
        boolean ui = name.startsWith("ui_");
        io.github.georgwav.cosmetics.sol.ui.kit.SolScreen.pointerOff = ui;
        // The toast and the HUD editor are shown with the HUD, as in play; every other frame keeps the HUD hidden.
        io.github.georgwav.cosmetics.sol.render.Mc.hideHud(!name.equals("ui_toast") && !name.equals("ui_hud_editor"));
        String what = ui ? name.substring(3) : "";
        boolean wardrobe = what.startsWith("wardrobe_") || what.startsWith("cosmetics_settings");
        wearForWardrobe(wardrobe);
        hudForEditor(what.equals("hud_editor"));
        if (!ui) {
            if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null) {
                io.github.georgwav.cosmetics.ui.ScreenOpener.open(null);
            }
            return;
        }
        if (what.startsWith("emote_")) {
            // The wheel with emotes on it (a fresh config has none yet).
            for (boolean elytra : new boolean[]{false, true}) {
                java.util.List<io.github.georgwav.cosmetics.emote.Emote> all = new java.util.ArrayList<>(
                        io.github.georgwav.cosmetics.emote.Emotes.all());
                all.sort(java.util.Comparator.comparing((io.github.georgwav.cosmetics.emote.Emote e) -> e.elytra() != elytra));
                for (int i = 0; i < Math.min(6, all.size()); i++) {
                    if (io.github.georgwav.cosmetics.emote.Emotes.at(elytra, i) == null) {
                        io.github.georgwav.cosmetics.emote.Emotes.assign(elytra, i, all.get(i).id());
                    }
                }
            }
            var wheel = new io.github.georgwav.cosmetics.sol.ui.screens.EmoteWheel(what.equals("emote_wheel_elytra"));
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(what.equals("emote_picker") ? wheel.stage("picker") : wheel);
            return;
        }
        if (what.equals("quick_wheel")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.QuickWheel());
            return;
        }
        if (what.equals("hud_editor")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.HudEditor(null));
            return;
        }
        if (wardrobe) {
            var w = new io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen(null);
            if (what.startsWith("cosmetics_settings")) {
                w.stage("settings");
                w.stage(what.equals("cosmetics_settings") ? "settings_display" : "settings_" + what.substring("cosmetics_settings_".length()));
            } else {
                String state = what.substring("wardrobe_".length());
                w.stage(state.equals("grid") ? "all" : state);
            }
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(w);
            return;
        }
        if (what.equals("toast")) {
            // A notification over the game, with a button, as Atmosphere's first start shows one.
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(null);
            io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(
                    net.minecraft.network.chat.Component.translatable("sol.notify.shaders_paused").getString(),
                    net.minecraft.network.chat.Component.translatable("sol.notify.undo").getString(), () -> { });
            return;
        }
        if (what.startsWith("quick_")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(
                    new io.github.georgwav.cosmetics.sol.ui.quick.QuickMenu(null).stage(what.substring("quick_".length())));
        } else {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(
                    io.github.georgwav.cosmetics.sol.ui.menu.SolMenu.create(null, "modules", false).stage(what.substring("menu_".length())));
        }
    }

    /** What you wore before the Wardrobe shots (null while they aren't on). */
    private static java.util.List<String> wornBefore;

    /**
     * The Wardrobe shots show you wearing a few things (the world shots wear nothing): a cape, a hat, wings and an
     * aura when there are some. Back to what you wore after them.
     */
    private static void wearForWardrobe(boolean on) {
        var config = io.github.georgwav.cosmetics.config.CosmeticsConfig.get();
        java.util.List<String> worn = config.localEquipped();
        if (on && wornBefore == null) {
            wornBefore = new java.util.ArrayList<>(worn);
            worn.clear();
            java.util.Set<String> taken = new java.util.HashSet<>();
            for (var c : io.github.georgwav.cosmetics.model.CosmeticRegistry.all()) {
                String kind = c.wings() != null ? "wings" : c.slot().name();
                if (!c.definition().prop() && java.util.Set.of("CAPE", "HEAD", "wings", "AURA").contains(kind) && taken.add(kind)) {
                    worn.add(c.id());
                }
            }
            io.github.georgwav.cosmetics.source.EquippedCosmetics.invalidate();
        } else if (!on && wornBefore != null) {
            worn.clear();
            worn.addAll(wornBefore);
            wornBefore = null;
            io.github.georgwav.cosmetics.source.EquippedCosmetics.invalidate();
        }
    }

    /** The HUD modules switched on for the HUD editor's shot (and off again after it). */
    private static java.util.List<io.github.georgwav.cosmetics.client.Module> hudShown;

    private static void hudForEditor(boolean on) {
        if (on && hudShown == null) {
            hudShown = new java.util.ArrayList<>();
            for (String id : new String[]{"fps", "coords", "keystrokes", "armor"}) {
                var m = io.github.georgwav.cosmetics.client.Modules.byId(id);
                if (m != null && !m.enabled()) {
                    m.setEnabled(true);
                    hudShown.add(m);
                }
            }
        } else if (!on && hudShown != null) {
            hudShown.forEach(m -> m.setEnabled(false));
            hudShown = null;
        }
    }

    private void finish(Minecraft mc) {
        finished = true;
        stageUi("");
        io.github.georgwav.cosmetics.sol.atmosphere.Extras.holdFlash = Float.NaN;
        lockedTime = -1L;
        crosshairOnly = false;
        if (!Guard.failed().isEmpty()) {
            problems.add("features switched off after errors: " + Guard.failed());
        }
        boolean passed = problems.isEmpty();
        CosmeticsMod.LOGGER.info("[Sol] Scenario {}: {}", passed ? "PASSED" : "FAILED", problems);
        try {
            com.google.gson.JsonObject out = new com.google.gson.JsonObject();
            out.addProperty("passed", passed);
            out.add("problems", new com.google.gson.Gson().toJsonTree(problems));
            if (bench.size() > 0) {
                out.add("bench", bench);
            }
            Files.writeString(mc.gameDirectory.toPath().resolve("cosmeticsmod-sol-results.json"), out.toString());
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't write the scenario result", e);
        }
        if (Boolean.parseBoolean(System.getProperty("cosmeticsmod.exitWhenDone", "true"))) {
            mc.stop();
        }
    }

    /** One preset's numbers: frames per second, frame time p50/p99, and the pass's own CPU time per frame. */
    private void benchResult(String preset) {
        recording = false;
        int n = frames;
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        if (n > 10) {
            float[] sorted = java.util.Arrays.copyOf(frameMs, n);
            java.util.Arrays.sort(sorted);
            double total = 0;
            for (int i = 0; i < n; i++) {
                total += sorted[i];
            }
            o.addProperty("frames", n);
            o.addProperty("fps", Math.round(1000.0 * n / total * 10) / 10.0);
            o.addProperty("p50Ms", Math.round(sorted[n / 2] * 100) / 100.0);
            o.addProperty("p99Ms", Math.round(sorted[Math.min(n - 1, (int) (n * 0.99))] * 100) / 100.0);
            o.addProperty("passCpuMs", Math.round(passMs / n * 1000) / 1000.0);
        }
        bench.add(preset, o);
        CosmeticsMod.LOGGER.info("[Sol] Bench {}: {}", preset, o);
    }

    private static void marker(Minecraft mc, String name) {
        try {
            Path p = mc.gameDirectory.toPath().resolve("cosmeticsmod-phase-" + name);
            Files.writeString(p, name);
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't write marker {}", name, e);
        }
    }

    /**
     * The day time (0 is 06:00) at which the sun stands at {@code altitude} degrees, in the morning or the evening:
     * the inverse of the game's time-of-day curve, found by bisection.
     */
    public static long dayTimeFor(float altitude, boolean morning) {
        double angle = Math.acos(Math.max(-1, Math.min(1, Math.sin(Math.toRadians(altitude)))));
        double tod = morning ? 1.0 - angle / (Math.PI * 2) : angle / (Math.PI * 2);
        double lo = 0, hi = 1;
        for (int i = 0; i < 60; i++) {
            double mid = (lo + hi) / 2;
            double e = 0.5 - Math.cos(mid * Math.PI) / 2.0;
            double v = (mid * 2.0 + e) / 3.0;
            if (v < tod) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double d = (lo + hi) / 2;
        // The game's day time (0 is 06:00, 6000 noon): d is measured from noon.
        return Math.round((d + 0.25) * 24000.0) % 24000L;
    }

    /**
     * The reference scene (the integrated server builds it): a sand beach under the player running down to water
     * that fills the view to the west, sugar cane at the water's edge 12 blocks out, a low far shore about 58
     * blocks away (under the sun, so it never hides it), and dunes and trees off to the sides that frame the view.
     */
    private static void buildScene(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        var id = mc.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                return;
            }
            int x = (int) Math.floor(player.getX());
            int y = (int) Math.floor(player.getY());
            int z = (int) Math.floor(player.getZ());
            int g = y - 1;
            List<String> cmds = new ArrayList<>(List.of(
                    "gamerule doDaylightCycle false",
                    "gamerule doWeatherCycle false",
                    "gamerule randomTickSpeed 0",
                    "weather clear",
                    // A clean slate, then the beach.
                    "fill " + (x - 62) + " " + (g + 1) + " " + (z - 40) + " " + (x + 10) + " " + (g + 16) + " " + (z + 40) + " air",
                    "fill " + (x - 12) + " " + (g - 1) + " " + (z - 40) + " " + (x + 10) + " " + g + " " + (z + 40) + " sand",
                    // The water: two blocks deep from the shore to the far side.
                    "fill " + (x - 54) + " " + (g - 1) + " " + (z - 40) + " " + (x - 13) + " " + g + " " + (z + 40) + " water",
                    // The far shore: a low sand bank with grass on top, a silhouette under the sun.
                    "fill " + (x - 62) + " " + (g - 1) + " " + (z - 40) + " " + (x - 55) + " " + (g + 1) + " " + (z + 40) + " sand",
                    "fill " + (x - 62) + " " + (g + 2) + " " + (z - 40) + " " + (x - 58) + " " + (g + 2) + " " + (z + 40) + " grass_block",
                    // Dunes to the sides, low enough to frame rather than block.
                    "fill " + (x - 6) + " " + (g + 1) + " " + (z - 30) + " " + (x + 8) + " " + (g + 1) + " " + (z - 13) + " sand",
                    "fill " + (x - 2) + " " + (g + 2) + " " + (z - 26) + " " + (x + 8) + " " + (g + 2) + " " + (z - 16) + " sand",
                    "fill " + (x - 6) + " " + (g + 1) + " " + (z + 13) + " " + (x + 8) + " " + (g + 1) + " " + (z + 30) + " sand",
                    "fill " + (x - 2) + " " + (g + 2) + " " + (z + 17) + " " + (x + 8) + " " + (g + 2) + " " + (z + 28) + " sand",
                    // Grass behind the beach for the trees, the meadow themes and fireflies.
                    "fill " + (x + 9) + " " + g + " " + (z - 40) + " " + (x + 10) + " " + g + " " + (z + 40) + " grass_block",
                    "fill " + (x - 4) + " " + (g + 3) + " " + (z - 24) + " " + (x + 8) + " " + (g + 3) + " " + (z - 18) + " grass_block",
                    "fill " + (x - 4) + " " + (g + 3) + " " + (z + 19) + " " + (x + 8) + " " + (g + 3) + " " + (z + 26) + " grass_block"));
            // Sugar cane along the water's edge (on sand beside the water, so it stays), two and three tall.
            int[][] cane = {{-7, 3}, {-6, 2}, {-3, 3}, {-2, 2}, {2, 2}, {3, 3}, {4, 2}, {8, 3}, {9, 2}, {-11, 2}, {12, 3}};
            for (int[] c : cane) {
                for (int h = 1; h <= c[1]; h++) {
                    cmds.add("setblock " + (x - 12) + " " + (g + h) + " " + (z + c[0]) + " sugar_cane");
                }
            }
            var source = server.createCommandSourceStack().withSuppressedOutput();
            for (String c : cmds) {
                try {
                    server.getCommands().performPrefixedCommand(source, c);
                } catch (RuntimeException e) {
                    CosmeticsMod.LOGGER.warn("[Sol] Scene command failed: {}", c, e);
                }
            }
            // Trees on the dunes and the grass behind, out of the line to the sun.
            String[][] trees = {{"cherry", "2", "22"}, {"oak", "6", "-21"}, {"birch", "9", "30"}, {"oak", "9", "-32"},
                    {"cherry", "-60", "28"}, {"oak", "-60", "-26"}};
            for (String[] t : trees) {
                try {
                    int tx = x + Integer.parseInt(t[1]), tz = z + Integer.parseInt(t[2]);
                    int ty = Integer.parseInt(t[1]) < -50 ? g + 3 : (Math.abs(Integer.parseInt(t[2])) < 27 && Integer.parseInt(t[1]) < 9 ? g + 4 : g + 1);
                    server.getCommands().performPrefixedCommand(source, "place feature minecraft:" + t[0] + " " + tx + " " + ty + " " + tz);
                } catch (RuntimeException e) {
                    CosmeticsMod.LOGGER.warn("[Sol] Tree failed: {}", t[0], e);
                }
            }
            // Stand on the beach, looking west over the water.
            server.getCommands().performPrefixedCommand(source, "tp " + player.getName().getString() + " " + (x + 0.5) + " "
                    + (g + 1) + " " + (z + 0.5) + " 90 0");
        });
        Modules.save();
    }
}
