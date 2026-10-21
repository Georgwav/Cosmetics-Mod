package io.github.georgwav.cosmetics.devtools;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.WorldOut;
import io.github.georgwav.cosmetics.sol.fx.effects.HitEffects;
import io.github.georgwav.cosmetics.sol.fx.effects.KillEffects;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The effects' phase of the Sol scenario (section D): each effect and style staged on a dummy in front of the camera,
 * its clock frozen at six moments of its life and each frame grabbed by the game itself (fx-<case>-<k>.png, which CI
 * stitches into a contact sheet), a frame with nothing playing for the comparison, the key moment again on a second
 * preset (the colour check), and the effect played live to see the pool empty a second after it ends. One kill goes
 * through the real path: a pig you hit, killed by the server, must start a kill effect. Results go to
 * cosmeticsmod-fx-results.json; the image checks are CI's.
 */
final class FxScenario {
    /** The two presets of the colour check. */
    static final String PRESET_A = "frost_morning", PRESET_B = "ember_solstice";

    /**
     * One staged effect: its name, module, how to set it up, where the camera stands, what CI checks. {@code kinds} is
     * the prefix of its effects' ids in the pool (null for one drawn on the HUD, which shows the HUD for its frames);
     * a grey one is also played behind a wall, where it mustn't show.
     */
    record Case(String name, FxModule module, Runnable setup, float distance, float pitch, boolean colour, boolean grey, String kinds) {
        boolean hud() {
            return kinds == null;
        }
    }

    private final List<Case> cases = new ArrayList<>();
    private final List<String> problems;
    private final JsonArray results = new JsonArray();
    private int index = -1, step, wait, bFrame;
    private float t0, life;
    private boolean done, killStarted, killChecked;
    private String themeWas;
    private String qualityWas;
    private boolean particlesWas;
    private int accentA, accentB;
    private FxPreview.Dummy dummy;
    private int zombie = Integer.MIN_VALUE;
    private int killTicks, cleanLeft;
    /** The camera for the case being staged. */
    static float pitch = 10f;

    FxScenario(List<String> problems) {
        this.problems = problems;
        KillEffects kill = Modules.KILL_EFFECTS;
        for (String style : KillEffects.STYLES) {
            float distance = 4f, p = 4f;
            switch (style) {
                case "firework" -> {
                    distance = 9f;
                    p = -25f;
                }
                case "sky_laser" -> {
                    distance = 7f;
                    p = -10f;
                }
                case "shockwave" -> {
                    distance = 8f;
                    p = 24f;
                }
                case "confetti" -> {
                    distance = 4.5f;
                    p = -8f;
                }
                case "burst" -> {
                    distance = 5f;
                    p = 14f;
                }
                default -> {
                }
            }
            // The copy's own skin (Shatter) and the burning copy's orange aren't the preset's colour.
            boolean colour = !style.equals("shatter") && !style.equals("burning_copy");
            cases.add(new Case("kill_" + style, kill, () -> {
                kill.fx.random.set(false);
                kill.fx.style.set(style);
                kill.fx.colourMode.set("theme");
            }, distance, p, colour, false, "kill_" + style));
        }
        HitEffects hit = Modules.HIT_EFFECTS;
        for (String style : HitEffects.PARTICLE_STYLES) {
            cases.add(new Case("hit_" + style, hit, () -> {
                only(hit, hit.particles.on);
                hit.particles.random.set(false);
                hit.particles.style.set(style);
            }, 3.2f, 8f, true, false, "hit_particles"));
        }
        cases.add(new Case("hit_bubbles", hit, () -> only(hit, hit.bubbles.on), 3.2f, 8f, true, false, "hit_bubble"));
        cases.add(new Case("hit_words", hit, () -> only(hit, hit.words.on), 3.6f, 2f, true, false, "hit_word"));
        cases.add(new Case("hit_damage", hit, () -> only(hit, hit.damage.on), 3.6f, -6f, true, true, "hit_damage"));
        cases.add(new Case("hit_marker", hit, () -> only(hit, hit.marker.on), 3.2f, 8f, true, false, null));
    }

    /** Hit Effects with only one of its effects on, in the preset's colour. */
    private static void only(HitEffects hit, io.github.georgwav.cosmetics.client.Setting.Toggle on) {
        for (var fs : List.of(hit.particles, hit.bubbles, hit.words, hit.marker, hit.damage)) {
            fs.on.set(fs.on == on);
            fs.colourMode.set("theme");
        }
    }

