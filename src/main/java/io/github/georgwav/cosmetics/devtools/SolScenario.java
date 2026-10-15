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
 * world (sand dunes and a lake to the west, hills, trees), then shows each theme at its showpiece moment, facing
 * the way its showpiece frame faces, and writes a marker ({@code cosmeticsmod-phase-sol_<theme>_show}) so the CI
 * script screenshots it. Checks that the Sol sky drew; the result goes to the log and
 * {@code cosmeticsmod-sol-results.json}.
 */
public final class SolScenario {
    private static final String MODE = System.getProperty("cosmeticsmod.solScenario", "");
    private static final int SETTLE = 70;
    private static final int HOLD = 60;

    private record Shot(String name, String theme, float altitude, boolean morning, float yaw, float pitch) {
    }

    private final List<Shot> shots = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private int tick;
    private int index = -1;
    private int phaseTick;
    private boolean built;
    private boolean finished;

    /** The time the scenario holds (0 is 06:00), or -1. Read by the time lock. */
    public static volatile long lockedTime = -1L;

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

    private void plan() {
        for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
            ThemeSpec s = e.base();
            if (!e.builtIn() || "empty".equals(e.id())) {
                continue;
            }
            String phase = s.show().phase();
            float yaw = s.show().azimuth();
            if ("silver_night".equals(e.id())) {
                // Facing the huge moon as it rises in the east, the sun 25° below the western horizon.
                shots.add(new Shot("sol_" + e.id() + "_show", e.id(), -25f, false, -90f, -Extras.SHOW_PITCH));
                continue;
            }
            switch (phase) {
                case "night" -> shots.add(new Shot("sol_" + e.id() + "_show", e.id(), -60f, false, yaw, -Extras.SHOW_PITCH));
                case "dusk" -> shots.add(new Shot("sol_" + e.id() + "_show", e.id(), showAltitude(e.id()), false, 90f,
                        -Extras.SHOW_PITCH));
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
        // The Sun lab's framing: Ember with the sun at e = .18.
        shots.add(0, new Shot("sol_ember_sunlab", "ember_solstice", 0.18f * 38f, false, 90f, -Extras.SHOW_PITCH));
        if (!"reduced".equals(MODE)) {
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
            case "sakura_dusk" -> 2f;
            case "maple_afternoon" -> 4f;
            case "firefly_meadow" -> 0.5f;
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
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            io.github.georgwav.cosmetics.sol.render.Mc.hideHud(true);
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
                if (!SkyPass.drewThisFrame && !"sol_ember_sunlab".equals(s.name()) && Guard.on(io.github.georgwav.cosmetics.sol.Feature.SKY)) {
                    problems.add(s.name() + ": the Sol sky didn't draw");
                }
                marker(mc, s.name());
            }
        }
        if (index < 0 || phaseTick >= SETTLE + HOLD) {
            index++;
            phaseTick = 0;
            if (index >= shots.size()) {
                finish(mc);
                return;
            }
            Shot s = shots.get(index);
            ThemeState.setTheme(s.theme(), false);
            // The Sun lab shows every layer; the showpieces use the hero frames' layers (no flare or glare).
            boolean lab = s.name().endsWith("sunlab");
            Atmosphere at = Atmosphere.INSTANCE;
            if (at != null) {
                at.flare.set(lab);
                at.glare.set(lab);
            }
            lockedTime = dayTimeFor(s.altitude(), s.morning());
        }
    }

    private void finish(Minecraft mc) {
        finished = true;
        lockedTime = -1L;
        if (!Guard.failed().isEmpty()) {
            problems.add("features switched off after errors: " + Guard.failed());
        }
        boolean passed = problems.isEmpty();
        CosmeticsMod.LOGGER.info("[Sol] Scenario {}: {}", passed ? "PASSED" : "FAILED", problems);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("cosmeticsmod-sol-results.json"),
                    "{\"passed\":" + passed + ",\"problems\":" + new com.google.gson.Gson().toJson(problems) + "}");
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't write the scenario result", e);
        }
        if (Boolean.parseBoolean(System.getProperty("cosmeticsmod.exitWhenDone", "true"))) {
            mc.stop();
        }
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

    /** Sand dunes and a lake to the west, grass hills and trees behind them (the integrated server builds it). */
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
            String[] cmds = {
                    "gamerule doDaylightCycle false",
                    "gamerule doWeatherCycle false",
                    "weather clear",
                    // Sand under the player and toward the west, with dunes.
                    "fill " + (x - 60) + " " + g + " " + (z - 40) + " " + (x + 6) + " " + g + " " + (z + 40) + " sand",
                    "fill " + (x - 18) + " " + (g + 1) + " " + (z - 30) + " " + (x - 10) + " " + (g + 1) + " " + (z - 12) + " sand",
                    "fill " + (x - 16) + " " + (g + 2) + " " + (z - 26) + " " + (x - 12) + " " + (g + 2) + " " + (z - 16) + " sand",
                    "fill " + (x - 14) + " " + (g + 1) + " " + (z + 12) + " " + (x - 6) + " " + (g + 1) + " " + (z + 30) + " sand",
                    // A lake.
                    "fill " + (x - 42) + " " + g + " " + (z - 14) + " " + (x - 22) + " " + g + " " + (z + 14) + " water",
                    // Hills further out.
                    "fill " + (x - 62) + " " + g + " " + (z - 40) + " " + (x - 54) + " " + (g + 6) + " " + (z - 6) + " grass_block",
                    "fill " + (x - 66) + " " + g + " " + (z + 4) + " " + (x - 56) + " " + (g + 10) + " " + (z + 34) + " stone",
                    "fill " + (x - 64) + " " + (g + 10) + " " + (z + 6) + " " + (x - 58) + " " + (g + 12) + " " + (z + 30) + " grass_block",
                    "fill " + (x - 50) + " " + g + " " + (z + 20) + " " + (x - 44) + " " + (g + 3) + " " + (z + 40) + " grass_block",
                    // Grass patches for trees and fireflies.
                    "fill " + (x - 12) + " " + g + " " + (z + 22) + " " + (x + 6) + " " + g + " " + (z + 40) + " grass_block",
                    "fill " + (x - 12) + " " + g + " " + (z - 40) + " " + (x + 6) + " " + g + " " + (z - 22) + " grass_block",
            };
            var source = server.createCommandSourceStack().withSuppressedOutput();
            for (String c : cmds) {
                try {
                    server.getCommands().performPrefixedCommand(source, c);
                } catch (RuntimeException e) {
                    CosmeticsMod.LOGGER.warn("[Sol] Scene command failed: {}", c, e);
                }
            }
            String[][] trees = {{"cherry", "-6", "28"}, {"oak", "2", "34"}, {"birch", "-8", "-30"}, {"oak", "4", "-26"},
                    {"cherry", "-46", "30"}, {"oak", "-52", "-20"}};
            for (String[] t : trees) {
                try {
                    server.getCommands().performPrefixedCommand(source, "place feature minecraft:" + t[0] + " "
                            + (x + Integer.parseInt(t[1])) + " " + y + " " + (z + Integer.parseInt(t[2])));
                } catch (RuntimeException e) {
                    CosmeticsMod.LOGGER.warn("[Sol] Tree failed: {}", t[0], e);
                }
            }
        });
        Modules.save();
    }
}
