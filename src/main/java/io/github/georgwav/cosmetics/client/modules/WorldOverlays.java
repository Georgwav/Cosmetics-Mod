package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

/**
 * World Overlays: the game's chunk borders kept on, the spots where mobs can spawn marked on the ground around you
 * (red where they can now, amber where only at night), slime chunks tinted green in singleplayer (from the
 * world's seed, which only your own world tells), and how far nearby beacons reach (a band at your height along
 * the edge of each one's effect).
 */
public final class WorldOverlays extends Module {
    private final Setting.Toggle chunks = add(new Setting.Toggle("chunk_borders", true));
    private final Setting.Toggle spawns = add(new Setting.Toggle("spawnable", true));
    private final Setting.Toggle slime = add(new Setting.Toggle("slime_chunks", false));
    private final Setting.Toggle beacons = add(new Setting.Toggle("beacon_range", false));
    /** Nearby beacons: centre x, z and their reach in blocks. */
    private final int[] bx = new int[16], bz = new int[16], reach = new int[16];
    private int beaconCount;

    private static final int RADIUS = 12, MAX = 2048;
    private final int[] sx = new int[MAX], sy = new int[MAX], sz = new int[MAX];
    private final boolean[] always = new boolean[MAX];
    private int count;
    private Boolean chunksBefore;
    private long seed = Long.MIN_VALUE;

    public WorldOverlays() {
        super("world_overlays", Category.WORLD, "grid");
    }

    private static boolean chunkBorders(Minecraft mc) {
        //? if >=1.21.9 {
        return mc.debugEntries.isCurrentlyEnabled(net.minecraft.client.gui.components.debug.DebugScreenEntries.CHUNK_BORDERS);
        //?} else {
        /*return chunkBordersShown;
        *///?}
    }

    /** Before 1.21.9 the game only flips its chunk borders, so the module keeps count of what it set. */
    private static boolean chunkBordersShown;

