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
            "quick_mods", "quick_eclipse", "quick_presets", "quick_emotes", "quick_emote_picker", "quick_back", "menu_modules", "menu_hud",
            "menu_world", "menu_world_night", "menu_wardrobe", "menu_music", "menu_music_lyrics", "menu_mods", "menu_settings",
            "menu_settings_about",
            "menu_drawer_atmosphere", "menu_drawer_advanced_atmosphere", "menu_drawer_crosshair", "menu_drawer_camera",
            "menu_drawer_crystal_pvp", "menu_drawer_pvp_effects", "menu_drawer_keystrokes", "menu_theme", "menu_theme_night", "menu_cmdbar", "menu_profiles", "toast", "title", "pause", "death", "loading", "mods_browse", "wardrobe_grid", "wardrobe_drawer",
            "wardrobe_drawer_cape", "wardrobe_drawer_pet", "wardrobe_mannequin", "wardrobe_outfits", "wardrobe_outfit_page", "wardrobe_emotes",
            "wardrobe_skins", "cosmetics_settings", "cosmetics_settings_performance", "cosmetics_settings_compatibility",
            "cosmetics_settings_maintenance", "hud_editor", "emote_wheel", "emote_wheel_elytra", "emote_picker", "quick_wheel",
            "hud_sol", "hud_off", "tab", "chat", "inventory", "hud_styles", "hands", "hands_swing", "hands_plain", "hands_night", "knife_idle", "knife_draw", "knife_light", "knife_heavy",
            "knife_inspect", "knife_glow", "knife_pvp", "knife_trail", "knife_third", "servers", "whats_new", "packs"};
    private static final int SETTLE = 70;
    private static final int HOLD = 60;
    /** How long each benchmark preset is measured (ticks). */
    private static final int BENCH_HOLD = 400;

    /** One frame: a theme at a sun height (or a fixed day time, when {@code time} isn't -1), facing yaw/pitch. */
    /** The shot through a spyglass (a narrow field of view, as Zoomify's). */
    private static final String SPYGLASS = "sol_spyglass";
    /** Night sky shots through the spyglass, on a planet. */
    private static final String ZOOM = "sol_night_zoom_";
    private static final String EVENTS = "sol_night_events_";
    /** A shot whose view keeps turning (left and right, a little), to show the sky's things turning with the sky. */
    private static final String SPIN = "_spin";
    private static boolean holdingSpyglass;
    /** The shot from inside a closed room (built in the air above the beach, taken away after). */
    private static final String UNDERGROUND = "sol_ember_underground";
    private static boolean inRoom;
    private static int flaresBefore;

    private record Shot(String name, String theme, float altitude, boolean morning, float yaw, float pitch, long time,
                        java.util.Map<String, Float> tune) {
        Shot(String name, String theme, float altitude, boolean morning, float yaw, float pitch, long time) {
            this(name, theme, altitude, morning, yaw, pitch, time, java.util.Map.of());
        }

        Shot(String name, String theme, float altitude, boolean morning, float yaw, float pitch) {
            this(name, theme, altitude, morning, yaw, pitch, -1L);
        }
    }

    /** The theme page's edits for a "custom" shot: none for a theme's own look. */
    private static io.github.georgwav.cosmetics.sol.theme.Customisation edits(String shot) {
        var c = io.github.georgwav.cosmetics.sol.theme.Customisation.NONE;
        if (shot.endsWith("_blue")) {
            c = c.withColour("sky_top", 0xFF0E2A6E).withColour("sky_middle", 0xFF2456B8).withColour("sky_horizon", 0xFF7DB4FF)
                    .withColour("sky_glow", 0xFF8FE0FF).withColour("corona", 0xFF5FE6FF).withColour("fog", 0xFF35558F)
                    .withColour("light", 0xFFA8C8FF).withColour("accent", 0xFF5FB4FF).withColour("night_top", 0xFF050B22)
                    .withColour("night_middle", 0xFF0A1A44).withColour("night_horizon", 0xFF1F3F7A).withColour("moon", 0xFFCFE6FF)
                    .withColour("moon_halo", 0xFF6FA8FF).withColour("airglow", 0xFF2F6FB0).withColour("particles", 0xFF7FD0FF).withMoonLook("silver");
        } else if (shot.endsWith("_more")) {
            c = c.withSunSize(1.4f).withRays(24).withDensity(2f).withAmount("sun_glow", 1.6f).withAmount("bloom", 2f)
                    .withAmount("particle_size", 1.6f).withAmount("ray_length", 1.5f);
        }
        return c;
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
    /** Only the shots whose names hold one of these (comma-separated; empty for all), for quick looks. */
    private static final String ONLY = System.getProperty("cosmeticsmod.solOnly", "");

    /** CI's mock Cider is running (ci/mock_cider.py): the music shots show its song and are checked. */
    private static final boolean MOCK_CIDER = Boolean.getBoolean("cosmeticsmod.mockCider");
    private static boolean musicWasOn = true;
    private static boolean albumUiWas = true;
    private static boolean albumSkyWas;
    /** The visualizer switches as they were, put back at the end. */
    private static final java.util.Map<io.github.georgwav.cosmetics.client.Setting.Toggle, Boolean> vizWas = new java.util.HashMap<>();

    private void plan() {
        if (MOCK_CIDER) {
            // On from the start, so the song, its lyrics and its cover are there by the music shots.
            musicWasOn = io.github.georgwav.cosmetics.client.Modules.MUSIC.enabled();
            albumUiWas = io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumUi;
            albumSkyWas = io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumSky;
            for (var setting : io.github.georgwav.cosmetics.client.Modules.MUSIC.settings()) {
                if (setting instanceof io.github.georgwav.cosmetics.client.Setting.Toggle t && t.id.startsWith("viz_")) {
                    vizWas.put(t, t.get());
                }
            }
            io.github.georgwav.cosmetics.client.Modules.MUSIC.setEnabled(true);
        }
        planAll();
        if (!THEMES.isBlank()) {
            java.util.Set<String> keep = new java.util.HashSet<>(java.util.Arrays.asList(THEMES.split(",")));
            shots.removeIf(s -> !keep.contains(s.theme()) && !s.name().equals("sol_ember_sunlab"));
        }
        if (!ONLY.isBlank()) {
            List<String> only = java.util.Arrays.asList(ONLY.split(","));
            shots.removeIf(s -> only.stream().noneMatch(s.name()::contains));
        }
    }

    private void planAll() {
        if ("night".equals(SHOTS)) {
            // The night sky on its own (-Dcosmeticsmod.solShots=night): Maple's night, which keeps the world's time.
            // Day 4 is the new moon, day 0 the full moon; 18,000 is midnight, 15,000 is 21:00.
            long newMoon = 4 * 24000L;
            shots.add(new Shot("sol_night_south", "maple_afternoon", 0f, false, 0f, -40f, newMoon + 18000L));
            shots.add(new Shot("sol_night_core", "maple_afternoon", 0f, false, 45f, -18f, newMoon + 15000L));
            shots.add(new Shot("sol_night_north", "maple_afternoon", 0f, false, 180f, -30f, newMoon + 18000L));
            shots.add(new Shot("sol_night_zenith", "maple_afternoon", 0f, false, 0f, -88f, newMoon + 18000L));
            shots.add(new Shot("sol_night_full", "maple_afternoon", 0f, false, 0f, -40f, 18000L));
            shots.add(new Shot("sol_night_dusk", "maple_afternoon", 0f, false, 90f, -20f, newMoon + 13300L));
            // A waxing crescent low in the west after sunset, its dark part lit by earthshine.
            shots.add(new Shot("sol_night_crescent", "maple_afternoon", 0f, false, 90f, -10f, 5 * 24000L + 13300L));
            // Twilight: the Earth's shadow and the Belt of Venus opposite the set sun, the glow where it went down,
            // the zodiacal light after dusk, the airglow round the horizon at midnight.
            shots.add(new Shot("sol_night_belt", "maple_afternoon", 0f, false, -90f, -8f, newMoon + sphereTime(-3f, false)));
            shots.add(new Shot("sol_night_afterglow", "maple_afternoon", 0f, false, 90f, -10f, newMoon + sphereTime(-8f, false)));
            shots.add(new Shot("sol_night_zodiacal", "maple_afternoon", 0f, false, 90f, -22f, newMoon + sphereTime(-17f, false)));
            shots.add(new Shot("sol_night_airglow", "maple_afternoon", 0f, false, 180f, -6f, newMoon + 18000L));
            // The moon looks, each facing the moon: a harvest moon rising at dusk, a halo night, a pink moon in its
            // corona, the blood moon, the supermoon, the Carian moon.
            moonShot("harvest", "maple_afternoon", sphereTime(-2f, false));
            moonShot("halo", "frost_morning", 18000L);
            moonShot("pink", "sakura_dusk", 18000L);
            moonShot("blood", "maple_afternoon", 18000L);
            moonShot("supermoon", "silver_night", 18000L);
            moonShot("carian", "grace", 18000L);
            // Through a spyglass on Saturn and on Jupiter.
            for (String id : new String[]{"saturn", "jupiter"}) {
                float[] aim = aimAt(id, 0.75, 0.5f);
                shots.add(new Shot(ZOOM + id, "maple_afternoon", 0f, false, aim[0], aim[1], newMoon + 18000L));
            }
            themeNights();
            // The sky's events, each a frozen moment: a burst of meteors with a fireball, its train and an
            // earthgrazer; the two-tailed comet after dusk; a satellite flaring, ships jumping and the superlaser
            // round the Death Star.
            shots.add(new Shot(EVENTS + "meteors", "maple_afternoon", 0f, false, 20f, -40f, newMoon + 18000L));
            long dusk = sphereTime(-16f, false);
            float[] comet = aimAtComet(dusk / 24000.0, celestial(dusk));
            shots.add(new Shot(EVENTS + "comet", "maple_afternoon", 0f, false, comet[0], comet[1], newMoon + dusk));
            float[] death = aimAt("deathstar", 0.75, 0.5f);
            shots.add(new Shot(EVENTS + "ships", "maple_afternoon", 0f, false, death[0], death[1] + 3f, newMoon + 18000L));
            // The same while the view turns: the beam stays on the Death Star's dish, not on the screen.
            shots.add(new Shot(EVENTS + "ships" + SPIN, "maple_afternoon", 0f, false, death[0], death[1] + 3f, newMoon + 18000L));
            // Looking up at Halo where it's highest, the view turning: it turns with the stars round it.
            long high = highest("halo", newMoon);
            float[] halo = aimAt("halo", high / 24000.0, celestial(high));
            shots.add(new Shot("sol_night_halo" + SPIN, "maple_afternoon", 0f, false, halo[0], Math.max(-89f, halo[1] - 25f), high));
            if ("clip".equals(System.getProperty("cosmeticsmod.solEvents", ""))) {
                // The events clip: sixteen frames a fifth of a second apart, round the Death Star.
                for (int k = 0; k < 16; k++) {
                    shots.add(new Shot(EVENTS + "clip" + k, "maple_afternoon", 0f, false, death[0], death[1] + 12f, newMoon + 18000L));
                }
            }
            return;
        }
        if ("timelapse".equals(SHOTS)) {
            timelapse();
            return;
        }
        if ("custom".equals(SHOTS)) {
            // A theme's own look and the same theme recoloured on the theme page (-Dcosmeticsmod.solShots=custom):
            // Eclipse as it comes and in blue, by day and at midnight; Ember at sunset with its effects turned up.
            ThemeRegistry.Entry ec = ThemeRegistry.find("eclipse");
            float yaw = ec == null ? 90f : ec.base().show().azimuth();
            shots.add(new Shot("sol_custom_eclipse_own", "eclipse", 30f, false, yaw, -Extras.SHOW_PITCH));
            shots.add(new Shot("sol_custom_eclipse_blue", "eclipse", 30f, false, yaw, -Extras.SHOW_PITCH));
            shots.add(new Shot("sol_custom_eclipse_night_own", "eclipse", 0f, false, 0f, -30f, 18000L));
            shots.add(new Shot("sol_custom_eclipse_night_blue", "eclipse", 0f, false, 0f, -30f, 18000L));
            shots.add(new Shot("sol_custom_ember_own", "ember_solstice", 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK));
            shots.add(new Shot("sol_custom_ember_more", "ember_solstice", 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK));
            return;
        }
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
            // Through a spyglass on Deep Field's galaxy: the field of view narrows tenfold, as a zoom mod's does (Sol
            // leaves zooming to Zoomify), and the sky's planets swap to their sharper sprites.
            ThemeRegistry.Entry deepZoom = ThemeRegistry.find("deep_field");
            shots.add(new Shot(SPYGLASS, "deep_field", -60f, false, deepZoom == null ? 180f : deepZoom.base().show().azimuth(),
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
                // The HUD in play: Ember Solstice by day, facing west (the hands also at Deep Field's night).
                shots.add(inPlay(ui) && !ui.equals("hands_night")
                        ? new Shot("ui_" + ui, "ember_solstice", 40f, false, 90f, -Extras.SHOW_PITCH)
                        : new Shot("ui_" + ui, "deep_field", -60f, false, yaw, -Extras.SHOW_PITCH));
            }
        }
        if (!KNIFE_IDS.isEmpty()) {
            // Local checks with real knives (-Dcosmeticsmod.knifeIds=ns:melee/a,... and knifeActions=idle,draw,...).
            for (int i = 0; i < KNIFE_IDS.size(); i++) {
                for (String action : System.getProperty("cosmeticsmod.knifeActions", "idle").split(",")) {
                    shots.add(new Shot("ui_knife_" + action + "_k" + i, "ember_solstice", 40f, false, 90f, -Extras.SHOW_PITCH));
                }
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
                case "sunset", "dusk" -> shots.add("ember_solstice".equals(e.id())
                        ? new Shot("sol_" + e.id() + "_show", e.id(), 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK)
                        : new Shot("sol_" + e.id() + "_show", e.id(), showAltitude(e.id()), false, 90f, -Extras.SHOW_PITCH));
                case "sunrise", "dawn" -> shots.add(new Shot("sol_" + e.id() + "_show", e.id(), showAltitude(e.id()), true, -90f,
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
        // The same sunset from inside a closed stone room: none of the sun's light may reach the screen.
        shots.add(1, new Shot(UNDERGROUND, "ember_solstice", 0f, false, 90f, -Extras.SHOW_PITCH, EMBER_TICK));
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
        // The swing shot swings the sword over and over, so the outline's motion trail shows.
        if (index >= 0 && index < shots.size() && shots.get(index).name().equals("ui_hands_swing") && tick % 7 == 0) {
            //? if >=26.3 {
            /*mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
            *///?} else {
            mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            //?}
        }
        if (!built) {
            built = true;
            // A window that changes size after the game started (a launcher maximising it): -Dcosmeticsmod.resizeTo=WxH.
            String resize = System.getProperty("cosmeticsmod.resizeTo", "");
            if (resize.matches("\\d+x\\d+")) {
                String[] wh = resize.split("x");
                mc.getWindow().setWindowed(Integer.parseInt(wh[0]), Integer.parseInt(wh[1]));
            }
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
            // The motion blur shot turns the view a little each tick (the blur needs movement to show); a spin shot
            // turns it left and right.
            float yaw = s.name().equals("sol_motion_blur") ? s.yaw() + phaseTick * 2.5f
                    : s.name().endsWith(SPIN) ? s.yaw() + 30f * (float) Math.sin(phaseTick * 0.06) : s.yaw();
            mc.player.yRotO = s.name().equals("sol_motion_blur") ? yaw - 2.5f
                    : s.name().endsWith(SPIN) ? s.yaw() + 30f * (float) Math.sin((phaseTick - 1) * 0.06) : yaw;
            mc.player.setYRot(yaw);
            mc.player.setXRot(s.pitch());
            mc.player.xRotO = s.pitch();
            boolean play = s.name().startsWith("ui_") && inPlay(s.name().substring(3));
            holdSpot(mc, play, s.name());
            knifeForShot(s.name().startsWith("ui_") ? s.name().substring(3) : "", s.name());
            if (play && phaseTick == SETTLE - 20) {
                // A second ahead, so frames draw it before the check even when the game runs slowly (it shows for
                // two seconds before it fades).
                asPlayer(mc, List.of("title @s actionbar {\"text\":\"Sol Client \u00b7 action bar\",\"color\":\"gold\"}"));
            }
            if (play && phaseTick == SETTLE - 2) {
                // The toasts from the items given for the HUD shots.
                io.github.georgwav.cosmetics.sol.render.Mc.clearToasts();
            }
            // The tab list shot holds the player list key.
            mc.options.keyPlayerList.setDown(s.name().equals("ui_tab"));
            mc.options.keyUse.setDown(spyglassShot(s.name()) && phaseTick > 4);
            if (phaseTick == SETTLE && play) {
                checkHud(s.name());
            }
            if (phaseTick == SETTLE - 20 && s.name().equals("ui_packs") && io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null) {
                // Typing in the pack screen's search (Sol's before 1.21.9, the game's after).
                for (var child : io.github.georgwav.cosmetics.ui.ScreenOpener.current().children()) {
                    if (child instanceof net.minecraft.client.gui.components.EditBox box) {
                        box.setValue("default");
                    }
                }
            }
            if (phaseTick == SETTLE && s.name().equals("ui_inventory") && io.github.georgwav.cosmetics.client.modules.ScreenTweaks.undimmed == 0) {
                problems.add(s.name() + ": the inventory darkened the world behind it with Screen's darkening off");
            }
            if (phaseTick == SETTLE && s.name().equals("ui_packs")) {
                if (io.github.georgwav.cosmetics.sol.client.PackPreview.previews == 0) {
                    problems.add(s.name() + ": the pack screen didn't preview a pack");
                }
                //? if <1.21.9 {
                /*if (io.github.georgwav.cosmetics.sol.client.PackPreview.searchBoxes == 0 || io.github.georgwav.cosmetics.sol.client.PackPreview.searched == 0) {
                    problems.add(s.name() + ": Sol's pack search wasn't there or didn't search");
                }
                *///?}
            }
            if (phaseTick == SETTLE && s.name().equals("ui_whats_new") && io.github.georgwav.cosmetics.sol.ui.screens.WhatsNew.shown == 0) {
                problems.add(s.name() + ": the what's new screen didn't draw");
            }
            if (phaseTick == SETTLE && s.name().equals("ui_servers") && io.github.georgwav.cosmetics.sol.client.ServerListUpgrade.filtered == 0) {
                problems.add(s.name() + ": the server list didn't show its Survival folder");
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
            if (s.name().equals(UNDERGROUND) && phaseTick == SETTLE - 20) {
                flaresBefore = io.github.georgwav.cosmetics.sol.atmosphere.LensFlare.drawn;
            }
            if (s.name().equals(UNDERGROUND) && phaseTick == SETTLE) {
                float open = io.github.georgwav.cosmetics.sol.atmosphere.SkyOpen.get();
                if (open > 0.05f) {
                    problems.add(s.name() + ": the sky still reaches the camera in a closed room (" + open + ")");
                }
                if (io.github.georgwav.cosmetics.sol.atmosphere.LensFlare.drawn != flaresBefore) {
                    problems.add(s.name() + ": the sun's flare or glare was drawn in a closed room");
                }
            }
            if (phaseTick == SETTLE) {
                // Empty is the game's own sky: nothing of Sol's draws there.
                if (!SkyPass.drewThisFrame && !"sol_ember_sunlab".equals(s.name()) && !"empty".equals(s.theme())
                        && Guard.on(io.github.georgwav.cosmetics.sol.Feature.SKY)) {
                    problems.add(s.name() + ": the Sol sky didn't draw");
                }
                float fov = io.github.georgwav.cosmetics.sol.render.FrameInfo.fovY;
                float want = spyglassShot(s.name()) ? 7f : 70f;
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
            room(mc, s.name().equals(UNDERGROUND));
            stageUi(s.name());
            spyglass(mc, spyglassShot(s.name()));
            motionBlur(s.name().equals("sol_motion_blur"));
            ThemeState.setTheme(s.theme(), false);
            if ("custom".equals(SHOTS)) {
                ThemeState.setCustomisation(edits(s.name()));
            }
            // The Sun lab shows every layer; the showpieces use the hero frames' layers (no flare or glare).
            // Glare is a setting for playing into the sun, not part of the reference frames.
            boolean lab = s.name().endsWith("sunlab");
            boolean room = s.name().equals(UNDERGROUND);
            Atmosphere at = Atmosphere.INSTANCE;
            if (at != null) {
                // In the closed room both are on: neither may show.
                at.flare.set(lab || room);
                at.glare.set(room);
                // Monsoon's showpiece is a thunderstorm, its frame taken in a lightning flash; every other frame
                // has a clear sky.
                boolean storm = "monsoon".equals(s.theme()) && !s.name().startsWith("sol_night_") && !s.name().startsWith("sol_tl_");
                at.lockWeather.set(true);
                at.weather.set(storm ? "thunder" : "clear");
                // At night no flash, as the lab's frame: the broken cloud with the stars in its gaps.
                io.github.georgwav.cosmetics.sol.atmosphere.Extras.holdFlash = !"monsoon".equals(s.theme()) ? Float.NaN
                        : s.name().startsWith("sol_night_") ? 0f : 0.32f;
            }
            lockedTime = s.time() >= 0 ? s.time() : dayTimeFor(s.altitude(), s.morning());
            // The sky's season from the shot's time, not the test world's age, so every run shows the same stars. The
            // night sky's shots keep the lab's autumn whatever the moon's day.
            io.github.georgwav.cosmetics.sol.atmosphere.NightSky.daysOverride =
                    (s.name().startsWith("sol_night_") ? lockedTime % 24000L : lockedTime) / 24000.0;
            // The sky's events held still, so every run shows the same frame; the event shots set up their scene.
            io.github.georgwav.cosmetics.sol.atmosphere.SkyEvents.stage(s.name().startsWith(EVENTS) ? s.name().substring(EVENTS.length()) : "");
            io.github.georgwav.cosmetics.sol.atmosphere.NightSky.lookOverride =
                    s.name().startsWith("sol_night_look_") ? s.name().substring("sol_night_look_".length()) : null;
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
            // The visualizers (album sun, horizon EQ, beat rings…) only in the music sky shots, likewise.
            boolean viz = name.equals("sol_music_viz") || name.equals("sol_music_stars");
            for (var t : vizWas.keySet()) {
                t.set(viz);
            }
        }
        java.util.Arrays.fill(io.github.georgwav.cosmetics.sol.hud.SolHud.DRAWN, 0);
        io.github.georgwav.cosmetics.sol.render.Mc.hideHud(!name.equals("ui_toast") && !name.equals("ui_hud_editor") && !hud);
        hudInPlay(hud, what.equals("hud_off"));
        boolean wardrobe = what.startsWith("wardrobe_") || what.startsWith("cosmetics_settings");
        wearForWardrobe(wardrobe);
        serversForShot(what.equals("servers"));
        handsForShot(what);
        stylesForShot(what.equals("hud_styles"));
        hudForEditor(what.equals("hud_editor") || inPlay(what));
        if (!ui) {
            if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null) {
                io.github.georgwav.cosmetics.ui.ScreenOpener.open(null);
            }
            return;
        }
        if (what.startsWith("emote_") || what.startsWith("quick_emote")) {
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
        }
        if (what.startsWith("emote_")) {
            var wheel = new io.github.georgwav.cosmetics.sol.ui.screens.EmoteWheel(what.equals("emote_wheel_elytra"));
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(what.equals("emote_picker") ? wheel.stage("picker") : wheel);
            return;
        }
        screenForShot(what.equals("inventory"));
        if (what.equals("inventory")) {
            // Your inventory over the world, which Screen leaves undarkened.
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.inventory.InventoryScreen(
                    Minecraft.getInstance().player));
            return;
        }
        if (what.equals("chat")) {
            // The chat screen with Sol Chat's emoji picker open over it.
            //? if >=1.21.9 {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.ChatScreen("gg :heart: ", false));
            //?} else {
            /*io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.ChatScreen("gg :heart: "));
            *///?}
            io.github.georgwav.cosmetics.sol.chat.EmojiPicker.openForShot();
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
        if (what.equals("servers")) {
            // The server list showing its Survival folder (the servers are made up; nothing answers them).
            io.github.georgwav.cosmetics.sol.client.ServerListUpgrade.show("Survival", serversShown);
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(
                    new net.minecraft.client.gui.screens.TitleScreen()));
            return;
        }
        io.github.georgwav.cosmetics.sol.client.PackPreview.forShot = what.equals("packs");
        if (what.equals("packs")) {
            // The resource pack screen with a pack's preview (and, before 1.21.9, Sol's search over the lists).
            Minecraft mc = Minecraft.getInstance();
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(
                    mc.getResourcePackRepository(), repo -> { }, mc.getResourcePackDirectory(),
                    net.minecraft.network.chat.Component.translatable("resourcePack.title")));
            return;
        }
        if (what.equals("whats_new")) {
            // A release's notes as GitHub gives them, before installing it.
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.WhatsNew(null, "0.2.0",
                    "## Atmosphere\n- **More ambience**: leaf bits under trees, rain splashes, fireflies at night\n"
                            + "- Underwater view up to 4x the game's\n\n## Servers\n- Pings in milliseconds and players online\n"
                            + "- Folders: name a server `PvP/Hypixel` to put it in [PvP](https://example.invalid)\n\n"
                            + "## Social\n- Streamer mode hides your name, skin, the server and private messages\n", true));
            return;
        }
        if (what.equals("mods_browse")) {
            io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.ModBrowser(null, ""));
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

    /** What you wore before the Wardrobe shots (null while they aren't on). */
    private static java.util.List<String> wornBefore;

    /**
     * The Wardrobe shots show you wearing a few things (the world shots wear nothing): a cape, a hat, wings and an
     * aura when there are some. Back to what you wore after them.
     */
    private static Boolean screenOnBeforeInventory;

    /** Screen on, with the darkening behind inventories off, for the inventory shot; as it was after. */
    private static void screenForShot(boolean on) {
        var screen = io.github.georgwav.cosmetics.client.Modules.SCREEN;
        if (on == (screenOnBeforeInventory != null)) {
            return;
        }
        for (var setting : screen.settings()) {
            if (setting.id.equals("inventory_dim")) {
                if (on) {
                    ((io.github.georgwav.cosmetics.client.Setting.Toggle) setting).set(false);
                } else {
                    setting.reset();
                }
            }
        }
        if (on) {
            screenOnBeforeInventory = screen.enabled();
            screen.setEnabled(true);
        } else {
            screen.setEnabled(screenOnBeforeInventory);
            screenOnBeforeInventory = null;
        }
    }

    private static net.minecraft.client.multiplayer.ServerList serversShown;
    private static final List<net.minecraft.client.multiplayer.ServerData> SHOT_SERVERS = new java.util.ArrayList<>();
    private static boolean serversModuleWasOn;

    /** Made-up servers in the list for the server list shot, two in a Survival folder; taken out after. */
    private static void serversForShot(boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (on && serversShown == null) {
            serversModuleWasOn = io.github.georgwav.cosmetics.client.Modules.SERVERS.enabled();
            io.github.georgwav.cosmetics.client.Modules.SERVERS.setEnabled(true);
            serversShown = new net.minecraft.client.multiplayer.ServerList(mc);
            serversShown.load();
            String[][] made = {{"Survival/Home", "127.0.0.1:1"}, {"Survival/Creative world", "127.0.0.1:2"},
                    {"PvP/Arena", "127.0.0.1:3"}, {"Lobby", "127.0.0.1:4"}};
            for (String[] m : made) {
                var data = new net.minecraft.client.multiplayer.ServerData(m[0], m[1], net.minecraft.client.multiplayer.ServerData.Type.OTHER);
                SHOT_SERVERS.add(data);
                serversShown.add(data, false);
            }
            serversShown.save();
        } else if (!on && serversShown != null) {
            var list = new net.minecraft.client.multiplayer.ServerList(mc);
            list.load();
            for (int i = list.size() - 1; i >= 0; i--) {
                var d = list.get(i);
                if (SHOT_SERVERS.stream().anyMatch(m -> m.ip.equals(d.ip) && m.name.equals(d.name))) {
                    list.remove(d);
                }
            }
            list.save();
            SHOT_SERVERS.clear();
            serversShown = null;
            io.github.georgwav.cosmetics.sol.client.ServerListUpgrade.show(null, list);
            io.github.georgwav.cosmetics.client.Modules.SERVERS.setEnabled(serversModuleWasOn);
        }
    }

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
            // Stats with a few lines.
            for (var setting : io.github.georgwav.cosmetics.client.Modules.STATS.settings()) {
                if (java.util.Set.of("cps", "ping", "clock").contains(setting.id) && Boolean.FALSE.equals(setting.get())) {
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
        if (name.equals("ui_chat") && io.github.georgwav.cosmetics.sol.chat.EmojiPicker.gridsDrawn == 0) {
            problems.add(name + ": Sol Chat's emoji picker didn't draw on the chat screen");
        }
        if (name.equals("ui_tab") && io.github.georgwav.cosmetics.sol.hud.HudTweaks.pingsWritten == 0) {
            problems.add(name + ": the tab list's ping wasn't written in milliseconds");
        }
        if (name.equals("ui_hud_sol") && io.github.georgwav.cosmetics.sol.glint.GlintColors.uploads == 0) {
            problems.add(name + ": Glint didn't recolour any glint texture");
        }
        if (name.equals("ui_hands") && io.github.georgwav.cosmetics.sol.render.LightPass.handGlowFrames == 0) {
            problems.add(name + ": the hand glow didn't run");
        }
        if (name.startsWith("ui_knife") && !name.equals("ui_knife_third") && io.github.georgwav.cosmetics.melee.KnifeRenderer.drawn == 0) {
            problems.add(name + ": the knife never drew");
        }
        if (name.equals("ui_knife_glow") && io.github.georgwav.cosmetics.sol.render.LightPass.handGlowFrames == 0) {
            problems.add(name + ": the hand glow didn't run on the knife");
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
        return what.equals("hud_sol") || what.equals("hud_off") || what.equals("tab") || what.equals("chat") || what.equals("inventory")
                || what.startsWith("hands") || what.startsWith("knife") || what.equals("hud_styles");
    }

    /** The settings the HUD styles shot changed, to put back after it. */
    private static java.util.List<io.github.georgwav.cosmetics.client.Setting<?>> stylesSet;

    /**
     * The HUD styles shot: the selected slot growing instead of lifting, the hotbar and bars a little bigger, the
     * music with its big cover, Stats without a background.
     */
    @SuppressWarnings("unchecked")
    private static void stylesForShot(boolean on) {
        if (on && stylesSet == null) {
            stylesSet = new java.util.ArrayList<>();
            var ui = io.github.georgwav.cosmetics.client.Modules.SOL_UI;
            ui.hotbarSelect.set("grow");
            ui.bottomScale.set(115.0);
            stylesSet.add(ui.hotbarSelect);
            stylesSet.add(ui.bottomScale);
            for (var m : java.util.List.of(io.github.georgwav.cosmetics.client.Modules.MUSIC, io.github.georgwav.cosmetics.client.Modules.STATS)) {
                for (var st : m.settings()) {
                    if (m == io.github.georgwav.cosmetics.client.Modules.MUSIC && st.id.equals("style")) {
                        ((io.github.georgwav.cosmetics.client.Setting<String>) st).set("big_art");
                        stylesSet.add(st);
                    } else if (m == io.github.georgwav.cosmetics.client.Modules.STATS && st.id.equals("backing")) {
                        ((io.github.georgwav.cosmetics.client.Setting<Boolean>) st).set(false);
                        stylesSet.add(st);
                    }
                }
            }
        } else if (!on && stylesSet != null) {
            stylesSet.forEach(io.github.georgwav.cosmetics.client.Setting::reset);
            stylesSet = null;
        }
    }

    /** Whether the Punchy module was on before the hands shot (null while it isn't on). */
    private static Boolean punchyWas;
    private static boolean handEmpty;

    /**
     * The hands shots, with an enchanted sword in hand: the Punchy module on (Sol's arms, the glow), off (the game's
     * own hands, to compare), swinging (the outline's motion trail), and on again at night with an empty hand (the
     * dark arm with its outline).
     */
    /** Whether the swing shot slowed the game (so the slow test renderer draws the trail's copies close together). */
    private static boolean slowed;

    private static void handsForShot(String what) {
        var punchy = io.github.georgwav.cosmetics.client.Modules.PUNCHY;
        boolean on = what.startsWith("hands");
        // The swing shot runs the game at a quarter speed: this renderer draws a few frames a second, so at full speed
        // a swing would leave one copy of the outline; slowed, it leaves the fan it does at a real frame rate.
        boolean slow = what.equals("hands_swing");
        if (slow != slowed) {
            slowed = slow;
            asPlayer(Minecraft.getInstance(), List.of("tick rate " + (slow ? 5 : 20)));
        }
        if (on && punchyWas == null) {
            punchyWas = punchy.enabled();
            asPlayer(Minecraft.getInstance(), List.of("item replace entity @s weapon.mainhand with diamond_sword[enchantment_glint_override=true]"));
            io.github.georgwav.cosmetics.sol.render.LightPass.handGlowFrames = 0;
        }
        if (on) {
            punchy.setEnabled(!what.equals("hands_plain"));
            // At night the hand is empty, so the arm shows (dark, outlined).
            if (what.equals("hands_night") != handEmpty) {
                handEmpty = what.equals("hands_night");
                asPlayer(Minecraft.getInstance(), List.of("item replace entity @s weapon.mainhand with "
                        + (handEmpty ? "air" : "diamond_sword[enchantment_glint_override=true]")));
            }
        } else if (punchyWas != null) {
            punchy.setEnabled(punchyWas);
            punchyWas = null;
        }
    }

    /** Whether this run has the shot called {@code name}. */
    private boolean shot(String name) {
        return shots.stream().anyMatch(x -> x.name().equals(name));
    }

    /** Knives to shoot one by one (local checks with real packs), by cosmetic id. */
    private static final List<String> KNIFE_IDS = System.getProperty("cosmeticsmod.knifeIds", "").isBlank() ? List.of()
            : List.of(System.getProperty("cosmeticsmod.knifeIds").split(","));
    private static String knifeWorn;

    /** What was worn and whether the Punchy module was on before the knife shots (null while they aren't on). */
    private static java.util.List<String> knifeWornBefore;
    private static boolean knifePunchyWas;
    private static io.github.georgwav.cosmetics.client.Setting<Boolean> knifePvp;

    /**
     * The knife shots: the stand-in knife (or the first knife loaded) worn, a sword in hand, and the knife held at a
     * moment of an animation (its clock stopped, so the frame is the same everywhere): at rest, mid-draw, a light and a
     * heavy hit at their strike, mid-inspect; then at rest with the Punchy module on (glow, dark arms) and in PvP mode.
     */
    @SuppressWarnings("unchecked")
    private void knifeForShot(String what, String name) {
        Minecraft mc = Minecraft.getInstance();
        var punchy = io.github.georgwav.cosmetics.client.Modules.PUNCHY;
        var config = io.github.georgwav.cosmetics.config.CosmeticsConfig.get();
        boolean on = what.startsWith("knife");
        // A local run's knife (ui_knife_<action>_k<n>); no other shot's name is read this way.
        int pick = on && what.matches(".*_k\\d+") ? Integer.parseInt(what.substring(what.lastIndexOf("_k") + 2)) : -1;
        if (pick >= 0) {
            what = what.substring(0, what.lastIndexOf("_k"));
        }
        String wanted = pick >= 0 && pick < KNIFE_IDS.size() ? KNIFE_IDS.get(pick) : null;
        if (on && (knifeWornBefore == null || wanted != null && !wanted.equals(knifeWorn))) {
            java.util.List<String> worn = config.localEquipped();
            if (knifeWornBefore == null) {
                knifeWornBefore = new java.util.ArrayList<>(worn);
                knifePunchyWas = punchy.enabled();
            }
            String knife = wanted;
            for (var c : io.github.georgwav.cosmetics.model.CosmeticRegistry.all()) {
                if (wanted == null && c.slot() == io.github.georgwav.cosmetics.model.CosmeticSlot.MELEE
                        && (knife == null || c.id().startsWith(io.github.georgwav.cosmetics.melee.StandInKnife.NAMESPACE + ":"))) {
                    knife = c.id();
                }
            }
            if (knife != null && io.github.georgwav.cosmetics.model.CosmeticRegistry.get(knife) == null) {
                problems.add(name + ": no knife " + knife);
                knife = null;
            }
            knifeWorn = knife;
            if (knife == null) {
                problems.add(name + ": no knife loaded (the stand-in pack didn't mount)");
            } else {
                worn.removeIf(id -> {
                    var c = io.github.georgwav.cosmetics.model.CosmeticRegistry.get(id);
                    return c != null && c.slot() == io.github.georgwav.cosmetics.model.CosmeticSlot.MELEE;
                });
                worn.add(knife);
                io.github.georgwav.cosmetics.source.EquippedCosmetics.invalidate();
            }
            asPlayer(mc, List.of("item replace entity @s weapon.mainhand with diamond_sword"));
            if (pick >= 0) {
                // The local runs with many knives are long: no mob comes to end them.
                asPlayer(mc, List.of("difficulty peaceful"));
            }
            io.github.georgwav.cosmetics.melee.KnifeRenderer.drawn = 0;
            for (var st : punchy.settings()) {
                if (st.id.equals("pvp")) {
                    knifePvp = (io.github.georgwav.cosmetics.client.Setting<Boolean>) st;
                }
            }
        } else if (!on && knifeWornBefore != null) {
            java.util.List<String> worn = config.localEquipped();
            worn.clear();
            worn.addAll(knifeWornBefore);
            knifeWornBefore = null;
            knifeWorn = null;
            mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
            io.github.georgwav.cosmetics.source.EquippedCosmetics.invalidate();
            punchy.setEnabled(knifePunchyWas);
            if (knifePvp != null) {
                knifePvp.reset();
            }
            io.github.georgwav.cosmetics.melee.KnifeRenderer.release();
            io.github.georgwav.cosmetics.melee.KnifeRenderer.timeScale(1f);
        }
        var camera = what.equals("knife_third") ? net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                : net.minecraft.client.CameraType.FIRST_PERSON;
        if (on && mc.options.getCameraType() != camera) {
            mc.options.setCameraType(camera);
        }
        if (!on) {
            return;
        }
        if (phaseTick == SETTLE && what.equals("knife_third") && io.github.georgwav.cosmetics.melee.KnifeThirdPerson.drawn == 0) {
            problems.add(name + ": the knife wasn't drawn in third person");
        }
        boolean module = what.equals("knife_glow") || what.equals("knife_pvp") || what.equals("knife_trail");
        // The trail shot: light attacks over and over in slow motion (this renderer draws a few frames a second).
        io.github.georgwav.cosmetics.melee.KnifeRenderer.timeScale(what.equals("knife_trail") ? 0.2f : 1f);
        if (what.equals("knife_trail") && phaseTick > 10 && phaseTick % 12 == 0) {
            io.github.georgwav.cosmetics.melee.KnifeRenderer.play("light");
        }
        if (punchy.enabled() != module) {
            punchy.setEnabled(module);
        }
        if (knifePvp != null && knifePvp.get() != what.equals("knife_pvp")) {
            knifePvp.set(what.equals("knife_pvp"));
        }
        if (phaseTick == 1) {
            io.github.georgwav.cosmetics.melee.KnifeRenderer.release();
            io.github.georgwav.cosmetics.sol.render.LightPass.handGlowFrames = 0;
        }
        if (phaseTick == SETTLE - 25 && what.equals("knife_spam")) {
            // Spam-inspect: an inspect, then the key again mid-way (a fresh one at once, below).
            io.github.georgwav.cosmetics.melee.KnifeRenderer.play("inspect");
        }
        if (phaseTick == SETTLE - 17 && what.equals("knife_light")) {
            // A real attack click (the knife's input hook), then the light attack held at its strike. The key by its
            // name: the left button's number differs from 26.3 on (SDL).
            net.minecraft.client.KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey("key.mouse.left"));
        }
        if (phaseTick == SETTLE - 15 && !what.equals("knife_trail")) {
            switch (what) {
                case "knife_draw" -> io.github.georgwav.cosmetics.melee.KnifeRenderer.hold("draw", 0.25f);
                case "knife_light" -> io.github.georgwav.cosmetics.melee.KnifeRenderer.hold("light", 0.15f);
                case "knife_heavy" -> io.github.georgwav.cosmetics.melee.KnifeRenderer.hold("heavy", 0.15f);
                case "knife_inspect" -> io.github.georgwav.cosmetics.melee.KnifeRenderer.hold("inspect", 0.5f);
                case "knife_spam" -> io.github.georgwav.cosmetics.melee.KnifeRenderer.hold("inspect", 0.3f);
                default -> io.github.georgwav.cosmetics.melee.KnifeRenderer.hold("idle", 0f);
            }
        }
    }

    /** Where the player stands for the in-play shots (null while they aren't on). */
    private static net.minecraft.world.phys.Vec3 playSpot;

    /**
     * Keeps the player on their spot through the in-play shots. They're in survival there, and in one CI run on
     * 1.21.4 the player walked off into the lake during the hands shots (nothing of ours pressed a key; the cause
     * wasn't found), which put every shot after it under water. Put back, and said in the log.
     */
    private void holdSpot(Minecraft mc, boolean play, String name) {
        if (!play || mc.player == null) {
            playSpot = null;
            return;
        }
        if (playSpot == null) {
            if (phaseTick > 5) {
                playSpot = mc.player.position();
            }
            return;
        }
        double moved = mc.player.position().distanceTo(playSpot);
        if (moved > 0.5 && phaseTick % 20 == 0) {
            CosmeticsMod.LOGGER.info("[Sol] {}: the player had moved {} blocks from their spot; put back", name,
                    String.format(java.util.Locale.ROOT, "%.1f", moved));
            asPlayer(mc, List.of(String.format(java.util.Locale.ROOT, "tp @s %.3f %.3f %.3f", playSpot.x, playSpot.y, playSpot.z)));
            mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        }
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
            // Easy, so the hearts don't heal back as they do in peaceful, with no mobs spawning (a slime from the flat
            // world once ended a long run). The game rules' names changed in 1.21.11; one of each pair works.
            difficultyBefore = mc.level == null ? "peaceful" : mc.level.getDifficulty().getSerializedName();
            chatShown = true;
            asPlayer(mc, List.of("gamerule announceAdvancements false", "gamerule show_advancement_messages false",
                    "gamerule doMobSpawning false", "gamerule spawn_mobs false", "kill @e[type=minecraft:slime]",
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
                    // Chat: a repeat that stacks, an emoji.
                    "tellraw @a \"[Sol] Welcome to the showcase\"", "tellraw @a \"[Sol] Welcome to the showcase\"",
                    "tellraw @a \"<Alex> nice sky, @s! \u2764\"", "tellraw @a \"<Sam> gg\""));
        }
        if (on) {
            cfg.solUi = !vanilla;
            // The enchanted bow and chestplate show Glint's outline round their shape too.
            glintInventory("both");
        } else if (modeBefore != null) {
            glintInventory("glint");
            cfg.solUi = solUiBefore;
            asPlayer(mc, List.of("clear @s", "effect clear @s", "xp set @s 0 levels", "xp set @s 0 points",
                    "bossbar remove cosmeticsmod:show", "scoreboard objectives remove solshow",
                    "gamerule announceAdvancements true", "gamerule show_advancement_messages true",
                    "gamerule doMobSpawning true", "gamerule spawn_mobs true", "difficulty " + difficultyBefore,
                    "effect give @s minecraft:instant_health 1 10 true", "gamemode " + modeBefore.getName() + " @s"));
            modeBefore = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void glintInventory(String how) {
        for (io.github.georgwav.cosmetics.client.Setting<?> st : io.github.georgwav.cosmetics.client.Modules.GLINT.settings()) {
            if (st.id.equals("inventory")) {
                ((io.github.georgwav.cosmetics.client.Setting<String>) st).set(how);
            }
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

    /** A spyglass into your hand (used while the shot holds the use key), or out of it again. */
    /**
     * Every theme's night as the Night Sky lab's theme table has it: the aurora over the north-east, the nebulae
     * overhead, the rune, Deep Field's Milky Way on a new moon, Silver Night's haze along the horizon, Monsoon's broken
     * cloud lit by lightning; the rest facing their moon as it climbs in the east at 21:00 on the full moon's night.
     */
    private void themeNights() {
        for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
            if (!e.builtIn() || "empty".equals(e.id())) {
                continue;
            }
            String id = e.id(), name = "sol_night_theme_" + id;
            switch (id) {
                // The lab's cameras (its azimuths are from north through east, the game's from south through west).
                case "aurora" -> shots.add(new Shot(name, id, 0f, false, 222f, -30f, 18000L));
                // Sakura's nebulae, looking up toward them.
                case "sakura_dusk" -> shots.add(new Shot(name, id, 0f, false, 2f, -46f, 18000L));
                case "grace" -> shots.add(new Shot(name, id, 0f, false, 342f, -22f, 18000L));
                case "deep_field" -> shots.add(new Shot(name, id, 0f, false, 45f, -18f, 4 * 24000L + 15000L));
                case "silver_night" -> shots.add(new Shot(name, id, 0f, false, 20f, -10f, 18000L));
                case "monsoon" -> shots.add(new Shot(name, id, 0f, false, 0f, -35f, 18000L));
                default -> {
                    float[] m = new float[3];
                    io.github.georgwav.cosmetics.sol.atmosphere.NightSky.moonAt(e.base(), 15000 / 24000.0, celestial(15000L), 0, null, m);
                    float yaw = (float) Math.toDegrees(Math.atan2(-m[0], m[2])), alt = (float) Math.toDegrees(Math.asin(m[1]));
                    shots.add(new Shot(name, id, 0f, false, yaw, -Math.max(4f, alt - 8f), 15000L));
                }
            }
        }
    }

    /** Each theme through the day from one place, facing its showpiece: the eight times of day in order. */
    private void timelapse() {
        String[] phases = {"predawn", "sunrise", "morning", "noon", "afternoon", "sunset", "bluehour", "night"};
        long[] times = {sphereTime(-8.5f, true), sphereTime(3f, true), sphereTime(17f, true), 6000L, sphereTime(17f, false),
                sphereTime(3f, false), sphereTime(-8.5f, false), 18000L};
        for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
            if (!e.builtIn() || "empty".equals(e.id())) {
                continue;
            }
            for (int i = 0; i < phases.length; i++) {
                shots.add(new Shot("sol_tl_" + e.id() + "_" + i + phases[i], e.id(), 0f, false, e.base().show().azimuth(),
                        -Extras.SHOW_PITCH, times[i]));
            }
        }
    }

    /** A shot facing the moon in a look, on the full moon's night (day 0) at a day time. */
    private void moonShot(String look, String theme, long dayTime) {
        ThemeRegistry.Entry e = ThemeRegistry.find(theme);
        if (e == null) {
            return;
        }
        float tod = celestial(dayTime);
        // A theme holding its showpiece shows its own time.
        if (e.base().show().hold()) {
            tod = e.base().show().heldTime();
        }
        float[] m = new float[3];
        io.github.georgwav.cosmetics.sol.atmosphere.NightSky.moonAt(e.base(), (dayTime % 24000L) / 24000.0, tod, 0, look, m);
        float yaw = (float) Math.toDegrees(Math.atan2(-m[0], m[2])), alt = (float) Math.toDegrees(Math.asin(m[1]));
        shots.add(new Shot("sol_night_look_" + look, theme, 0f, false, yaw, -Math.max(4f, alt - 8f), dayTime));
    }

    /** The game's sun angle (0 noon) for a day time, as the game eases it. */
    private static float celestial(long dayTime) {
        double d = dayTime / 24000.0 - 0.25;
        d -= Math.floor(d);
        double e = 0.5 - Math.cos(d * Math.PI) / 2.0;
        return (float) ((d * 2.0 + e) / 3.0);
    }

    /** The day time the sky sphere's sun stands at an altitude (at the default sky latitude). */
    private static long sphereTime(float sunAlt, boolean morning) {
        double c = Math.sin(Math.toRadians(sunAlt)) / Math.cos(Math.toRadians(io.github.georgwav.cosmetics.sol.sky.SkySphere.DEFAULT_LATITUDE));
        double hourAngle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c))));
        // The game's own path puts its sun at 90° less the hour angle.
        return dayTimeFor((float) (90.0 - hourAngle), morning);
    }

    private static boolean spyglassShot(String name) {
        return name.equals(SPYGLASS) || name.startsWith(ZOOM);
    }

    /** The view (yaw, pitch) onto the comet (44° east of the sun along the ecliptic, 11° above it). */
    private static float[] aimAtComet(double days, float timeOfDay) {
        io.github.georgwav.cosmetics.sol.sky.SkySphere sky = new io.github.georgwav.cosmetics.sol.sky.SkySphere();
        sky.update(days, timeOfDay, 0, io.github.georgwav.cosmetics.sol.sky.SkySphere.DEFAULT_LATITUDE,
                io.github.georgwav.cosmetics.sol.sky.SkySphere.DEFAULT_YEAR);
        double[] eq = io.github.georgwav.cosmetics.sol.sky.SkySphere.ecl(sky.lamSun + 44, 11, new double[3]);
        float[] w = new float[3];
        sky.toWorld(eq[0], eq[1], eq[2], w);
        // A little above it, so the tails rising away from the set sun are in the frame.
        return new float[]{(float) Math.toDegrees(Math.atan2(-w[0], w[2])), -(float) Math.toDegrees(Math.asin(w[1])) - 8f};
    }

    /** The day time on the night from {@code day} (a day's first tick) when a planet stands highest. */
    private static long highest(String id, long day) {
        long best = day + 18000L;
        float top = Float.MAX_VALUE;
        for (long t = day + 13000L; t <= day + 23000L; t += 250L) {
            float pitch = aimAt(id, t / 24000.0, celestial(t))[1];
            if (pitch < top) {
                top = pitch;
                best = t;
            }
        }
        return best;
    }

    /** The view (yaw, pitch) onto a planet on a day of the sky's year at a time of day (0 noon). */
    private static float[] aimAt(String id, double days, float timeOfDay) {
        io.github.georgwav.cosmetics.sol.sky.SkySphere sky = new io.github.georgwav.cosmetics.sol.sky.SkySphere();
        sky.update(days, timeOfDay, 0, io.github.georgwav.cosmetics.sol.sky.SkySphere.DEFAULT_LATITUDE,
                io.github.georgwav.cosmetics.sol.sky.SkySphere.DEFAULT_YEAR);
        double[] eq = io.github.georgwav.cosmetics.sol.sky.Bodies.place(io.github.georgwav.cosmetics.sol.sky.Bodies.find(id),
                sky.lamSun, new double[3]);
        float[] w = new float[3];
        sky.toWorld(eq[0], eq[1], eq[2], w);
        return new float[]{(float) Math.toDegrees(Math.atan2(-w[0], w[2])), -(float) Math.toDegrees(Math.asin(w[1]))};
    }

    private static void spyglass(Minecraft mc, boolean in) {
        if (in == holdingSpyglass) {
            return;
        }
        holdingSpyglass = in;
        if (!in) {
            mc.options.keyUse.setDown(false);
        }
        asPlayer(mc, List.of("item replace entity @s weapon.mainhand with minecraft:" + (in ? "spyglass" : "air")));
    }

    /**
     * Into a closed stone room 24 blocks above where you stand (nothing of the scene to put back), or back down and
     * the room taken away.
     */
    private static void room(Minecraft mc, boolean in) {
        if (in == inRoom) {
            return;
        }
        inRoom = in;
        asPlayer(mc, in
                ? List.of("execute at @s run fill ~-3 ~23 ~-3 ~3 ~29 ~3 stone hollow", "execute at @s run tp @s ~ ~24 ~")
                : List.of("execute at @s run tp @s ~ ~-24 ~", "execute at @s run fill ~-3 ~23 ~-3 ~3 ~29 ~3 air"));
    }

    private void finish(Minecraft mc) {
        finished = true;
        room(mc, false);
        stageUi("");
        if (MOCK_CIDER) {
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumUi = albumUiWas;
            io.github.georgwav.cosmetics.sol.config.SolConfig.get().music.albumSky = albumSkyWas;
            vizWas.forEach(io.github.georgwav.cosmetics.client.Setting.Toggle::set);
            if (!musicWasOn) {
                io.github.georgwav.cosmetics.client.Modules.MUSIC.setEnabled(false);
            }
        }
        spyglass(mc, false);
        io.github.georgwav.cosmetics.sol.atmosphere.Extras.holdFlash = Float.NaN;
        lockedTime = -1L;
        io.github.georgwav.cosmetics.sol.atmosphere.NightSky.daysOverride = Double.NaN;
        io.github.georgwav.cosmetics.sol.atmosphere.NightSky.lookOverride = null;
        crosshairOnly = false;
        if (!Guard.failed().isEmpty()) {
            problems.add("features switched off after errors: " + Guard.failed());
        }
        // The knife's own cost, steady: at most 0.2 ms a frame, and nothing allocated per frame.
        java.util.List<io.github.georgwav.cosmetics.melee.Knife> benchKnives = new java.util.ArrayList<>();
        for (String id : KNIFE_IDS.isEmpty() ? List.of(io.github.georgwav.cosmetics.melee.StandInKnife.NAMESPACE + ":melee/"
                + io.github.georgwav.cosmetics.melee.StandInKnife.ID) : KNIFE_IDS) {
            var k = io.github.georgwav.cosmetics.melee.KnifeRegistry.get(id);
            if (k != null) {
                benchKnives.add(k);
            }
        }
        if (shot("ui_knife_idle") || !KNIFE_IDS.isEmpty()) {
            for (var k : benchKnives) {
                double[] cost = io.github.georgwav.cosmetics.melee.KnifeBench.run(k, 20_000, 3_000);
                CosmeticsMod.LOGGER.info("[Sol] knife bench, steady: {}: {} ms and {} bytes a frame", k.id,
                        String.format(java.util.Locale.ROOT, "%.4f", cost[0]), String.format(java.util.Locale.ROOT, "%.1f", cost[1]));
                if (cost[0] > 0.2) {
                    problems.add("knife " + k.id + " took " + String.format(java.util.Locale.ROOT, "%.3f", cost[0])
                            + " ms a frame (budget 0.2)");
                }
                if (cost[1] > 1.0) {
                    problems.add("knife " + k.id + " allocated " + String.format(java.util.Locale.ROOT, "%.0f", cost[1]) + " bytes a frame");
                }
            }
        }
        // And in the shots' own frames (this software renderer, so for information): its hand-over and vertices.
        if (io.github.georgwav.cosmetics.melee.KnifeRenderer.benchFrames > 0) {
            int frames = io.github.georgwav.cosmetics.melee.KnifeRenderer.benchFrames;
            double ms = io.github.georgwav.cosmetics.melee.KnifeRenderer.benchNanos / 1.0e6 / frames;
            double bytes = (double) io.github.georgwav.cosmetics.melee.KnifeRenderer.benchBytes / frames;
            CosmeticsMod.LOGGER.info("[Sol] knife bench, in the shots: {} ms and {} bytes a frame over {} frames (with the game's hand-over)",
                    String.format(java.util.Locale.ROOT, "%.4f", ms), String.format(java.util.Locale.ROOT, "%.1f", bytes), frames);
        }
        // Every module hook each frame passes through was reached; the chat's too (the HUD shots post messages).
        for (io.github.georgwav.cosmetics.sol.Hooks hook : io.github.georgwav.cosmetics.sol.Hooks.values()) {
            boolean expected = hook.everyFrame || hook == io.github.georgwav.cosmetics.sol.Hooks.CHAT_ADD && chatShown
                    || hook == io.github.georgwav.cosmetics.sol.Hooks.KNIFE_PACKS
                    || hook == io.github.georgwav.cosmetics.sol.Hooks.KNIFE_HAND && shot("ui_knife_idle")
                    || hook == io.github.georgwav.cosmetics.sol.Hooks.KNIFE_INPUT && shot("ui_knife_light")
                    || hook == io.github.georgwav.cosmetics.sol.Hooks.KNIFE_THIRD && shot("ui_knife_third");
            if (expected && hook.count() == 0) {
                problems.add("the " + hook.name().toLowerCase(java.util.Locale.ROOT) + " hook was never reached");
            }
        }
        // The title shot and the Wardrobe's mannequin shot draw you without the world's own renderer.
        if (shots.stream().anyMatch(x -> x.name().equals("ui_title") || x.name().equals("ui_wardrobe_mannequin"))
                && io.github.georgwav.cosmetics.ui.Mannequin.framesPainted == 0) {
            problems.add("the mannequin (you without a world) was never drawn");
        }
        motionBlur(false);
        if (shots.stream().anyMatch(x -> x.name().equals("sol_motion_blur"))
                && io.github.georgwav.cosmetics.sol.render.LightPass.blurFrames == 0) {
            problems.add("motion blur never ran in its shot");
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
