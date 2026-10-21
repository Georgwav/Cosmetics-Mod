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
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
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

    /**
     * Pop ghost: a ring of gold light bursts from the feet, and the body's golden spirit lifts out, shrinking and
     * fading as sparkles drift up round it.
     */
    static final FxKind GHOST = new FxKind("totem_ghost", 6) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float e = fx.early(now), gk = FxBits.glowK(fx), sz = fx.size;
            int c = fx.tone(Ramp.MAIN, 0, now);
            float h = fx.copy != null ? fx.copy.height : 1.8f;
            float k = Math.min(1f, t / 1f);
            if (t < 0.5f) {
                float rk = t / 0.5f;
                if (out.glowPass()) {
                    Vfx.ring(out, FxSoft.FRONT, 0f, 0.05f, 0f, (0.3f + 1.8f * Vfx.outCubic(rk)) * sz, 0.5f * sz,
                            Vfx.with(Vfx.white(c, 0.3f), (1f - rk) * e * gk), true);
                    Vfx.hot(out, 0f, h * 0.55f, 0f, 1.1f * sz, Vfx.with(Vfx.white(c, 0.6f), (1f - rk) * (1f - rk) * e * gk));
                }
            }
            float rise = 1.1f * Vfx.outCubic(k) * sz;
            float shrink = 1f - 0.4f * Vfx.inOut(k);
            float a = Vfx.life(t, 1f, 0.05f, 0.65f) * e;
            // A column of light carrying it up, and streaks racing up through it.
            if (out.glowPass()) {
                float col = Vfx.life(t, 1f, 0.06f, 0.6f) * e * gk;
                Vfx.line(out, FxSoft.LINE, 0f, 0f, 0f, 0f, h * 1.9f * sz, 0f, 1.3f * sz, Vfx.with(Vfx.white(c, 0.4f), 0.45f * col),
                        Vfx.with(c, 0f));
                Vfx.pool(out, 0f, 0f, 0f, 1.5f * sz, Vfx.with(c, 0.55f * col));
                for (int i = 0; i < 10; i++) {
                    float p = (t * (1.4f + 0.8f * Vfx.h(fx.seed, i + 70)) + Vfx.h(fx.seed, i + 80)) % 1f;
                    double ang = Vfx.h(fx.seed, i + 90) * Math.PI * 2;
                    float r = (0.25f + 0.3f * Vfx.h(fx.seed, i + 95)) * sz;
                    float y = p * h * 1.8f * sz;
                    Vfx.streak(out, (float) Math.cos(ang) * r, y, (float) Math.sin(ang) * r, 0f, 1f, 0f, 0.5f * sz, 0.06f * sz,
                            Vfx.with(Vfx.white(c, 0.5f), (float) Math.sin(p * Math.PI) * col));
                }
            }
            FxBits.spirit(out, fx, 0f, rise + h * (1f - shrink) * 0.5f * sz, 0f, sz * shrink, c, a);
            FxBits.embers(out, fx, t, 0f, 0.3f, 0f, Math.round(26 * fx.amount), 0.5f * sz, 1.6f, 0.4f, 0.9f, c, sz, 2);
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

    /**
     * Shatter: the totem pops in glowing, cracks of light run through it, and it bursts in a flash into gold shards that
     * tumble down, catching the light, with sparks and a ring of light.
     */
    static final FxKind SHATTER = new FxKind("totem_shatter", 6) {
        private final float[] p = new float[3];

        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float px = 0.75f * T * fx.size;
            float e = fx.early(now), gk = FxBits.glowK(fx);
            int c = fx.tone(Ramp.MAIN, 0, now);
            float hold = 0.35f;
            if (t < hold) {
                float pop = Ramp.pop(t, 0.15f, false);
                float bob = 0.04f * (float) Math.sin(t * 18f);
                if (out.glowPass()) {
                    Vfx.glow(out, 0f, bob, 0f, 0.55f * fx.size * pop, Vfx.with(c, 0.6f * e * gk));
                    float crack = Math.max(0f, (t - 0.18f) / (hold - 0.18f));
                    if (crack > 0f) {
                        Vfx.sprite(out, FxSoft.CRACKS, 0f, bob, 0f, 0.4f * fx.size * crack, fx.seed, Vfx.with(0xFFFFF1C2, crack * e * gk));
                    }
                } else {
                    FxDraw.sprite(out, fx, FxSprites.TOTEM, 0f, bob, 0f, px * pop, 0, e, now);
                }
                return;
            }
            float dt = t - hold;
            FxBits.impact(out, fx, dt, 0f, 0f, 0f, fx.c, 0.4f * fx.size, c);
            FxBits.sparks(out, fx, dt, 0f, 0f, 0f, Math.round(24 * fx.amount), 4.5f * fx.size, 0.4f, -9f, 2f, 0.7f, c, fx.size, fx.c, 1);
            float life = 1.6f - hold;
            float a = Math.min(1f, (life - dt) / 0.45f) * e;
            if (a <= 0f) {
                return;
            }
            out.right(RR);
            float rx = RR[0], rz = RR[2];
            float l = (float) Math.sqrt(rx * rx + rz * rz);
            rx /= Math.max(1e-4f, l);
            rz /= Math.max(1e-4f, l);
            int n = fx.i0 * 2;
            for (int i = 0; i < n; i++) {
                shard(fx, i, dt, px, p);
                float x = p[0] * rx - p[2] * rz, z = p[0] * rz + p[2] * rx, y = p[1];
                boolean resting = p[1] <= fx.c + 1e-4f;
                float spin = (resting ? 0.6f : dt) * (6f + 8f * FxDraw.hash(fx.seed, i + 50)) + i;
                float size = (0.035f + 0.04f * FxDraw.hash(fx.seed, i + 60)) * fx.size;
                if (out.glowPass()) {
                    float shine = (float) Math.pow(Math.max(0f, Math.sin(spin * 1.6f)), 10f);
                    if (!resting && shine > 0.05f) {
                        Vfx.glint(out, x, y, z, size * 4.5f, spin, Vfx.with(0xFFFFF4C8, shine * a * gk));
                    }
                    Vfx.glow(out, x, y, z, size * 2.5f, Vfx.with(c, 0.3f * a * gk));
                } else {
                    float flip = 0.3f + 0.7f * Math.abs((float) Math.cos(spin));
                    int col = Vfx.mix(Vfx.white(c, 0.35f), Vfx.mix(c, 0xFF000000, 0.35f), FxDraw.hash(fx.seed, i + 70));
                    Vfx.billboard(out, FxSoft.SHARD[i % 3], x, y, z, size * flip, size * 1.3f, spin * 0.3f, Vfx.with(col, a));
                }
            }
        }
    };

    /**
     * Angel: holy light bursts out behind whoever popped and a totem rises out of them on feathered wings that beat
     * twice, a glowing halo over it, gold dust drifting down from it.
     */
    static final FxKind ANGEL = new FxKind("totem_angel", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float px = 0.6f * T * fx.size;
            float sz = fx.size, e = fx.early(now), gk = FxBits.glowK(fx);
            boolean glow = out.glowPass();
            int c = fx.tone(Ramp.MAIN, 0, now);
            float rise = 1.5f * Vfx.outCubic(t / 1.4f) * sz;
            float a = Vfx.life(t, 1.4f, 0.12f, 0.45f) * e;
            if (a <= 0f) {
                return;
            }
            out.right(RR);
            out.up(UU);
            // A beam of holy light down onto you, and the light it throws on the ground.
            if (glow) {
                float beam = Vfx.life(t, 1.4f, 0.08f, 0.7f) * e;
                Vfx.line(out, FxSoft.LINE, 0f, rise + 4f * sz, 0f, 0f, -1f, 0f, 1.5f * sz, Vfx.with(Vfx.white(c, 0.6f), 0f),
                        Vfx.with(Vfx.white(c, 0.5f), 0.35f * beam * gk));
                Vfx.line(out, FxSoft.TUBE, 0f, rise + 4f * sz, 0f, 0f, -1f, 0f, 0.35f * sz, Vfx.with(0xFFFFFFFF, 0f),
                        Vfx.with(0xFFFFFFFF, 0.45f * beam * gk));
                Vfx.pool(out, 0f, -1f, 0f, 1.6f * sz, Vfx.with(c, 0.5f * beam * gk));
                Vfx.glow(out, 0f, rise, 0f, 1.8f * sz, Vfx.with(Vfx.white(c, 0.4f), 0.45f * a * gk));
                Vfx.glint(out, 0f, rise, 0f, 2.4f * sz * (0.9f + 0.1f * (float) Math.sin(t * 6f)), t * 0.4f,
                        Vfx.with(Vfx.white(c, 0.6f), 0.6f * a * gk));
                if (t < 0.4f) {
                    float k = t / 0.4f;
                    Vfx.hot(out, 0f, rise, 0f, 1.6f * sz, Vfx.with(0xFFFFFFFF, (1f - k) * (1f - k) * e * gk));
                }
            }
            // Wings: two strong beats in the first second, then held open, breathing.
            float beat = t < 1f ? 0.5f + 0.5f * (float) Math.cos(t * Math.PI * 4) : 0.85f + 0.15f * (float) Math.sin(t * 3f);
            float spread = Vfx.outBack(Math.min(1f, t / 0.25f));
            int white = Vfx.white(fx.ramp2[Ramp.MAIN], 0.7f), shade = Vfx.white(fx.ramp2[Ramp.MAIN], 0.45f);
            for (int side = -1; side <= 1; side += 2) {
                float elev = (float) Math.toRadians(-20 + 55 * beat);
                // Shoulder, elbow and wrist, in the picture's plane (x across, y up).
                float sx = side * 0.12f * sz, sy = rise + 0.08f * sz;
                float ex = sx + side * (float) Math.cos(elev) * 0.42f * sz * spread, ey = sy + (float) Math.sin(elev) * 0.42f * sz * spread;
                float elev2 = elev + (float) Math.toRadians(18 - 30 * beat);
                float wx = ex + side * (float) Math.cos(elev2) * 0.4f * sz * spread, wy = ey + (float) Math.sin(elev2) * 0.4f * sz * spread;
                // Secondaries along the arm, hanging down and back; primaries fanning from the wrist; coverts over them.
                for (int layer = 0; layer < 3; layer++) {
                    int n = layer == 1 ? 7 : layer == 0 ? 11 : 9;
                    for (int f = 0; f < n; f++) {
                        float k = f / (n - 1f);
                        float bx, by, dirA, len;
                        if (layer == 0) {
                            bx = sx + (wx - sx) * (0.1f + 0.85f * k);
                            by = sy + (wy - sy) * (0.1f + 0.85f * k);
                            dirA = (float) Math.toRadians(-90 + 25 * k);
                            len = (0.42f + 0.12f * k) * sz;
                        } else if (layer == 1) {
                            bx = wx - side * 0.04f * sz * k;
                            by = wy - 0.03f * sz * k;
                            dirA = elev2 + (float) Math.toRadians(5 - 85 * k);
                            len = (0.75f - 0.25f * k) * sz;
                        } else {
                            bx = sx + (wx - sx) * (0.05f + 0.9f * k);
                            by = sy + (wy - sy) * (0.05f + 0.9f * k) + 0.02f * sz;
                            dirA = (float) Math.toRadians(-80 + 30 * k);
                            len = (0.24f + 0.06f * k) * sz;
                        }
                        len *= spread;
                        float dx = side * (float) Math.cos(dirA), dy = (float) Math.sin(dirA);
                        float cx = bx + dx * len * 0.5f, cy = by + dy * len * 0.5f;
                        float x = RR[0] * cx, z = RR[2] * cx;
                        float rot = (float) Math.atan2(-dx, dy);
                        float w = len * (layer == 2 ? 0.36f : layer == 0 ? 0.3f : 0.22f);
                        if (glow) {
                            if (f % 2 == 0) {
                                Vfx.glow(out, x, cy, z, len * 0.8f, Vfx.with(c, 0.16f * a * gk));
                            }
                        } else {
                            Vfx.billboard(out, FxSoft.FEATHER, x, cy, z, w, len * 0.5f, rot, Vfx.with(layer == 2 ? white : shade, a));
                        }
                    }
                }
            }
            if (!glow) {
                FxDraw.sprite(out, fx, FxSprites.TOTEM, 0f, rise, 0f, px, 0, a, now);
            }
            // The halo.
            float hy = rise + 7.5f * px;
            if (glow) {
                Vfx.ring(out, FxSoft.TUBE, 0f, hy, 0f, 0.18f * sz, 0.08f * sz, Vfx.with(Vfx.white(c, 0.5f), a * gk), false);
                Vfx.glow(out, 0f, hy, 0f, 0.4f * sz, Vfx.with(c, 0.4f * a * gk));
            } else {
                Vfx.ring(out, FxSoft.TUBE, 0f, hy, 0f, 0.18f * sz, 0.04f * sz, Vfx.with(Vfx.white(c, 0.4f), 0.9f * a), false);
            }
            // Gold dust and loose feathers drifting down.
            for (int i = 0; i < 30; i++) {
                float born = FxDraw.hash(fx.seed, i) * 1.1f;
                float tt = t - born;
                if (tt < 0f || tt > 1f) {
                    continue;
                }
                float bx = FxDraw.signed(fx.seed, i + 10) * 1.1f * sz, bz = FxDraw.signed(fx.seed, i + 20) * 0.3f * sz;
                float by = 1.5f * Vfx.outCubic(born / 1.4f) * sz + 0.3f * sz - tt * 0.7f;
                bx += 0.08f * sz * (float) Math.sin(tt * 5f + i);
                float da = (1f - tt) * a;
                float x = RR[0] * bx + bz * RR[2], z = RR[2] * bx - bz * RR[0];
                if (i % 5 == 0) {
                    if (!glow) {
                        Vfx.billboard(out, FxSoft.FEATHER, x, by, z, 0.03f * sz, 0.08f * sz, (float) Math.sin(tt * 4f + i) * 0.8f,
                                Vfx.with(white, da));
                    }
                } else if (glow) {
                    Vfx.hot(out, x, by, z, 0.09f * sz, Vfx.with(Vfx.white(c, 0.5f), da * gk));
                }
            }
        }
    };
}