    private static void chunkBorders(Minecraft mc, boolean on) {
        //? if >=1.21.9 {
        mc.debugEntries.setStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.CHUNK_BORDERS,
                on ? net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.ALWAYS_ON
                        : net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.NEVER);
        //?} else {
        /*if (chunkBordersShown != on) {
            chunkBordersShown = mc.debugRenderer.switchRenderChunkborder();
        }
        *///?}
    }

    @Override
    protected void onEnable(Minecraft mc) {
        onSettingsChanged(mc);
    }

    @Override
    protected void onSettingsChanged(Minecraft mc) {
        if (chunks.get()) {
            if (chunksBefore == null) {
                chunksBefore = chunkBorders(mc);
            }
            chunkBorders(mc, true);
        } else if (chunksBefore != null) {
            chunkBorders(mc, chunksBefore);
            chunksBefore = null;
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        if (chunksBefore != null) {
            chunkBorders(mc, chunksBefore);
            chunksBefore = null;
        }
        count = 0;
    }

    @Override
    protected void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            count = 0;
            seed = Long.MIN_VALUE;
            return;
        }
        if (slime.get() && seed == Long.MIN_VALUE && mc.getSingleplayerServer() != null) {
            seed = mc.getSingleplayerServer().overworld().getSeed();
        }
        if (beacons.get() && mc.level.getGameTime() % 20 == 0) {
            findBeacons(mc);
        } else if (!beacons.get()) {
            beaconCount = 0;
        }
        if (!spawns.get() || mc.level.getGameTime() % 10 != 0) {
            if (!spawns.get()) {
                count = 0;
            }
            return;
        }
        // Where a mob could stand: a solid top below, room for it, and no block light.
        BlockPos at = mc.player.blockPosition();
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), below = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS && n < MAX; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS && n < MAX; dz++) {
                for (int dy = -4; dy <= 3 && n < MAX; dy++) {
                    p.set(at.getX() + dx, at.getY() + dy, at.getZ() + dz);
                    below.set(p.getX(), p.getY() - 1, p.getZ());
                    BlockState floor = mc.level.getBlockState(below);
                    if (!floor.isFaceSturdy(mc.level, below, Direction.UP) || !mc.level.getBlockState(p).getCollisionShape(mc.level, p).isEmpty()
                            || !mc.level.getFluidState(p).isEmpty() || !mc.level.getBlockState(p.above()).getCollisionShape(mc.level, p.above()).isEmpty()) {
                        continue;
                    }
                    if (mc.level.getBrightness(LightLayer.BLOCK, p) > 0) {
                        continue;
                    }
                    sx[n] = p.getX();
                    sy[n] = p.getY();
                    sz[n] = p.getZ();
                    always[n] = mc.level.getBrightness(LightLayer.SKY, p) <= 7;
                    n++;
                }
            }
        }
        count = n;
    }

    /** The beacons in the chunks around you that light a beam (their pyramid's levels known on the client). */
    private void findBeacons(Minecraft mc) {
        int n = 0;
        int pcx = mc.player.blockPosition().getX() >> 4, pcz = mc.player.blockPosition().getZ() >> 4;
        for (int dx = -4; dx <= 4 && n < bx.length; dx++) {
            for (int dz = -4; dz <= 4 && n < bx.length; dz++) {
                var chunk = mc.level.getChunkSource().getChunk(pcx + dx, pcz + dz, false);
                if (chunk == null) {
                    continue;
                }
                for (var be : chunk.getBlockEntities().values()) {
                    if (be instanceof net.minecraft.world.level.block.entity.BeaconBlockEntity
                            && be instanceof io.github.georgwav.cosmetics.mixin.BeaconLevelsAccess beacon && n < bx.length) {
                        int levels = beacon.cosmeticsmod$levels();
                        if (levels > 0) {
                            bx[n] = be.getBlockPos().getX();
                            bz[n] = be.getBlockPos().getZ();
                            reach[n] = levels * 10 + 10;
                            n++;
                        }
                    }
                }
            }
        }
        beaconCount = n;
    }

    /** Draws the marks (end of the level render, with the world's depth). */
    public static void render() {
        WorldOverlays w = Modules.WORLD_OVERLAYS;
        if (!w.enabled() || !FrameInfo.inWorld || (w.count == 0 && w.beaconCount == 0 && !(w.slime.get() && w.seed != Long.MIN_VALUE))) {
            return;
        }
        Guard.run(Feature.MODULES, w::draw);
    }

    private void draw() {
        var view = FrameInfo.view;
        WorldDraw.begin(WorldDraw.WORLD_ALPHA, null);
        for (int i = 0; i < count; i++) {
            float x = (float) (sx[i] - FrameInfo.camX), y = (float) (sy[i] - FrameInfo.camY) + 0.02f, z = (float) (sz[i] - FrameInfo.camZ);
            int c = always[i] ? 0x66FF4040 : 0x55FFC35C;
            // A small diamond in the middle of the block's top.
            corner(view, x + 0.5f, y, z + 0.2f, c);
            corner(view, x + 0.8f, y, z + 0.5f, c);
            corner(view, x + 0.5f, y, z + 0.8f, c);
            corner(view, x + 0.2f, y, z + 0.5f, c);
        }
        if (slime.get() && seed != Long.MIN_VALUE) {
            int cx = (int) Math.floor(FrameInfo.camX) >> 4, cz = (int) Math.floor(FrameInfo.camZ) >> 4;
            Minecraft mc = Minecraft.getInstance();
            float y = mc.player == null ? -1.6f : (float) (mc.player.getY() - FrameInfo.camY) + 0.03f;
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    if (!slimeChunk(seed, cx + dx, cz + dz)) {
                        continue;
                    }
                    float x0 = (float) (((cx + dx) << 4) - FrameInfo.camX), z0 = (float) (((cz + dz) << 4) - FrameInfo.camZ);
                    int c = 0x3355E06A;
                    corner(view, x0, y, z0, c);
                    corner(view, x0 + 16f, y, z0, c);
                    corner(view, x0 + 16f, y, z0 + 16f, c);
                    corner(view, x0, y, z0 + 16f, c);
                }
            }
        }
        Minecraft mc = Minecraft.getInstance();
        if (beaconCount > 0 && mc.player != null) {
            float y0 = (float) (mc.player.getY() - FrameInfo.camY) - 0.5f, y1 = y0 + 2.5f;
            int c = 0x2E66D9FF;
            for (int i = 0; i < beaconCount; i++) {
                // The effect reaches a square round the beacon's block: reach blocks out from its edges.
                float x0 = (float) (bx[i] - reach[i] - FrameInfo.camX), x1 = (float) (bx[i] + 1 + reach[i] - FrameInfo.camX);
                float z0 = (float) (bz[i] - reach[i] - FrameInfo.camZ), z1 = (float) (bz[i] + 1 + reach[i] - FrameInfo.camZ);
                wall(view, x0, z0, x1, z0, y0, y1, c);
                wall(view, x1, z0, x1, z1, y0, y1, c);
                wall(view, x1, z1, x0, z1, y0, y1, c);
                wall(view, x0, z1, x0, z0, y0, y1, c);
            }
        }
        WorldDraw.end();
    }

    private static void wall(org.joml.Matrix4f view, float xa, float za, float xb, float zb, float y0, float y1, int c) {
        corner(view, xa, y0, za, c);
        corner(view, xb, y0, zb, c);
        corner(view, xb, y1, zb, c);
        corner(view, xa, y1, za, c);
    }

    /** The game's slime chunk rule (its seed, the chunk's place and its fixed salt, one in ten). */
    static boolean slimeChunk(long seed, int x, int z) {
        Random r = new Random(seed + (long) (x * x * 0x4c1906) + (long) (x * 0x5ac0db) + (long) (z * z) * 0x4307a7L
                + (long) (z * 0x5f24f) ^ 0x3ad8025fL);
        return r.nextInt(10) == 0;
    }

    private static void corner(org.joml.Matrix4f view, float x, float y, float z, int c) {
        WorldDraw.vertex(view.m00() * x + view.m10() * y + view.m20() * z, view.m01() * x + view.m11() * y + view.m21() * z,
                view.m02() * x + view.m12() * y + view.m22() * z, 0f, 0f, c);
    }
}