    boolean done() {
        return done;
    }

    /** Once a client tick while the phase runs; the scenario holds its phase until this is done. */
    void tick(Minecraft mc) {
        if (done || mc.player == null || mc.level == null) {
            return;
        }
        if (io.github.georgwav.cosmetics.sol.render.Mc.overlay() != null) {
            // A resource reload in progress (a shot before put packs back): its loading screen covers the world.
            wait = Math.max(wait, 20);
            return;
        }
        if (index < 0) {
            start(mc);
            index = 0;
            step = 0;
            // Three seconds for the frame to settle after the shots before (the grade and light ease to the new
            // quality), so the first effect's empty frame is the scene its frames are drawn over.
            wait = 60;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (FrameGrab.PENDING.get() > 0) {
            return;
        }
        if (index >= cases.size()) {
            if (!killChecked) {
                realKill(mc);
                return;
            }
            finish(mc);
            return;
        }
        Case c = cases.get(index);
        switch (step) {
            case 0 -> {
                FxWorld.hold = Float.NaN;
                FxWorld.POOL.clear();
                pitch = c.pitch();
                c.module().setEnabled(true);
                c.setup().run();
                io.github.georgwav.cosmetics.sol.render.Mc.hideHud(!c.hud());
                stageDummy(mc, c);
                wait = 8;
                step = 1;
            }
            case 1 -> {
                FrameGrab.grab(mc, "fx-" + c.name() + "-off");
                accentA = 0xFF000000 | ThemeState.tokens().accent;
                wait = 2;
                step = 2;
            }
            case 2 -> {
                // Frozen at the moment it starts; the frames step the frozen clock through its life.
                t0 = FrameInfo.time;
                FxWorld.hold = t0;
                dummy = place(mc, c);
                life = c.module().tryIt(FxWorld.POOL, dummy);
                if (life <= 0f || !c.hud() && FxWorld.POOL.count(c.kinds()) == 0) {
                    problems.add("fx " + c.name() + ": nothing started");
                }
                step = 3;
                frame(0, c);
            }
            case 3, 4, 5, 6, 7, 8 -> {
                int k = step - 3;
                FrameGrab.grab(mc, "fx-" + c.name() + "-" + k);
                if (k == 2 && !c.hud() && FxWorld.POOL.count(c.kinds()) == 0) {
                    problems.add("fx " + c.name() + ": gone halfway through its life");
                }
                step++;
                if (step <= 8) {
                    frame(step - 3, c);
                } else {
                    // The second preset, for the colour check.
                    ThemeState.setTheme(PRESET_B, false);
                    FxWorld.POOL.clear();
                    FxWorld.hold = Float.NaN;
                    stageDummy(mc, c);
                    wait = 10;
                }
            }
            case 9 -> {
                // The second preset's own frame with nothing playing, then its key moment.
                FrameGrab.grab(mc, "fx-" + c.name() + "-boff");
                accentB = 0xFF000000 | ThemeState.tokens().accent;
                wait = 2;
                step = 13;
            }
            case 13 -> {
                t0 = FrameInfo.time;
                FxWorld.hold = t0;
                dummy = place(mc, c);
                life = c.module().tryIt(FxWorld.POOL, dummy);
                bFrame = 1;
                FxWorld.hold = t0 + life * (bFrame + 0.5f) / 6f;
                wait = 3;
                step = 10;
            }
            case 10 -> {
                // The second preset at the same three moments as the first one's colour check.
                FrameGrab.grab(mc, "fx-" + c.name() + "-b" + bFrame);
                if (++bFrame <= 3) {
                    FxWorld.hold = t0 + life * (bFrame + 0.5f) / 6f;
                    wait = 3;
                    return;
                }
                ThemeState.setTheme(PRESET_A, false);
                FxWorld.POOL.clear();
                FxWorld.hold = Float.NaN;
                wait = 10;
                step = 11;
            }
            case 11 -> {
                // Played live, it must be gone a second after its life.
                dummy = place(mc, c);
                life = c.module().tryIt(FxWorld.POOL, dummy);
                t0 = FrameInfo.time;
                step = 12;
            }
            case 12 -> {
                if (FrameInfo.time - t0 < life + 1.0f) {
                    return;
                }
                cleanLeft = c.hud() ? 0 : FxWorld.POOL.count(c.kinds());
                if (cleanLeft > 0) {
                    problems.add("fx " + c.name() + ": " + cleanLeft + " still in the pool a second after its life");
                }
                if (c.grey()) {
                    wall(mc, true);
                    wait = 20;
                    step = 14;
                    return;
                }
                record(c);
            }
            case 14 -> {
                // Behind a wall: a grey effect mustn't show through it.
                FxWorld.POOL.clear();
                stageDummy(mc, c);
                FrameGrab.grab(mc, "fx-" + c.name() + "-wall-off");
                wait = 2;
                step = 15;
            }
            case 15 -> {
                t0 = FrameInfo.time;
                FxWorld.hold = t0;
                dummy = place(mc, c);
                life = c.module().tryIt(FxWorld.POOL, dummy);
                bFrame = 1;
                FxWorld.hold = t0 + life * (bFrame + 0.5f) / 6f;
                wait = 3;
                step = 16;
            }
            case 16 -> {
                FrameGrab.grab(mc, "fx-" + c.name() + "-wall-" + bFrame);
                if (++bFrame <= 3) {
                    FxWorld.hold = t0 + life * (bFrame + 0.5f) / 6f;
                    wait = 3;
                    return;
                }
                FxWorld.POOL.clear();
                FxWorld.hold = Float.NaN;
                wall(mc, false);
                wait = 20;
                step = 17;
            }
            case 17 -> record(c);
            default -> {
            }
        }
    }

    /** The case's line in the results, then on to the next case. */
    private void record(Case c) {
        JsonObject r = new JsonObject();
        r.addProperty("name", c.name());
        r.addProperty("module", c.module().id);
        r.addProperty("life", life);
        r.addProperty("colour", c.colour());
        r.addProperty("grey", c.grey());
        r.addProperty("accent_a", String.format("#%06X", accentA & 0xFFFFFF));
        r.addProperty("accent_b", String.format("#%06X", accentB & 0xFFFFFF));
        r.addProperty("cleaned", cleanLeft == 0);
        r.addProperty("wall", c.grey());
        r.addProperty("hud", c.hud());
        results.add(r);
        index++;
        step = 0;
    }

    /**
     * For an effect that doesn't take the dummy away: the dummy stands there before the frame with nothing playing,
     * so only the effect differs between the frames.
     */
    private void stageDummy(Minecraft mc, Case c) {
        if (!c.module().hidesDummy()) {
            place(mc, c);
        }
    }

    /** Puts up (or takes down) a wall of black concrete two blocks ahead, between you and the dummy. */
    private static void wall(Minecraft mc, boolean up) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        double a = Math.toRadians(mc.player.getYRot());
        double fx = -Math.sin(a), fz = Math.cos(a);
        int bx = (int) Math.floor(mc.player.getX() + fx * 2.2), bz = (int) Math.floor(mc.player.getZ() + fz * 2.2);
        int by = (int) Math.floor(mc.player.getY());
        String box = Math.abs(fx) > Math.abs(fz)
                ? String.format(java.util.Locale.ROOT, "%d %d %d %d %d %d", bx, by, bz - 6, bx, by + 5, bz + 6)
                : String.format(java.util.Locale.ROOT, "%d %d %d %d %d %d", bx - 6, by, bz, bx + 6, by + 5, bz);
        String cmd = up ? "fill " + box + " minecraft:black_concrete replace minecraft:air"
                : "fill " + box + " minecraft:air replace minecraft:black_concrete";
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
    }

