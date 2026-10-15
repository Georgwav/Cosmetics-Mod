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
            "menu_world", "menu_wardrobe", "menu_wardrobe_outfits", "menu_music", "menu_music_lyrics", "menu_mods", "menu_settings",
            "menu_drawer_atmosphere", "menu_cmdbar", "menu_profiles", "toast", "title", "pause", "death", "loading", "keys", "gallery", "crash", "mods_browse", "quickplay", "wardrobe_grid", "wardrobe_drawer",
            "wardrobe_drawer_cape", "wardrobe_drawer_pet", "wardrobe_mannequin", "wardrobe_outfits", "wardrobe_outfit_page", "wardrobe_emotes",
            "wardrobe_skins", "cosmetics_settings", "cosmetics_settings_performance", "cosmetics_settings_compatibility",
            "cosmetics_settings_maintenance", "hud_editor", "emote_wheel", "emote_wheel_elytra", "emote_picker", "quick_wheel",
            "hud_sol", "hud_off", "tab"};
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

    /** CI's mock Cider is running (ci/mock_cider.py): the music shots show its song and are checked. */
    private static final boolean MOCK_CIDER = Boolean.getBoolean("cosmeticsmod.mockCider");
    private static boolean musicWasOn = true;
    private static boolean albumUiWas = true;
    private static boolean albumSkyWas;

    private void plan() {
        if (MOCK_CIDER) {
            // On from the start, so the song, its lyrics and its cover are there by the music shots.
            musicWasOn = io.github.georgwav.cosmetics.client.Modules.MUSIC.enabled();
            albumUiWas = io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumUi;
            albumSkyWas = io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumSky;
            io.github.georgwav.cosmetics.client.Modules.MUSIC.setEnabled(true);
        }
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
        if (!"sheet".equals(SHOTS) && !"ui".equals(SHOTS)) {
            // Zoom at 8x on Deep Field's galaxy: the field of view narrows (and the sky's planets swap sprites).
            ThemeRegistry.Entry deepZoom = ThemeRegistry.find("deep_field");
            shots.add(new Shot("sol_zoom_8x", "deep_field", -60f, false, deepZoom == null ? 180f : deepZoom.base().show().azimuth(),
                    -Extras.SHOW_PITCH));
        }
        if (!"world".equals(SHOTS) && !"sheet".equals(SHOTS)) {
            // The menus over the game, as the prototype shows them: Deep Field's night, facing its galaxy.
            ThemeRegistry.Entry deep = ThemeRegistry.find("deep_field");
            float yaw = deep == null ? 180f : deep.base().show().azimuth();
            // Only some menu shots (-Dcosmeticsmod.solUi=hud_sol,hud_off), for quick looks.
            java.util.List<String> only = java.util.Arrays.asList(System.getProperty("cosmeticsmod.solUi", "").split(","));
            for (String ui : UI_SHOTS) {
                if (!only.get(0).isBlank() && !only.contains(ui)) {
                    continue;
                }
                // The HUD in play: Ember Solstice by day, facing west.
                shots.add(inPlay(ui)
                        ? new Shot("ui_" + ui, "ember_solstice", 40f, false, 90f, -Extras.SHOW_PITCH)
                        : new Shot("ui_" + ui, "deep_field", -60f, false, yaw, -Extras.SHOW_PITCH));
            }
        }
        if (MOCK_CIDER && !"sheet".equals(SHOTS) && !"bench".equals(MODE)) {
            java.util.List<String> only = java.util.Arrays.asList(System.getProperty("cosmeticsmod.solUi", "").split(","));
            if (only.get(0).isBlank() || only.contains("music_viz") || only.contains("music_stars")) {
                // The music in the sky: Ember at sunset with every visualizer (the made-up song's beat).
                shots.add(new Shot("sol_music_viz", "ember_solstice", 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK));
                // The album sky at night with the line being sung written in stars, facing away from the set sun.
                shots.add(new Shot("sol_music_stars", "ember_solstice", -40f, false, -90f, -26f));
            }
        }
        if ("ui".equals(SHOTS)) {
            return;
        }
        if (!"sheet".equals(SHOTS) && io.github.georgwav.cosmetics.sol.render.LightPass.blurSupported()) {
            // Motion blur while the view turns: Ember by day, the camera sweeping over the lake.
            shots.add(new Shot("sol_motion_blur", "ember_solstice", 40f, false, 60f, -Extras.SHOW_PITCH));
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
            // The motion blur shot turns the view a little each tick (the blur needs movement to show).
            float yaw = s.name().equals("sol_motion_blur") ? s.yaw() + phaseTick * 2.5f : s.yaw();
            mc.player.yRotO = s.name().equals("sol_motion_blur") ? yaw - 2.5f : yaw;
            mc.player.setYRot(yaw);
            mc.player.setXRot(s.pitch());
            mc.player.xRotO = s.pitch();
            boolean play = s.name().startsWith("ui_") && inPlay(s.name().substring(3));
            if (play && phaseTick == SETTLE - 10) {
                asPlayer(mc, List.of("title @s actionbar {\"text\":\"Sol Client \u00b7 action bar\",\"color\":\"gold\"}"));
            }
            if (play && phaseTick == SETTLE - 2) {
                // The toasts from the items given for the HUD shots.
                io.github.georgwav.cosmetics.sol.render.Mc.clearToasts();
            }
            // The tab list shot holds the player list key.
            mc.options.keyPlayerList.setDown(s.name().equals("ui_tab"));
            if (phaseTick == SETTLE && play) {
                checkHud(s.name());
            }
            if (phaseTick == SETTLE && MOCK_CIDER && (s.name().equals("ui_hud_sol") || s.name().startsWith("ui_menu_music"))) {
                checkMusic(s.name());
            }
            if (phaseTick == SETTLE && (s.name().equals("ui_pause") || s.name().equals("ui_death") || s.name().equals("ui_loading"))) {
                var look = io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook.class;
                if (s.name().equals("ui_loading") ? io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook.loading == 0
                        : io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook.veils == 0
                        || io.github.georgwav.cosmetics.sol.ui.screens.VanillaLook.buttons == 0) {
                    problems.add(s.name() + ": the game's screen didn't take Sol's look (" + look.getSimpleName() + ")");
                }
            }
            if (phaseTick == SETTLE && s.name().equals("sol_music_viz")) {
                if (!"listening".equals(io.github.georgwav.cosmetics.sol.music.AudioCapture.state())) {
                    problems.add(s.name() + ": the visualizers aren't listening (" + io.github.georgwav.cosmetics.sol.music.AudioCapture.state() + ")");
                }
                if (io.github.georgwav.cosmetics.sol.music.Visualizers.beats() == 0) {
                    problems.add(s.name() + ": no beat was heard in the made-up song");
                }
                if (io.github.georgwav.cosmetics.sol.atmosphere.MusicSky.eq == null) {
                    problems.add(s.name() + ": the horizon EQ isn't on");
                }
            }
            if (phaseTick == SETTLE) {
                // Empty is the game's own sky: nothing of Sol's draws there.
                if (!SkyPass.drewThisFrame && !"sol_ember_sunlab".equals(s.name()) && !"empty".equals(s.theme())
                        && Guard.on(io.github.georgwav.cosmetics.sol.Feature.SKY)) {
                    problems.add(s.name() + ": the Sol sky didn't draw");
                }
                float fov = io.github.georgwav.cosmetics.sol.render.FrameInfo.fovY;
                float want = s.name().equals("sol_zoom_8x") ? 70f / 8f : 70f;
                if (Math.abs(fov - want) > Math.max(1f, want * 0.06f)) {
                    problems.add(s.name() + ": the field of view read from the game is " + fov + "°, not " + want + "°");
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
            io.github.georgwav.cosmetics.client.modules.Zoom.hold(s.name().equals("sol_zoom_8x") ? 8.0 : 0.0);
            motionBlur(s.name().equals("sol_motion_blur"));
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

    private static boolean screenWasOn;
    private static boolean blurOn;

    /** The Screen module's motion blur at 85% for the motion blur shot, and back as it was after. */
    private static void motionBlur(boolean on) {
        var screen = io.github.georgwav.cosmetics.client.Modules.SCREEN;
        if (on == blurOn) {
            return;
        }
        blurOn = on;
        for (var setting : screen.settings()) {
            if (setting.id.equals("motion_blur")) {
                if (on) {
                    ((io.github.georgwav.cosmetics.client.Setting.Slider) setting).set(85.0);
                } else {
                    setting.reset();
                }
            }
        }
        if (on) {
            screenWasOn = screen.enabled();
            screen.setEnabled(true);
        } else {
            screen.setEnabled(screenWasOn);
        }
    }

    /** Opens the menu a "ui_" shot shows, in its state; any other shot closes it. */
    private static boolean loadingShown;

    private static void stageUi(String name) {
        boolean ui = name.startsWith("ui_");
        if (loadingShown) {
            loadingShown = false;
            io.github.georgwav.cosmetics.sol.render.Mc.setOverlay(null);
        }
        io.github.georgwav.cosmetics.sol.ui.kit.SolScreen.pointerOff = ui;
        // The toast, the HUD editor and the HUD shots are shown with the HUD, as in play; every other frame keeps the
        // HUD hidden.
        String what = ui ? name.substring(3) : "";
        boolean hud = inPlay(what);
        if (MOCK_CIDER) {
            // The album's colours only in the music shots: every other shot shows its theme.
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumUi = what.startsWith("menu_music") || what.equals("hud_sol")
                    || name.equals("sol_music_stars");
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumSky = name.equals("sol_music_stars");
        }
        java.util.Arrays.fill(io.github.georgwav.cosmetics.sol.hud.SolHud.DRAWN, 0);
        io.github.georgwav.cosmetics.sol.render.Mc.hideHud(!name.equals("ui_toast") && !name.equals("ui_hud_editor") && !hud);
        hudInPlay(hud, what.equals("hud_off"));
        boolean wardrobe = what.startsWith("wardrobe_") || what.startsWith("cosmetics_settings");
        wearForWardrobe(wardrobe);
        hudForEditor(what.equals("hud_editor") || inPlay(what));
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
        if (hud) {
            if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null) {
                io.github.georgwav.cosmetics.ui.ScreenOpener.open(null);
            }
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
        // Before a world: you as the mannequin, in the Wardrobe's preview.
        io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen.forceMannequin = what.equals("wardrobe_mannequin");
        if (what.equals("wardrobe_mannequin")) {
            var w = new io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen(null);
            w.stage("all");
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(w);
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
        if (what.equals("mods_browse")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.ModBrowser(null, ""));
            return;
        }
        if (what.equals("keys")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.KeybindSearch(null));
            return;
        }
        if (what.equals("quickplay")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.QuickplayScreen(null));
            return;
        }
        if (what.equals("gallery")) {
            sampleScreenshots();
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.ScreenshotGallery(null));
            return;
        }
        if (what.equals("crash")) {
            var crash = sampleCrash();
            if (crash != null) {
                io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.CrashHelperScreen(null, crash));
            }
            return;
        }
        if (what.equals("title")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.SolTitleScreen());
            return;
        }
        if (what.equals("pause")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.PauseScreen(true));
            return;
        }
        if (what.equals("death")) {
            net.minecraft.network.chat.Component cause = net.minecraft.network.chat.Component.literal("CosmeticsCI fell out of the sky");
            //? if >=1.21.11 {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.DeathScreen(cause, false,
                    Minecraft.getInstance().player));
            //?} else {
            /*io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.DeathScreen(cause, false));
            *///?}
            return;
        }
        if (what.equals("loading")) {
            // A resource reload held at 62% (it never finishes until the next shot takes it away).
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(null);
            loadingShown = true;
            io.github.georgwav.cosmetics.sol.render.Mc.setOverlay(new net.minecraft.client.gui.screens.LoadingOverlay(Minecraft.getInstance(),
                    new net.minecraft.server.packs.resources.ReloadInstance() {
                        private final java.util.concurrent.CompletableFuture<?> never = new java.util.concurrent.CompletableFuture<>();

                        @Override
                        public java.util.concurrent.CompletableFuture<?> done() {
                            return never;
                        }

                        @Override
                        public float getActualProgress() {
                            return 0.62f;
                        }
                    }, error -> { }, true));
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

    /** The gallery shot's pictures: a few themes' frames as screenshots, when the folder has none. */
    private static void sampleScreenshots() {
        java.nio.file.Path dir = io.github.georgwav.cosmetics.sol.ui.screens.ScreenshotGallery.folder();
        try {
            java.nio.file.Files.createDirectories(dir);
            try (var files = java.nio.file.Files.list(dir)) {
                if (files.anyMatch(p -> p.toString().endsWith(".png"))) {
                    return;
                }
            }
            int i = 0;
            for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
                if (!e.builtIn() || i >= 10) {
                    continue;
                }
                int[] px = io.github.georgwav.cosmetics.sol.util.MiniSky.render(e.base(), e.id(), 480, 270, 3f + i).toArgb(null);
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(480, 270, java.awt.image.BufferedImage.TYPE_INT_RGB);
                img.setRGB(0, 0, 480, 270, px, 0, 480);
                java.nio.file.Path f = dir.resolve(String.format(java.util.Locale.ROOT, "2026-10-14_21.%02d.00.png", 10 + i));
                javax.imageio.ImageIO.write(img, "png", f.toFile());
                f.toFile().setLastModified(System.currentTimeMillis() - (10 - i) * 3_600_000L);
                i++;
            }
        } catch (java.io.IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't write the gallery's sample screenshots", e);
        }
    }

    /** The crash helper shot's report: a made-up crash in a made-up mod. */
    private static io.github.georgwav.cosmetics.sol.client.CrashHelper.Crash sampleCrash() {
        try {
            java.nio.file.Path dir = io.github.georgwav.cosmetics.sol.client.CrashHelper.folder();
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path f = dir.resolve("crash-2026-10-14_21.03.11-client.txt");
            java.nio.file.Files.writeString(f, String.join("\n", "---- Minecraft Crash Report ----", "// Oops.", "",
                    "Time: 2026-10-14 21:03:11", "Description: Rendering screen", "",
                    "java.lang.NullPointerException: Cannot invoke \"String.length()\" because \"label\" is null",
                    "\tat com.example.fancymenu.Widgets.label(Widgets.java:42)",
                    "\tat net.minecraft.client.gui.screens.TitleScreen.handler$zza000$fancymenu$onRender(TitleScreen.java:9001)",
                    "\tat net.minecraft.client.gui.screens.TitleScreen.render(TitleScreen.java:300)", ""));
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().lastCrashSeen = 0L;
            var crash = io.github.georgwav.cosmetics.sol.client.CrashHelper.unseen();
            // Read: gone again, so the run's own crash check doesn't take it for a real one.
            java.nio.file.Files.deleteIfExists(f);
            return crash;
        } catch (java.io.IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't write the crash helper's sample report", e);
            return null;
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

    /** The HUD modules switched on for the HUD editor's and the HUD shots (and off again after them). */
    private static java.util.List<io.github.georgwav.cosmetics.client.Module> hudShown;
    private static java.util.List<io.github.georgwav.cosmetics.client.Setting<?>> statsShown;

    private static void hudForEditor(boolean on) {
        if (on && hudShown == null) {
            hudShown = new java.util.ArrayList<>();
            statsShown = new java.util.ArrayList<>();
            for (var m : io.github.georgwav.cosmetics.client.Modules.hud()) {
                if (!m.enabled()) {
                    m.setEnabled(true);
                    hudShown.add(m);
                }
            }
            if (!io.github.georgwav.cosmetics.client.Modules.GLINT.enabled()) {
                io.github.georgwav.cosmetics.client.Modules.GLINT.setEnabled(true);
                hudShown.add(io.github.georgwav.cosmetics.client.Modules.GLINT);
            }
            if (!io.github.georgwav.cosmetics.client.Modules.SOL_CHAT.enabled()) {
                io.github.georgwav.cosmetics.client.Modules.SOL_CHAT.setEnabled(true);
                hudShown.add(io.github.georgwav.cosmetics.client.Modules.SOL_CHAT);
            }
            // Stats with a few lines, Coordinates with the biome.
            for (var setting : io.github.georgwav.cosmetics.client.Modules.STATS.settings()) {
                if (java.util.Set.of("cps", "ping", "clock").contains(setting.id) && Boolean.FALSE.equals(setting.get())) {
                    ((io.github.georgwav.cosmetics.client.Setting.Toggle) setting).set(true);
                    statsShown.add(setting);
                }
            }
            for (var setting : io.github.georgwav.cosmetics.client.Modules.COORDINATES.settings()) {
                if (setting.id.equals("biome") && Boolean.FALSE.equals(setting.get())) {
                    ((io.github.georgwav.cosmetics.client.Setting.Toggle) setting).set(true);
                    statsShown.add(setting);
                }
            }
        } else if (!on && hudShown != null) {
            hudShown.forEach(m -> m.setEnabled(false));
            statsShown.forEach(io.github.georgwav.cosmetics.client.Setting::reset);
            hudShown = null;
            statsShown = null;
        }
    }

    /**
     * Every Sol UI hook drew in the HUD shots (a hook this version lacks would leave the game's look), and none in
     * the shot with Sol UI off.
     */
    private void checkHud(String name) {
        int[] drawn = io.github.georgwav.cosmetics.sol.hud.SolHud.DRAWN;
        java.util.List<io.github.georgwav.cosmetics.sol.hud.SolHud.Part> want = switch (name) {
            case "ui_hud_sol" -> java.util.List.of(io.github.georgwav.cosmetics.sol.hud.SolHud.Part.values()).stream()
                    .filter(p -> p != io.github.georgwav.cosmetics.sol.hud.SolHud.Part.AIR
                            && p != io.github.georgwav.cosmetics.sol.hud.SolHud.Part.MOUNT
                            && p != io.github.georgwav.cosmetics.sol.hud.SolHud.Part.TAB).toList();
            case "ui_tab" -> java.util.List.of(io.github.georgwav.cosmetics.sol.hud.SolHud.Part.TAB);
            default -> java.util.List.of();
        };
        for (var p : want) {
            if (drawn[p.ordinal()] == 0) {
                problems.add(name + ": Sol UI's " + p.id + " didn't draw");
            }
        }
        if (name.equals("ui_hud_sol") && io.github.georgwav.cosmetics.sol.glint.GlintColors.uploads == 0) {
            problems.add(name + ": Glint didn't recolour any glint texture");
        }
        if (name.equals("ui_hud_off")) {
            for (var p : io.github.georgwav.cosmetics.sol.hud.SolHud.Part.values()) {
                if (drawn[p.ordinal()] > 0) {
                    problems.add(name + ": Sol UI's " + p.id + " drew with Sol UI off");
                }
            }
        }
    }

    /**
     * The mock Cider's song reached the game: REST and its Socket.IO events, Apple's syllable lyrics (words, the
     * duet's second voice, background vocals) and the cover.
     */
    private void checkMusic(String name) {
        var now = io.github.georgwav.cosmetics.sol.music.MusicService.now();
        if (now == null || !"Sol Sunset".equals(now.title()) || !"cider".equals(now.source())) {
            problems.add(name + ": the mock Cider's song isn't playing (" + now + ")");
            return;
        }
        if (!io.github.georgwav.cosmetics.sol.music.MusicService.ciderEvents()) {
            problems.add(name + ": no Socket.IO events came from the mock Cider");
        }
        var l = io.github.georgwav.cosmetics.sol.music.MusicService.lyrics();
        boolean words = l.lines().stream().anyMatch(x -> x.words().size() > 3);
        boolean duet = l.lines().stream().anyMatch(x -> "v2".equals(x.agent()));
        boolean bg = l.lines().stream().anyMatch(io.github.georgwav.cosmetics.sol.music.Lyrics.Line::background);
        if (!l.synced() || !"apple".equals(l.source()) || !words || !duet || !bg) {
            problems.add(name + ": Apple's lyrics didn't arrive whole (" + l.source() + ", " + l.lines().size() + " lines, synced "
                    + l.synced() + ", words " + words + ", duet " + duet + ", background " + bg + ")");
        }
        if (io.github.georgwav.cosmetics.sol.music.Covers.current() == null) {
            problems.add(name + ": the cover wasn't loaded");
        }
        if (!io.github.georgwav.cosmetics.sol.theme.ThemeState.albumActive()) {
            problems.add(name + ": the album's colours aren't on the UI");
        }
        CosmeticsMod.LOGGER.info("[Sol] {}: music {} at {} ms, {} lyric lines from {}", name, now.title(), now.position(),
                l.lines().size(), l.source());
    }

    /** The shots of the HUD in play (the HUD shown, a player in survival with a boss bar and a scoreboard). */
    private static boolean inPlay(String what) {
        return what.equals("hud_sol") || what.equals("hud_off") || what.equals("tab");
    }

    /** The game mode before the HUD shots (null while they aren't on). */
    private static net.minecraft.world.level.GameType modeBefore;
    private static boolean solUiBefore;
    /** Whether the HUD shots posted chat (the chat hook then has to have been reached). */
    private static boolean chatShown;
    private static String difficultyBefore = "peaceful";

    /**
     * The HUD shots show a player in play: survival, hurt with absorption hearts, armour, an offhand shield, a full
     * hotbar with counts and a worn pickaxe, and some levels; with Sol UI ({@code ui_hud_sol}) and without
     * ({@code ui_hud_off}). All of it is put back after them.
     */
    private static void hudInPlay(boolean on, boolean vanilla) {
        Minecraft mc = Minecraft.getInstance();
        io.github.georgwav.cosmetics.sol.config.SolConfig cfg = io.github.georgwav.cosmetics.sol.config.SolConfig.get();
        if (on && modeBefore == null && mc.gameMode != null) {
            modeBefore = mc.gameMode.getPlayerMode();
            solUiBefore = cfg.solUi;
            // Hunger first (food 20 to about 15 in 2 s), so the hearts don't heal back before the shot; the golden
            // apple in hand shows the shanks it would fill.
            // Easy, so the hearts don't heal back as they do in peaceful. The game rule's name changed in 1.21.11; one
            // of the two works.
            difficultyBefore = mc.level == null ? "peaceful" : mc.level.getDifficulty().getSerializedName();
            chatShown = true;
            asPlayer(mc, List.of("gamerule announceAdvancements false", "gamerule show_advancement_messages false",
                    "difficulty easy", "gamemode survival @s", "clear @s", "effect clear @s",
                    "effect give @s minecraft:hunger 2 199 true",
                    "item replace entity @s hotbar.0 with golden_apple 5", "item replace entity @s hotbar.1 with bow[enchantment_glint_override=true]",
                    "item replace entity @s hotbar.2 with cobblestone 48", "item replace entity @s hotbar.3 with diamond_sword",
                    "item replace entity @s hotbar.4 with iron_pickaxe[damage=190]", "item replace entity @s hotbar.5 with torch 23",
                    "item replace entity @s hotbar.6 with cooked_beef 12", "item replace entity @s hotbar.7 with ender_pearl 3",
                    "item replace entity @s weapon.offhand with shield", "item replace entity @s armor.head with iron_helmet",
                    "item replace entity @s armor.chest with iron_chestplate[enchantment_glint_override=true]", "item replace entity @s armor.feet with iron_boots",
                    "xp set @s 23 levels", "xp set @s 40 points", "damage @s 7 minecraft:generic",
                    "effect give @s minecraft:absorption 120 0 true",
                    // A boss bar, a scoreboard in the sidebar and in the tab list (which a lone player only sees
                    // with one).
                    "bossbar add cosmeticsmod:show {\"text\":\"Ender Dragon\"}", "bossbar set cosmeticsmod:show players @s",
                    "bossbar set cosmeticsmod:show value 62", "bossbar set cosmeticsmod:show color purple",
                    "bossbar set cosmeticsmod:show style notched_10",
                    "scoreboard objectives add solshow dummy {\"text\":\"Sol Client\",\"color\":\"gold\"}",
                    "scoreboard objectives setdisplay sidebar solshow", "scoreboard objectives setdisplay list solshow",
                    "scoreboard players set @s solshow 23", "scoreboard players set Kills solshow 12",
                    "scoreboard players set Deaths solshow 3", "scoreboard players set Coins solshow 450",
                    // Chat: a repeat that stacks, a mention of you, an emoji.
                    "tellraw @a \"[Sol] Welcome to the showcase\"", "tellraw @a \"[Sol] Welcome to the showcase\"",
                    "tellraw @a \"<Alex> nice sky, @s! \u2764\"", "tellraw @a \"<Sam> gg\"",
                    "tellraw @a \"<@s> the sunset is on\""));
        }
        if (on) {
            cfg.solUi = !vanilla;
        } else if (modeBefore != null) {
            cfg.solUi = solUiBefore;
            asPlayer(mc, List.of("clear @s", "effect clear @s", "xp set @s 0 levels", "xp set @s 0 points",
                    "bossbar remove cosmeticsmod:show", "scoreboard objectives remove solshow",
                    "gamerule announceAdvancements true", "gamerule show_advancement_messages true",
                    "difficulty " + difficultyBefore,
                    "effect give @s minecraft:instant_health 1 10 true", "gamemode " + modeBefore.getName() + " @s"));
            modeBefore = null;
        }
    }

    /** Commands as if the player typed them (on the game's own server; {@code @s} is the player). */
    private static void asPlayer(Minecraft mc, List<String> cmds) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        String name = mc.player.getName().getString();
        server.execute(() -> {
            // Not suppressed: the game logs each command's result (and why one failed).
            var source = server.createCommandSourceStack();
            for (String c : cmds) {
                try {
                    server.getCommands().performPrefixedCommand(source, c.replace("@s", name));
                } catch (RuntimeException e) {
                    CosmeticsMod.LOGGER.warn("[Sol] HUD shot command failed: {}", c, e);
                }
            }
        });
    }

    private void finish(Minecraft mc) {
        finished = true;
        stageUi("");
        if (MOCK_CIDER) {
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumUi = albumUiWas;
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumSky = albumSkyWas;
            if (!musicWasOn) {
                io.github.georgwav.cosmetics.client.Modules.MUSIC.setEnabled(false);
            }
        }
        io.github.georgwav.cosmetics.client.modules.Zoom.hold(0.0);
        io.github.georgwav.cosmetics.sol.atmosphere.Extras.holdFlash = Float.NaN;
        lockedTime = -1L;
        crosshairOnly = false;
        if (!Guard.failed().isEmpty()) {
            problems.add("features switched off after errors: " + Guard.failed());
        }
        // Every module hook each frame passes through was reached; the chat's too (the HUD shots post messages).
        for (io.github.georgwav.cosmetics.sol.Hooks hook : io.github.georgwav.cosmetics.sol.Hooks.values()) {
            boolean expected = hook.everyFrame || hook == io.github.georgwav.cosmetics.sol.Hooks.CHAT_ADD && chatShown;
            if (expected && hook.count() == 0) {
                problems.add("the " + hook.name().toLowerCase(java.util.Locale.ROOT) + " hook was never reached");
            }
        }
        // The title shot and the Wardrobe's mannequin shot draw you without the world's own renderer.
        if (shots.stream().anyMatch(x -> x.name().equals("ui_title") || x.name().equals("ui_wardrobe_mannequin"))
                && io.github.georgwav.cosmetics.ui.Mannequin.framesPainted == 0) {
            problems.add("the mannequin (you without a world) was never drawn");
        }
        if (chatShown) {
            // The chat's search: text no message has leaves every message out, then all come back.
            io.github.georgwav.cosmetics.client.modules.SolChat.search("no message says this");
            io.github.georgwav.cosmetics.client.modules.SolChat.search("");
            if (io.github.georgwav.cosmetics.client.modules.SolChat.filteredOut == 0) {
                problems.add("the chat's search never left a message out");
            }
        }
        motionBlur(false);
        if (shots.stream().anyMatch(x -> x.name().equals("sol_motion_blur"))
                && io.github.georgwav.cosmetics.sol.render.LightPass.blurFrames == 0) {
            problems.add("motion blur never ran in its shot");
        }
        //? if >=1.21.9 {
        // The HUD shots post a line of yours: Sol Chat puts your face before it.
        if (chatShown && io.github.georgwav.cosmetics.client.modules.SolChat.headsShown == 0) {
            problems.add("Sol Chat never put a sender's face before a message");
        }
        //?}
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
