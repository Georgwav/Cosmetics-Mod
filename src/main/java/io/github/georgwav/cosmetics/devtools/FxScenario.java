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
import io.github.georgwav.cosmetics.sol.fx.effects.WorldFx;
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
    static final String PRESET_A = System.getProperty("cosmeticsmod.fxPreset", "frost_morning"), PRESET_B = "ember_solstice";
    /**
     * Review runs (local only, never CI): {@code -Dcosmeticsmod.fxReview=<frames>} grabs that many moments of each
     * effect's life (fx-<case>-r<k>.png, for a moving picture) and skips the checks after its six frames;
     * {@code fxTime} locks the time of day, {@code fxQuality} sets Atmosphere's quality (the light pass), and
     * {@code fxNear} moves the camera closer (a share of each case's distance).
     */
    static final int REVIEW = Integer.getInteger("cosmeticsmod.fxReview", 0);
    static final long REVIEW_TIME = Long.getLong("cosmeticsmod.fxTime", -1L);
    static final String REVIEW_QUALITY = System.getProperty("cosmeticsmod.fxQuality", "low");
    static final float NEAR = Float.parseFloat(System.getProperty("cosmeticsmod.fxNear", "1"));

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
    /** Whether the meal holds the use key down (and the mining the attack key), before each tick as well (registered once). */
    private static volatile boolean holdUse, holdAttack;

    static {
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.START_CLIENT_TICK.register(mc -> {
            if (holdUse) {
                mc.options.keyUse.setDown(true);
            }
            if (holdAttack) {
                mc.options.keyAttack.setDown(true);
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
                case "soul_rise" -> {
                    distance = 5f;
                    p = -10f;
                }
                case "lightning", "black_hole", "gravestone" -> {
                    distance = 5f;
                    p = 0f;
                }
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
        // Hitboxes (a plain module, staged on Target's turn): corners only, on the chams cases' crystal four blocks ahead
        // (Chams on you takes it away after).
        cases.add(new Case("hitboxes_corners", target, () -> {
            target.mark.on.set(false);
            target.chams.set(false);
            target.friend.on.set(false);
            summonCrystal();
        }, 4f, 0f, false, false, LAYER, () -> {
            hitboxSetting("style", "corners");
            hitboxSetting("show_on", "everyone");
            Modules.HITBOXES.setEnabled(true);
        }, () -> {
            Modules.HITBOXES.setEnabled(false);
            hitboxSetting("style", "full");
        }));
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
        // World FX, a part at a time: snow prints by the dummy's feet (in the snow's own shade), rain rings round it,
        // leaves blowing past (their own green), a broken block's star burst, and a short rainbow, opposite the
        // morning sun and so ahead and up.
        WorldFx w = Modules.WORLD_FX;
        cases.add(new Case("world_snow", w, () -> only(w, w.snow), 3f, 32f, false, false, "world_snow_print"));
        cases.add(new Case("world_ripples", w, () -> only(w, w.ripples), 4f, 30f, true, false, "world_ripple"));
        cases.add(new Case("world_leaves", w, () -> only(w, w.leaves), 4f, 2f, false, false, "world_leaf"));
        cases.add(new Case("world_cosmos", w, () -> only(w, w.cosmos), 3f, 8f, true, false, "world_star_burst"));
        cases.add(new Case("world_rainbow", w, () -> only(w, w.rainbow), 4f, -16f, false, false, "world_rainbow"));
    }

    /** World FX with only one of its parts on (the prints shortest-lived, so the case doesn't wait long). */
    private static void only(WorldFx w, io.github.georgwav.cosmetics.sol.fx.FxSettings on) {
        for (var fs : List.of(w.snow, w.rainbow, w.leaves, w.ripples, w.cosmos)) {
            fs.on.set(fs == on);
        }
        w.ripples.colourMode.set("theme");
        w.cosmos.colourMode.set("theme");
        w.snow.duration.set(50.0);
        w.itemPhysics.set(false);
        w.chunkRise.set(false);
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

    /** Sets one of Hitboxes' choices (its settings are its own). */
    @SuppressWarnings("unchecked")
    private static void hitboxSetting(String id, String value) {
        for (io.github.georgwav.cosmetics.client.Setting<?> s : Modules.HITBOXES.settings()) {
            if (s.id.equals(id)) {
                ((io.github.georgwav.cosmetics.client.Setting<String>) s).set(value);
            }
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
            "trail_contrails", "you_naruto", "you_bubble", "you_eat", "world_snow", "world_ripples", "world_leaves", "world_cosmos",
            "world_rainbow");

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
            if (worldStep < W_DONE) {
                worldEvents(mc);
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
                } else if (REVIEW > 0) {
                    // A review run: the moving picture's frames, then the next case.
                    bFrame = 0;
                    FxWorld.hold = t0 + life * 0.5f / REVIEW;
                    wait = 3;
                    step = 20;
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
            case 20 -> {
                FrameGrab.grab(mc, "fx-" + c.name() + "-r" + bFrame);
                if (++bFrame < REVIEW) {
                    FxWorld.hold = t0 + life * (bFrame + 0.5f) / REVIEW;
                    wait = 2;
                    return;
                }
                stop(c);
                FxWorld.hold = Float.NaN;
                record(c);
            }
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
        r.addProperty("settings", settingsOf(c.module()));
        results.add(r);
        index++;
        step = 0;
    }

    /** The case's settings as staged (those not at their defaults, by name), for the gallery. */
    @SuppressWarnings("unchecked")
    private static String settingsOf(FxModule m) {
        StringBuilder b = new StringBuilder();
        for (var st : m.settings()) {
            if (!st.changed() || !st.shown()) {
                continue;
            }
            String v;
            if (st instanceof io.github.georgwav.cosmetics.client.Setting.Choice ch) {
                v = m.optionName(ch, ch.get());
            } else if (st instanceof io.github.georgwav.cosmetics.client.Setting.Toggle t) {
                v = t.get() ? "on" : "off";
            } else if (st instanceof io.github.georgwav.cosmetics.client.Setting.Slider sl) {
                v = sl.text();
            } else if (st instanceof io.github.georgwav.cosmetics.client.Setting.Color col) {
                v = col.followsTheme() ? "theme" : String.format("#%06X", col.argb() & 0xFFFFFF);
            } else {
                v = String.valueOf(st.get());
            }
            if (b.length() > 0) {
                b.append(" · ");
            }
            b.append(m.settingName(st)).append(": ").append(v);
        }
        return b.length() == 0 ? "defaults" : b.toString();
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
        double d = c.distance() * NEAR;
        double x = mc.player.getX() - Math.sin(a) * d, z = mc.player.getZ() + Math.cos(a) * d;
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
        // The world's real events (worldEvents) change these too.
        touched.add(Modules.WORLD_FX);
        touched.add(Modules.OUTLINE);
        touched.add(Modules.CAMERA);
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
            at.quality.set(REVIEW_QUALITY);
            at.particles.set(false);
        }
        ThemeState.setTheme(PRESET_A, false);
        if (REVIEW_TIME >= 0L) {
            SolScenario.lockedTime = REVIEW_TIME;
        }
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

    // ------------------------------------------------------------------ the world's real events (World FX and friends)

    private static final int W_SNOW = 0, W_RAIN = 1, W_LEAVES = 2, W_MINE = 3, W_ITEMS = 4, W_OUTLINE = 5, W_CHUNKS = 6, W_GLIDE = 7,
            W_DONE = 8;
    private static final String[] W_NAMES = {"snow walk", "rain and rainbow", "leaves", "mining", "dropped items", "block outline",
            "chunk rise", "F5 glide"};
    private int worldStep, worldTicks, worldPhase, worldMark;
    /** The box the step built (snow, leaves, a block), to clear after; empty when none. */
    private String worldBox = "", worldBlock = "";
    private boolean weatherLockWas;

    private static void run(Minecraft mc, List<String> cmds) {
        var server = mc.getSingleplayerServer();
        if (server == null || cmds.isEmpty()) {
            return;
        }
        server.execute(() -> {
            for (String c : cmds) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), c);
            }
        });
    }

    /** A setting of {@code m} by its id (for the modules whose settings aren't public). */
    @SuppressWarnings("unchecked")
    private static <T> void set(io.github.georgwav.cosmetics.client.Module m, String id, T value) {
        for (var setting : m.settings()) {
            if (setting.id.equals(id)) {
                ((io.github.georgwav.cosmetics.client.Setting<T>) setting).set(value);
                return;
            }
        }
    }

    /**
     * World FX through the game's own events, one at a time: walking through snow lays prints; rain makes rings on the
     * ground, and a rainbow comes out when it stops; a canopy of leaves sheds leaves; mining a plank fills it with stars
     * and it bursts when it breaks; dropped items fall tumbling and lie flat; the block outline glows with stars inside;
     * the chunks rise when chunk rise is switched on (where the version has it); and the camera glides out to third
     * person and back in. Each grabs a frame or two (fx-real-*) and must do its part within its time.
     */
    private void worldEvents(Minecraft mc) {
        if (mc.getSingleplayerServer() == null) {
            worldStep = W_DONE;
            return;
        }
        WorldFx w = Modules.WORLD_FX;
        String me = mc.player.getName().getString();
        double a = Math.toRadians(mc.player.getYRot());
        double fx = -Math.sin(a), fz = Math.cos(a);
        int px = (int) Math.floor(mc.player.getX()), py = (int) Math.floor(mc.player.getY()), pz = (int) Math.floor(mc.player.getZ());
        if (worldTicks == 0) {
            FxWorld.POOL.clear();
            worldPhase = 0;
            List<String> cmds = new ArrayList<>();
            // Only World FX's effects in these frames (the trails and the rest put back at the end).
            for (var m : List.<io.github.georgwav.cosmetics.client.Module>of(Modules.TRAILS, Modules.YOU, Modules.JUMP_FX,
                    Modules.TOTEMS, Modules.CRYSTAL_PVP, Modules.KILL_EFFECTS, Modules.HIT_EFFECTS, Modules.TARGET_MARK)) {
                enabledWere.putIfAbsent(m, m.enabled());
                m.setEnabled(false);
            }
            w.setEnabled(true);
            for (var fs : List.of(w.snow, w.rainbow, w.leaves, w.ripples, w.cosmos)) {
                fs.on.set(false);
            }
            walkX = mc.player.getX();
            walkY = mc.player.getY();
            walkZ = mc.player.getZ();
            switch (worldStep) {
                case W_SNOW -> {
                    // A layer of snow all round, walked through sideways in third person.
                    w.snow.on.set(true);
                    w.snow.duration.set(100.0);
                    pitch = 34f;
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                    worldBox = String.format(java.util.Locale.ROOT, "%d %d %d %d %d %d", px - 5, py, pz - 5, px + 5, py, pz + 5);
                    worldBlock = "minecraft:snow";
                    cmds.add("fill " + worldBox + " minecraft:snow replace minecraft:air");
                }
                case W_RAIN -> {
                    // The world's own weather (the shots lock it clear).
                    w.ripples.on.set(true);
                    w.rainbow.on.set(true);
                    pitch = 30f;
                    Atmosphere at = Atmosphere.INSTANCE;
                    if (at != null) {
                        weatherLockWas = at.lockWeather.get();
                        at.lockWeather.set(false);
                    }
                    SolScenario.realWeather = true;
                    cmds.add("weather rain");
                }
                case W_LEAVES -> {
                    // A canopy overhead, a little ahead.
                    w.leaves.on.set(true);
                    pitch = -4f;
                    int cx = (int) Math.floor(mc.player.getX() + fx * 4.0), cz = (int) Math.floor(mc.player.getZ() + fz * 4.0);
                    worldBox = String.format(java.util.Locale.ROOT, "%d %d %d %d %d %d", cx - 6, py + 5, cz - 6, cx + 6, py + 6, cz + 6);
                    worldBlock = "minecraft:oak_leaves";
                    cmds.add("fill " + worldBox + " minecraft:oak_leaves[persistent=true] replace minecraft:air");
                }
                case W_MINE -> {
                    // A plank at eye height just ahead, mined by hand in survival (three seconds).
                    w.cosmos.on.set(true);
                    pitch = 0f;
                    int bx = (int) Math.floor(mc.player.getX() + fx * 1.7), bz = (int) Math.floor(mc.player.getZ() + fz * 1.7);
                    worldBox = String.format(java.util.Locale.ROOT, "%d %d %d %d %d %d", bx, py + 1, bz, bx, py + 1, bz);
                    worldBlock = "minecraft:oak_planks";
                    modeWas = mc.gameMode != null ? mc.gameMode.getPlayerMode().getName() : "creative";
                    cmds.add("gamemode survival " + me);
                    cmds.add("item replace entity " + me + " weapon.mainhand with minecraft:air");
                    cmds.add("fill " + worldBox + " minecraft:oak_planks replace minecraft:air");
                }
                case W_ITEMS -> {
                    // A diamond and a block dropped from a block and a half up, three blocks ahead.
                    w.itemPhysics.set(true);
                    io.github.georgwav.cosmetics.render.ItemPhysics.laidFlat = 0;
                    pitch = 20f;
                    double ix = mc.player.getX() + fx * 3.0, iz = mc.player.getZ() + fz * 3.0;
                    cmds.add(String.format(java.util.Locale.ROOT,
                            "summon item %.2f %.2f %.2f {Item:{id:\"minecraft:diamond\",count:1},PickupDelay:32767,Motion:[0.0,0.1,0.0]}",
                            ix - fz * 0.5, mc.player.getY() + 1.5, iz + fx * 0.5));
                    cmds.add(String.format(java.util.Locale.ROOT,
                            "summon item %.2f %.2f %.2f {Item:{id:\"minecraft:grass_block\",count:1},PickupDelay:32767,Motion:[0.0,0.1,0.0]}",
                            ix + fz * 0.5, mc.player.getY() + 1.5, iz - fx * 0.5));
                }
                case W_OUTLINE -> {
                    // A block of stone ahead, looked at: the glow outline with the stars inside.
                    var o = Modules.OUTLINE;
                    o.setEnabled(true);
                    set(o, "outline_look", "glow");
                    set(o, "fill", true);
                    set(o, "fill_look", "stars");
                    io.github.georgwav.cosmetics.sol.fx.BlockHighlight.framesDrawn = 0;
                    pitch = 22f;
                    int bx = (int) Math.floor(mc.player.getX() + fx * 2.6), bz = (int) Math.floor(mc.player.getZ() + fz * 2.6);
                    worldBox = String.format(java.util.Locale.ROOT, "%d %d %d %d %d %d", bx, py, bz, bx, py, bz);
                    worldBlock = "minecraft:stone";
                    cmds.add("fill " + worldBox + " minecraft:stone replace minecraft:air");
                }
                case W_CHUNKS -> {
                    pitch = 6f;
                    worldMark = io.github.georgwav.cosmetics.render.ChunkRise.started;
                    if (io.github.georgwav.cosmetics.render.ChunkRise.works()) {
                        w.chunkRise.set(true);
                    }
                }
                default -> {
                    var cam = Modules.CAMERA;
                    cam.setEnabled(true);
                    set(cam, "glide", true);
                    worldMark = io.github.georgwav.cosmetics.client.modules.CameraTweaks.glides;
                    pitch = 10f;
                }
            }
            run(mc, cmds);
            worldTicks = 1;
            return;
        }
        worldTicks++;
        int t = worldTicks;
        boolean ok = false, over = t > 200, noted = false;
        switch (worldStep) {
            case W_SNOW -> {
                if (t > 4 && t <= 20) {
                    // A walk's pace to your right.
                    mc.player.setPos(mc.player.getX() - Math.cos(a) * 0.3, mc.player.getY(), mc.player.getZ() - Math.sin(a) * 0.3);
                }
                if (t == 24) {
                    ok = FxWorld.POOL.count("world_snow_print") >= 3;
                    FrameGrab.grab(mc, "fx-real-snow");
                    over = true;
                }
            }
            case W_RAIN -> {
                over = t > 600;
                if (worldPhase == 0 && mc.level.getRainLevel(1f) > 0.7f && FxWorld.POOL.count("world_ripple") > 0) {
                    FrameGrab.grab(mc, "fx-real-rain");
                    worldPhase = 1;
                    worldMark = WorldFx.rainbowsMade;
                    run(mc, List.of("weather clear"));
                } else if (worldPhase == 1 && WorldFx.rainbowsMade > worldMark) {
                    // Out over three seconds, opposite the morning sun: look up a little.
                    pitch = -16f;
                    worldPhase = 2;
                    worldMark = t;
                } else if (worldPhase == 2 && t - worldMark > 70) {
                    FrameGrab.grab(mc, "fx-real-rainbow");
                    ok = true;
                    over = true;
                }
                if (over && !ok) {
                    problems.add("fx real rain: rain " + mc.level.getRainLevel(1f) + ", " + WorldFx.ripplesMade + " ripples, "
                            + WorldFx.rainbowsMade + " rainbows (phase " + worldPhase + ")");
                    noted = true;
                }
                if (over) {
                    run(mc, List.of("weather clear"));
                    if (Atmosphere.INSTANCE != null) {
                        Atmosphere.INSTANCE.lockWeather.set(weatherLockWas);
                    }
                    SolScenario.realWeather = false;
                }
            }
            case W_LEAVES -> {
                if (worldPhase == 0 && FxWorld.POOL.count("world_leaf") > 0) {
                    worldPhase = 1;
                    worldMark = t;
                }
                if (worldPhase == 1 && t - worldMark > 30) {
                    FrameGrab.grab(mc, "fx-real-leaves");
                    ok = true;
                    over = true;
                }
                over |= t > 400;
            }
            case W_MINE -> {
                if (t > 4) {
                    holdAttack = true;
                    mc.options.keyAttack.setDown(true);
                }
                if (worldPhase == 0 && t > 4 + 34) {
                    // Most of the way: the block full of stars, its cracks glowing.
                    FrameGrab.grab(mc, "fx-real-cosmos");
                    worldPhase = 1;
                }
                if (worldPhase == 1 && FxWorld.POOL.count("world_star_burst") > 0) {
                    worldPhase = 2;
                    worldMark = t;
                    holdAttack = false;
                    mc.options.keyAttack.setDown(false);
                }
                if (worldPhase == 2 && t - worldMark >= 3) {
                    FrameGrab.grab(mc, "fx-real-cosmos-burst");
                    ok = WorldFx.cosmosFrames > 0;
                    if (!ok) {
                        problems.add("fx real mining: it burst, but the block of stars never drew");
                        noted = true;
                    }
                    over = true;
                }
                if (over) {
                    holdAttack = false;
                    mc.options.keyAttack.setDown(false);
                    List<String> back = new ArrayList<>(List.of("gamemode " + modeWas + " " + me, "kill @e[type=item]"));
                    run(mc, back);
                }
            }
            case W_ITEMS -> {
                if (t == 5) {
                    FrameGrab.grab(mc, "fx-real-items-fall");
                }
                if (t > 40) {
                    FrameGrab.grab(mc, "fx-real-items");
                    ok = io.github.georgwav.cosmetics.render.ItemPhysics.laidFlat > 0;
                    over = true;
                    run(mc, List.of("kill @e[type=item]"));
                    w.itemPhysics.set(false);
                }
            }
            case W_OUTLINE -> {
                if (t == 12) {
                    FrameGrab.grab(mc, "fx-real-outline");
                    ok = io.github.georgwav.cosmetics.sol.fx.BlockHighlight.framesDrawn > 0;
                    over = true;
                    Modules.OUTLINE.setEnabled(false);
                }
            }
            case W_CHUNKS -> {
                if (!io.github.georgwav.cosmetics.render.ChunkRise.works()) {
                    CosmeticsMod.LOGGER.info("[Sol] Effects phase: chunk rise isn't on this version (or Sodium draws the chunks)");
                    ok = true;
                    over = true;
                } else if (t == 4) {
                    // A fifth of a second in: everything still on its way up.
                    FrameGrab.grab(mc, "fx-real-chunks");
                } else if (t == 30) {
                    ok = io.github.georgwav.cosmetics.render.ChunkRise.started > worldMark;
                    over = true;
                    w.chunkRise.set(false);
                }
            }
            default -> {
                if (t == 2) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                } else if (t == 4) {
                    FrameGrab.grab(mc, "fx-real-glide-out");
                } else if (t == 12) {
                    mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                } else if (t == 14) {
                    FrameGrab.grab(mc, "fx-real-glide-in");
                } else if (t == 24) {
                    ok = io.github.georgwav.cosmetics.client.modules.CameraTweaks.glides >= worldMark + 2;
                    over = true;
                    Modules.CAMERA.setEnabled(false);
                }
            }
        }
        if (!ok && !over) {
            return;
        }
        if (!worldBox.isEmpty()) {
            run(mc, List.of("fill " + worldBox + " minecraft:air replace " + worldBlock));
            worldBox = "";
        }
        mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
        mc.player.setPos(walkX, walkY, walkZ);
        if (ok) {
            CosmeticsMod.LOGGER.info("[Sol] Effects phase: the real {} did its part after {} ticks", W_NAMES[worldStep], t);
        } else if (!noted) {
            problems.add("fx real " + W_NAMES[worldStep] + ": it didn't do its part");
        }
        worldStep++;
        worldTicks = 0;
        wait = 10;
    }

    private void finish(Minecraft mc) {
        done = true;
        FxWorld.hold = Float.NaN;
        FxWorld.POOL.clear();
        for (Case c : cases) {
            c.module().setEnabled(false);
        }
        Modules.KILL_FEED.setEnabled(false);
        holdAttack = false;
        mc.options.keyAttack.setDown(false);
        SolScenario.realWeather = false;
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
