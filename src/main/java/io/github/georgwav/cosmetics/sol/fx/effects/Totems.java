package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.FxColour;
import io.github.georgwav.cosmetics.sol.fx.FxDraw;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxKind;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxOut;
import io.github.georgwav.cosmetics.sol.fx.FxPool;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxScreen;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.SolPicks;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.render.SoftRaster;
import io.github.georgwav.cosmetics.ui.TilePictures;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Totems (C.4): what a totem pop looks like. The floating totem on your screen in the game's own way or one of Sol's
 * (Roll, Rocket, Fall, Spin, Zoom, Fade), placed, sized and timed as you like, or hidden; the popping player's gold copy
 * rising and fading (Pop ghost); a two-step flash round the screen's edge in your colour when you pop; the pop
 * particles in two colours of yours fading to a third; your totem count under the crosshair while you hold one; the
 * totem breaking into gold shards that tumble to the ground (Shatter); and a winged totem with a halo rising out of
 * whoever popped (Angel). Only on your screen.
 */
public final class Totems extends FxModule {
    public static final List<String> POP_STYLES = List.of("vanilla", "roll", "rocket", "fall", "spin", "zoom", "fade", "hidden");
    public static final int GOLD = 0xFFFFC23D;
    private static final List<String> ONE = List.of("one");
    private static final String TEXTURE = "minecraft:textures/item/totem_of_undying.png";

    public final Setting.Choice popStyle = add(new Setting.Choice("pop_style", "vanilla", POP_STYLES).group("totem_pop"));
    public final Setting.Slider popScale = add(new Setting.Slider("pop_scale", 100, 25, 200, 5, v -> Math.round(v) + "%").group("totem_pop"));
    public final Setting.Slider popX = add(new Setting.Slider("pop_x", 0, -100, 100, 5, Totems::signed).group("totem_pop"));
    public final Setting.Slider popY = add(new Setting.Slider("pop_y", 0, -100, 100, 5, Totems::signed).group("totem_pop"));
    public final Setting.Slider popDuration = add(new Setting.Slider("pop_duration", 100, 50, 200, 10, v -> Math.round(v) + "%")
            .group("totem_pop"));
    public final FxSettings ghost = fx("ghost", ONE, new FxSettings.Spec().toggle(true).noAmount().who(true).firstPerson().ownColour(GOLD));
    public final FxSettings shatter = fx("shatter", ONE, new FxSettings.Spec().toggle(true).who(true).ownColour(GOLD));
    public final FxSettings angel = fx("angel", ONE, new FxSettings.Spec().toggle(false).noAmount().who(true).firstPerson().ownColour(GOLD));
    public final Setting.Toggle flash = add(new Setting.Toggle("flash_on", true).group("totem_flash"));
    public final Setting.Color flashColour = add(new Setting.Color("flash_colour", Setting.Color.THEME).themed().group("totem_flash"));
    public final Setting.Toggle particles = add(new Setting.Toggle("pop_particles_on", true).group("totem_particles"));
    public final Setting.Color particleA = add(new Setting.Color("pop_particles_a", Setting.Color.THEME).themed().group("totem_particles"));
    public final Setting.Color particleB = add(new Setting.Color("pop_particles_b", GOLD).themed().group("totem_particles"));
    public final Setting.Color particleFade = add(new Setting.Color("pop_particles_fade", 0xFFFFF4D6).themed().group("totem_particles"));
    public final Setting.Toggle count = add(new Setting.Toggle("count_on", true).group("totem_count"));

    /** Your floating totem: when it started on the effects' clock (NaN for none), and its picture. */
    private float popAt = Float.NaN;
    private SolTexture popTexture;
    private final int[] particleColours = new int[3];
    private static final float[] XS = new float[4], YS = new float[4], LX = new float[4], LY = new float[4];
    private ItemStack countIcon;

    public Totems() {
        super("totems", Category.VIEW, "totem");
        for (Setting<?> s : List.of(popX, popY, popDuration)) {
            s.shownWhen(() -> !popStyle.get().equals("vanilla") && !popStyle.get().equals("hidden"));
        }
        popScale.shownWhen(() -> !popStyle.get().equals("hidden"));
        flashColour.shownWhen(flash::get);
        for (Setting<?> s : List.of(particleA, particleB, particleFade)) {
            s.shownWhen(particles::get);
        }
        FxEvents.TOTEM.add(new FxEvents.Totem() {
            @Override
            public boolean listening() {
                return enabled();
            }

            @Override
            public void popped(LivingEntity who, int n) {
                PoseSnapshot copy = ghost.on() ? PoseSnapshot.of(who, 1f) : null;
                pop(FxWorld.POOL, who, copy, who.getX(), who.getY(), who.getZ(), who.getYRot(), who.getBbHeight());
            }
        });
        SolPicks.module(this);
        SolPicks.set(shatter.on, true);
        SolPicks.set(ghost.on, true);
    }

