package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
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

    private static final float T = FxDraw.TEXEL;
    private static final float[] RR = new float[3];

    /** A ring of texel cells round the middle at height {@code y}: dashes turning, banded core to shade, brighter on a hit. */
    private static void ring(FxOut out, Fx fx, float y, float radius, float turn, boolean flash, float alpha, float now) {
        boolean glow = out.glowPass();
        if (glow && fx.glow == 0) {
            return;
        }
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        int steps = Math.max(16, (int) Math.ceil(2.0 * Math.PI * radius / T));
        int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        int shift = (int) Math.floor(turn * steps / (2.0 * Math.PI));
        for (int s = 0; s < steps; s++) {
            int dash = Math.floorMod(s + shift, 8);
            if (dash >= 6) {
                continue;
            }
            double ang = s * 2.0 * Math.PI / steps;
            int gx = Math.round((float) Math.cos(ang) * radius / T), gz = Math.round((float) Math.sin(ang) * radius / T);
            if (gx == lastX && gz == lastZ) {
                continue;
            }
            lastX = gx;
            lastZ = gz;
            float x = gx * T, z = gz * T;
            int tone = flash ? Ramp.CORE : dash < 2 ? Ramp.CORE : dash < 4 ? Ramp.MAIN : Ramp.SHADE;
            if (glow) {
                float k = fx.glow == 2 || flash ? 1.7f : 1f;
                FxDraw.flat(out, x, y + 0.01f, z, T, T, uv, Colors.withAlpha(fx.glowColour, Math.min(1f, 0.22f * k * alpha)));
            } else {
                FxDraw.flat(out, x, y + 0.006f, z, T / 2f, T / 2f, uv, Colors.withAlpha(fx.tone(tone, s, now), alpha));
            }
        }
    }

    /** A small bolt of texels from {@code top} down to {@code bottom} over the middle, facing the camera. */
    private static void bolt(FxOut out, Fx fx, float top, float bottom, int seed, float alpha, float now) {
        out.right(RR);
        float x = 0f, y = top;
        int steps = 8;
        for (int k = 0; k < steps; k++) {
            float ny = top + (bottom - top) * (k + 1) / steps;
            float nx = k == steps - 1 ? 0f : FxDraw.signed(seed, k) * 0.12f;
            for (int j = 0; j < 3; j++) {
                float px = x + (nx - x) * j / 3f, py = y + (ny - y) * j / 3f;
                float wx = Ramp.snap(RR[0] * px, fx.snap), wz = Ramp.snap(RR[2] * px, fx.snap);
                FxDraw.square(out, wx, Ramp.snap(py, fx.snap), wz, T * fx.size, Colors.withAlpha(fx.tone(Ramp.CORE, k, now), alpha));
                FxDraw.squareGlow(out, fx, wx, py, wz, T * fx.size * 2f, alpha);
            }
            x = nx;
            y = ny;
        }
    }

    /** The target mark, every style; fx.a and fx.b are the target's height and width, fx.d when it was last hit. */
    static final FxKind MARK = new FxKind("target_mark", 2) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.calm ? 0.3f : age * fx.speed;
            FxDraw.origin(out, fx);
            float h = fx.a <= 0f ? 1.8f : fx.a, w = fx.b <= 0f ? 0.6f : fx.b;
            float in = Ramp.steps(Math.min(1f, age / 0.2f), 3, fx.snap);
            float a = in * fx.early(now);
            boolean flash = now - fx.d >= 0f && now - fx.d < 0.2f;
            float size = fx.size;
            switch (fx.style) {
                case 0 -> ring(out, fx, 0.02f, (w * 0.9f + 0.25f) * size, t * 1.6f, flash, a, now);
                case 1 -> {
                    float y = h * (0.5f + 0.45f * (float) Math.sin(t * Math.PI * 2.0 / 1.2));
                    ring(out, fx, Ramp.snap(y, fx.snap), (w * 0.8f + 0.2f) * size, t * 1.2f, flash, a, now);
                }
                case 2 -> {
                    float y = Ramp.snap(h + 0.45f + 0.06f * (float) Math.sin(t * 3.0), fx.snap);
                    float spin = (float) Math.cos(t * 2.5);
                    FxDraw.spriteScaled(out, fx, FxSprites.PLUMBOB, 0f, y, 0f, 0.8f * T * size, Ramp.steps(Math.abs(spin), 3, fx.snap)
                            * Math.signum(spin == 0f ? 1f : spin), 1f, flash ? 1 : 0, a, now);
                }
                case 3 -> {
                    int n = Math.max(3, Math.min(6, Math.round(4 * fx.amount)));
                    boolean spiral = fx.i1 == 1;
                    float r = (w * 0.8f + 0.35f) * size;
                    for (int i = 0; i < n; i++) {
                        for (int k = 3; k >= 0; k--) {
                            float tt = t - k * 0.06f;
                            double ang = tt * 2.2 + i * Math.PI * 2 / n;
                            float y = spiral ? h * (0.5f + 0.42f * (float) Math.sin(tt * 1.4 + i * Math.PI * 2 / n)) : h * 0.6f
                                    + 0.08f * (float) Math.sin(tt * 3 + i);
                            float x = Ramp.snap((float) Math.cos(ang) * r, fx.snap), z = Ramp.snap((float) Math.sin(ang) * r, fx.snap);
                            float yy = Ramp.snap(y, fx.snap);
                            if (k == 0) {
                                FxDraw.sprite(out, fx, FxSprites.ORB, x, yy, z, 0.6f * T * size, i, a, now);
                            } else {
                                int tone = k == 1 ? Ramp.MAIN : Ramp.SHADE;
                                FxDraw.square(out, x, yy, z, T * size, Colors.withAlpha(fx.tone(flash ? Ramp.CORE : tone, i, now),
                                        a * (1f - k * 0.22f)));
                            }
                        }
                    }
                }
                case 4 -> {
                    float r = (w * 0.8f + 0.3f) * size;
                    for (int ring = 0; ring < 2; ring++) {
                        float y = Ramp.snap(h * (ring == 0 ? 0.35f : 0.7f), fx.snap);
                        float turn = (ring == 0 ? 1f : -1f) * t * 1.2f;
                        int links = 10;
                        for (int i = 0; i < links; i++) {
                            double ang = turn + i * Math.PI * 2 / links;
                            float x = Ramp.snap((float) Math.cos(ang) * r, fx.snap), z = Ramp.snap((float) Math.sin(ang) * r, fx.snap);
                            FxDraw.sprite(out, fx, FxSprites.CHAIN, x, y, z, 0.7f * T * size, i + ring, a, now);
                        }
                    }
                }
                case 5 -> {
                    float r = (w * 0.9f + 0.45f) * size;
                    for (int i = 0; i < 3; i++) {
                        double tilt = (i - 1) * 0.6, phase = i * 2.1;
                        for (int k = 5; k >= 0; k--) {
                            double ang = (t - k * 0.05f) * (2.0 + i * 0.3) + phase;
                            float ox = (float) Math.cos(ang) * r, oz = (float) Math.sin(ang) * r;
                            // Tilted orbit: lift by the angle round its own axis.
                            float oy = (float) (oz * Math.sin(tilt));
                            oz = (float) (oz * Math.cos(tilt));
                            float x = Ramp.snap(ox, fx.snap), y = Ramp.snap(h * 0.6f + oy, fx.snap), z = Ramp.snap(oz, fx.snap);
                            if (k == 0) {
                                FxDraw.sprite(out, fx, FxSprites.ORB, x, y, z, 0.55f * T * size, i, a, now);
                            } else {
                                FxDraw.square(out, x, y, z, T * size, Colors.withAlpha(fx.tone(k < 3 ? Ramp.MAIN : Ramp.SHADE, i, now),
                                        a * (1f - k * 0.15f)));
                            }
                        }
                    }
                }
                case 6 -> {
                    // Crackling at the head; bolts strike it for a quarter second after each hit.
                    float since = now - fx.d;
                    if (since >= 0f && since < 0.25f) {
                        float ba = Ramp.steps(1f - since / 0.25f, 3, fx.snap) * a;
                        bolt(out, fx, h + 1.3f * size, h + 0.05f, (int) (fx.d * 100f), ba, now);
                        bolt(out, fx, h + 1.0f * size, h + 0.1f, (int) (fx.d * 100f) + 7, ba * 0.8f, now);
                    }
                    for (int i = 0; i < 4; i++) {
                        double ang = t * 3.0 + i * Math.PI / 2;
                        float flick = FxDraw.hash((int) (t * 12f), i) > 0.4f ? 1f : 0f;
                        float x = Ramp.snap((float) Math.cos(ang) * 0.3f * size, fx.snap), z = Ramp.snap((float) Math.sin(ang) * 0.3f * size, fx.snap);
                        FxDraw.sprite(out, fx, FxSprites.PLUS, x, Ramp.snap(h + 0.15f, fx.snap), z, 0.4f * T * size, i, a * flick, now);
                    }
                }
                default -> {
                    float y = Ramp.snap(h + 0.28f, fx.snap);
                    float r = 0.3f * size;
                    for (int i = 0; i < 6; i++) {
                        double ang = t * 1.1 + i * Math.PI / 3;
                        float x = Ramp.snap((float) Math.cos(ang) * r, fx.snap), z = Ramp.snap((float) Math.sin(ang) * r, fx.snap);
                        FxDraw.sprite(out, fx, FxSprites.CRYSTAL, x, y, z, 0.45f * T * size, flash ? 0 : i, a, now);
                    }
                    ring(out, fx, y - 0.12f, r, t * 1.1f, flash, a * 0.7f, now);
                }
            }
        }
    };

    /** The friend marker: a green crystal turning over the head, bobbing a little. */
    static final FxKind FRIEND = new FxKind("target_friend", 1) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            FxDraw.origin(out, fx);
            float h = fx.a <= 0f ? 1.8f : fx.a;
            float t = fx.calm ? 0f : now;
            float y = Ramp.snap(h + 0.55f + 0.05f * (float) Math.sin(t * 2.4), fx.snap);
            float spin = (float) Math.cos(t * 1.8);
            FxDraw.spriteScaled(out, fx, FxSprites.CRYSTAL, 0f, y, 0f, 0.7f * T * fx.size, Ramp.steps(Math.abs(spin), 3, fx.snap)
                    * (spin < 0f ? -1f : 1f), 1f, 0, 1f, now);
        }
    };
}
