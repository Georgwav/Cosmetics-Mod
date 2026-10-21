package io.github.georgwav.cosmetics.sol.fx.effects;

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
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.SkinPixels;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Trails (C.4): what follows things through the air, only on your screen.
 * <ul>
 * <li>Projectile trails: arrows, ender pearls, tridents and thrown potions leave trails of texels (dots, sparkles,
 * hearts or Sol's glyph) that fade in 0.6 s, yours only by default.</li>
 * <li>Behind you, hidden in first person unless asked: a Ribbon of whole-texel colour bands from your waist or feet
 * (the preset's ramp, a rainbow, or blue, pink and white), stepping up and down a texel like the Nyan Cat's; a Firefly
 * tail of blinking texel dots left drifting where you went; a Ghost trail of 3 to 6 copies of you, a fifth of a second
 * apart, in colours shifting round yours; Footsteps, pixel prints that fade after a while; and Elytra contrails, two
 * streams from the wing tips that close in as the wings fold, with a two-step afterburner flare and a puff of smoke on
 * every rocket.</li>
 * </ul>
 * Every piece is laid in the world a tick at a time where you were, so the trails stay put as you move on; the
 * newest piece grows through the tick to reach you.
 */
public final class Trails extends FxModule {
    public static final List<String> PROJECTILE_STYLES = List.of("dots", "sparkles", "hearts", "glyphs");
    public static final List<String> RIBBON_STYLES = List.of("theme_bands", "rainbow_bands", "pastel_bands");
    private static final List<String> ONE = List.of("one");

    public final FxSettings projectiles = fx("projectile", PROJECTILE_STYLES, new FxSettings.Spec().toggle(true).noSpeed());
    public final Setting.Choice projectileWho = add(new Setting.Choice("projectile_whose", "yours", List.of("yours", "everyone"))
            .group("projectile"));
    public final Setting.Toggle arrows = add(new Setting.Toggle("projectile_arrows", true).group("projectile"));
    public final Setting.Toggle pearls = add(new Setting.Toggle("projectile_pearls", true).group("projectile"));
    public final Setting.Toggle tridents = add(new Setting.Toggle("projectile_tridents", true).group("projectile"));
    public final Setting.Toggle potions = add(new Setting.Toggle("projectile_potions", true).group("projectile"));

    /** Its size is the bands' width (2 texels each at 100%) and its duration the length (1.5 s of your way). */
    public final FxSettings ribbon = fx("ribbon", RIBBON_STYLES, new FxSettings.Spec().toggle(true).noSpeed().noAmount().firstPerson());
    public final Setting.Choice ribbonFrom = add(new Setting.Choice("ribbon_from", "waist", List.of("waist", "feet")).group("ribbon"));
    public final FxSettings firefly = fx("firefly", ONE, new FxSettings.Spec().toggle(false).noSpeed().firstPerson());
    /** Its duration is how far apart in time the copies are. */
    public final FxSettings ghost = fx("ghost_trail", ONE, new FxSettings.Spec().toggle(false).noSpeed().noAmount().noGlow().firstPerson());
    public final Setting.Slider ghostCount = add(new Setting.Slider("ghost_trail_count", 4, 3, 6, 1, v -> String.valueOf(Math.round(v)))
            .group("ghost_trail"));
    public final FxSettings footsteps = fx("footsteps", ONE, new FxSettings.Spec().toggle(false).noSpeed().noAmount().noGlow()
            .firstPerson());
    public final FxSettings contrails = fx("contrails", ONE, new FxSettings.Spec().toggle(true).noSpeed().noAmount().firstPerson());
    public final Setting.Toggle rocketFlare = add(new Setting.Toggle("contrails_rocket", true).group("contrails"));

    /** Each tracked projectile's place last tick, by entity id. */
    private final Int2ObjectOpenHashMap<double[]> last = new Int2ObjectOpenHashMap<>();
    private int sweep;

    /** Your trails' state: ticks counted, where you were last tick, and each trail's last point. */
    private int ticks, flyTicks;
    private boolean have, ribbonHave, tipsHave;
    private double px, py, pz;
    private final double[] ribbonLast = new double[3];
    private final double[] tipsLast = new double[6];
    private final double[] tipsNow = new double[6];
    private float fireflyDue;
    private int ghostDue, ghostSeq;
    private double walked;
    private boolean leftFoot;
    /** The ghosts' copies, reused in turn (more than can be alive at once). */
    private final PoseSnapshot[] ghosts = new PoseSnapshot[8];
    private int ghostNext;

    public Trails() {
        super("trails", Category.VIEW, "trail");
        for (Setting<?> s : List.of(projectileWho, arrows, pearls, tridents, potions)) {
            s.shownWhen(projectiles.on::get);
        }
        ribbonFrom.shownWhen(ribbon.on::get);
        ghostCount.shownWhen(ghost.on::get);
        rocketFlare.shownWhen(contrails.on::get);
        FxEvents.ELYTRA.add(new FxEvents.Elytra() {
            @Override
            public boolean listening() {
                return enabled() && contrails.on() && rocketFlare.get();
            }

            @Override
            public void opened(Player p) {
            }

            @Override
            public void closed(Player p) {
            }

            @Override
            public void rocket(Player p) {
                Minecraft mc = Minecraft.getInstance();
                if (p == mc.player && shows(contrails, mc)) {
                    Vec3 look = p.getLookAngle();
                    Trails.this.rocket(FxWorld.POOL, p.getX() - look.x * 0.3, p.getY() + 0.3 - look.y * 0.3, p.getZ() - look.z * 0.3, 0f);
                }
            }
        });
    }

    @Override
    protected void onDisable(Minecraft mc) {
        last.clear();
        forget();
    }

    private void forget() {
        have = ribbonHave = tipsHave = false;
        flyTicks = 0;
        walked = 0;
    }

    /** Whether one of your trails shows now: not in first person unless it's set to. */
    private static boolean shows(FxSettings fs, Minecraft mc) {
        return fs.on() && (!mc.options.getCameraType().isFirstPerson() || fs.inFirstPerson());
    }

    @Override
    protected void tick(Minecraft mc) {
        projectileTick(mc);
        youTick(mc);
    }

    // ------------------------------------------------------------------ projectiles

    /** Which trail a projectile gets, by its type: 1 arrow, 2 pearl, 3 trident, 4 potion, 0 none. */
    private int kind(Entity e) {
        String id = String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()));
        return switch (id) {
            case "minecraft:arrow", "minecraft:spectral_arrow" -> arrows.get() ? 1 : 0;
            case "minecraft:ender_pearl" -> pearls.get() ? 2 : 0;
            case "minecraft:trident" -> tridents.get() ? 3 : 0;
            case "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion" -> potions.get() ? 4 : 0;
            default -> 0;
        };
    }

    private void projectileTick(Minecraft mc) {
        Player you = mc.player;
        if (you == null || mc.level == null || !projectiles.on()) {
            last.clear();
            return;
        }
        boolean everyone = projectileWho.get().equals("everyone");
        sweep++;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Projectile p) || e.distanceToSqr(you) > 64 * 64 || kind(e) == 0) {
                continue;
            }
            if (!everyone && p.getOwner() != you) {
                continue;
            }
            double[] was = last.get(e.getId());
            if (was == null) {
                last.put(e.getId(), new double[]{e.getX(), e.getY(), e.getZ(), sweep});
                continue;
            }
            double dx = e.getX() - was[0], dy = e.getY() - was[1], dz = e.getZ() - was[2];
            // Stuck in the ground (an arrow at rest) leaves nothing more.
            if (dx * dx + dy * dy + dz * dz > 0.0025) {
                segment(FxWorld.POOL, was[0], was[1], was[2], e.getX(), e.getY(), e.getZ(), e.getId(), 0f);
            }
            was[0] = e.getX();
            was[1] = e.getY();
            was[2] = e.getZ();
            was[3] = sweep;
        }
        // Forget the ones gone.
        if (!last.isEmpty()) {
            last.values().removeIf(v -> v[3] != sweep);
        }
    }

    /** One tick of a trail: from (fx0, fy0, fz0) to (x, y, z). */
    private void segment(FxPool pool, double fx0, double fy0, double fz0, double x, double y, double z, int id, float delay) {
        int style = projectiles.pick(id);
        Fx f = start(pool, projectiles, TRAIL, x, y, z, 0.6f);
        projectiles.apply(f, style);
        f.tx = fx0 - x;
        f.ty = fy0 - y;
        f.tz = fz0 - z;
        f.i0 = id;
        FxPool.delay(f, delay);
    }

    // ------------------------------------------------------------------ yours

    private void youTick(Minecraft mc) {
        Player you = mc.player;
        ticks++;
        boolean any = ribbon.on() || firefly.on() || ghost.on() || footsteps.on() || contrails.on();
        if (you == null || mc.level == null || !any) {
            forget();
            return;
        }
        if (ghost.on()) {
            PoseSnapshot.HISTORY.want();
        }
        double x = you.getX(), y = you.getY(), z = you.getZ();
        double mx = x - px, my = y - py, mz = z - pz;
        double moved2 = mx * mx + my * my + mz * mz;
        boolean was = have;
        have = true;
        px = x;
        py = y;
        pz = z;
        // A teleport (or the first tick) starts every trail again.
        if (!was || moved2 > 64.0) {
            forget();
            have = true;
            return;
        }
        boolean moving = moved2 > 0.0004;
        // The body's length: up when standing, along the look once gliding (as the game turns the model) or swimming.
        boolean flying = you.isFallFlying();
        flyTicks = flying ? flyTicks + 1 : 0;
        float lying = flying ? Math.min(1f, flyTicks * flyTicks / 100f) : you.isVisuallySwimming() ? 1f : 0f;
        Vec3 look = you.getLookAngle();
        double ax = look.x * lying, ay = (1f - lying) + look.y * lying, az = look.z * lying;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1e-4) {
            ax = 0;
            ay = 1;
            az = 0;
        } else {
            ax /= al;
            ay /= al;
            az /= al;
        }
        float height = you.isCrouching() ? 1.5f : 1.8f;

        // The ribbon.
        if (shows(ribbon, mc)) {
            float h = ribbonFrom.get().equals("feet") ? ribbonHalfWidth() + FxDraw.TEXEL : height * 0.42f;
            double rx = x + ax * h, ry = y + ay * h, rz = z + az * h;
            if (!ribbonHave) {
                ribbonLast[0] = rx;
                ribbonLast[1] = ry;
                ribbonLast[2] = rz;
                ribbonHave = true;
            } else {
                double dx = rx - ribbonLast[0], dy = ry - ribbonLast[1], dz = rz - ribbonLast[2];
                if (dx * dx + dy * dy + dz * dz > 0.0004) {
                    ribbonPiece(FxWorld.POOL, ribbonLast[0], ribbonLast[1], ribbonLast[2], rx, ry, rz, ticks, 0f);
                    ribbonLast[0] = rx;
                    ribbonLast[1] = ry;
                    ribbonLast[2] = rz;
                }
            }
        } else {
            ribbonHave = false;
        }

        // The firefly tail: about one dot a tick while you move.
        if (shows(firefly, mc) && moving) {
            fireflyDue += firefly.amount == null ? 1f : (float) (firefly.amount.get() / 100.0);
            while (fireflyDue >= 1f) {
                fireflyDue -= 1f;
                int seed = ticks * 7 + (int) (fireflyDue * 100f);
                float along = 0.25f + FxDraw.hash(seed, 1) * 0.6f;
                double sx = (FxDraw.hash(seed, 2) - 0.5f) * 0.6f, sz = (FxDraw.hash(seed, 3) - 0.5f) * 0.6f;
                fireflyDot(FxWorld.POOL, x + ax * height * along + sx, y + ay * height * along, z + az * height * along + sz, seed, 0f);
            }
        } else {
            fireflyDue = 0f;
        }

        // The ghost trail: a copy every so often, of you as you were that long ago.
        if (shows(ghost, mc)) {
            int every = ghostEvery();
            if (--ghostDue <= 0) {
                ghostDue = every;
                PoseSnapshot.Pose pose = PoseSnapshot.HISTORY.ago(every);
                PoseSnapshot.Pose newest = PoseSnapshot.HISTORY.ago(0);
                if (pose != null && newest != null) {
                    double gx = newest.x - pose.x, gy = newest.y - pose.y, gz = newest.z - pose.z;
                    // Only while you move: copies standing in you would only darken you.
                    if (gx * gx + gy * gy + gz * gz > 0.36) {
                        PoseSnapshot copy = ghosts[ghostNext];
                        if (copy == null) {
                            copy = ghosts[ghostNext] = new PoseSnapshot();
                        }
                        ghostNext = (ghostNext + 1) % ghosts.length;
                        copy.capture(SkinPixels.of(you), pose, false);
                        ghostCopy(FxWorld.POOL, copy, pose.x, pose.y, pose.z, ghostSeq++, 0f);
                    }
                }
            }
        } else {
            ghostDue = 0;
        }

        // Footsteps: a print every stride on the ground, left and right in turn.
        if (shows(footsteps, mc) && you.onGround() && !you.isInWater() && !flying) {
            walked += Math.sqrt(mx * mx + mz * mz);
            if (walked >= STRIDE) {
                walked -= STRIDE;
                leftFoot = !leftFoot;
                footprint(FxWorld.POOL, x, y, z, mx, mz, leftFoot, 0f);
            }
        } else {
            walked = 0;
        }

        // Elytra contrails: a stream from each wing tip.
        if (shows(contrails, mc) && flying && lying > 0.5f) {
            wingTips(x, y, z, look, you.getDeltaMovement(), you.getYRot(), tipsNow);
            if (tipsHave) {
                for (int side = 0; side < 2; side++) {
                    int o = side * 3;
                    contrailPiece(FxWorld.POOL, tipsLast[o], tipsLast[o + 1], tipsLast[o + 2], tipsNow[o], tipsNow[o + 1], tipsNow[o + 2], ticks * 2 + side, 0f);
                }
            }
            System.arraycopy(tipsNow, 0, tipsLast, 0, 6);
            tipsHave = true;
        } else {
            tipsHave = false;
        }
    }

    /** A stride, blocks. */
    private static final double STRIDE = 0.62;

    /** Ticks between ghost copies: four at 100% duration. */
    private int ghostEvery() {
        float k = ghost.duration == null ? 1f : (float) (ghost.duration.get() / 100.0);
        return Math.max(2, Math.round(4 * k));
    }

    /** Half the ribbon's width across, blocks (its widest style's bands at the set size). */
    private float ribbonHalfWidth() {
        int px = bandTexels((float) (ribbon.size.get() / 100.0));
        return BANDS[Math.max(0, ribbon.pick(0))].length * px * FxDraw.TEXEL / 2f;
    }

    private static int bandTexels(float size) {
        return Math.max(1, Math.round(2f * size));
    }

    /**
     * Where the elytra's wing tips are, as the game's model holds them: spread out to the sides in level flight,
     * folded back along the body in a dive. Fills (x, y, z) of the left tip and then the right one.
     */
    static void wingTips(double x, double y, double z, Vec3 look, Vec3 motion, float yaw, double[] out) {
        float open = 1f;
        double ml = Math.sqrt(motion.x * motion.x + motion.y * motion.y + motion.z * motion.z);
        if (motion.y < 0 && ml > 1e-4) {
            open = 1f - (float) Math.pow(-motion.y / ml, 1.5);
        }
        double theta = Math.toRadians(open * 90f + (1f - open) * 15f);
        double phi = Math.toRadians(open * 20f + (1f - open) * 15f);
        double a = Math.toRadians(yaw);
        // The body's right (flat), its length along the look, and its back.
        double rx = -Math.cos(a), rz = -Math.sin(a);
        double lx = look.x, ly = look.y, lz = look.z;
        double bx = -rz * ly, by = rz * lx - rx * lz, bz = rx * ly;
        double lateral = (5 + 20 * Math.sin(theta)) / 16.0;
        double down = 20 * Math.cos(theta) * Math.cos(phi) / 16.0;
        double back = (20 * Math.cos(theta) * Math.sin(phi) + 2) / 16.0;
        // The shoulders, a block and a half along the body from the feet.
        double sx = x + lx * (1.5 - down) + bx * back, sy = y + ly * (1.5 - down) + by * back, sz = z + lz * (1.5 - down) + bz * back;
        out[0] = sx - rx * lateral;
        out[1] = sy;
        out[2] = sz - rz * lateral;
        out[3] = sx + rx * lateral;
        out[4] = sy;
        out[5] = sz + rz * lateral;
    }

    // ------------------------------------------------------------------ starting them

    private void ribbonPiece(FxPool pool, double x0, double y0, double z0, double x, double y, double z, int tick, float delay) {
        int style = ribbon.pick(tick);
        Fx f = start(pool, ribbon, RIBBON, x, y, z, 1.5f);
        ribbon.apply(f, style);
        f.tx = x0 - x;
        f.ty = y0 - y;
        f.tz = z0 - z;
        f.i0 = tick;
        f.d = bandTexels(f.size);
        FxPool.delay(f, delay);
    }

    private void fireflyDot(FxPool pool, double x, double y, double z, int seed, float delay) {
        Fx f = start(pool, firefly, FIREFLY, x, y, z, 1.4f);
        firefly.apply(f, 0);
        // A firefly glows in its own colour: a pale halo round it would read as a hollow box.
        f.glowColour = f.ramp[Ramp.MAIN];
        f.seed = seed;
        // A slow drift, a little up.
        f.tx = FxDraw.signed(seed, 4) * 0.18;
        f.ty = 0.06 + FxDraw.hash(seed, 5) * 0.14;
        f.tz = FxDraw.signed(seed, 6) * 0.18;
        f.a = FxDraw.hash(seed, 7) * 6.28f;
        f.b = 7f + FxDraw.hash(seed, 8) * 6f;
        FxPool.delay(f, delay);
    }

    private static final int[] RAMP = new int[Ramp.TONES];

    private void ghostCopy(FxPool pool, PoseSnapshot copy, double x, double y, double z, int seq, float delay) {
        int count = (int) Math.round(ghostCount.get());
        float life = ghostEvery() * count / 20f;
        Fx f = pool == FxWorld.POOL ? FxWorld.spawn(GHOST, x, y, z, life) : pool.spawn(GHOST, x, y, z, life, 2);
        f.base = life;
        ghost.apply(f, 0);
        f.copy = copy;
        f.i0 = seq;
        // Colours shifting from yours as it ages: 30 then 60 degrees round, one way for a copy and the other for the next,
        // the last step in the shade tone.
        int main = f.ramp[Ramp.MAIN];
        float way = (seq & 1) == 0 ? 1f : -1f;
        f.palette[0] = main;
        f.palette[1] = Colors.shiftHue(main, 30f * way, 1f);
        Ramp.of(Colors.shiftHue(main, 60f * way, 1f), RAMP);
        f.palette[2] = RAMP[Ramp.SHADE];
        FxPool.delay(f, delay);
    }

    private void footprint(FxPool pool, double x, double y, double z, double dirX, double dirZ, boolean left, float delay) {
        double l = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (l < 1e-4) {
            return;
        }
        dirX /= l;
        dirZ /= l;
        // Prints lie along the nearer of the block grid's ways, as pixel prints do.
        boolean alongX = Math.abs(dirX) > Math.abs(dirZ);
        double fx = alongX ? Math.signum(dirX) : 0, fz = alongX ? 0 : Math.signum(dirZ);
        // The foot's side: to the right of the way you go, or the left.
        double sx = -fz, sz = fx;
        double side = (left ? -1 : 1) * 0.16;
        double px = x + sx * side, pz = z + sz * side;
        float gy = ground(pool, px, pz, y + 0.5);
        Fx f = start(pool, footsteps, FOOTPRINT, px, gy, pz, 4f);
        footsteps.apply(f, 0);
        f.tx = fx;
        f.tz = fz;
        f.i0 = left ? 1 : 0;
        FxPool.delay(f, delay);
    }

    private void contrailPiece(FxPool pool, double x0, double y0, double z0, double x, double y, double z, int part, float delay) {
        Fx f = start(pool, contrails, CONTRAIL, x, y, z, 1f);
        contrails.apply(f, 0);
        f.tx = x0 - x;
        f.ty = y0 - y;
        f.tz = z0 - z;
        f.i0 = part;
        f.d = Math.max(1, Math.round(f.size));
        FxPool.delay(f, delay);
    }

    /** A rocket's afterburner flare and its puff of smoke, at (x, y, z). */
    private void rocket(FxPool pool, double x, double y, double z, float delay) {
        Fx flare = start(pool, contrails, FLARE, x, y, z, 0.2f);
        contrails.apply(flare, 0);
        FxPool.delay(flare, delay);
        Fx smoke = start(pool, contrails, SMOKE, x, y, z, 0.9f);
        contrails.apply(smoke, 0);
        smoke.seed = (int) (System.nanoTime() >>> 8);
        FxPool.delay(smoke, delay);
    }

    // ------------------------------------------------------------------ Try it

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        float longest = 0f;
        if (projectiles.on()) {
            longest = Math.max(longest, tryArrow(pool, dummy));
        }
        if (ribbon.on() || firefly.on() || ghost.on() || footsteps.on()) {
            longest = Math.max(longest, tryWalk(pool, dummy));
        }
        if (contrails.on()) {
            longest = Math.max(longest, tryFlight(pool, dummy));
        }
        return longest > 0f ? longest : 0.6f;
    }

    /** An arrow flying past the dummy's chest, from its left to its right, in an arc: a segment every tick. */
    private float tryArrow(FxPool pool, FxPreview.Dummy dummy) {
        double a = Math.toRadians(dummy.yaw);
        double rx = Math.cos(a), rz = Math.sin(a);
        // A block in front of it, so its body doesn't hide the trail.
        double fx = -Math.sin(a), fz = Math.cos(a);
        double px = dummy.x - rx * 3.0 + fx, pz = dummy.z - rz * 3.0 + fz, py = dummy.chestY() - 0.4;
        double vx = rx * 0.5, vz = rz * 0.5, vy = 0.16;
        for (int k = 0; k < 12; k++) {
            double nx = px + vx, ny = py + vy, nz = pz + vz;
            vy -= 0.03;
            segment(pool, px, py, pz, nx, ny, nz, 7, k * 0.05f);
            px = nx;
            py = ny;
            pz = nz;
        }
        return 1.2f;
    }

    /** Ticks of the walk Try it shows. */
    private static final int WALK = 26;

    /**
     * The way the walk takes, tick {@code k} of {@link #WALK}: a curve in from beside and behind the dummy (on the
     * viewer's right, in front of its body) that ends at it, as if it had just run there. Fills x, z.
     */
    private static void walkPoint(FxPreview.Dummy dummy, int k, double[] out) {
        double a = Math.toRadians(dummy.yaw);
        // The dummy's left (the viewer's right) and its front (toward the viewer).
        double lx = Math.cos(a), lz = Math.sin(a), fx = -Math.sin(a), fz = Math.cos(a);
        double s = k / (double) WALK;
        // A quadratic curve: from 3.6 left and 1.4 back, bowing out in front, to the dummy's side.
        double l0 = 3.6, f0 = -1.4, l1 = 2.8, f1 = 1.9, l2 = 0.35, f2 = 0.25;
        double l = (1 - s) * (1 - s) * l0 + 2 * (1 - s) * s * l1 + s * s * l2;
        double f = (1 - s) * (1 - s) * f0 + 2 * (1 - s) * s * f1 + s * s * f2;
        out[0] = dummy.x + lx * l + fx * f;
        out[1] = dummy.z + lz * l + fz * f;
    }

    private float tryWalk(FxPool pool, FxPreview.Dummy dummy) {
        double[] p = new double[2], q = new double[2];
        float last = 0f;
        float height = dummy.body != null ? dummy.body.height : 1.8f;
        double walkedHere = 0;
        boolean left = false;
        for (int k = 1; k <= WALK; k++) {
            walkPoint(dummy, k - 1, p);
            walkPoint(dummy, k, q);
            float delay = (k - 1) * 0.05f;
            float gy0 = ground(pool, p[0], p[1], dummy.y + 0.5), gy1 = ground(pool, q[0], q[1], dummy.y + 0.5);
            if (ribbon.on()) {
                float h = ribbonFrom.get().equals("feet") ? ribbonHalfWidth() + FxDraw.TEXEL : height * 0.42f;
                ribbonPiece(pool, p[0], gy0 + h, p[1], q[0], gy1 + h, q[1], k, delay);
                last = Math.max(last, delay + ribbon.life(1.5f));
            }
            if (firefly.on()) {
                int n = Math.max(1, Math.round(firefly.amount == null ? 1f : (float) (firefly.amount.get() / 100.0)));
                for (int i = 0; i < n; i++) {
                    int seed = k * 7 + i * 131;
                    float along = 0.25f + FxDraw.hash(seed, 1) * 0.6f;
                    fireflyDot(pool, q[0] + (FxDraw.hash(seed, 2) - 0.5f) * 0.6f, gy1 + height * along,
                            q[1] + (FxDraw.hash(seed, 3) - 0.5f) * 0.6f, seed, delay);
                }
                last = Math.max(last, delay + firefly.life(1.4f));
            }
            if (footsteps.on()) {
                double dx = q[0] - p[0], dz = q[1] - p[1];
                walkedHere += Math.sqrt(dx * dx + dz * dz);
                if (walkedHere >= STRIDE) {
                    walkedHere -= STRIDE;
                    left = !left;
                    footprint(pool, q[0], gy1, q[1], dx, dz, left, delay);
                    last = Math.max(last, delay + footsteps.life(4f));
                }
            }
            if (ghost.on() && dummy.body != null && k % ghostEvery() == 0 && k < WALK) {
                // The copies stand where the dummy was a moment before.
                walkPoint(dummy, Math.max(0, k - ghostEvery()), p);
                ghostCopy(pool, dummy.body, p[0], ground(pool, p[0], p[1], dummy.y + 0.5), p[1], k / ghostEvery() - 1, delay);
                last = Math.max(last, delay + ghostEvery() * (float) Math.round(ghostCount.get()) / 20f);
            }
        }
        return last;
    }

    /** A glide past the dummy a little above it, diving as it goes, with a rocket at its start. */
    private float tryFlight(FxPool pool, FxPreview.Dummy dummy) {
        double a = Math.toRadians(dummy.yaw);
        double lx = Math.cos(a), lz = Math.sin(a), fx = -Math.sin(a), fz = Math.cos(a);
        int steps = 22;
        double[] tips = new double[6], was = new double[6];
        float last = 0f;
        for (int k = 0; k <= steps; k++) {
            double s = k / (double) steps;
            // From the viewer's right to the left, 1.5 blocks in front of the dummy, from 3 up to 1.4.
            double cx = dummy.x + lx * (4.5 - 9 * s) + fx * 1.5, cz = dummy.z + lz * (4.5 - 9 * s) + fz * 1.5;
            double cy = dummy.y + 3.0 - 1.6 * s * s;
            // Gliding left (toward -l), diving more and more: the wings fold.
            double dy = -0.1 - 1.2 * s;
            double vx = -lx, vz = -lz;
            double vl = Math.sqrt(vx * vx + dy * dy + vz * vz);
            Vec3 look = new Vec3(vx / vl, dy / vl, vz / vl);
            float yaw = (float) Math.toDegrees(Math.atan2(lx, -lz));
            // The feet a block and a half behind the shoulders.
            wingTips(cx - look.x * 1.5, cy - look.y * 1.5, cz - look.z * 1.5, look, look, yaw, tips);
            if (k == 0 && rocketFlare.get()) {
                rocket(pool, cx - look.x * 1.8, cy - look.y * 1.8, cz - look.z * 1.8, 0f);
            }
            if (k > 0) {
                float delay = (k - 1) * 0.05f;
                for (int side = 0; side < 2; side++) {
                    int o = side * 3;
                    contrailPiece(pool, was[o], was[o + 1], was[o + 2], tips[o], tips[o + 1], tips[o + 2], k * 2 + side, delay);
                }
                last = Math.max(last, delay + contrails.life(1f));
            }
            System.arraycopy(tips, 0, was, 0, 6);
        }
        return last;
    }

    // ------------------------------------------------------------------ the kinds

    private static final float T = FxDraw.TEXEL;
    private static final FxSprites.Sprite[] SPRITES = {FxSprites.SQUARE, FxSprites.PLUS, FxSprites.HEART, FxSprites.SUN};
    private static final float[] PIXEL = {1f, 0.5f, 0.45f, 0.5f};

    /** A tick of a projectile's trail: texels along the way it went, fading out in steps over 0.6 s. */
    static final FxKind TRAIL = new FxKind("trail_projectile", 64) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            FxDraw.origin(out, fx);
            float a = Ramp.steps(1f - t / 0.6f, 4, fx.snap) * fx.early(now);
            if (a <= 0f) {
                return;
            }
            float len = (float) Math.sqrt(fx.tx * fx.tx + fx.ty * fx.ty + fx.tz * fx.tz);
            float gap = fx.style == 0 ? 0.2f : 0.45f;
            int n = Math.max(1, Math.min(16, Math.round(len / gap * fx.amount)));
            float px = PIXEL[fx.style] * T * fx.size;
            for (int i = 0; i < n; i++) {
                float k = i / (float) n;
                float x = Ramp.snap((float) fx.tx * k, fx.snap), y = Ramp.snap((float) fx.ty * k, fx.snap), z = Ramp.snap((float) fx.tz * k, fx.snap);
                int part = fx.i0 + i;
                if (fx.style == 0) {
                    int tone = (part & 3) == 0 ? Ramp.CORE : Ramp.MAIN;
                    FxDraw.square(out, x, y, z, px, Colors.withAlpha(fx.tone(tone, part, now), a));
                    FxDraw.squareGlow(out, fx, x, y, z, px, a);
                } else {
                    // Their glows at half: close together, full ones would run into one band.
                    FxDraw.sprite(out, fx, SPRITES[fx.style], x, y, z, px, part, out.glowPass() ? a * 0.5f : a, now);
                }
            }
        }
    };

    /**
     * The ribbon's bands, top to bottom: the theme style's four are the effect's ramp (core, main, shade, deep, the
     * colours here unused), then a rainbow's six, and blue, pink and white.
     */
    static final int[][] BANDS = {
            {0, 0, 0, 0},
            {0xFFFF3B3B, 0xFFFF9A2E, 0xFFFFE23D, 0xFF4FE04A, 0xFF3BA7FF, 0xFF8B5BFF},
            {0xFF5BCEFA, 0xFFF5A9B8, 0xFFFFFFFF, 0xFFF5A9B8, 0xFF5BCEFA}};
    private static final int[] COLOURS = new int[6];
    private static final float[] EYE = new float[3], UP = new float[3];

    /** How much of a piece of your way is drawn: it reaches you as you move through the tick it was laid in. */
    private static float grown(float age) {
        return Math.min(1f, Math.max(0f, age * 20f));
    }

    /** Fades out in steps over the last {@code share} of its life. */
    private static float tail(Fx fx, float now, float share) {
        return Ramp.steps(Math.min(1f, (1f - fx.progress(now)) / share), 4, fx.snap) * fx.early(now);
    }

    /**
     * A strip of whole-texel colour bands from (x0, y0, z0) to (x1, y1, z1) (from the origin), facing the camera round
     * its length, band 0 at the top as seen: {@code n} bands of {@code colours}, each {@code px} texels wide, raised
     * {@code lift} texels, at alpha {@code a}. In the glow pass a stepped halo of two fainter bands above and below, in
     * the edge bands' colours (or the effect's glow colour when {@code glowColour} isn't 0).
     */
    static void bands(FxOut out, Fx fx, float x0, float y0, float z0, float x1, float y1, float z1, int[] colours, int n, int px,
                      int lift, float a, int glowColour) {
        float ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        if (ax * ax + ay * ay + az * az < 1e-6f || a <= 0f) {
            return;
        }
        boolean glow = out.glowPass();
        if (glow && fx.glow == 0) {
            return;
        }
        out.eye(EYE);
        float vx = EYE[0] - (x0 + x1) / 2f, vy = EYE[1] - (y0 + y1) / 2f, vz = EYE[2] - (z0 + z1) / 2f;
        float sx = ay * vz - az * vy, sy = az * vx - ax * vz, sz = ax * vy - ay * vx;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-5f) {
            sx = 0f;
            sy = 1f;
            sz = 0f;
        } else {
            sx /= sl;
            sy /= sl;
            sz /= sl;
        }
        // The same way up on screen for every piece, so the bands never swap over from one to the next.
        out.up(UP);
        if (sx * UP[0] + sy * UP[1] + sz * UP[2] < 0f) {
            sx = -sx;
            sy = -sy;
            sz = -sz;
        }
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        float bw = px * T, half = n * bw / 2f, up = lift * T;
        if (glow) {
            float k = fx.glow == 2 ? 1.7f : 1f;
            int top = glowColour != 0 ? glowColour : colours[0], bottom = glowColour != 0 ? glowColour : colours[n - 1];
            // Two rings of whole texels: half a band each, at least one texel.
            float w1 = T * Math.max(1, px / 2), w2 = w1 * 2f;
            quad(out, x0, y0, z0, x1, y1, z1, sx, sy, sz, up + half + w1, up + half, uv, Colors.withAlpha(top, Math.min(1f, 0.35f * k * a)));
            quad(out, x0, y0, z0, x1, y1, z1, sx, sy, sz, up + half + w2, up + half + w1, uv, Colors.withAlpha(top, Math.min(1f, 0.15f * k * a)));
            quad(out, x0, y0, z0, x1, y1, z1, sx, sy, sz, up - half, up - half - w1, uv, Colors.withAlpha(bottom, Math.min(1f, 0.35f * k * a)));
            quad(out, x0, y0, z0, x1, y1, z1, sx, sy, sz, up - half - w1, up - half - w2, uv, Colors.withAlpha(bottom, Math.min(1f, 0.15f * k * a)));
            return;
        }
        for (int b = 0; b < n; b++) {
            float hi = up + half - b * bw;
            quad(out, x0, y0, z0, x1, y1, z1, sx, sy, sz, hi, hi - bw, uv, Colors.withAlpha(colours[b], a));
        }
    }

    /** One band: the strip from (x0, y0, z0) to (x1, y1, z1), from {@code hi} to {@code lo} along the side (s). */
    private static void quad(FxOut out, float x0, float y0, float z0, float x1, float y1, float z1, float sx, float sy, float sz, float hi,
                             float lo, float[] uv, int argb) {
        out.vertex(x0 + sx * hi, y0 + sy * hi, z0 + sz * hi, uv[0], uv[1], argb);
        out.vertex(x1 + sx * hi, y1 + sy * hi, z1 + sz * hi, uv[2], uv[1], argb);
        out.vertex(x1 + sx * lo, y1 + sy * lo, z1 + sz * lo, uv[2], uv[3], argb);
        out.vertex(x0 + sx * lo, y0 + sy * lo, z0 + sz * lo, uv[0], uv[3], argb);
    }

    /** A piece of the ribbon: its bands from where you were a tick before to where you were. */
    static final FxKind RIBBON = new FxKind("trail_ribbon", 96) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float a = tail(fx, now, 0.4f);
            if (a <= 0f) {
                return;
            }
            FxDraw.origin(out, fx);
            int[] bands = BANDS[Math.max(0, Math.min(BANDS.length - 1, fx.style))];
            int n = bands.length;
            for (int b = 0; b < n; b++) {
                COLOURS[b] = fx.style == 0 ? fx.tone(b, fx.i0, now) : bands[b];
            }
            int px = Math.max(1, (int) fx.d);
            // The Nyan step: every other piece a texel up, the steps swapping four times a second.
            int wave = fx.calm ? 0 : (int) (now * 4f);
            int lift = ((fx.i0 + wave) & 1) == 0 ? 0 : Math.max(1, px / 2);
            float g = 1f - grown(age);
            float x0 = Ramp.snap((float) fx.tx, fx.snap), y0 = Ramp.snap((float) fx.ty, fx.snap), z0 = Ramp.snap((float) fx.tz, fx.snap);
            bands(out, fx, x0, y0, z0, x0 * g, y0 * g, z0 * g, COLOURS, n, px, lift, a, fx.style == 0 ? fx.glowColour : 0);
        }
    };

    /** A piece of a contrail: a thin stream of three bands, its middle the core tone. */
    static final FxKind CONTRAIL = new FxKind("trail_contrail", 96) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float a = tail(fx, now, 0.6f);
            if (a <= 0f) {
                return;
            }
            FxDraw.origin(out, fx);
            COLOURS[0] = fx.tone(Ramp.MAIN, fx.i0, now);
            COLOURS[1] = fx.tone(Ramp.CORE, fx.i0, now);
            COLOURS[2] = COLOURS[0];
            float g = 1f - grown(age);
            float x0 = Ramp.snap((float) fx.tx, fx.snap), y0 = Ramp.snap((float) fx.ty, fx.snap), z0 = Ramp.snap((float) fx.tz, fx.snap);
            bands(out, fx, x0, y0, z0, x0 * g, y0 * g, z0 * g, COLOURS, 3, Math.max(1, (int) fx.d), 0, a, fx.glowColour);
        }
    };

    /** A firefly: a dot of 2 x 2 texels drifting slowly up, blinking, fading out in steps. */
    static final FxKind FIREFLY = new FxKind("trail_firefly", 64) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float a = tail(fx, now, 0.5f);
            if (a <= 0f) {
                return;
            }
            FxDraw.origin(out, fx);
            float t = fx.time(age);
            float blink = 0.5f + 0.5f * (float) Math.sin(t * fx.b + fx.a);
            blink = Ramp.steps(blink * blink, 3, fx.snap);
            float x = Ramp.snap((float) fx.tx * t + 0.05f * (float) Math.sin(t * 2.3f + fx.a), fx.snap);
            float y = Ramp.snap((float) fx.ty * t, fx.snap);
            float z = Ramp.snap((float) fx.tz * t + 0.05f * (float) Math.cos(t * 1.9f + fx.a), fx.snap);
            float side = T * Math.max(1, Math.round(2f * fx.size));
            // Lit, it's solid; between blinks a faint shade.
            float lit = blink > 0.4f ? a : a * 0.35f;
            int tone = blink >= 1f ? Ramp.CORE : blink > 0.4f ? Ramp.MAIN : Ramp.SHADE;
            FxDraw.square(out, x, y, z, side, Colors.withAlpha(fx.tone(tone, fx.seed, now), lit));
            if (out.glowPass() && fx.glow > 0 && blink > 0.4f) {
                // One faint ring, a texel out: the dot stays the brightest part even against a pale sky.
                float k = fx.glow == 2 ? 1.7f : 1f;
                out.use(FxSprites.atlas());
                float h = side / 2f + T;
                FxDraw.billboard(out, x, y, z, h, h, FxSprites.SQUARE.uv[FxSprites.ALL],
                        Colors.withAlpha(fx.glowColour, Math.min(1f, 0.2f * k * a * blink)));
            }
        }
    };

    /** A copy of you where you were, see-through, in your colour shifting round the hue and darker as it fades. */
    static final FxKind GHOST = new FxKind("trail_ghost", 12) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (fx.copy == null || out.glowPass()) {
                return;
            }
            float p = fx.progress(now);
            float a = Ramp.steps(1f - p, 4, fx.snap) * 0.85f * fx.early(now);
            if (a <= 0f) {
                return;
            }
            FxDraw.origin(out, fx);
            int step = p < 0.34f ? 0 : p < 0.67f ? 1 : 2;
            int colour = fx.rainbow ? fx.tone(step == 2 ? Ramp.SHADE : Ramp.MAIN, fx.i0 * 2 + step, now) : fx.palette[step];
            fx.copy.draw(out, 0f, 0f, 0f, fx.size, 0f, fx.copy.tinted(colour, 1f), Colors.withAlpha(colour, a), PoseSnapshot.WHOLE, 0f);
        }
    };

    /** A pixel footprint lying on the ground along the way you went, held, then fading out in steps. */
    static final FxKind FOOTPRINT = new FxKind("trail_footprint", 48) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass()) {
                return;
            }
            float a = tail(fx, now, 0.3f) * 0.9f;
            if (a <= 0f) {
                return;
            }
            FxDraw.origin(out, fx);
            FxSprites.Sprite s = FxSprites.FOOTPRINT;
            float fx0 = (float) fx.tx, fz0 = (float) fx.tz;
            // The print's right, on the ground.
            float rx = -fz0, rz = fx0;
            float cell = T * Math.max(1, Math.round(fx.size));
            boolean mirror = fx.i0 == 1;
            for (int py = 0; py < s.h; py++) {
                for (int px = 0; px < s.w; px++) {
                    int tone = s.tone(mirror ? s.w - 1 - px : px, py);
                    if (tone < 0) {
                        continue;
                    }
                    // Its top row is the toes, ahead.
                    float across = (px - (s.w - 1) / 2f) * cell, ahead = ((s.h - 1) / 2f - py) * cell;
                    FxDraw.cell(out, rx * across + fx0 * ahead, 0.002f, rz * across + fz0 * ahead, cell,
                            Colors.withAlpha(fx.tone(tone, 0, now), a));
                }
            }
        }
    };

    /** A rocket's afterburner: a big bright flash for a tenth of a second, then a smaller one, then gone. */
    static final FxKind FLARE = new FxKind("trail_flare", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            if (t >= 0.2f) {
                return;
            }
            FxDraw.origin(out, fx);
            boolean first = t < 0.1f;
            float side = T * (first ? 6f : 3f) * fx.size;
            float a = fx.early(now);
            FxDraw.square(out, 0f, 0f, 0f, side, Colors.withAlpha(fx.tone(first ? Ramp.CORE : Ramp.MAIN, 0, now), a));
            if (out.glowPass() && fx.glow > 0) {
                // Brighter than a normal glow: it's a flame.
                FxDraw.squareGlow(out, fx, 0f, 0f, 0f, side * 1.4f, a);
            }
        }
    };

    private static final int[] SMOKE_GREYS = {0xFFD6D6D6, 0xFFA8A8A8, 0xFF7E7E7E};

    /** The rocket's puff: grey texel squares spreading out and drifting up, fading in steps. */
    static final FxKind SMOKE = new FxKind("trail_smoke", 4) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass()) {
                return;
            }
            float t = fx.time(age);
            float a = Ramp.steps(1f - t / 0.9f, 4, fx.snap) * 0.85f * fx.early(now);
            if (a <= 0f) {
                return;
            }
            FxDraw.origin(out, fx);
            float spread = 0.15f + 0.45f * (1f - (1f - t / 0.9f) * (1f - t / 0.9f));
            for (int i = 0; i < 10; i++) {
                double u = FxDraw.hash(fx.seed, i) * Math.PI * 2, v = FxDraw.signed(fx.seed, i + 20) * 1.2;
                float r = spread * (0.5f + 0.5f * FxDraw.hash(fx.seed, i + 40));
                float x = Ramp.snap((float) (Math.cos(u) * Math.cos(v)) * r, fx.snap);
                float y = Ramp.snap((float) Math.sin(v) * r * 0.7f + t * 0.35f, fx.snap);
                float z = Ramp.snap((float) (Math.sin(u) * Math.cos(v)) * r, fx.snap);
                float side = T * (2 + (i % 3 == 0 ? 1 : 0)) * fx.size;
                // Grey, a touch of the effect's shade in it.
                int grey = Colors.mixRgb(SMOKE_GREYS[i % 3], fx.ramp[Ramp.SHADE], 0.18f);
                FxDraw.square(out, x, y, z, side, Colors.withAlpha(grey, a));
            }
        }
    };
}