    private static String signed(double v) {
        long r = Math.round(v);
        return (r > 0 ? "+" : "") + r + "%";
    }

    // ------------------------------------------------------------------ the game's hooks

    /** Whether the game's floating totem isn't drawn (one of Sol's takes its place, or none): TotemSizeMixin. */
    public static boolean replacesPop() {
        Totems t = Modules.TOTEMS;
        return t.enabled() && !t.popStyle.get().equals("vanilla");
    }

    /** The game's floating totem's size (1 = its own): TotemSizeMixin. */
    public static float vanillaScale() {
        Totems t = Modules.TOTEMS;
        return t.enabled() ? (float) (t.popScale.get() / 100.0) : 1f;
    }

    /** The pop particles' two colours and the one they fade to, or null for the game's own: TotemParticleMixin. */
    public static int[] particleColours() {
        Totems t = Modules.TOTEMS;
        if (!t.enabled() || !t.particles.get()) {
            return null;
        }
        t.particleColours[0] = t.particleA.argb() & 0xFFFFFF;
        t.particleColours[1] = t.particleB.argb() & 0xFFFFFF;
        t.particleColours[2] = t.particleFade.argb() & 0xFFFFFF;
        return t.particleColours;
    }

    // ------------------------------------------------------------------ playing

    /** A pop by {@code who} (null: the dummy) at its feet (x, y, z), {@code height} tall. */
    private void pop(FxPool pool, LivingEntity who, PoseSnapshot copy, double x, double y, double z, float yaw, float height) {
        Minecraft mc = Minecraft.getInstance();
        boolean you = who != null && who == mc.player;
        boolean firstPerson = you && mc.options.getCameraType().isFirstPerson();
        float popLength = 0f;
        if (you) {
            popLength = startPop();
            if (flash.get() && !FxWorld.calm()) {
                FxScreen.edgeFlash(flashColour.argb() | 0xFF000000, 0.09f, 0.55f, 0.25f);
            }
        }
        if (ghost.on() && copy != null && (who == null || ghost.allows(who)) && (!firstPerson || ghost.inFirstPerson())) {
            Fx f = start(pool, ghost, GHOST, x, y, z, 1f);
            ghost.apply(f, 0);
            f.copy = copy;
        }
        if (shatter.on() && (who == null || shatter.allows(who))) {
            double sx = x, sy = y + height + 0.55, sz = z;
            float delay = 0f;
            if (firstPerson) {
                // Your own: the floating totem breaking where it was, as it ends.
                Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
                sx = eye.x + look.x * 1.6;
                sy = eye.y + look.y * 1.6 - 0.1;
                sz = eye.z + look.z * 1.6;
                delay = popStyle.get().equals("vanilla") ? 1.6f : popStyle.get().equals("hidden") ? 0f : popLength * 0.85f;
            }
            Fx f = start(pool, shatter, SHATTER, sx, sy, sz, 1.6f);
            shatter.apply(f, 0);
            f.c = Math.min(-0.2f, ground(pool, sx, sz, sy) - (float) sy);
            f.i0 = Math.max(12, Math.min(20, Math.round(16 * f.amount)));
            FxPool.delay(f, delay);
        }
        if (angel.on() && (who == null || angel.allows(who)) && (!firstPerson || angel.inFirstPerson())) {
            Fx f = start(pool, angel, ANGEL, x, y + height * 0.55, z, 1.4f);
            angel.apply(f, 0);
            Ramp.of(0xFFF4F7FF, f.ramp2);
            f.two = true;
        }
    }

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        Minecraft mc = Minecraft.getInstance();
        if (pool == FxWorld.POOL && mc.player != null) {
            startPop();
            if (flash.get() && !FxWorld.calm()) {
                FxScreen.edgeFlash(flashColour.argb() | 0xFF000000, 0.09f, 0.55f, 0.25f);
            }
        }
        pop(pool, null, dummy.body, dummy.x, dummy.y, dummy.z, dummy.yaw, dummy.body.height);
        // As long as the longest of what's on.
        float life = 0.5f;
        if (pool == FxWorld.POOL && !Float.isNaN(popAt)) {
            life = Math.max(life, popLength());
        }
        if (ghost.on()) {
            life = Math.max(life, ghost.life(1f));
        }
        if (shatter.on()) {
            life = Math.max(life, shatter.life(1.6f));
        }
        if (angel.on()) {
            life = Math.max(life, angel.life(1.4f));
        }
        return life;
    }

    // ------------------------------------------------------------------ your floating totem (Sol's styles)

    /** Starts your floating totem in Sol's style; returns how long it plays (0 for the game's own, or none). */
    private float startPop() {
        String s = popStyle.get();
        if (s.equals("vanilla") || s.equals("hidden")) {
            popAt = Float.NaN;
            return 0f;
        }
        popAt = FxWorld.now();
        SoftRaster.Texture t = TilePictures.read(TEXTURE);
        int w = t.width(), h = Math.min(t.height(), t.width());
        int[] px = t.argb().length == w * h ? t.argb() : java.util.Arrays.copyOf(t.argb(), w * h);
        if (popTexture == null || popTexture.released() || popTexture.width != w || popTexture.height != h) {
            if (popTexture != null) {
                popTexture.release();
            }
            popTexture = SolTexture.of("totem_pop", w, h, false, px);
        } else {
            popTexture.upload(px);
        }
        return popLength();
    }

    private float popLength() {
        return FxWorld.calm() ? 0.6f : 1.2f * (float) (popDuration.get() / 100.0);
    }

    /** Over the HUD (Hud.draw): your floating totem in Sol's style while it plays. */
    public static void drawHud(Canvas c) {
        Totems t = Modules.TOTEMS;
        if (!t.enabled() || Float.isNaN(t.popAt)) {
            return;
        }
        float age = FxWorld.now() - t.popAt;
        if (age < 0f || age >= t.popLength()) {
            if (age >= t.popLength()) {
                t.popAt = Float.NaN;
            }
            return;
        }
        io.github.georgwav.cosmetics.sol.hud.SolHud.custom(c, Totems::drawPop);
    }

    private static float ease(float k) {
        k = Math.max(0f, Math.min(1f, k));
        return 1f - (1f - k) * (1f - k) * (1f - k);
    }

    /** Overshoots a little past 1 and settles. */
    private static float back(float k) {
        k = Math.max(0f, Math.min(1f, k));
        float c = 1.7f;
        float u = k - 1f;
        return 1f + (c + 1f) * u * u * u + c * u * u;
    }

    private static void drawPop(Gfx g) {
        Totems t = Modules.TOTEMS;
        Minecraft mc = Minecraft.getInstance();
        float length = t.popLength();
        float p = FxWorld.calm() ? 0.5f : Math.min(1f, (FxWorld.now() - t.popAt) / length);
        float W = mc.getWindow().getGuiScaledWidth(), H = mc.getWindow().getGuiScaledHeight();
        float size = 0.3f * H * (float) (t.popScale.get() / 100.0);
        float cx = W / 2f + (float) (t.popX.get() / 100.0) * W / 2f, cy = H / 2f - (float) (t.popY.get() / 100.0) * H / 2f;
        float in = Math.min(1f, p / 0.25f), out = Math.max(0f, (p - 0.75f) / 0.25f);
        float dx = 0f, dy = 0f, rot = 0f, sx = 1f, sy = 1f, alpha = 1f;
        switch (t.popStyle.get()) {
            case "roll" -> {
                dx = -(1f - ease(in)) * 0.7f * W + out * out * 0.7f * W;
                rot = (float) (-(1f - ease(in)) * Math.PI * 2 + out * out * Math.PI * 2);
            }
            case "rocket" -> {
                dy = (1f - back(in)) * 0.7f * H - out * out * 0.9f * H;
                rot = (float) (0.06 * Math.sin(p * 30.0) * (1f - in));
            }
            case "fall" -> {
                float u = Math.min(1f, p / 0.5f);
                float b = u < 0.5f ? 1f - (u / 0.5f) * (u / 0.5f)
                        : u < 0.8f ? 0.22f * (1f - square((u - 0.65f) / 0.15f)) : 0.05f * (1f - square((u - 0.9f) / 0.1f));
                dy = -Math.max(0f, b) * 0.7f * H + out * out * 0.8f * H;
            }
            case "spin" -> {
                float k = ease(Math.min(1f, p / 0.6f));
                sx = (float) Math.cos(k * Math.PI * 6.0);
                float pop = Ramp.pop(p * length, 0.2f, true);
                sx *= pop;
                sy = pop;
                alpha = Ramp.steps(1f - out, 4, true);
            }
            case "zoom" -> {
                float s = p < 0.25f ? 2.4f - 1.5f * ease(in) : p < 0.35f ? 0.9f + (p - 0.25f) : 1f;
                s *= 1f - out;
                sx = sy = s;
            }
            default -> {
                alpha = Ramp.steps(in, 4, true) * Ramp.steps(1f - out, 4, true);
                sx = sy = 0.85f + 0.15f * ease(in);
            }
        }
        if (alpha <= 0f || Math.abs(sx) < 1e-3f || sy <= 0f) {
            return;
        }
        // Whole screen pixels per texel of the picture when it's upright.
        float u = g.d.u;
        int tex = t.popTexture == null ? 16 : t.popTexture.width;
        float texel = Math.max(1f, Math.round(size * u / tex));
        float half = texel * tex / 2f;
        float ox = Math.round(g.d.sx(cx + dx)), oy = Math.round(g.d.sy(cy + dy));
        float cs = (float) Math.cos(rot), sn = (float) Math.sin(rot);
        LX[0] = LX[1] = -half * sx;
        LX[2] = LX[3] = half * sx;
        LY[0] = LY[3] = -half * sy;
        LY[1] = LY[2] = half * sy;
        for (int i = 0; i < 4; i++) {
            XS[i] = ox + LX[i] * cs - LY[i] * sn;
            YS[i] = oy + LX[i] * sn + LY[i] * cs;
        }
        g.imageQuad(t.popTexture, XS, YS, Colors.withAlpha(0xFFFFFFFF, alpha), false);
    }

    private static float square(float v) {
        return v * v;
    }

    /** Your totem count under the crosshair while you hold one (with the crosshair, in GUI units). */
    public static void drawCount(Canvas g, Minecraft mc) {
        Totems t = Modules.TOTEMS;
        Player you = mc.player;
        if (!t.enabled() || !t.count.get() || you == null
                || !you.getMainHandItem().is(Items.TOTEM_OF_UNDYING) && !you.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return;
        }
        int n = 0;
        var inv = you.getInventory();
        for (int i = 0, size = inv.getContainerSize(); i < size; i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(Items.TOTEM_OF_UNDYING)) {
                n += s.getCount();
            }
        }
        if (n == 0) {
            return;
        }
        if (t.countIcon == null) {
            t.countIcon = new ItemStack(Items.TOTEM_OF_UNDYING);
        }
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        String s = String.valueOf(n);
        int tw = mc.font.width(s);
        int x = w / 2 - (8 + 1 + tw) / 2, y = h / 2 + 9;
        g.push();
        g.translate(x, y);
        g.scale(0.5f, 0.5f);
        g.item(t.countIcon, 0, 0);
        g.pop();
        g.text(mc.font, s, x + 9, y, FxColour.accent(), true);
    }

    // ------------------------------------------------------------------ the kinds

    private static final float T = FxDraw.TEXEL;
    private static final float[] RR = new float[3], UU = new float[3];

    /** Pop ghost: the copy, tinted gold, rises a block, shrinks to 60% in steps and fades out in a second. */
    static final FxKind GHOST = new FxKind("totem_ghost", 6) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass() || fx.copy == null) {
                return;
            }
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float k = Math.min(1f, t / 1f);
            float rise = Ramp.snap(1f * KillEffects.easeOut(k), fx.snap);
            float shrink = 1f - 0.4f * Ramp.steps(k, 4, fx.snap);
            float a = Ramp.steps(1f - Math.max(0f, (k - 0.3f) / 0.7f), 4, fx.snap) * 0.85f * fx.early(now);
            fx.copy.draw(out, 0f, rise, 0f, fx.size * shrink, 0f, fx.copy.tinted(fx.tone(Ramp.MAIN, 0, now), 0.6f),
                    Colors.withAlpha(0xFFFFFFFF, a), PoseSnapshot.WHOLE, 0f);
        }
    };

    /** Where shard {@code i} is at {@code dt} after the break (into {@code p}): out from the totem, falling, resting. */
    static void shard(Fx fx, int i, float dt, float px, float[] p) {
        float hx = FxDraw.signed(fx.seed, i * 3), hy = FxDraw.signed(fx.seed, i * 3 + 1), hz = FxDraw.hash(fx.seed, i * 3 + 2);
        // Starting on the totem (in the camera's plane, filled in by the caller), flying out from its middle.
        float vx = hx * 1.6f, vz = (hz - 0.5f) * 1.6f, vy = 0.8f + hy * 0.9f + 0.6f;
        float g = 7f;
        float y0 = hy * 5f * px;
        float c = fx.c - y0;
        float land = (vy + (float) Math.sqrt(vy * vy - 2f * g * c)) / g;
        float tt = Math.min(dt, land);
        p[0] = hx * 4f * px + vx * tt;
        p[1] = y0 + Math.max(c, vy * tt - 0.5f * g * tt * tt);
        p[2] = vz * tt;
    }

    /** Shatter: the totem pops in, then breaks into gold texel shards that tumble to the ground and fade. */
    static final FxKind SHATTER = new FxKind("totem_shatter", 6) {
        private final float[] p = new float[3];

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float px = 0.75f * T * fx.size;
            float early = fx.early(now);
            if (t < 0.35f) {
                float pop = Ramp.pop(t, 0.15f, fx.snap);
                // Its glow at half, so the gold shows.
                FxDraw.sprite(out, fx, FxSprites.TOTEM, 0f, 0f, 0f, px * pop, 0, out.glowPass() ? early * 0.5f : early, now);
                return;
            }
            float dt = t - 0.35f;
            if (dt < 0.08f) {
                // The break: a bright plus where it was.
                FxDraw.sprite(out, fx, FxSprites.PLUS, 0f, 0f, 0f, px * 1.5f, 0, early, now);
            }
            float a = Ramp.steps(Math.min(1f, (1.6f - t) / 0.45f), 4, fx.snap) * early;
            if (a <= 0f) {
                return;
            }
            out.right(RR);
            for (int i = 0; i < fx.i0; i++) {
                shard(fx, i, dt, px, p);
                // Across the view (the camera's right, flattened) and toward or away from it.
                float rx = RR[0], rz = RR[2];
                float l = (float) Math.sqrt(rx * rx + rz * rz);
                rx /= Math.max(1e-4f, l);
                rz /= Math.max(1e-4f, l);
                float x = p[0] * rx - p[2] * rz, z = p[0] * rz + p[2] * rx;
                x = Ramp.snap(x, fx.snap);
                float y = Ramp.snap(p[1], fx.snap);
                z = Ramp.snap(z, fx.snap);
                // Tumbling: each shard turns between its two sizes and its tones as it falls, until it rests.
                int flip = (int) (dt * 12f + i) & 1;
                boolean resting = p[1] <= fx.c + 1e-4f;
                float side = T * fx.size * (resting || flip == 0 ? 1f : 2f);
                int tone = resting ? Ramp.SHADE : flip == 0 ? Ramp.CORE : Ramp.MAIN;
                FxDraw.square(out, x, y, z, side, Colors.withAlpha(fx.tone(tone, i, now), a));
                if (tone == Ramp.CORE) {
                    FxDraw.squareGlow(out, fx, x, y, z, side, a * 0.7f);
                }
            }
        }
    };

    /** Angel: a winged totem with a halo rises out of whoever popped, flaps twice and fades. */
    static final FxKind ANGEL = new FxKind("totem_angel", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float px = 0.6f * T * fx.size;
            float rise = Ramp.snap(1.6f * KillEffects.easeOut(t / 1.4f), fx.snap);
            float a = Ramp.steps(Math.min(1f, (1.4f - t) / 0.45f), 4, fx.snap) * Ramp.steps(Math.min(1f, t / 0.15f), 3, fx.snap)
                    * fx.early(now);
            if (a <= 0f) {
                return;
            }
            out.right(RR);
            // Two flaps in the first second: the wings fold in and open out in three steps.
            float flap = (float) Math.abs(Math.cos(Math.min(t, 1f) * Math.PI * 2.0));
            float open = 0.35f + 0.65f * Ramp.steps(flap, 3, fx.snap);
            float off = (4.5f + 3f * open) * px;
            // Its glow at half, so the gold and the white show.
            float g = out.glowPass() ? a * 0.5f : a;
            FxDraw.sprite(out, fx, FxSprites.TOTEM, 0f, rise, 0f, px, 0, g, now);
            FxDraw.spriteScaled(out, fx, FxSprites.WING, -RR[0] * off, rise + 2f * px, -RR[2] * off, px, open, 1f, 1, g, now);
            FxDraw.spriteScaled(out, fx, FxSprites.WING, RR[0] * off, rise + 2f * px, RR[2] * off, px, -open, 1f, 1, g, now);
            // The halo: a flat ring of texels over its head.
            FxDraw.ring(out, fx, fx.x, fx.y, fx.z, 0f, rise + 7.5f * px, 0f, 3.2f * px, 1, Ramp.CORE, a, false, now);
        }
    };
}
