package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.FxDraw;
import io.github.georgwav.cosmetics.sol.fx.FxKind;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxOut;
import io.github.georgwav.cosmetics.sol.fx.FxPool;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxSprites;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.util.Colors;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.List;

/**
 * Trails (C.4): what follows things through the air, only on your screen. Projectile trails: arrows, ender pearls,
 * tridents and thrown potions leave trails of texels (dots, sparkles, hearts or Sol's glyph) that fade in 0.6 s, yours
 * only by default.
 */
public final class Trails extends FxModule {
    public static final List<String> PROJECTILE_STYLES = List.of("dots", "sparkles", "hearts", "glyphs");

    public final FxSettings projectiles = fx("projectile", PROJECTILE_STYLES, new FxSettings.Spec().toggle(true).noSpeed());
    public final Setting.Choice projectileWho = add(new Setting.Choice("projectile_whose", "yours", List.of("yours", "everyone"))
            .group("projectile"));
    public final Setting.Toggle arrows = add(new Setting.Toggle("projectile_arrows", true).group("projectile"));
    public final Setting.Toggle pearls = add(new Setting.Toggle("projectile_pearls", true).group("projectile"));
    public final Setting.Toggle tridents = add(new Setting.Toggle("projectile_tridents", true).group("projectile"));
    public final Setting.Toggle potions = add(new Setting.Toggle("projectile_potions", true).group("projectile"));

    /** Each tracked projectile's place last tick, by entity id. */
    private final Int2ObjectOpenHashMap<double[]> last = new Int2ObjectOpenHashMap<>();
    private int sweep;

    public Trails() {
        super("trails", Category.VIEW, "trail");
        for (Setting<?> s : List.of(projectileWho, arrows, pearls, tridents, potions)) {
            s.shownWhen(projectiles.on::get);
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        last.clear();
    }

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

    @Override
    protected void tick(Minecraft mc) {
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

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        if (!projectiles.on()) {
            return 0.6f;
        }
        // An arrow flying past the dummy's chest, from its left to its right, in an arc: a segment every tick.
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

    // ------------------------------------------------------------------ the kind

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
}