    /** Freezes the clock at the k-th of six moments of the effect's life and waits for frames to draw it. */
    private void frame(int k, Case c) {
        FxWorld.hold = t0 + life * (k + 0.5f) / 6f;
        wait = 3;
    }

    /** The dummy, {@code distance} blocks ahead along the way you look, on the ground, facing you. */
    private FxPreview.Dummy place(Minecraft mc, Case c) {
        float yaw = mc.player.getYRot();
        double a = Math.toRadians(yaw);
        double x = mc.player.getX() - Math.sin(a) * c.distance(), z = mc.player.getZ() + Math.cos(a) * c.distance();
        double y = WorldOut.INSTANCE.ground(x, z, mc.player.getY() + 1.0);
        return FxPreview.stage(c.module(), x, y, z, yaw + 180f, 30f);
    }

    private void start(Minecraft mc) {
        Atmosphere at = Atmosphere.INSTANCE;
        themeWas = ThemeState.baseEntry().id();
        if (at != null) {
            qualityWas = at.quality.get();
            particlesWas = at.particles.get();
            // The colours as drawn: no grade over the frame, no theme particles in the comparison.
            at.quality.set("low");
            at.particles.set(false);
        }
        ThemeState.setTheme(PRESET_A, false);
        CosmeticsMod.LOGGER.info("[Sol] Effects phase: {} cases", cases.size());
    }

