package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Bolts;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
import io.github.georgwav.cosmetics.sol.fx.FxDraw;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxKind;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxOut;
import io.github.georgwav.cosmetics.sol.fx.FxPool;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxTex;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.SolPicks;
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.WorldOut;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.util.Colors;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Target (C.4; grey, rule 4): a mark on the one you're fighting (the last one you hit in three seconds, or the one you
 * look at), players only by default, flashing when it's hit: a turning Ring at its feet, a Jello ring sliding up and
 * down, a Sims plumbob, circling Wisps (a circle or a spiral), two Chains turning opposite ways, three Comets on tilted
 * orbits, tiny Lightning bolts on each hit, or a Halo of crystals. A green crystal over your friends (a list in the
 * profile; /friend adds them by name, from the tab list). And chams: players and crystals drawn in a flat colour, a
 * glowing fill, an outline or a pixel texture, in a colour each for friends, others and crystals. Everything is
 * depth-tested: only what you can already see is marked, never anything through walls, and only from what the game
 * shows you.
 */
public final class Target extends FxModule implements FxWorld.Layer {
    public static final List<String> MARK_STYLES = List.of("ring", "jello", "sims", "wisps", "chains", "comets", "lightning", "halo");
    private static final List<String> ONE = List.of("one");
    public static final int FRIEND_GREEN = 0xFF4CFF6A;

    public final FxSettings mark = fx("mark", MARK_STYLES, new FxSettings.Spec().toggle(true).random().noDuration().who(true));
    public final Setting.Choice markPath = add(new Setting.Choice("mark_path", "circle", List.of("circle", "spiral")).group("mark"));
    public final Setting.Choice markWhich = add(new Setting.Choice("mark_target", "both", List.of("both", "hit", "looking")).group("mark"));
    public final FxSettings friend = fx("friend", ONE, new FxSettings.Spec().toggle(true).noAmount().noDuration().noSpeed()
            .ownColour(FRIEND_GREEN));
    public final Setting.Text friends = add(new Setting.Text("friends", "", 600).group("friend"));
    public final Setting.Toggle chams = add(new Setting.Toggle("chams_on", false).group("chams"));
    public final Setting.Choice chamsLook = add(new Setting.Choice("chams_look", "glow", List.of("flat", "glow", "outline", "texture"))
            .group("chams"));
    public final Setting.Toggle chamsPlayers = add(new Setting.Toggle("chams_players", true).group("chams"));
    public final Setting.Toggle chamsCrystals = add(new Setting.Toggle("chams_crystals", true).group("chams"));
    public final Setting.Color chamsFriends = add(new Setting.Color("chams_friends", FRIEND_GREEN).themed().group("chams"));
    public final Setting.Color chamsOthers = add(new Setting.Color("chams_others", Setting.Color.THEME).themed().group("chams"));
    public final Setting.Color chamsCrystalColour = add(new Setting.Color("chams_crystal_colour", 0xFFFF5FD2).themed().group("chams"));
    public final Setting.Slider chamsOpacity = add(new Setting.Slider("chams_opacity", 60, 20, 100, 5, v -> Math.round(v) + "%")
            .group("chams"));

    /** The target: its id, and the last one you hit and when (ms). */
    private int targetId = Integer.MIN_VALUE, hitId = Integer.MIN_VALUE;
    private long hitAt;
    /** The mark's and the friend crystal's effect, kept (they follow their entities instead of playing once). */
    private final Fx markFx = new Fx(), friendFx = new Fx();
    private float markSince;
    private String friendsText;
    private final Set<String> friendSet = new HashSet<>();
    /** Each chammed entity's copy, by id, made again every frame from its pose. */
    private final Int2ObjectOpenHashMap<PoseSnapshot> copies = new Int2ObjectOpenHashMap<>();
    private static final FxTex WHITE = new FxTex("chams_white", 1, 1, new int[]{0xFFFFFFFF});
    /** The scenario's own check: chams and the friend marker on you too, in third person (nobody else is in its world). */
    public static boolean chamsYou;

