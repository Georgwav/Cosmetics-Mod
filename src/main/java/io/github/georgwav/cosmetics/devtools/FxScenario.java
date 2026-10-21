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
import io.github.georgwav.cosmetics.sol.fx.effects.Totems;
import io.github.georgwav.cosmetics.sol.fx.effects.Trails;
import io.github.georgwav.cosmetics.sol.fx.effects.You;
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
    record Case(String name, FxModule module, Runnable setup, float distance, float pitch, boolean colour, boolean grey, String kinds,
                Runnable on, Runnable off) {
        Case(String name, FxModule module, Runnable setup, float distance, float pitch, boolean colour, boolean grey, String kinds) {
            this(name, module, setup, distance, pitch, colour, grey, kinds, null, null);
        }

        boolean hud() {
            return kinds == null;
        }

        /** Whether its effects are in the pool (a layer's, like chams, are drawn every frame instead). */
        boolean pooled() {
            return kinds != null && !kinds.equals(LAYER);
        }
    }

    /** The kinds of a case drawn by a layer (FxWorld.LAYERS), not in the pool. */
    static final String LAYER = "layer";

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
    /** The other real events, in turn: a totem pop, a crystal appearing, an explosion, an arrow in flight. */
    private int realStep, realTicks;
    private static final int REAL_TOTEM = 0, REAL_CRYSTAL = 1, REAL_BLAST = 2, REAL_ARROW = 3, REAL_LAND = 4, REAL_WALK = 5, REAL_CHAT = 6,
            REAL_EAT = 7, REAL_SPRINT = 8, REAL_CUBES = 9, REAL_PETS = 10, REAL_DONE = 11;
    private static final String[] REAL_NAMES = {"totem pop", "crystal", "explosion", "arrow", "landing", "walk", "chat line", "meal",
            "sprint", "glowing cubes", "pets"};
    /** The kinds each real event must start (the sprint's lean and the cubes aren't in the pool: they're checked on their own). */
    private static final String[] REAL_KINDS = {"totem_", "crystal_rings", "crystal_rings", "trail_projectile", "jump_", "trail_ribbon",
            "you_bubble", "you_eat", null, null, null};
    /** What you wore before the pets were put on. */
    private List<String> wornWas;
    /** More ambience and its cubes as they were. */
    private boolean ambienceWas, cubesWas;
    /** Whether the meal holds the use key down, before each tick as well (registered once). */
    private static volatile boolean holdUse;

    static {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (holdUse) {
                mc.options.keyUse.setDown(true);
            }
        });
    }

    /** Where the walk started, to put you back, and your game mode before the meal. */
    private double walkX, walkY, walkZ;
    private String modeWas = "creative";
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
        // Totems: the gold ghost, shatter and angel in their own gold; the floating totem in each of Sol's styles.
        Totems totems = Modules.TOTEMS;
        cases.add(new Case("totem_ghost", totems, () -> only(totems, totems.ghost.on, "theme_pop"), 4f, 2f, false, false, "totem_ghost"));
        cases.add(new Case("totem_shatter", totems, () -> only(totems, totems.shatter.on, "theme_pop"), 4f, -4f, false, false, "totem_shatter"));
        cases.add(new Case("totem_angel", totems, () -> only(totems, totems.angel.on, "theme_pop"), 4.5f, -6f, false, false, "totem_angel"));
        for (String style : Totems.POP_STYLES) {
            if (!style.equals("vanilla") && !style.equals("hidden")) {
                cases.add(new Case("totem_pop_" + style, totems, () -> only(totems, null, style), 4f, 4f, false, false, null));
            }
        }
        // Target: each mark on the dummy (grey: also behind a wall); chams on a real crystal (behind a wall too) and on you.
        var target = Modules.TARGET_MARK;
        for (String style : io.github.georgwav.cosmetics.sol.fx.effects.Target.MARK_STYLES) {
            cases.add(new Case("target_" + style, target, () -> {
                target.mark.on.set(true);
                target.mark.random.set(false);
                target.mark.style.set(style);
                target.mark.colourMode.set("theme");
                target.chams.set(false);
                target.friend.on.set(false);
            }, 3.6f, 0f, true, true, "target_mark"));
        }
        for (String look : List.of("flat", "glow", "outline")) {
            cases.add(new Case("chams_crystal_" + look, target, () -> {
                target.mark.on.set(false);
                target.chamsLook.set(look);
                target.chamsCrystals.set(true);
                target.chamsCrystalColour.set(io.github.georgwav.cosmetics.client.Setting.Color.THEME);
                summonCrystal();
            }, 4f, 0f, true, true, LAYER, () -> target.chams.set(true), () -> target.chams.set(false)));
        }
        cases.add(new Case("chams_you", target, () -> {
            removeCrystal();
            target.mark.on.set(false);
            target.chamsLook.set("glow");
            target.chamsPlayers.set(true);
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }, 4f, 10f, true, false, LAYER, () -> {
            io.github.georgwav.cosmetics.sol.fx.effects.Target.chamsYou = true;
            target.chams.set(true);
        }, () -> {
            io.github.georgwav.cosmetics.sol.fx.effects.Target.chamsYou = false;
            target.chams.set(false);
        }));
        cases.add(new Case("friend_marker", target, () -> {
            target.mark.on.set(false);
            target.chams.set(false);
            target.friend.colourMode.set("custom");
            target.friends.set(Minecraft.getInstance().getUser().getName());
            Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
        }, 4f, 6f, false, false, LAYER, () -> {
            io.github.georgwav.cosmetics.sol.fx.effects.Target.chamsYou = true;
            target.friend.on.set(true);
        }, () -> {
            io.github.georgwav.cosmetics.sol.fx.effects.Target.chamsYou = false;
            target.friend.on.set(false);
        }));
        // Jump FX: each style on the dummy's landing, and the ring with each particle set.
        var jump = Modules.JUMP_FX;
        for (String style : io.github.georgwav.cosmetics.sol.fx.effects.JumpFx.STYLES) {
            boolean colour = !style.equals("block_wave");
            cases.add(new Case("jump_" + style, jump, () -> {
                jump.fx.random.set(false);
                jump.fx.style.set(style);
                jump.fx.colourMode.set("theme");
                jump.particles.set("off");
            }, 4.5f, style.equals("hologram") || style.equals("landing_bolts") ? 6f : 28f, colour, false, "jump_"));
        }
        for (String set : List.of("ice", "fire", "soul", "galaxy")) {
            cases.add(new Case("jump_particles_" + set, jump, () -> {
                jump.fx.random.set(false);
                jump.fx.style.set("ring");
                jump.fx.colourMode.set("theme");
                jump.particles.set(set);
            }, 4.5f, 20f, false, false, "jump_"));
        }
        var crystal = Modules.CRYSTAL_PVP;
        cases.add(new Case("crystal_rings", crystal, () -> {
            crystal.rings.on.set(true);
            crystal.rings.colourMode.set("theme");
        }, 6.5f, 12f, true, false, "crystal_rings"));
        Trails trails = Modules.TRAILS;
        for (String style : Trails.PROJECTILE_STYLES) {
            cases.add(new Case("trail_" + style, trails, () -> {
                only(trails, trails.projectiles.on);
                trails.projectiles.style.set(style);
            }, 4f, 4f, true, false, "trail_projectile"));
        }
        // Your trails, on the dummy's walk (or glide) past: the ribbon in each style and from the feet, then the rest.
        for (String style : Trails.RIBBON_STYLES) {
            String name = style.equals("theme_bands") ? "trail_ribbon" : "trail_ribbon_" + style.substring(0, style.indexOf('_'));
            cases.add(new Case(name, trails, () -> {
                only(trails, trails.ribbon.on);
                trails.ribbon.style.set(style);
                trails.ribbonFrom.set("waist");
            }, 5f, 14f, style.equals("theme_bands"), false, "trail_ribbon"));
        }
        cases.add(new Case("trail_ribbon_feet", trails, () -> {
            only(trails, trails.ribbon.on);
            trails.ribbon.style.set("theme_bands");
            trails.ribbonFrom.set("feet");
        }, 5f, 26f, true, false, "trail_ribbon"));
        cases.add(new Case("trail_firefly", trails, () -> only(trails, trails.firefly.on), 4.5f, 10f, true, false, "trail_firefly"));
        cases.add(new Case("trail_ghost", trails, () -> only(trails, trails.ghost.on), 5f, 8f, true, false, "trail_ghost"));
        cases.add(new Case("trail_footsteps", trails, () -> only(trails, trails.footsteps.on), 4.5f, 34f, true, false, "trail_footprint"));
        cases.add(new Case("trail_contrails", trails, () -> only(trails, trails.contrails.on), 6f, -12f, true, false, "trail_contrail"));
        // You: the Naruto run (a copy of you in it), a chat bubble, and a burst of crumbs then drips.
        You you = Modules.YOU;
        cases.add(new Case("you_naruto", you, () -> only(you, "naruto"), 3.6f, 4f, false, false, "you_naruto"));
        // The bubble's rim is the accent, but over the sky behind it (as near Frost's accent as makes no difference) it
        // can't be told apart in the frame: its colour isn't measured.
        cases.add(new Case("you_bubble", you, () -> only(you, "bubble"), 4.5f, -10f, false, false, "you_bubble"));
        cases.add(new Case("you_eat", you, () -> only(you, "eat"), 2.4f, 8f, false, false, "you_eat"));
    }

    /** You with only one of its parts on, in the preset's colour. */
    private static void only(You y, String which) {
        y.naruto.set(which.equals("naruto"));
        y.bubbles.on.set(which.equals("bubble"));
        y.bubbles.colourMode.set("theme");
        y.bubbleWhose.set("everyone");
        y.eating.set(which.equals("eat"));
        y.eatingWhose.set("everyone");
    }

    /** Trails with only one of its trails on, in the preset's colour. */
    private static void only(Trails t, io.github.georgwav.cosmetics.client.Setting.Toggle on) {
        for (var fs : List.of(t.projectiles, t.ribbon, t.firefly, t.ghost, t.footsteps, t.contrails)) {
            fs.on.set(fs.on == on);
            fs.colourMode.set("theme");
        }
    }

    @SuppressWarnings("unchecked")
    private void restoreSettings() {
        for (var e : settingsWere.entrySet()) {
            ((io.github.georgwav.cosmetics.client.Setting<Object>) e.getKey()).set(e.getValue());
        }
        for (var e : enabledWere.entrySet()) {
            e.getKey().setEnabled(e.getValue());
        }
    }

    /** An end crystal on the ground four blocks ahead, once (the chams cases'), kept still. */
    private static boolean crystalSummoned;

    private static void removeCrystal() {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "kill @e[tag=sol_cham]"));
        }
    }

    private static void summonCrystal() {
        Minecraft mc = Minecraft.getInstance();
        var server = mc.getSingleplayerServer();
        if (crystalSummoned || server == null || mc.player == null) {
            return;
        }
        crystalSummoned = true;
        double a = Math.toRadians(mc.player.getYRot());
        double x = mc.player.getX() - Math.sin(a) * 4.0, z = mc.player.getZ() + Math.cos(a) * 4.0;
        String cmd = String.format(java.util.Locale.ROOT, "summon end_crystal %.2f %.2f %.2f {ShowBottom:0b,Tags:[\"sol_cham\"]}", x,
                mc.player.getY(), z);
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
    }

    /** Totems with only one of its effects on ({@code on}, or none) and the floating totem in {@code pop}'s style. */
    private static void only(Totems t, io.github.georgwav.cosmetics.client.Setting.Toggle on, String pop) {
        for (var fs : List.of(t.ghost, t.shatter, t.angel)) {
            fs.on.set(fs.on == on);
            fs.colourMode.set("custom");
        }
        t.flash.set(false);
        t.popStyle.set(pop.equals("theme_pop") ? "hidden" : pop);
    }

    /** Hit Effects with only one of its effects on, in the preset's colour. */
    private static void only(HitEffects hit, io.github.georgwav.cosmetics.client.Setting.Toggle on) {
        for (var fs : List.of(hit.particles, hit.bubbles, hit.words, hit.marker, hit.damage)) {
            fs.on.set(fs.on == on);
            fs.colourMode.set("theme");
        }
    }

    /**
     * The cases the reduced run plays (the older patch releases'): each effect once in its default style, every grey
     * one (for the wall check) and every effect with no styles. The full run (one release per era) plays them all.
     */
    static final java.util.Set<String> REDUCED = java.util.Set.of("kill_soul_rise", "kill_lightning", "hit_pixels", "hit_bubbles",
            "hit_words", "hit_damage", "hit_marker", "totem_ghost", "totem_shatter", "totem_angel", "totem_pop_roll", "target_ring",
            "chams_crystal_glow", "chams_you", "friend_marker", "jump_square_ring", "jump_hologram", "jump_particles_fire",
            "crystal_rings", "trail_dots", "trail_ribbon", "trail_ribbon_rainbow", "trail_firefly", "trail_ghost", "trail_footsteps",
            "trail_contrails", "you_naruto", "you_bubble", "you_eat");

    /**
     * Only the cases whose names start with one of these (-Dcosmeticsmod.fxOnly=jump_,hit_), for quick looks; the
     * reduced run's set unless -Dcosmeticsmod.fxAll=true.
     */
    private void only() {
        String only = System.getProperty("cosmeticsmod.fxOnly", "");
        if (!only.isBlank()) {
            List<String> prefixes = List.of(only.split(","));
            cases.removeIf(c -> prefixes.stream().noneMatch(c.name()::startsWith));
        } else if ("reduced".equals(System.getProperty("cosmeticsmod.solScenario", "")) && !Boolean.getBoolean("cosmeticsmod.fxAll")) {
            for (String name : REDUCED) {
                if (cases.stream().noneMatch(c -> c.name().equals(name))) {
                    problems.add("fx: the reduced set names a case there isn't: " + name);
                }
            }
            cases.removeIf(c -> !REDUCED.contains(c.name()));
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
            if (realStep < REAL_DONE) {
                realEvents(mc);
                return;
            }
            finish(mc);
            return;
        }
        Case c = cases.get(index);
        switch (step) {
            case 0 -> {
                // A line per case: the run's hang check wants the log to move at least every few minutes.
                CosmeticsMod.LOGGER.info("[Sol] Effects phase: case {}/{}: {}", index + 1, cases.size(), c.name());
                FxWorld.hold = Float.NaN;
                FxWorld.POOL.clear();
                pitch = c.pitch();
                c.module().setEnabled(true);
                c.setup().run();
                io.github.georgwav.cosmetics.sol.render.Mc.hideHud(!c.hud());
                if (c.off() != null) {
                    c.off().run();
                }
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
                if (c.on() != null) {
                    c.on().run();
                }
                if (life <= 0f || c.pooled() && FxWorld.POOL.count(c.kinds()) == 0) {
                    problems.add("fx " + c.name() + ": nothing started");
                }
                step = 3;
                frame(0, c);
            }
            case 3, 4, 5, 6, 7, 8 -> {
                int k = step - 3;
                FrameGrab.grab(mc, "fx-" + c.name() + "-" + k);
                if (k == 2 && c.pooled() && FxWorld.POOL.count(c.kinds()) == 0) {
                    problems.add("fx " + c.name() + ": gone halfway through its life");
                }
                step++;
                if (step <= 8) {
                    frame(step - 3, c);
                } else {
                    // The second preset, for the colour check.
                    ThemeState.setTheme(PRESET_B, false);
                    stop(c);
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
                if (c.on() != null) {
                    c.on().run();
                }
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
                stop(c);
                FxWorld.hold = Float.NaN;
                wait = 10;
                step = 11;
            }
            case 11 -> {
                // Played live, it must be gone a second after its life.
                dummy = place(mc, c);
                life = c.module().tryIt(FxWorld.POOL, dummy);
                if (c.on() != null) {
                    c.on().run();
                }
                t0 = FrameInfo.time;
                step = 12;
            }
            case 12 -> {
                if (FrameInfo.time - t0 < life + 1.0f) {
                    return;
                }
                cleanLeft = c.pooled() ? FxWorld.POOL.count(c.kinds()) : 0;
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
                stop(c);
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
                if (c.on() != null) {
                    c.on().run();
                }
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
                stop(c);
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

    /** Ends what a case started: its effects in the pool, and a layer's effect switched off. */
    private static void stop(Case c) {
        FxWorld.POOL.clear();
        if (c.off() != null) {
            c.off().run();
        }
    }

    /** The case's line in the results, then on to the next case. */
    private void record(Case c) {
        stop(c);
        // A case that looked from behind you (chams on you) puts the camera back.
        Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
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

    /** Every setting of the modules the phase touches, as it found them, to put back when it's done. */
    private final java.util.Map<io.github.georgwav.cosmetics.client.Setting<?>, Object> settingsWere = new java.util.HashMap<>();
    private final java.util.Map<io.github.georgwav.cosmetics.client.Module, Boolean> enabledWere = new java.util.HashMap<>();

    private void start(Minecraft mc) {
        only();
        java.util.Set<io.github.georgwav.cosmetics.client.Module> touched = new java.util.LinkedHashSet<>();
        for (Case c : cases) {
            touched.add(c.module());
        }
        touched.add(Modules.KILL_FEED);
        for (var m : touched) {
            enabledWere.put(m, m.enabled());
            for (var setting : m.settings()) {
                settingsWere.put(setting, setting.get());
            }
        }
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

    /**
     * The real events after the kill: each set off with a command and waited for (two seconds at most) until its
     * effect starts through the game's own events. A pig holding a totem takes lethal damage (Totems: ghost and
     * shatter), an end crystal is summoned ahead (Crystal PvP's rings), TNT goes off twenty blocks up (rings, from the
     * explosion the server sends; nothing breaks and nobody is hurt up there), and an arrow flies across the view
     * (Trails, everyone's).
     */
    private void realEvents(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (server == null) {
            realStep = REAL_DONE;
            return;
        }
        if (realTicks == 0) {
            FxWorld.POOL.clear();
            double a = Math.toRadians(mc.player.getYRot());
            double fx = -Math.sin(a), fz = Math.cos(a);
            double x = mc.player.getX() + fx * 4.0, y = mc.player.getY(), z = mc.player.getZ() + fz * 4.0;
            List<String> cmds = new ArrayList<>();
            switch (realStep) {
                case REAL_TOTEM -> {
                    Totems t = Modules.TOTEMS;
                    t.setEnabled(true);
                    only(t, t.shatter.on, "hidden");
                    t.ghost.on.set(true);
                    t.ghost.who.set("everything");
                    t.shatter.who.set("everything");
                    cmds.add(String.format(java.util.Locale.ROOT, "summon pig %.2f %.2f %.2f {NoAI:1b,Silent:1b,Tags:[\"sol_totem\"]}", x, y, z));
                    cmds.add("item replace entity @e[tag=sol_totem,limit=1] weapon.mainhand with minecraft:totem_of_undying");
                    cmds.add("damage @e[tag=sol_totem,limit=1] 50 minecraft:generic");
                }
                case REAL_CRYSTAL -> {
                    Modules.CRYSTAL_PVP.setEnabled(true);
                    Modules.CRYSTAL_PVP.rings.on.set(true);
                    cmds.add("kill @e[tag=sol_totem]");
                    cmds.add(String.format(java.util.Locale.ROOT, "summon end_crystal %.2f %.2f %.2f {ShowBottom:0b,Tags:[\"sol_crystal\"]}",
                            x, y + 1.0, z));
                }
                case REAL_BLAST -> {
                    cmds.add("kill @e[tag=sol_crystal]");
                    cmds.add(String.format(java.util.Locale.ROOT, "summon tnt %.2f %.2f %.2f {fuse:0}", x, y + 20.0, z));
                }
                case REAL_LAND -> {
                    var j = Modules.JUMP_FX;
                    j.setEnabled(true);
                    j.fx.random.set(false);
                    j.fx.style.set("square_ring");
                    j.when.set("land");
                    // Three blocks up: the fall and the landing are the game's own.
                    cmds.add("kill @e[tag=sol_arrow]");
                    cmds.add(String.format(java.util.Locale.ROOT, "tp %s %.2f %.2f %.2f", mc.player.getName().getString(), mc.player.getX(),
                            mc.player.getY() + 3.0, mc.player.getZ()));
                }
                case REAL_CHAT -> {
                    // Saying something: the server sends it back as a chat line, and a bubble pops up over you.
                    You yo = Modules.YOU;
                    yo.setEnabled(true);
                    only(yo, "bubble");
                    yo.bubbles.firstPerson.set(true);
                    if (mc.player.connection != null) {
                        mc.player.connection.sendChat("Sol says hi");
                    }
                }
                case REAL_EAT -> {
                    // Eating a golden apple in third person: crumbs at your mouth.
                    You yo = Modules.YOU;
                    yo.setEnabled(true);
                    only(yo, "eat");
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    // Only a survival player eats (the server stops anyone else's eating straight away).
                    modeWas = mc.gameMode != null ? mc.gameMode.getPlayerMode().getName() : "creative";
                    cmds.add("gamemode survival " + mc.player.getName().getString());
                    cmds.add("item replace entity " + mc.player.getName().getString() + " weapon.mainhand with minecraft:golden_apple");
                }
                case REAL_SPRINT -> {
                    // Sprinting forward in third person: you lean into the Naruto run.
                    You yo = Modules.YOU;
                    yo.setEnabled(true);
                    only(yo, "naruto");
                    cmds.add("item replace entity " + mc.player.getName().getString() + " weapon.mainhand with minecraft:air");
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    walkX = mc.player.getX();
                    walkY = mc.player.getY();
                    walkZ = mc.player.getZ();
                }
                case REAL_CUBES -> {
                    // More ambience's glowing cubes: a dozen about within two seconds.
                    Atmosphere at = Atmosphere.INSTANCE;
                    if (at != null) {
                        ambienceWas = at.ambience.get();
                        cubesWas = at.ambienceCubes.get();
                        at.ambience.set(true);
                        at.ambienceCubes.set(true);
                    }
                }
                case REAL_PETS -> {
                    // Sol's three following pets on you: they follow your walk, then the dog sits when you stop.
                    var worn = io.github.georgwav.cosmetics.config.CosmeticsConfig.get().localEquipped();
                    wornWas = new ArrayList<>(worn);
                    worn.clear();
                    worn.addAll(List.of("cosmeticsmod:sol_dog", "cosmeticsmod:sol_ghost", "cosmeticsmod:sol_mini_you"));
                    io.github.georgwav.cosmetics.source.EquippedCosmetics.invalidate();
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    walkX = mc.player.getX();
                    walkY = mc.player.getY();
                    walkZ = mc.player.getZ();
                }
                case REAL_WALK -> {
                    // Walking sideways in third person: the ribbon is laid from your own ticks.
                    Trails t = Modules.TRAILS;
                    t.setEnabled(true);
                    only(t, t.ribbon.on);
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    walkX = mc.player.getX();
                    walkY = mc.player.getY();
                    walkZ = mc.player.getZ();
                    cmds.add("kill @e[tag=sol_arrow]");
                }
                default -> {
                    Trails t = Modules.TRAILS;
                    t.setEnabled(true);
                    t.projectiles.on.set(true);
                    t.projectileWho.set("everyone");
                    // From the left of the view to its right, two blocks ahead at eye height.
                    double rx = -fz, rz = fx;
                    cmds.add(String.format(java.util.Locale.ROOT, "summon arrow %.2f %.2f %.2f {Motion:[%.2f,0.15,%.2f],pickup:0b,Tags:[\"sol_arrow\"]}",
                            mc.player.getX() + fx * 2.5 - rx * 3.0, mc.player.getEyeY(), mc.player.getZ() + fz * 2.5 - rz * 3.0, rx * 1.2, rz * 1.2));
                }
            }
            server.execute(() -> {
                for (String c : cmds) {
                    server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
                }
            });
            realTicks = 1;
            return;
        }
        realTicks++;
        if (realStep == REAL_WALK && realTicks > 2) {
            // A step of 0.3 blocks a tick to your right (a walk's pace).
            double a = Math.toRadians(mc.player.getYRot());
            mc.player.setPos(mc.player.getX() - Math.cos(a) * 0.3, mc.player.getY(), mc.player.getZ() - Math.sin(a) * 0.3);
        }
        if (realStep == REAL_PETS && realTicks > 4 && realTicks <= 24) {
            // Twenty ticks of walking forward, then standing still.
            double a = Math.toRadians(mc.player.getYRot());
            mc.player.setPos(mc.player.getX() - Math.sin(a) * 0.22, mc.player.getY(), mc.player.getZ() + Math.cos(a) * 0.22);
            if (realTicks == 18) {
                FrameGrab.grab(mc, "fx-real-pets-walk");
            }
        }
        if (realStep == REAL_EAT && realTicks > 2) {
            // Start eating as a right click does, then hold the key so it goes on (the mouse's buttons are read again
            // during the tick, so it's held down before each tick too).
            holdUse = true;
            mc.options.keyUse.setDown(true);
            if (!mc.player.isUsingItem() && mc.gameMode != null && !mc.player.getMainHandItem().isEmpty()) {
                mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            }
        }
        if (realStep == REAL_SPRINT && realTicks > 2) {
            mc.options.keyUp.setDown(true);
            mc.options.keySprint.setDown(true);
        }
        var pets = io.github.georgwav.cosmetics.sol.fx.FollowPets.INSTANCE;
        int n = REAL_KINDS[realStep] != null ? FxWorld.POOL.count(REAL_KINDS[realStep])
                : realStep == REAL_SPRINT ? (Modules.YOU.lean() > 0.9f ? 1 : 0)
                : realStep == REAL_PETS ? (realTicks > 24 && pets.count() >= 3 && pets.drawnFrames > 0 && pets.sitting(mc.player.getUUID()) ? 1 : 0)
                : io.github.georgwav.cosmetics.sol.atmosphere.GlowCubes.INSTANCE.count() >= 12 ? 1 : 0;
        if (n > 0 || realTicks > (realStep == REAL_PETS ? 90 : 40)) {
            if (realStep == REAL_PETS) {
                if (n > 0) {
                    FrameGrab.grab(mc, "fx-real-pets-sit");
                } else {
                    problems.add("fx real pets: " + pets.count() + " following, " + pets.drawnFrames + " frames drawn, sitting "
                            + pets.sitting(mc.player.getUUID()));
                    n = 1;
                }
                var worn = io.github.georgwav.cosmetics.config.CosmeticsConfig.get().localEquipped();
                worn.clear();
                if (wornWas != null) {
                    worn.addAll(wornWas);
                }
                io.github.georgwav.cosmetics.source.EquippedCosmetics.invalidate();
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                mc.player.setPos(walkX, walkY, walkZ);
            }
            if (realStep == REAL_SPRINT && n > 0) {
                FrameGrab.grab(mc, "fx-real-naruto");
            }
            if (realStep == REAL_CUBES) {
                if (n > 0) {
                    FrameGrab.grab(mc, "fx-real-cubes");
                }
                Atmosphere at = Atmosphere.INSTANCE;
                if (at != null) {
                    at.ambience.set(ambienceWas);
                    at.ambienceCubes.set(cubesWas);
                }
            }
            if (realStep == REAL_WALK || realStep == REAL_EAT || realStep == REAL_SPRINT) {
                holdUse = false;
                mc.options.keyUse.setDown(false);
                mc.options.keyUp.setDown(false);
                mc.options.keySprint.setDown(false);
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                if (realStep != REAL_EAT) {
                    mc.player.setPos(walkX, walkY, walkZ);
                } else if (server != null) {
                    String back = "gamemode " + modeWas + " " + mc.player.getName().getString();
                    server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), back));
                }
            }
            if (n > 0) {
                CosmeticsMod.LOGGER.info("[Sol] Effects phase: a real {} started its effect after {} ticks", REAL_NAMES[realStep], realTicks);
            } else {
                problems.add("fx real " + REAL_NAMES[realStep] + ": no effect started");
            }
            realStep++;
            realTicks = 0;
            wait = 10;

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
        restoreSettings();
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        var server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                    "kill @e[tag=sol_cham]"));
        }
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