    /** A mob you hit, then killed by the server: the kill effect must start through the real events. */
    private void realKill(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            killChecked = true;
            return;
        }
        KillEffects kill = Modules.KILL_EFFECTS;
        if (!killStarted) {
            killStarted = true;
            kill.setEnabled(true);
            kill.fx.style.set("soul_rise");
            kill.fx.who.set("everything");
            // A real hit starts hit particles, and the kill a line in the kill feed.
            HitEffects hit = Modules.HIT_EFFECTS;
            hit.setEnabled(true);
            only(hit, hit.particles.on);
            Modules.KILL_FEED.setEnabled(true);
            FxWorld.POOL.clear();
            double a = Math.toRadians(mc.player.getYRot());
            double x = mc.player.getX() - Math.sin(a) * 3.0, z = mc.player.getZ() + Math.cos(a) * 3.0;
            String cmd = String.format(java.util.Locale.ROOT, "summon pig %.2f %.2f %.2f {NoAI:1b,Silent:1b,Tags:[\"sol_fx\"]}", x,
                    mc.player.getY(), z);
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
            killTicks = 0;
            return;
        }
        killTicks++;
        if (zombie == Integer.MIN_VALUE) {
            for (Entity e : mc.level.entitiesForRendering()) {
                String type = String.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
                if (type.endsWith(":pig") && e.distanceTo(mc.player) < 6f) {
                    zombie = e.getId();
                    // Your hit, as the game reports it.
                    mc.gameMode.attack(mc.player, e);
                    if (FxWorld.POOL.count("hit_particles") == 0) {
                        problems.add("fx real hit: no hit particles");
                    }
                    server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                            "kill @e[tag=sol_fx]"));
                    break;
                }
            }
            if (killTicks > 100) {
                problems.add("fx real kill: the pig never showed up");
                killChecked = true;
            }
            return;
        }
        if (FxWorld.POOL.count("kill_") > 0) {
            CosmeticsMod.LOGGER.info("[Sol] Effects phase: a real kill started its effect after {} ticks", killTicks);
            if (Modules.KILL_FEED.lines() == 0) {
                problems.add("fx real kill: no line in the kill feed");
            }
            killChecked = true;
            return;
        }
        Entity e = mc.level.getEntity(zombie);
        if (killTicks > 120) {
            problems.add("fx real kill: no kill effect started (pig " + (e instanceof LivingEntity l ? l.isDeadOrDying() ? "dead" : "alive" : "gone")
                    + ")");
            killChecked = true;
        }
    }

    private void finish(Minecraft mc) {
        done = true;
        FxWorld.hold = Float.NaN;
        FxWorld.POOL.clear();
        for (Case c : cases) {
            c.module().setEnabled(false);
        }
        Modules.KILL_FEED.setEnabled(false);
        io.github.georgwav.cosmetics.sol.render.Mc.hideHud(true);
        Atmosphere at = Atmosphere.INSTANCE;
        if (at != null && qualityWas != null) {
            at.quality.set(qualityWas);
            at.particles.set(particlesWas);
        }
        if (themeWas != null) {
            ThemeState.setTheme(themeWas, false);
        }
        JsonObject out = new JsonObject();
        out.addProperty("preset_a", PRESET_A);
        out.addProperty("preset_b", PRESET_B);
        out.add("cases", results);
        out.addProperty("peak", FxWorld.peak);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("cosmeticsmod-fx-results.json"), out.toString());
        } catch (Exception e) {
            problems.add("fx: couldn't write the results: " + e);
        }
        CosmeticsMod.LOGGER.info("[Sol] Effects phase done: {} cases", results.size());
    }
}