    public Target() {
        super("target", Category.VIEW, "aim");
        markPath.shownWhen(() -> mark.on() && (mark.style.get().equals("wisps") || mark.random.get()));
        markWhich.shownWhen(mark::on);
        friends.shownWhen(friend::on);
        for (Setting<?> s : List.of(chamsLook, chamsPlayers, chamsCrystals, chamsFriends, chamsOthers, chamsCrystalColour, chamsOpacity)) {
            s.shownWhen(chams::get);
        }
        FxEvents.HIT.add(new FxEvents.Hit() {
            @Override
            public boolean listening() {
                return enabled() && mark.on();
            }

            @Override
            public void hit(LivingEntity target, double x, double y, double z, boolean crit, float healthBefore) {
                hitId = target.getId();
                hitAt = System.currentTimeMillis();
                // The flash, on the effects' clock.
                markFx.d = FxWorld.now();
            }
        });
        FxWorld.LAYERS.add(this);
        SolPicks.module(this);
        SolPicks.set(mark.on, true);
        SolPicks.set(mark.style, "ring");
        SolPicks.set(mark.random, false);
    }

    @Override
    protected void onDisable(Minecraft mc) {
        targetId = hitId = Integer.MIN_VALUE;
        copies.clear();
    }

    // ------------------------------------------------------------------ who

    /** Your friends, by name (any case), from the list in the profile. */
    public Set<String> friends() {
        String text = friends.get();
        if (!text.equals(friendsText)) {
            friendsText = text;
            friendSet.clear();
            for (String n : text.split("[,\\s]+")) {
                if (!n.isBlank()) {
                    friendSet.add(n.toLowerCase(Locale.ROOT));
                }
            }
        }
        return friendSet;
    }

    public boolean isFriend(Player p) {
        return friends().contains(p.getName().getString().toLowerCase(Locale.ROOT));
    }

