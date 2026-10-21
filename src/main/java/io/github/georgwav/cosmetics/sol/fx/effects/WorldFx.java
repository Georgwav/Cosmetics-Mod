package io.github.georgwav.cosmetics.sol.fx.effects;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxColour;
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
import io.github.georgwav.cosmetics.sol.fx.SolPicks;
import io.github.georgwav.cosmetics.sol.fx.SolSounds;
import io.github.georgwav.cosmetics.sol.fx.WorldOut;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Mulberry32;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * World FX (C.4): the world a little more alive, only on your screen.
 * <ul>
 * <li>Weather details, each with a quiet sound of its own: snow keeps your footprints (pixel prints that stay half a
 * minute), a rainbow of stepped colour bands comes out when rain stops in daylight, and pixel leaves blow off the trees
 * in the wind.</li>
 * <li>Rain ripples: pixel rings on water and on the ground under real rain (no wet reflections).</li>
 * <li>Cosmos break: the block you mine fills with stars as it goes, Sol's own cracks glowing across it, and bursts into
 * stars when it breaks.</li>
 * </ul>
 * Item physics and chunk rise are drawn by their own hooks (they read this module's switches).
 */
public final class WorldFx extends FxModule implements FxWorld.Layer {
    private static final List<String> ONE = List.of("one");

    /** Snow prints: their duration is how long they stay (30 s at 100%). */
    public final FxSettings snow = fx("snow_prints", ONE, new FxSettings.Spec().toggle(true).noSpeed().noAmount().noGlow()
            .ownColour(0xFFB9C7DE));
    public final Setting.Toggle snowSound = add(new Setting.Toggle("snow_prints_sound", true).group("snow_prints"));
    /** The rainbow keeps the spectrum's own bands; its duration is how long it stays (a minute at 100%). */
    public final FxSettings rainbow = fx("rainbow", ONE, new FxSettings.Spec().toggle(true).noSpeed().noAmount().noGlow());
    public final Setting.Toggle rainbowSound = add(new Setting.Toggle("rainbow_sound", true).group("rainbow"));
    /** Leaves in the wind: in each leaf's own colour unless you pick one. */
    public final FxSettings leaves = fx("leaves", ONE, new FxSettings.Spec().toggle(true).noSpeed().noGlow());
    public final Setting.Toggle leafColours = add(new Setting.Toggle("leaves_own_colour", true).group("leaves"));
    public final Setting.Toggle leavesSound = add(new Setting.Toggle("leaves_sound", true).group("leaves"));
    public final FxSettings ripples = fx("rain_ripples", ONE, new FxSettings.Spec().toggle(true).noSpeed().noGlow());
    public final Setting.Toggle ripplesSound = add(new Setting.Toggle("rain_ripples_sound", true).group("rain_ripples"));
    public final FxSettings cosmos = fx("cosmos_break", ONE, new FxSettings.Spec().toggle(false).noSpeed().noAmount().noDuration());
    public final Setting.Toggle cosmosSound = add(new Setting.Toggle("cosmos_break_sound", true).group("cosmos_break"));
    /** Item physics: dropped items lie flat on the ground and tumble as they fall. */
    public final Setting.Toggle itemPhysics = add(new Setting.Toggle("item_physics", false).group("items"));
    /** Chunk rise: new chunk sections rise from 8 blocks below over 0.6 s (where the version allows it). */
    public final Setting.Toggle chunkRise = add(new Setting.Toggle("chunk_rise", false).group("chunks"));
    /** The world sounds' loudness. */
    public final Setting.Slider volume = add(new Setting.Slider("world_volume", 60, 0, 150, 10, v -> Math.round(v) + "%").group("sounds"));

    private static final float T = FxDraw.TEXEL;
    private final Mulberry32 random = new Mulberry32(907);
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

    /** For the in-game test: snow prints, rainbows, leaves, ripples and star bursts started, and frames the block of stars drew. */
    public static int printsMade, rainbowsMade, leavesMade, ripplesMade, burstsMade, cosmosFrames;

    public WorldFx() {
        super("world_fx", Category.WORLD, "globe");
        snowSound.shownWhen(snow.on::get);
        rainbowSound.shownWhen(rainbow.on::get);
        leafColours.shownWhen(leaves.on::get);
        leavesSound.shownWhen(leaves.on::get);
        ripplesSound.shownWhen(ripples.on::get);
        cosmosSound.shownWhen(cosmos.on::get);
        // The rainbow keeps the spectrum's colours: no colour to pick.
        rainbow.colourMode.alsoShownWhen(() -> false);
        rainbow.colour.alsoShownWhen(() -> false);
        rainbow.colour2.alsoShownWhen(() -> false);
        // The leaves' own colour stands in for the colour settings.
        leaves.colourMode.alsoShownWhen(() -> !leafColours.get());
        leaves.colour.alsoShownWhen(() -> !leafColours.get());
        FxWorld.LAYERS.add(this);
        chunkRise.shownWhen(io.github.georgwav.cosmetics.render.ChunkRise::works);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) ->
                io.github.georgwav.cosmetics.render.ChunkRise.unload(chunk.getPos().getMinBlockX() >> 4, chunk.getPos().getMinBlockZ() >> 4));
        SolPicks.module(this);
        SolPicks.set(snow.on, true);
        SolPicks.set(rainbow.on, true);
        SolPicks.set(leaves.on, true);
        SolPicks.set(ripples.on, true);
        SolPicks.set(cosmos.on, true);
    }

    /** A quiet world sound (the World FX volume, and a little lower still: these sit under the game's own). */
    private void sound(String name, float loud, float pitch) {
        SolSounds.play("world", name, (float) (volume.get() / 100.0) * loud, pitch);
    }

    // ------------------------------------------------------------------ every tick

    private int ticks;
    private double walked, lastX, lastZ;
    private boolean have, leftFoot;
    /** Rain seen lately (for the rainbow after it). */
    private boolean wasRaining;
    private Fx rainbowNow;
    /** The block being mined (its stage, and where), for the star burst when it breaks. */
    private final BlockPos.MutableBlockPos mining = new BlockPos.MutableBlockPos();
    private int miningStage = -1;
    private BlockState miningState;

    /** The level last ticked in: a new one (another world, or a dimension) has all its chunks rise again. */
    private ClientLevel lastLevel;

    @Override
    protected void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        Player you = mc.player;
        if (level != lastLevel) {
            lastLevel = level;
            io.github.georgwav.cosmetics.render.ChunkRise.clear();
        }
        if (level == null || you == null || mc.isPaused()) {
            have = false;
            return;
        }
        ticks++;
        snowTick(level, you);
        rainbowTick(level);
        if (leaves.on() && (ticks & 1) == 0) {
            leavesTick(level, you);
        }
        if (ripples.on()) {
            ripplesTick(level, you);
        }
        cosmosTick(mc, level);
    }

    // ------------------------------------------------------------------ snow prints

    private static final double STRIDE = 0.62;

    private void snowTick(ClientLevel level, Player you) {
        double x = you.getX(), z = you.getZ();
        double mx = have ? x - lastX : 0, mz = have ? z - lastZ : 0;
        lastX = x;
        lastZ = z;
        have = true;
        if (!snow.on() || !you.onGround() || you.isInWater() || you.isPassenger()) {
            walked = 0;
            return;
        }
        walked += Math.sqrt(mx * mx + mz * mz);
        if (walked < STRIDE) {
            return;
        }
        walked -= STRIDE;
        float top = snowTop(level, you.blockPosition());
        if (Float.isNaN(top)) {
            return;
        }
        leftFoot = !leftFoot;
        print(FxWorld.POOL, x, top, z, mx, mz, leftFoot, 0f);
        if (snowSound.get()) {
            sound("snow", 0.35f, 0.85f + random.nextFloat() * 0.3f);
        }
    }

    /** The top of the snow you stand in at {@code feet} (a layer there, or a snow block under you), or NaN. */
    private float snowTop(ClientLevel level, BlockPos feet) {
        BlockState at = level.getBlockState(feet);
        if (at.is(Blocks.SNOW)) {
            return feet.getY() + at.getValue(SnowLayerBlock.LAYERS) * 2f / 16f;
        }
        below.set(feet.getX(), feet.getY() - 1, feet.getZ());
        BlockState under = level.getBlockState(below);
        if (under.is(Blocks.SNOW_BLOCK) || under.is(Blocks.POWDER_SNOW)) {
            return feet.getY();
        }
        if (under.is(Blocks.SNOW)) {
            return below.getY() + under.getValue(SnowLayerBlock.LAYERS) * 2f / 16f;
        }
        return Float.NaN;
    }

    private void print(FxPool pool, double x, double y, double z, double dirX, double dirZ, boolean left, float delay) {
        double l = Math.sqrt(dirX * dirX + dirZ * dirZ);
        if (l < 1e-4) {
            return;
        }
        dirX /= l;
        dirZ /= l;
        boolean alongX = Math.abs(dirX) > Math.abs(dirZ);
        double fx = alongX ? Math.signum(dirX) : 0, fz = alongX ? 0 : Math.signum(dirZ);
        double side = (left ? -1 : 1) * 0.16;
        Fx f = start(pool, snow, SNOW_PRINT, x - fz * side, y, z + fx * side, 30f);
        snow.apply(f, 0);
        f.tx = fx;
        f.tz = fz;
        f.i0 = left ? 1 : 0;
        FxPool.delay(f, delay);
        played = Math.max(played, f.life + delay);
        printsMade++;
    }

    /** A print pressed into the snow, in the snow's shade: held, then fading in steps over its last fifth. */
    static final FxKind SNOW_PRINT = new FxKind("world_snow_print", 96) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass()) {
                return;
            }
            float a = (float) Math.pow(Math.min(1f, (1f - fx.progress(now)) / 0.3f), 1.3f) * fx.early(now) * 0.6f;
            if (a <= 0f) {
                return;
            }
            Vfx.origin(out, fx);
            float f0 = (float) fx.tx, f2 = (float) fx.tz;
            float rx = -f2, rz = f0;
            float len = 0.2f * fx.size, wid = 0.1f * fx.size;
            FxSoft.Region r = FxSoft.FOOT;
            boolean mirror = fx.i0 == 1;
            float u0 = mirror ? r.u1 : r.u0, u1 = mirror ? r.u0 : r.u1;
            // The snow pressed down: a cold shadow of the effect's deep tone.
            int argb = Vfx.with(Vfx.mix(fx.ramp[Ramp.DEEP], 0xFF6C7A99, 0.5f), a);
            out.use(FxSoft.atlas());
            out.vertex(-rx * wid + f0 * len, 0.003f, -rz * wid + f2 * len, u0, r.v0, argb);
            out.vertex(rx * wid + f0 * len, 0.003f, rz * wid + f2 * len, u1, r.v0, argb);
            out.vertex(rx * wid - f0 * len, 0.003f, rz * wid - f2 * len, u1, r.v1, argb);
            out.vertex(-rx * wid - f0 * len, 0.003f, -rz * wid - f2 * len, u0, r.v1, argb);
        }
    };

    // ------------------------------------------------------------------ the rainbow

    private void rainbowTick(ClientLevel level) {
        float rain = level.getRainLevel(1f);
        if (rain > 0.6f) {
            wasRaining = true;
            if (rainbowNow != null && rainbowNow.kind == RAINBOW) {
                // Rain again: it goes.
                rainbowNow.end = Math.min(rainbowNow.end, FxWorld.now() + 0.5f);
                rainbowNow = null;
            }
            return;
        }
        if (!wasRaining || rain > 0.15f) {
            return;
        }
        wasRaining = false;
        // Only in daylight, under a sky.
        if (!rainbow.on() || !level.dimensionType().hasSkyLight() || Math.cos(FrameInfo.sunAngle) < 0.08) {
            return;
        }
        rainbowNow = startRainbow(FxWorld.POOL, 60f);
        if (rainbowSound.get()) {
            sound("rainbow", 0.6f, 1f);
        }
    }

    private Fx startRainbow(FxPool pool, float seconds) {
        Fx f = start(pool, rainbow, RAINBOW, FrameInfo.camX, FrameInfo.camY, FrameInfo.camZ, seconds);
        rainbow.apply(f, 0);
        played = Math.max(played, f.life);
        rainbowsMade++;
        return f;
    }

    private static final float[] BASIS = new float[4];

    /** The lowest the rainbow's centre goes: with the sun higher, it stays a tall arc (a real one would sink away). */
    static final double LOWEST = Math.toRadians(-12);

    /**
     * The rainbow's centre (x, y; the sun goes from the east, +x, over the top to the west): straight opposite the sun,
     * but never lower than {@link #LOWEST} under the horizon; and "up" round it (the sky's up made square to it;
     * across is north-south), for a sun {@code sunAngle} radians past noon.
     */
    static void antiSun(double sunAngle, float[] out) {
        double sin = Math.sin(sunAngle);
        double side = sin < 0 ? -1 : 1;
        double e = Math.max(-Math.asin(Math.max(-1, Math.min(1, Math.cos(sunAngle)))), LOWEST);
        out[0] = (float) (side * Math.cos(e));
        out[1] = (float) Math.sin(e);
        out[2] = (float) (-side * Math.sin(e));
        out[3] = (float) Math.cos(e);
    }

    /** The spectrum, outside in: red to violet. */
    private static final int[] SPECTRUM = {0xFFFF4A3D, 0xFFFF9A2E, 0xFFFFE14A, 0xFF5FD45A, 0xFF46B4FF, 0xFF5A6CFF, 0xFFB46CFF};

    /** The spectrum's colour {@code k} (0 outside, 1 inside), blended smoothly between its seven colours. */
    private static int spectrum(float k) {
        float f = Math.max(0f, Math.min(1f, k)) * (SPECTRUM.length - 1);
        int i = Math.min(SPECTRUM.length - 2, (int) f);
        return Vfx.mix(SPECTRUM[i], SPECTRUM[i + 1], f - i);
    }

    /**
     * A rainbow opposite the sun: the spectrum blended smoothly, 42 degrees out from the point straight opposite it, its
     * edges soft, with a fainter second bow outside it in the reverse order; far off (hills in front hide its feet). It
     * comes out over three seconds and goes over the last six.
     */
    static final FxKind RAINBOW = new FxKind("world_rainbow", 1) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float in = Vfx.inOut(Math.min(1f, age / 3f));
            float left = Vfx.inOut(Math.min(1f, (fx.start + fx.life - now) / 6f)) * fx.early(now);
            float a = 0.42f * Math.min(in, left) * Math.min(1.5f, fx.size);
            if (out.glowPass()) {
                a *= 0.35f;
            }
            if (a <= 0f) {
                return;
            }
            antiSun(FrameInfo.sunAngle, BASIS);
            float ax = BASIS[0], ay = BASIS[1], ux = BASIS[2], uy = BASIS[3];
            float dist = Math.min(160f, FrameInfo.renderDistance * 0.85f);
            out.origin(FrameInfo.camX, FrameInfo.camY, FrameInfo.camZ);
            out.use(FxSoft.atlas());
            FxSoft.Region sq = FxSoft.SQUARE;
            float u = (sq.u0 + sq.u1) / 2f, v = (sq.v0 + sq.v1) / 2f;
            bow(out, ax, ay, ux, uy, dist, (float) Math.toRadians(42.5), (float) Math.toRadians(11), a, false, u, v);
            bow(out, ax, ay, ux, uy, dist, (float) Math.toRadians(54), (float) Math.toRadians(13), a * 0.32f, true, u, v);
        }

        /** One bow, {@code outer} radians out at its red edge and {@code width} radians across, soft at both edges. */
        private void bow(FxOut out, float ax, float ay, float ux, float uy, float dist, float outer, float width, float a, boolean reversed,
                         float u, float v) {
            int segs = 72, rows = 14;
            for (int b = 0; b < rows; b++) {
                float k0 = b / (float) rows, k1 = (b + 1f) / rows;
                float r0 = outer - k0 * width, r1 = outer - k1 * width;
                int c0 = Vfx.with(spectrum(reversed ? 1f - k0 : k0), a * edge(k0)), c1 = Vfx.with(spectrum(reversed ? 1f - k1 : k1), a * edge(k1));
                for (int s = 0; s < segs; s++) {
                    float p0 = (float) (-Math.PI / 2 + Math.PI * s / segs), p1 = (float) (-Math.PI / 2 + Math.PI * (s + 1) / segs);
                    if (dirY(ay, uy, r0, p0) < -0.08f && dirY(ay, uy, r0, p1) < -0.08f) {
                        continue;
                    }
                    vert(out, ax, ay, ux, uy, r0, p0, dist, u, v, c0);
                    vert(out, ax, ay, ux, uy, r1, p0, dist, u, v, c1);
                    vert(out, ax, ay, ux, uy, r1, p1, dist, u, v, c1);
                    vert(out, ax, ay, ux, uy, r0, p1, dist, u, v, c0);
                }
            }
        }

        /** Soft at both edges of the bow, full across its middle. */
        private float edge(float k) {
            float e = Math.min(k, 1f - k) / 0.22f;
            return e >= 1f ? 1f : e * e * (3f - 2f * e);
        }

        private float dirY(float ay, float uy, float r, float p) {
            return ay * (float) Math.cos(r) + uy * (float) Math.cos(p) * (float) Math.sin(r);
        }

        /** A point {@code r} radians out from the anti-sun, at angle {@code p} round it (0 straight up), {@code dist} off. */
        private void vert(FxOut out, float ax, float ay, float ux, float uy, float r, float p, float dist, float u, float v, int c) {
            float cr = (float) Math.cos(r), sr = (float) Math.sin(r), cp = (float) Math.cos(p), sp = (float) Math.sin(p);
            float x = ax * cr + ux * cp * sr;
            float y = ay * cr + uy * cp * sr;
            float z = sp * sr;
            out.vertex(x * dist, y * dist, z * dist, u, v, c);
        }
    };

    // ------------------------------------------------------------------ leaves in the wind

    private static final int LEAF_RADIUS = 12;

    private void leavesTick(ClientLevel level, Player you) {
        // A dozen looks round a tick: a wood sheds a couple of leaves a second, a lone tree now and then.
        int tries = Math.round(12f * (float) (leaves.amount.get() / 100.0));
        BlockPos at = you.blockPosition();
        for (int i = 0; i < tries; i++) {
            pos.set(at.getX() + random.nextInt(LEAF_RADIUS * 2 + 1) - LEAF_RADIUS, at.getY() + random.nextInt(14) - 3,
                    at.getZ() + random.nextInt(LEAF_RADIUS * 2 + 1) - LEAF_RADIUS);
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LEAVES)) {
                continue;
            }
            below.set(pos.getX(), pos.getY() - 1, pos.getZ());
            if (!level.getBlockState(below).isAir() || random.nextFloat() > 0.35f) {
                continue;
            }
            int colour = leafColour(state, level);
            leaf(FxWorld.POOL, pos.getX() + random.nextFloat(), pos.getY() - 0.05, pos.getZ() + random.nextFloat(), colour,
                    windX(level), windZ(level), 0f);
            if (leavesSound.get() && random.nextFloat() < 0.05f) {
                sound("leaves", 0.3f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
    }

    /** A leaf's colour: its block's tint where it has one (the biome's foliage, birch's, spruce's), else its map colour. */
    private int leafColour(BlockState state, ClientLevel level) {
        //? if >=26.1 {
        /*var source = Minecraft.getInstance().getBlockColors().getTintSource(state, 0);
        int tint = source == null ? -1 : source.colorInWorld(state, level, pos);
        *///?} else {
        int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
        //?}
        return (tint != -1 ? tint : state.getMapColor(level, pos).col) | 0xFF000000;
    }

    /** The wind the leaves go with (blocks a second): the theme's, or a slow turning breeze; stronger in rain and storms. */
    private float windAngle() {
        return (float) (FxWorld.now() * 0.02);
    }

    private float windStrength(ClientLevel level) {
        return 0.7f * (1f + 0.6f * level.getRainLevel(1f) + 0.8f * level.getThunderLevel(1f));
    }

    private float windX(ClientLevel level) {
        return (float) Math.cos(windAngle()) * windStrength(level);
    }

    private float windZ(ClientLevel level) {
        return (float) Math.sin(windAngle()) * windStrength(level);
    }

    private void leaf(FxPool pool, double x, double y, double z, int colour, float wx, float wz, float delay) {
        Fx f = start(pool, leaves, LEAF, x, y, z, 6f);
        leaves.apply(f, 0);
        if (leafColours.get()) {
            FxColour.fixed(f, colour);
        }
        f.seed = random.nextInt(1 << 20);
        f.tx = wx * (0.7f + random.nextFloat() * 0.6f);
        f.tz = wz * (0.7f + random.nextFloat() * 0.6f);
        // Falls about half a block a second; lands on the ground under where it started.
        f.a = 0.45f + random.nextFloat() * 0.2f;
        f.c = ground(pool, x, z, y) - (float) y;
        FxPool.delay(f, delay);
        played = Math.max(played, f.life + delay);
        leavesMade++;
    }

    /**
     * A leaf blown along by the wind, swaying, tumbling and turning over as it falls (it narrows as it turns edge-on),
     * resting on the ground at the end and fading there.
     */
    static final FxKind LEAF = new FxKind("world_leaf", 64) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            if (out.glowPass()) {
                return;
            }
            float t = fx.time(age);
            float fall = Math.max(fx.c, -fx.a * t);
            boolean landed = fall <= fx.c;
            float flyT = landed ? (fx.a > 0f ? -fx.c / fx.a : t) : t;
            float sway = (float) Math.sin(flyT * 2.1f + (fx.seed & 63)) * 0.35f;
            float x = (float) fx.tx * flyT + sway, z = (float) fx.tz * flyT + sway * 0.5f;
            float alpha = (float) Math.pow(Math.min(1f, (1f - fx.progress(now)) / 0.25f), 1.2f) * fx.early(now);
            Vfx.origin(out, fx);
            int c = fx.tone(Ramp.MAIN, fx.seed, now);
            float size = 0.13f * Math.max(0.6f, fx.size);
            if (landed) {
                Vfx.decal(out, FxSoft.LEAF, x, fall + 0.004f, z, size, (fx.seed & 255) * 0.1f, Vfx.with(c, alpha));
                return;
            }
            float turn = (float) Math.cos(flyT * 3.3f + (fx.seed & 31));
            float spin = flyT * 1.7f * ((fx.seed & 1) == 0 ? 1f : -1f) + sway;
            int shade = Vfx.mix(c, turn < 0f ? 0xFF000000 : 0xFFFFFFFF, turn < 0f ? 0.25f : 0.1f);
            Vfx.billboard(out, FxSoft.LEAF, x, fall + 0.05f, z, size * Math.max(0.12f, Math.abs(turn)), size, spin, Vfx.with(shade, alpha));
        }
    };

    // ------------------------------------------------------------------ rain ripples

    private static final int RIPPLE_RADIUS = 10;

    private void ripplesTick(ClientLevel level, Player you) {
        float rain = level.getRainLevel(1f);
        if (rain < 0.2f) {
            return;
        }
        int n = Math.round(3f * rain * (float) (ripples.amount.get() / 100.0));
        BlockPos at = you.blockPosition();
        for (int i = 0; i < n; i++) {
            int x = at.getX() + random.nextInt(RIPPLE_RADIUS * 2 + 1) - RIPPLE_RADIUS;
            int z = at.getZ() + random.nextInt(RIPPLE_RADIUS * 2 + 1) - RIPPLE_RADIUS;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            pos.set(x, top, z);
            if (Math.abs(top - at.getY()) > 12 || !level.isRainingAt(pos)) {
                continue;
            }
            below.set(x, top - 1, z);
            FluidState fluid = level.getFluidState(below);
            boolean water = fluid.is(FluidTags.WATER);
            float y = water ? below.getY() + fluid.getHeight(level, below) : top;
            ripple(FxWorld.POOL, x + 0.15 + random.nextFloat() * 0.7, y, z + 0.15 + random.nextFloat() * 0.7, water, 0f);
            if (water && ripplesSound.get() && random.nextFloat() < 0.04f) {
                sound("ripple", 0.18f, 0.8f + random.nextFloat() * 0.4f);
            }
        }
    }

    private void ripple(FxPool pool, double x, double y, double z, boolean water, float delay) {
        Fx f = start(pool, ripples, RIPPLE, x, y, z, water ? 0.8f : 0.5f);
        ripples.apply(f, 0);
        f.i0 = water ? 1 : 0;
        FxPool.delay(f, delay);
        played = Math.max(played, f.life + delay);
        ripplesMade++;
    }

    /** A ring of texel cells widening on the surface; on water a second, smaller one follows it. */
    static final FxKind RIPPLE = new FxKind("world_ripple", 64) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float p = fx.progress(now);
            float alpha = (float) Math.pow(1f - p, 1.4f) * fx.early(now);
            Vfx.origin(out, fx);
            boolean glow = out.glowPass();
            float gk = FxBits.glowK(fx), s = fx.size;
            int c = fx.tone(Ramp.MAIN, 0, now);
            float r = (0.06f + 0.5f * Vfx.outCubic(p)) * s;
            // The drop's splash: a few droplets jumping up and falling back.
            if (p < 0.35f && !glow) {
                float k = p / 0.35f;
                for (int i = 0; i < 4; i++) {
                    double ang = i * Math.PI / 2 + Vfx.h(fx.seed, i) * 0.8;
                    float d = 0.08f * s * k;
                    float y = 0.003f + 0.12f * s * 4f * k * (1f - k);
                    Vfx.sprite(out, FxSoft.DOT, (float) Math.cos(ang) * d, y, (float) Math.sin(ang) * d, 0.018f * s, 0f,
                            Vfx.with(Vfx.white(c, 0.6f), 0.8f * (1f - k)));
                }
            }
            if (glow) {
                Vfx.ring(out, FxSoft.TUBE, 0f, 0.004f, 0f, r, 0.08f * s, Vfx.with(c, 0.5f * alpha * gk), false);
            } else {
                Vfx.ring(out, FxSoft.TUBE, 0f, 0.003f, 0f, r, 0.035f * s, Vfx.with(Vfx.white(c, 0.55f), 0.9f * alpha), false);
                if (fx.i0 == 1 && p > 0.25f) {
                    float q = (p - 0.25f) / 0.75f;
                    Vfx.ring(out, FxSoft.TUBE, 0f, 0.003f, 0f, (0.05f + 0.32f * Vfx.outCubic(q)) * s, 0.026f * s,
                            Vfx.with(Vfx.white(c, 0.55f), 0.55f * (1f - q)), false);
                }
            }
        }
    };

    // ------------------------------------------------------------------ cosmos break

    private void cosmosTick(Minecraft mc, ClientLevel level) {
        int stage = mc.gameMode != null && mc.gameMode.isDestroying() ? mc.gameMode.getDestroyStage() : -1;
        if (stage >= 0 && mc.hitResult instanceof BlockHitResult bh) {
            mining.set(bh.getBlockPos());
            miningStage = stage;
            miningState = level.getBlockState(mining);
            return;
        }
        // It broke: was nearly done and now it's air.
        if (miningStage >= 6 && miningState != null && level.getBlockState(mining).isAir() && cosmos.on()) {
            burst(FxWorld.POOL, mining.getX() + 0.5, mining.getY() + 0.5, mining.getZ() + 0.5, 0f);
            if (cosmosSound.get()) {
                sound("sparkle", 0.45f, 0.9f + random.nextFloat() * 0.2f);
            }
        }
        miningStage = -1;
        miningState = null;
    }

    /** Whether the cracks of {@code breaker}'s mining are Sol's (yours, while Cosmos break is on): the game's stand aside. */
    public static boolean ownCracks(int breaker) {
        WorldFx w = io.github.georgwav.cosmetics.client.Modules.WORLD_FX;
        Player you = Minecraft.getInstance().player;
        return w.enabled() && w.cosmos.on() && you != null && breaker == you.getId();
    }

    private void burst(FxPool pool, double x, double y, double z, float delay) {
        Fx f = start(pool, cosmos, BURST, x, y, z, 0.9f);
        cosmos.apply(f, 0);
        f.seed = random.nextInt(1 << 20);
        FxPool.delay(f, delay);
        played = Math.max(played, f.life + delay);
        burstsMade++;
    }

    /** Where the block was: a flash, then stars bursting out of it, slowing, twinkling and going out, with motes of light. */
    static final FxKind BURST = new FxKind("world_star_burst", 6) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age), p = Math.min(1f, t / 0.9f);
            float alpha = (float) Math.pow(1f - p, 1.2f) * fx.early(now);
            Vfx.origin(out, fx);
            boolean glow = out.glowPass();
            float gk = FxBits.glowK(fx), s = fx.size;
            int c = fx.tone(Ramp.MAIN, 0, now), core = fx.tone(Ramp.CORE, 0, now);
            if (glow && t < 0.25f) {
                float k = t / 0.25f;
                Vfx.hot(out, 0f, 0f, 0f, 0.9f * s, Vfx.with(Vfx.white(c, 0.6f), (1f - k) * (1f - k) * fx.early(now) * gk));
                Vfx.glint(out, 0f, 0f, 0f, 1.1f * s, 0.3f, Vfx.with(0xFFFFFFFF, (1f - k) * fx.early(now) * gk));
            }
            float out1 = Vfx.outCubic(p);
            for (int i = 0; i < 18; i++) {
                float a1 = Vfx.h(fx.seed, i) * 6.2832f, a2 = Vfx.s(fx.seed, i + 40) * 1.2f;
                float r = (0.4f + 0.7f * Vfx.h(fx.seed, i + 80)) * out1 * s;
                float x = (float) (Math.cos(a1) * Math.cos(a2)) * r, y = (float) Math.sin(a2) * r - 0.2f * p * p, z = (float) (Math.sin(a1) * Math.cos(a2)) * r;
                float tw = 0.55f + 0.45f * (float) Math.sin(t * 20f + i * 1.9f);
                int col = (i & 1) == 0 ? c : core;
                if (glow) {
                    Vfx.glint(out, x, y, z, 0.18f * s * tw, t * 2f + i, Vfx.with(Vfx.white(col, 0.4f), alpha * gk));
                } else {
                    Vfx.sprite(out, FxSoft.STAR, x, y, z, 0.05f * s * tw, t * 2f + i, Vfx.with(Vfx.white(col, 0.4f), alpha));
                }
            }
            FxBits.embers(out, fx, t, 0f, -0.2f, 0f, 10, 0.4f * s, 0.6f, 0.2f, 0.8f, c, s, 7);
        }
    };

    /** The block's boxes while it's mined (made again only when the block or its state changes). */
    private BlockState shapeOf;
    private final BlockPos.MutableBlockPos shapeAt = new BlockPos.MutableBlockPos();
    private List<AABB> boxes = List.of();
    private final int[] cosmosRamp = new int[Ramp.TONES];

    @Override
    public boolean active() {
        return enabled() && cosmos.on() && miningStage >= 0 && miningState != null;
    }

    /**
     * The block you mine as a window onto stars: each face (just over the block) fills with a deep sky in steps as the
     * cracks spread, stars twinkling in it, and Sol's own cracks across it in the ramp's core tone, glowing.
     */
    @Override
    public void draw(WorldOut out, float now) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        if (shapeOf != miningState || !shapeAt.equals(mining)) {
            shapeOf = miningState;
            shapeAt.set(mining);
            boxes = miningState.getShape(level, mining).toAabbs();
            if (boxes.size() > 4) {
                boxes = boxes.subList(0, 4);
            }
        }
        if (boxes.isEmpty()) {
            return;
        }
        Ramp.of(cosmos.colourMode.get().equals("custom") ? cosmos.colour.argb() : FxColour.accent(), cosmosRamp);
        boolean glow = out.glowPass();
        out.origin(mining.getX(), mining.getY(), mining.getZ());
        out.use(FxSprites.atlas());
        float[] uv = FxSprites.SQUARE.uv[FxSprites.ALL];
        float u = (uv[0] + uv[2]) / 2f, v = (uv[1] + uv[3]) / 2f;
        int stage = Math.min(9, miningStage);
        float fill = 0.55f + 0.045f * stage;
        int sky = Colors.withAlpha(Colors.scale(cosmosRamp[Ramp.DEEP], 0.45f), fill);
        int seedBase = (int) mining.asLong();
        for (AABB b : boxes) {
            float x0 = (float) b.minX - 0.003f, y0 = (float) b.minY - 0.003f, z0 = (float) b.minZ - 0.003f;
            float x1 = (float) b.maxX + 0.003f, y1 = (float) b.maxY + 0.003f, z1 = (float) b.maxZ + 0.003f;
            if (!glow) {
                FxDraw.box(out, x0, y0, z0, x1, y1, z1, sky, sky, sky, sky);
            }
            for (int face = 0; face < 6; face++) {
                cosmosFace(out, face, x0, y0, z0, x1, y1, z1, stage, seedBase + face * 977, now, glow, u, v);
            }
        }
        cosmosFrames++;
    }

    /** One face's stars and cracks, texel cells on the face's 16 x 16 grid (whatever the box's size). */
    private void cosmosFace(WorldOut out, int face, float x0, float y0, float z0, float x1, float y1, float z1, int stage, int seed,
                            float now, boolean glow, float u, float v) {
        // Stars: twenty per face, twinkling in steps, brighter as it breaks.
        if (!glow) {
            for (int i = 0; i < 20; i++) {
                float tw = Ramp.steps(0.6f + 0.4f * (float) Math.sin(now * (1.5f + FxDraw.hash(seed, i) * 2f) + i), 3, true);
                int tone = FxDraw.hash(seed, i + 50) > 0.75f ? Ramp.CORE : Ramp.MAIN;
                int gx = (int) (FxDraw.hash(seed, i + 100) * 16), gy = (int) (FxDraw.hash(seed, i + 150) * 16);
                faceCell(out, face, x0, y0, z0, x1, y1, z1, gx, gy,
                        Colors.withAlpha(cosmosRamp[tone], Math.min(1f, tw * (0.7f + 0.035f * stage))), u, v);
            }
        }
        // Cracks: stage + 1 of them, each a random walk from the face's middle, eight cells long.
        int cracks = stage + 1;
        for (int k = 0; k < cracks; k++) {
            int gx = 7 + (k & 1), gy = 7 + ((k >> 1) & 1);
            int dir = (int) (FxDraw.hash(seed, k + 300) * 8);
            for (int step = 0; step < 8; step++) {
                int c = glow ? Colors.withAlpha(FxColour.glow(), 0.35f) : Colors.withAlpha(cosmosRamp[Ramp.CORE], 0.95f);
                faceCell(out, face, x0, y0, z0, x1, y1, z1, gx, gy, c, u, v);
                if (FxDraw.hash(seed, k * 31 + step + 500) < 0.35f) {
                    dir = (dir + (FxDraw.hash(seed, k * 17 + step) < 0.5f ? 1 : 7)) & 7;
                }
                gx += DX[dir];
                gy += DY[dir];
                if (gx < 0 || gy < 0 || gx > 15 || gy > 15) {
                    break;
                }
            }
        }
    }

    private static final int[] DX = {1, 1, 0, -1, -1, -1, 0, 1}, DY = {0, 1, 1, 1, 0, -1, -1, -1};

    /** Cell (gx, gy) of a face's 16 x 16 grid, a hair off the face. */
    public static void faceCell(WorldOut out, int face, float x0, float y0, float z0, float x1, float y1, float z1, int gx, int gy,
                                 int argb, float u, float v) {
        float a0 = gx / 16f, a1 = (gx + 1) / 16f, b0 = gy / 16f, b1 = (gy + 1) / 16f;
        float e = 0.002f;
        switch (face) {
            case 0 -> quad(out, x0 + (x1 - x0) * a0, y1 + e, z0 + (z1 - z0) * b0, x0 + (x1 - x0) * a1, y1 + e, z0 + (z1 - z0) * b1, 1, argb, u, v);
            case 1 -> quad(out, x0 + (x1 - x0) * a0, y0 - e, z0 + (z1 - z0) * b0, x0 + (x1 - x0) * a1, y0 - e, z0 + (z1 - z0) * b1, 1, argb, u, v);
            case 2 -> quad(out, x0 + (x1 - x0) * a0, y0 + (y1 - y0) * b0, z0 - e, x0 + (x1 - x0) * a1, y0 + (y1 - y0) * b1, z0 - e, 2, argb, u, v);
            case 3 -> quad(out, x0 + (x1 - x0) * a0, y0 + (y1 - y0) * b0, z1 + e, x0 + (x1 - x0) * a1, y0 + (y1 - y0) * b1, z1 + e, 2, argb, u, v);
            case 4 -> quad(out, x0 - e, y0 + (y1 - y0) * b0, z0 + (z1 - z0) * a0, x0 - e, y0 + (y1 - y0) * b1, z0 + (z1 - z0) * a1, 0, argb, u, v);
            default -> quad(out, x1 + e, y0 + (y1 - y0) * b0, z0 + (z1 - z0) * a0, x1 + e, y0 + (y1 - y0) * b1, z0 + (z1 - z0) * a1, 0, argb, u, v);
        }
    }

    /** An axis-aligned quad between two corners, flat across axis {@code flat} (0 x, 1 y, 2 z). */
    private static void quad(WorldOut out, float ax, float ay, float az, float bx, float by, float bz, int flat, int argb, float u, float v) {
        switch (flat) {
            case 0 -> {
                out.vertex(ax, ay, az, u, v, argb);
                out.vertex(ax, by, az, u, v, argb);
                out.vertex(ax, by, bz, u, v, argb);
                out.vertex(ax, ay, bz, u, v, argb);
            }
            case 1 -> {
                out.vertex(ax, ay, az, u, v, argb);
                out.vertex(ax, ay, bz, u, v, argb);
                out.vertex(bx, ay, bz, u, v, argb);
                out.vertex(bx, ay, az, u, v, argb);
            }
            default -> {
                out.vertex(ax, ay, az, u, v, argb);
                out.vertex(bx, ay, az, u, v, argb);
                out.vertex(bx, by, az, u, v, argb);
                out.vertex(ax, by, az, u, v, argb);
            }
        }
    }

    // ------------------------------------------------------------------ try it

    /** The longest of what Try it started (its life and delay), for how long it plays. */
    private float played;

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        played = 0f;
        if (snow.on()) {
            for (int i = 0; i < 4; i++) {
                print(pool, dummy.x, dummy.y, dummy.z - 0.6 + i * 0.4, 0, 1, (i & 1) == 0, i * 0.3f);
            }
        }
        if (ripples.on()) {
            for (int i = 0; i < 8; i++) {
                ripple(pool, dummy.x + FxDraw.signed(i, 3) * 1.5, dummy.y, dummy.z + FxDraw.signed(i, 7) * 1.5, (i & 1) == 0, i * 0.2f);
            }
        }
        if (leaves.on()) {
            for (int i = 0; i < 6; i++) {
                leaf(pool, dummy.x - 1 + FxDraw.signed(i, 11), dummy.y + 2.2, dummy.z + FxDraw.signed(i, 13), 0xFF4E8F2E, 0.6f, 0.2f, i * 0.3f);
            }
        }
        if (cosmos.on()) {
            burst(pool, dummy.x + 1.2, dummy.y + 0.5, dummy.z, 0.4f);
        }
        if (rainbow.on()) {
            // A short one, opposite the sun wherever you look.
            startRainbow(pool, 12f);
        }
        return played;
    }
}