    /** Adds or takes away a friend by name (/friend); returns whether the list changed. */
    public boolean setFriend(String name, boolean on) {
        String key = name.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty() || friends().contains(key) == on) {
            return false;
        }
        StringBuilder out = new StringBuilder();
        for (String n : friends.get().split("[,\\s]+")) {
            if (!n.isBlank() && !n.toLowerCase(Locale.ROOT).equals(key)) {
                out.append(out.length() == 0 ? "" : ", ").append(n);
            }
        }
        if (on) {
            out.append(out.length() == 0 ? "" : ", ").append(name.trim());
        }
        friends.set(out.toString());
        return true;
    }

    @Override
    protected void tick(Minecraft mc) {
        Player you = mc.player;
        int id = Integer.MIN_VALUE;
        if (you != null && mc.level != null && mark.on()) {
            String which = markWhich.get();
            if (!which.equals("looking") && System.currentTimeMillis() - hitAt < 3000L && ok(mc.level.getEntity(hitId))) {
                id = hitId;
            } else if (!which.equals("hit") && mc.crosshairPickEntity instanceof LivingEntity l && ok(l)) {
                id = l.getId();
            }
        }
        if (id != targetId) {
            targetId = id;
            markSince = FxWorld.now();
        }
        if (id != Integer.MIN_VALUE) {
            mark.apply(markFx, mark.pick(id));
            markFx.kind = MARK;
            markFx.i1 = markPath.get().equals("spiral") ? 1 : 0;
            markFx.calm = FxWorld.calm();
        }
        if (friend.on()) {
            friend.apply(friendFx, 0);
            friendFx.calm = FxWorld.calm();
        }
        if (mc.level == null || !chams.get()) {
            copies.clear();
        } else if (copies.size() > 64) {
            copies.clear();
        }
    }

    private boolean ok(Entity e) {
        return e instanceof LivingEntity l && l.isAlive() && !l.isInvisible() && mark.allows(l) && l != Minecraft.getInstance().player;
    }

    // ------------------------------------------------------------------ drawing in the world (FxWorld.Layer)

    @Override
    public boolean active() {
        return enabled() && (targetId != Integer.MIN_VALUE || friend.on() && !friends().isEmpty() || chams.get());
    }

    @Override
    public void draw(WorldOut out, float now) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float pt = FrameInfo.partialTick;
        if (chams.get()) {
            drawChams(mc, out, pt);
        }
        if (targetId != Integer.MIN_VALUE && mc.level.getEntity(targetId) instanceof LivingEntity t) {
            follow(markFx, t, pt);
            markFx.start = markSince;
            markFx.life = 1e6f;
            markFx.end = markSince + 1e6f;
            MARK.draw(markFx, now - markSince, now, out);
        }
        if (friend.on() && !friends().isEmpty()) {
            boolean you = chamsYou && !mc.options.getCameraType().isFirstPerson();
            for (Player p : mc.level.players()) {
                if ((p != mc.player || you) && !p.isInvisible() && p.distanceToSqr(mc.player) < 96 * 96 && isFriend(p)) {
                    follow(friendFx, p, pt);
                    friendFx.start = 0f;
                    friendFx.life = 1e6f;
                    friendFx.end = 1e6f;
                    FRIEND.draw(friendFx, now, now, out);
                }
            }
        }
    }

    /** Puts an effect at an entity's feet as it's drawn this frame; its height and width in a and b. */
    private static void follow(Fx fx, Entity e, float pt) {
        fx.x = e.xo + (e.getX() - e.xo) * pt;
        fx.y = e.yo + (e.getY() - e.yo) * pt;
        fx.z = e.zo + (e.getZ() - e.zo) * pt;
        fx.a = e.getBbHeight();
        fx.b = e.getBbWidth();
    }

    private void drawChams(Minecraft mc, WorldOut out, float pt) {
        float opacity = (float) (chamsOpacity.get() / 100.0);
        String look = chamsLook.get();
        // Every look fills (or outlines) in the body pass; Glowing adds light over its fill in the glow pass.
        boolean glow = out.glowPass();
        if (glow && !look.equals("glow")) {
            return;
        }
        boolean you = chamsYou && !mc.options.getCameraType().isFirstPerson();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e == mc.player && !you || e.isInvisible() || e.distanceToSqr(mc.player) > 64 * 64) {
                continue;
            }
            int colour;
            if (e instanceof Player p && chamsPlayers.get()) {
                colour = (isFriend(p) ? chamsFriends : chamsOthers).argb() | 0xFF000000;
            } else if (e instanceof EndCrystal c && chamsCrystals.get()) {
                drawCrystal(out, c, pt, chamsCrystalColour.argb() | 0xFF000000, opacity, look);
                continue;
            } else {
                continue;
            }
            PoseSnapshot copy = copies.get(e.getId());
            if (copy == null) {
                copy = new PoseSnapshot();
                copies.put(e.getId(), copy);
            }
            copy.capture(e, pt, false);
            double x = e.xo + (e.getX() - e.xo) * pt, y = e.yo + (e.getY() - e.yo) * pt, z = e.zo + (e.getZ() - e.zo) * pt;
            out.origin(x, y, z);
            switch (look) {
                case "flat" -> {
                    out.bias = 0.012f;
                    copy.drawFaces(out, 0f, 0f, 0f, 1f, WHITE, Colors.withAlpha(colour, opacity), false);
                }
                case "glow" -> {
                    out.bias = 0.012f;
                    copy.drawFaces(out, 0f, 0f, 0f, 1f, WHITE, Colors.withAlpha(colour, glow ? opacity * 0.45f : opacity * 0.75f), false);
                }
                case "texture" -> {
                    out.bias = 0.012f;
                    copy.drawFaces(out, 0f, 0f, 0f, 1f, copy.tinted(colour, 1f), Colors.withAlpha(0xFFFFFFFF, Math.min(1f, opacity + 0.2f)),
                            false);
                }
                default -> {
                    // The faces turned away of a copy a little bigger: they show round the body's edges only.
                    out.bias = 0f;
                    float k = 1.07f;
                    copy.drawFaces(out, 0f, -copy.height * (k - 1f) / 2f, 0f, k, WHITE, Colors.withAlpha(colour, Math.min(1f, opacity + 0.3f)),
                            true);
                }
            }
            out.bias = 0f;
        }
    }

    /** A crystal's chams: its turning glass cube and its core, as the game turns and bobs them. */
    private static void drawCrystal(WorldOut out, EndCrystal c, float pt, int colour, float opacity, String look) {
        float time = c.time + pt;
        float f = (float) Math.sin(time * 0.2f) / 2f + 0.5f;
        float bob = (f * f + f) * 0.4f - 1.4f;
        double x = c.xo + (c.getX() - c.xo) * pt, y = c.yo + (c.getY() - c.yo) * pt + 2f + bob, z = c.zo + (c.getZ() - c.zo) * pt;
        out.origin(x, y, z);
        float yaw = (float) Math.toRadians(time * 3f);
        boolean back = look.equals("outline");
        int argb = Colors.withAlpha(colour, look.equals("glow") ? out.glowPass() ? opacity * 0.45f : opacity * 0.75f
                : back ? Math.min(1f, opacity + 0.3f) : opacity);
        out.bias = back ? 0f : 0.012f;
        cube(out, back ? 0.56f : 0.5f, yaw, argb, back);
        if (!back) {
            cube(out, 0.33f, -yaw * 1.3f, Colors.withAlpha(colour, Math.min(1f, opacity + 0.2f)), false);
        }
        out.bias = 0f;
    }

    private static final float[] CX = new float[8], CY = new float[8], CZ = new float[8];
    private static final int[][] FACES = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 4, 5, 1}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 5, 7, 3}};

    /**
     * A cube of half size {@code h} round the origin, turned {@code yaw} round the vertical and tipped onto a corner like
     * the game's crystal; its faces toward the camera (or away, for an outline).
     */
    private static void cube(WorldOut out, float h, float yaw, int argb, boolean back) {
        // Tip 54.7 degrees round the (1, 0, -1) axis so a corner points up, then turn.
        float tip = 0.9553f, ct = (float) Math.cos(tip), st = (float) Math.sin(tip);
        float cy = (float) Math.cos(yaw), sy = (float) Math.sin(yaw);
        float ax = 0.70710677f, az = -0.70710677f;
        for (int i = 0; i < 8; i++) {
            float px = (i & 4) == 0 ? -h : h, py = (i & 2) == 0 ? -h : h, pz = (i & 1) == 0 ? -h : h;
            // Rodrigues' rotation round (ax, 0, az).
            float dot = ax * px + az * pz;
            float crx = -az * py, cry = az * px - ax * pz, crz = ax * py;
            float rx = px * ct + crx * st + ax * dot * (1 - ct);
            float ry = py * ct + cry * st;
            float rz = pz * ct + crz * st + az * dot * (1 - ct);
            CX[i] = rx * cy - rz * sy;
            CY[i] = ry;
            CZ[i] = rx * sy + rz * cy;
        }
        float[] eye = EYE;
        out.eye(eye);
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        float u = (uv[0] + uv[2]) / 2f, v = (uv[1] + uv[3]) / 2f;
        for (int[] f : FACES) {
            float mx = (CX[f[0]] + CX[f[2]]) / 2f, my = (CY[f[0]] + CY[f[2]]) / 2f, mz = (CZ[f[0]] + CZ[f[2]]) / 2f;
            // The face's outward normal: from the middle out through its centre.
            boolean toward = mx * (eye[0] - mx) + my * (eye[1] - my) + mz * (eye[2] - mz) > 0f;
            if (toward == back) {
                continue;
            }
            for (int k = 0; k < 4; k++) {
                out.vertex(CX[f[k]], CY[f[k]], CZ[f[k]], u, v, argb);
            }
        }
    }

    private static final float[] EYE = new float[3];

    // ------------------------------------------------------------------ Try it and the preview

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        if (!mark.on()) {
            return 1f;
        }
        Fx f = start(pool, mark, MARK, dummy.x, dummy.y, dummy.z, 2.2f);
        mark.apply(f, mark.pick((int) (System.nanoTime() >>> 12)));
        f.i1 = markPath.get().equals("spiral") ? 1 : 0;
        f.a = dummy.body.height;
        f.b = 0.6f;
        // A hit half a second in: the flash (and Lightning's bolts).
        f.d = f.start + 0.5f;
        if (pool == FxWorld.POOL) {
            hitAt = 0L;
        }
        return f.life;
    }

    // ------------------------------------------------------------------ the kinds

    /** How hard a hit flashes {@code since} seconds after it: 1 at once, gone in a third of a second. */
    private static float hitFlash(float since) {
        if (since < 0f || since >= 0.35f) {
            return 0f;
        }
        float k = 1f - since / 0.35f;
        return k * k;
    }

    /**
     * The target mark, every style; fx.a and fx.b are the target's height and width, fx.d when it was last hit. Each
     * hit flashes it white-hot and throws a ring of light out round the body.
     */
    static final FxKind MARK = new FxKind("target_mark", 2) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.calm ? 0.3f : age * fx.speed;
            Vfx.origin(out, fx);
            float h = fx.a <= 0f ? 1.8f : fx.a, w = fx.b <= 0f ? 0.6f : fx.b;
            float a = Vfx.outCubic(Math.min(1f, age / 0.25f)) * fx.early(now);
            float gk = FxBits.glowK(fx), s = fx.size;
            boolean glow = out.glowPass();
            float hit = hitFlash(now - fx.d);
            int c = Vfx.mix(fx.tone(Ramp.MAIN, 0, now), 0xFFFFFFFF, 0.6f * hit), c2 = Vfx.mix(fx.tone(Ramp.MAIN, 1, now), 0xFFFFFFFF, 0.6f * hit);
            float lift = 1f + 0.8f * hit;
            if (hit > 0f && glow) {
                float k = 1f - (float) Math.sqrt(hit);
                Vfx.ring(out, FxSoft.FRONT, 0f, h * 0.5f, 0f, (w * 0.6f + 0.2f + 1.4f * k) * s, 0.5f * s, Vfx.with(Vfx.white(c, 0.4f), hit * a * gk), false);
                Vfx.glow(out, 0f, h * 0.55f, 0f, h * 0.9f, Vfx.with(c, 0.35f * hit * a * gk));
            }
            switch (fx.style) {
                case 0 -> runeRing(out, fx, t, a, gk, s, w, c, c2, lift);
                case 1 -> jello(out, t, a, gk, s, h, w, c, lift);
                case 2 -> {
                    float y = h + 0.6f * s + 0.06f * s * (float) Math.sin(t * 3.0);
                    float spin = t * 2.2f;
                    if (glow) {
                        Vfx.glow(out, 0f, y, 0f, 0.7f * s * lift, Vfx.with(c, 0.5f * a * gk));
                        Vfx.glint(out, 0.05f * s, y + 0.1f * s, 0f, 0.26f * s * (0.6f + 0.4f * (float) Math.sin(t * 5f)), t, Vfx.with(0xFFFFFFFF, 0.8f * a * gk));
                    }
                    Vfx.gem(out, 0f, y, 0f, 0.16f * s, 0.22f * s, 0.38f * s, 4, spin, Vfx.with(c, a));
                }
                case 3 -> wisps(out, fx, t, a, gk, s, h, w, c, c2, lift);
                case 4 -> chains(out, t, a, gk, s, h, w, c, lift);
                case 5 -> comets(out, fx, t, a, gk, s, h, w, c, c2, lift);
                case 6 -> lightning(out, fx, t, now, a, gk, s, h, c, lift);
                default -> halo(out, t, a, gk, s, h, c, c2, lift);
            }
        }
    };

    /** A ring of light at the feet with a rune circle turning inside it and a pulse running out every second or so. */
    private static void runeRing(FxOut out, Fx fx, float t, float a, float gk, float s, float w, int c, int c2, float lift) {
        float r = (w * 0.9f + 0.3f) * s;
        if (out.glowPass()) {
            Vfx.ring(out, FxSoft.TUBE, 0f, 0.04f, 0f, r, 0.24f * s * lift, Vfx.with(c, 0.85f * a * gk), true);
            Vfx.pool(out, 0f, 0f, 0f, r * 1.5f, Vfx.with(c, 0.4f * a * gk * lift));
            Vfx.decalGround(out, FxSoft.RUNES, 0f, 0f, 0f, r * 0.98f, t * 0.7f, Vfx.with(c2, 0.55f * a * gk));
            float p = (t % 1.3f) / 1.3f;
            Vfx.ring(out, FxSoft.FRONT, 0f, 0.05f, 0f, r * (1f + 0.7f * Vfx.outCubic(p)), 0.3f * s, Vfx.with(c, 0.6f * (1f - p) * (1f - p) * a * gk), true);
        } else {
            Vfx.ring(out, FxSoft.TUBE, 0f, 0.03f, 0f, r, 0.06f * s, Vfx.with(Vfx.white(c, 0.45f), 0.85f * a), true);
            Vfx.decalGround(out, FxSoft.RUNES, 0f, 0f, 0f, r * 0.98f, t * 0.7f, Vfx.with(c2, 0.45f * a));
        }
    }

    /** A hoop of light sliding up and down the body, squashing as it turns, with fainter copies trailing it. */
    private static void jello(FxOut out, float t, float a, float gk, float s, float h, float w, int c, float lift) {
        float r0 = (w * 0.8f + 0.22f) * s;
        for (int k = out.glowPass() ? 4 : 0; k >= 0; k--) {
            float tt = t - k * 0.045f;
            double ph = tt * Math.PI * 2.0 / 1.3;
            float y = h * (0.5f + 0.44f * (float) Math.sin(ph));
            // Wider where it turns, as if it had weight.
            float r = r0 * (1f + 0.12f * (float) Math.abs(Math.sin(ph)) - 0.05f);
            float ka = (1f - k * 0.2f) * a;
            if (out.glowPass()) {
                Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, (k == 0 ? 0.2f : 0.12f) * s * lift, Vfx.with(c, (k == 0 ? 0.9f : 0.35f) * ka * gk), false);
            } else {
                Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, 0.055f * s, Vfx.with(Vfx.white(c, 0.45f), 0.9f * ka), false);
            }
        }
    }

    /** Glowing wisps circling the body, each with a long soft tail (a circle, or a spiral up and down). */
    private static void wisps(FxOut out, Fx fx, float t, float a, float gk, float s, float h, float w, int c, int c2, float lift) {
        int n = Math.max(3, Math.min(6, Math.round(4 * fx.amount)));
        boolean spiral = fx.i1 == 1;
        float r = (w * 0.8f + 0.38f) * s;
        boolean glow = out.glowPass();
        for (int i = 0; i < n; i++) {
            int col = (i & 1) == 0 ? c : c2;
            float px = 0f, py = 0f, pz = 0f;
            for (int k = 0; k <= 10; k++) {
                float tt = t - k * 0.035f;
                double ang = tt * 2.3 + i * Math.PI * 2 / n;
                float y = spiral ? h * (0.5f + 0.42f * (float) Math.sin(tt * 1.4 + i * Math.PI * 2 / n))
                        : h * 0.6f + 0.1f * s * (float) Math.sin(tt * 3 + i);
                float x = (float) Math.cos(ang) * r, z = (float) Math.sin(ang) * r;
                if (k == 0) {
                    if (glow) {
                        Vfx.hot(out, x, y, z, 0.2f * s * lift, Vfx.with(Vfx.white(col, 0.25f), 0.85f * a * gk));
                        Vfx.glow(out, x, y, z, 0.6f * s, Vfx.with(col, 0.45f * a * gk));
                    } else {
                        Vfx.sprite(out, FxSoft.DOT, x, y, z, 0.05f * s, 0f, Vfx.with(Vfx.white(col, 0.3f), a));
                    }
                } else {
                    float f = 1f - k / 11f;
                    if (glow) {
                        Vfx.line(out, FxSoft.LINE, px, py, pz, x, y, z, 0.16f * s * f, Vfx.with(col, 0.55f * f * a * gk), Vfx.with(col, 0.5f * f * a * gk));
                    } else if (k < 6) {
                        Vfx.line(out, FxSoft.TUBE, px, py, pz, x, y, z, 0.035f * s * f, Vfx.with(col, 0.5f * f * a), Vfx.with(col, 0.45f * f * a));
                    }
                }
                px = x;
                py = y;
                pz = z;
            }
        }
    }

    /** Two rings of real chain, links interlocking, turning opposite ways round the body. */
    private static void chains(FxOut out, float t, float a, float gk, float s, float h, float w, int c, float lift) {
        float r = (w * 0.8f + 0.32f) * s;
        float link = 0.07f * s;
        int links = Math.max(12, Math.round((float) (2 * Math.PI * r / (link * 1.55f))));
        boolean glow = out.glowPass();
        int metal = Vfx.mix(c, 0xFFE4E8EE, 0.45f);
        for (int ring = 0; ring < 2; ring++) {
            float y = h * (ring == 0 ? 0.36f : 0.7f) + 0.03f * s * (float) Math.sin(t * 2f + ring * 2f);
            float turn = (ring == 0 ? 1f : -1f) * t * 0.9f;
            if (glow) {
                Vfx.ring(out, FxSoft.LINE, 0f, y, 0f, r, 0.32f * s * lift, Vfx.with(c, 0.3f * a * gk), false);
                Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, 0.1f * s * lift, Vfx.with(c, 0.35f * a * gk), false);
            }
            for (int i = 0; i < links; i++) {
                double ang = turn + i * Math.PI * 2 / links;
                float cx = (float) Math.cos(ang), cz = (float) Math.sin(ang);
                // Every other link turned a quarter round, so they hook into each other.
                float nx = (i & 1) == 0 ? cx : 0f, ny = (i & 1) == 0 ? 0f : 1f, nz = (i & 1) == 0 ? cz : 0f;
                // The links themselves over the world; their light is the band of glow round the ring.
                if (!glow) {
                    Vfx.hoop(out, FxSoft.TUBE, cx * r, y, cz * r, nx, ny, nz, link, 0.03f * s, Vfx.with(metal, 0.95f * a));
                }
            }
        }
    }

    /** Three comets on tilted orbits round the body, long tails of light behind them, shedding sparks. */
    private static void comets(FxOut out, Fx fx, float t, float a, float gk, float s, float h, float w, int c, int c2, float lift) {
        float r = (w * 0.9f + 0.5f) * s;
        boolean glow = out.glowPass();
        for (int i = 0; i < 3; i++) {
            double tilt = (i - 1) * 0.65, phase = i * 2.1;
            int col = i == 1 ? c2 : c;
            float px = 0f, py = 0f, pz = 0f;
            for (int k = 0; k <= 14; k++) {
                double ang = (t - k * 0.03f) * (2.1 + i * 0.3) + phase;
                float ox = (float) Math.cos(ang) * r, oz = (float) Math.sin(ang) * r;
                float y = h * 0.58f + (float) (oz * Math.sin(tilt));
                float z = (float) (oz * Math.cos(tilt));
                if (k == 0) {
                    if (glow) {
                        Vfx.hot(out, ox, y, z, 0.26f * s * lift, Vfx.with(Vfx.white(col, 0.35f), a * gk));
                        Vfx.glow(out, ox, y, z, 0.6f * s, Vfx.with(col, 0.35f * a * gk));
                    } else {
                        Vfx.sprite(out, FxSoft.DOT, ox, y, z, 0.055f * s, 0f, Vfx.with(Vfx.white(col, 0.4f), a));
                    }
                } else {
                    float f = 1f - k / 15f;
                    if (glow) {
                        Vfx.line(out, FxSoft.LINE, px, py, pz, ox, y, z, 0.2f * s * f, Vfx.with(col, 0.6f * f * a * gk), Vfx.with(col, 0.55f * f * a * gk));
                    } else if (k < 8) {
                        Vfx.line(out, FxSoft.TUBE, px, py, pz, ox, y, z, 0.04f * s * f, Vfx.with(col, 0.7f * f * a), Vfx.with(col, 0.65f * f * a));
                    }
                    // Sparks shed along the tail, drifting.
                    if (glow && k % 4 == 2) {
                        float j = Vfx.s(fx.seed, i * 20 + k + (int) (t * 8f)) * 0.08f * s;
                        Vfx.hot(out, ox + j, y - 0.04f * k * s, z - j, 0.06f * s, Vfx.with(Vfx.white(col, 0.5f), 0.7f * f * a * gk));
                    }
                }
                px = ox;
                py = y;
                pz = z;
            }
        }
    }

    private static final float[] END = new float[3];

    /** Arcs of lightning crackling round the head; each hit, a bolt strikes it from above. */
    private static void lightning(FxOut out, Fx fx, float t, float now, float a, float gk, float s, float h, int c, float lift) {
        boolean glow = out.glowPass();
        float head = h - 0.2f * s;
        if (glow) {
            Vfx.glow(out, 0f, head, 0f, 0.8f * s * lift, Vfx.with(c, (0.2f + 0.08f * (float) Math.sin(t * 13f)) * a * gk));
        }
        int frame = (int) (t * 14f);
        for (int i = 0; i < 4; i++) {
            int seed = frame * 7 + i * 131 + fx.seed;
            if (Vfx.h(seed, 0) < 0.25f) {
                continue;
            }
            double a0 = Vfx.h(seed, 1) * Math.PI * 2, a1 = a0 + 1.2 + Vfx.h(seed, 2) * 2.0;
            float r = (0.45f + 0.15f * Vfx.h(seed, 5)) * s;
            float y0 = head + Vfx.s(seed, 3) * 0.45f * s, y1 = head - 0.2f * s + Vfx.s(seed, 4) * 0.6f * s;
            Bolts.build(seed, (float) Math.cos(a0) * r, y0, (float) Math.sin(a0) * r, (float) Math.cos(a1) * r, y1, (float) Math.sin(a1) * r,
                    0.22f, 0);
            Bolts.draw(out, 0.028f * s, 0.2f * s, Vfx.with(Vfx.white(c, 0.7f), a * gk), Vfx.with(c, 0.55f * a * gk), 1f);
        }
        float since = now - fx.d;
        if (since >= 0f && since < 0.3f) {
            float k = since / 0.3f, f = (1f - k) * (1f - k) * a;
            int seed = (int) (fx.d * 1000f) + fx.seed;
            Bolts.build(seed, Vfx.s(seed, 9) * 0.8f * s, h + 3.2f * s, Vfx.s(seed, 10) * 0.8f * s, 0f, head, 0f, 0.16f, 2);
            Bolts.draw(out, 0.045f * s, 0.4f * s, Vfx.with(0xFFFFFFFF, f * Math.max(0.6f, gk)), Vfx.with(c, 0.75f * f * gk),
                    Math.min(1f, since / 0.05f));
            if (glow) {
                Vfx.hot(out, 0f, head, 0f, 0.9f * s, Vfx.with(Vfx.white(c, 0.7f), f * gk));
                Vfx.flare(out, 0f, head, 0f, 1.4f * s, 0.2f, Vfx.with(Vfx.white(c, 0.5f), 0.8f * f * gk));
            }
        }
    }

    /** A halo of light over the head with six small crystals turning round it, glinting. */
    private static void halo(FxOut out, float t, float a, float gk, float s, float h, int c, int c2, float lift) {
        float y = h + 0.3f * s + 0.03f * s * (float) Math.sin(t * 2f);
        float r = 0.38f * s;
        boolean glow = out.glowPass();
        int gold = Vfx.white(c, 0.25f);
        if (glow) {
            Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, 0.16f * s * lift, Vfx.with(gold, 0.95f * a * gk), false);
            Vfx.glow(out, 0f, y, 0f, 0.75f * s, Vfx.with(c, 0.35f * a * gk));
        } else {
            Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, 0.05f * s, Vfx.with(Vfx.white(c, 0.55f), a), false);
        }
        for (int i = 0; i < 6; i++) {
            double ang = t * 1.1 + i * Math.PI / 3;
            float x = (float) Math.cos(ang) * (r + 0.12f * s), z = (float) Math.sin(ang) * (r + 0.12f * s);
            float gy = y + 0.04f * s * (float) Math.sin(t * 3f + i);
            int col = (i & 1) == 0 ? c : c2;
            if (glow) {
                float tw = 0.5f + 0.5f * (float) Math.sin(t * 4f + i * 1.7f);
                Vfx.glint(out, x, gy, z, 0.12f * s * tw, t + i, Vfx.with(0xFFFFFFFF, 0.8f * tw * a * gk));
            }
            Vfx.gem(out, x, gy, z, 0.05f * s, 0.07f * s, 0.1f * s, 4, t * 2f + i, Vfx.with(col, a));
        }
    }

    /** The friend marker: a green gem turning over the head, bobbing, glowing, with a ring of light under it. */
    static final FxKind FRIEND = new FxKind("target_friend", 1) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            Vfx.origin(out, fx);
            float h = fx.a <= 0f ? 1.8f : fx.a, s = fx.size;
            float t = fx.calm ? 0f : now;
            float y = h + 0.55f * s + 0.05f * s * (float) Math.sin(t * 2.4);
            int c = fx.tone(Ramp.MAIN, 0, now);
            float gk = FxBits.glowK(fx);
            if (out.glowPass()) {
                Vfx.glow(out, 0f, y, 0f, 0.5f * s, Vfx.with(c, 0.45f * gk));
                Vfx.ring(out, FxSoft.TUBE, 0f, y - 0.3f * s, 0f, 0.13f * s, 0.06f * s, Vfx.with(c, 0.5f * gk), false);
                Vfx.glint(out, 0.03f * s, y + 0.07f * s, 0f, 0.16f * s * (0.6f + 0.4f * (float) Math.sin(t * 4.3f)), t, Vfx.with(0xFFFFFFFF, 0.7f * gk));
            }
            Vfx.gem(out, 0f, y, 0f, 0.1f * s, 0.14f * s, 0.22f * s, 6, t * 1.6f, c);
        }
    };
}
