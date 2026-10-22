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
 * (red where they can now, amber where only at night), slime chunks tinted green (from the world's seed: your own
 * world's, one you type in, or as an operator the server's answer to /seed, asked once and kept out of the chat),
 * how far nearby beacons reach (a band at your height along
 * the edge of each one's effect), and in singleplayer the outlines of the structures round you (villages, temples,
 * strongholds…), which only the world's server knows of.
 */
public final class WorldOverlays extends Module {
    private final Setting.Toggle chunks = add(new Setting.Toggle("chunk_borders", true).group("overlays"));
    private final Setting.Toggle spawns = add(new Setting.Toggle("spawnable", true).group("overlays"));
    private final Setting.Toggle slime = add(new Setting.Toggle("slime_chunks", false).group("slime"));
    /** The world's seed for slime chunks on a server (singleplayer knows its own). */
    private final Setting.Text typedSeed = add(new Setting.Text("seed", "", 32).group("slime"));
    private final Setting.Toggle beacons = add(new Setting.Toggle("beacon_range", false).group("overlays"));
    private final Setting.Toggle structures = add(new Setting.Toggle("structures", false).group("overlays"));
    /** Nearby beacons: centre x, z and their reach in blocks. */
    private final int[] bx = new int[16], bz = new int[16], reach = new int[16];
    private int beaconCount;
    /** The level the marks were found in. */
    private net.minecraft.client.multiplayer.ClientLevel shownLevel;
    /** The structures round you (min x, y, z, max x, y, z each), found on the game's server thread. */
    private volatile int[] structureBoxes = NONE;
    private volatile boolean structureSearch;
    /** Client ticks since the last look for structures. */
    private int structureWait;
    private volatile Throwable structureError;
    /** How many searches the server thread has finished (the self-test waits for one). */
    public static volatile int structureScans;
    private static final int[] NONE = new int[0];
    private static final int STRUCTURE_CHUNKS = 8, MAX_STRUCTURES = 32;

    private static final int RADIUS = 12, MAX = 2048;
    private final int[] sx = new int[MAX], sy = new int[MAX], sz = new int[MAX];
    private final boolean[] always = new boolean[MAX];
    private int count;
    private Boolean chunksBefore;
    private long seed = Long.MIN_VALUE;
    /** Asked this server for its seed (as an operator); waiting for the answer (until a tick count). */
    private boolean seedAsked;
    private volatile boolean seedAwaited;
    private long seedWaitUntil;
    /** The server's answer to /seed on this connection, or Long.MIN_VALUE: one server, one seed, every dimension. */
    private long serverSeed = Long.MIN_VALUE;
    /** The connection the server seed belongs to. */
    private Object seedConnection;

    public WorldOverlays() {
        super("world_overlays", Category.WORLD, "grid");
        // The answer to our own /seed: its number taken, the line kept out of the chat.
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (!seedAwaited || overlay || !(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t)
                    || !t.getKey().equals("commands.seed.success") || t.getArgs().length == 0) {
                return true;
            }
            Object arg = t.getArgs()[0];
            Long found = io.github.georgwav.cosmetics.sol.client.Seeds.fromReply(
                    arg instanceof net.minecraft.network.chat.Component c ? c.getString() : String.valueOf(arg));
            if (found == null) {
                return true;
            }
            seedAwaited = false;
            Minecraft.getInstance().execute(() -> {
                serverSeed = found;
                seed = Long.MIN_VALUE;
            });
            return false;
        });
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
        // A seed typed in (or taken away) is read again on the next tick.
        seed = Long.MIN_VALUE;
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
        beaconCount = 0;
        structureBoxes = NONE;
    }

    @Override
    protected void tick(Minecraft mc) {
        if (mc.level != shownLevel) {
            // Another world or dimension: nothing found in the last one stays.
            shownLevel = mc.level;
            count = 0;
            beaconCount = 0;
            seed = Long.MIN_VALUE;
            structureBoxes = NONE;
            structureSearch = false;
        }
        if (mc.getConnection() != seedConnection) {
            // Another server (or none): its seed is asked for again, once.
            seedConnection = mc.getConnection();
            seedAsked = false;
            seedAwaited = false;
            serverSeed = Long.MIN_VALUE;
        }
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (seedAwaited && mc.level.getGameTime() > seedWaitUntil) {
            // No answer in ten seconds: a /seed of your own later isn't taken for ours.
            seedAwaited = false;
        }
        Throwable failed = structureError;
        if (failed != null) {
            structureError = null;
            io.github.georgwav.cosmetics.sol.util.LogOnce.warn("structures", "Structure outlines are off for this session: {}", failed.toString());
            structures.set(false);
        }
        var server = mc.getSingleplayerServer();
        if (structures.get() && server != null) {
            // Once a second by the client's own ticks: the world's clock jumps when the server lags, and could step
            // over every whole second for a long time.
            if (!structureSearch && ++structureWait >= 20) {
                structureWait = 0;
                findStructures(server, mc.level.dimension(), mc.player.blockPosition());
            }
        } else {
            structureBoxes = NONE;
        }
        if (slime.get() && seed == Long.MIN_VALUE) {
            Long typed = io.github.georgwav.cosmetics.sol.client.Seeds.typed(typedSeed.get());
            if (mc.getSingleplayerServer() != null) {
                seed = mc.getSingleplayerServer().overworld().getSeed();
            } else if (typed != null) {
                seed = typed;
            } else if (serverSeed != Long.MIN_VALUE) {
                seed = serverSeed;
            } else if (!seedAsked && CameraTweaks.mayFreecam(mc)) {
                seedAsked = true;
                seedAwaited = true;
                seedWaitUntil = mc.level.getGameTime() + 200;
                mc.player.connection.sendCommand("seed");
            }
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

    /**
     * The structures whose box reaches the chunks round you, looked up on the game's server thread (the client never
     * hears of structures): each chunk lists the chunks where the structures overlapping it start.
     */
    private void findStructures(net.minecraft.server.MinecraftServer server,
                                net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos at) {
        structureSearch = true;
        int pcx = at.getX() >> 4, pcz = at.getZ() >> 4;
        server.execute(() -> {
            try {
                var level = server.getLevel(dimension);
                structureBoxes = level == null ? NONE : structuresNear(level, pcx, pcz);
                structureScans++;
            } catch (RuntimeException | LinkageError e) {
                structureBoxes = NONE;
                structureError = e;
            } finally {
                structureSearch = false;
            }
        });
    }

    private static int[] structuresNear(net.minecraft.server.level.ServerLevel level, int pcx, int pcz) {
        int[] out = new int[MAX_STRUCTURES * 6];
        int n = 0;
        java.util.Set<net.minecraft.world.level.levelgen.structure.StructureStart> seen =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (int dx = -STRUCTURE_CHUNKS; dx <= STRUCTURE_CHUNKS; dx++) {
            for (int dz = -STRUCTURE_CHUNKS; dz <= STRUCTURE_CHUNKS; dz++) {
                var chunk = level.getChunkSource().getChunkNow(pcx + dx, pcz + dz);
                if (chunk == null) {
                    continue;
                }
                for (var e : chunk.getAllReferences().entrySet()) {
                    for (var it = e.getValue().iterator(); it.hasNext(); ) {
                        long from = it.nextLong();
                        var home = level.getChunk(net.minecraft.world.level.ChunkPos.getX(from), net.minecraft.world.level.ChunkPos.getZ(from),
                                net.minecraft.world.level.chunk.status.ChunkStatus.STRUCTURE_STARTS, false);
                        var start = home == null ? null : home.getStartForStructure(e.getKey());
                        if (start == null || !start.isValid() || !seen.add(start)) {
                            continue;
                        }
                        var box = start.getBoundingBox();
                        out[n * 6] = box.minX();
                        out[n * 6 + 1] = box.minY();
                        out[n * 6 + 2] = box.minZ();
                        out[n * 6 + 3] = box.maxX();
                        out[n * 6 + 4] = box.maxY();
                        out[n * 6 + 5] = box.maxZ();
                        if (++n == MAX_STRUCTURES) {
                            return out;
                        }
                    }
                }
            }
        }
        return java.util.Arrays.copyOf(out, n * 6);
    }

    /** The boxes found last (min x, y, z, max x, y, z each), for the self-test. */
    public static int[] structureBoxes() {
        return Modules.WORLD_OVERLAYS.structureBoxes;
    }

    /** Draws the marks (end of the level render, with the world's depth). */
    public static void render() {
        WorldOverlays w = Modules.WORLD_OVERLAYS;
        if (!w.enabled() || !FrameInfo.inWorld || (w.count == 0 && w.beaconCount == 0 && w.structureBoxes.length == 0
                && !(w.slime.get() && w.seed != Long.MIN_VALUE))) {
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
            float y = mc.player == null ? -1.6f : (float) (feetY(mc) - FrameInfo.camY) + 0.03f;
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
            float y0 = (float) (feetY(mc) - FrameInfo.camY) - 0.5f, y1 = y0 + 2.5f;
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
        int[] boxes = structureBoxes;
        for (int i = 0; i + 5 < boxes.length; i += 6) {
            // The box's blocks from its first to the far side of its last.
            boxEdges(view, (float) (boxes[i] - FrameInfo.camX), (float) (boxes[i + 1] - FrameInfo.camY), (float) (boxes[i + 2] - FrameInfo.camZ),
                    (float) (boxes[i + 3] + 1 - FrameInfo.camX), (float) (boxes[i + 4] + 1 - FrameInfo.camY),
                    (float) (boxes[i + 5] + 1 - FrameInfo.camZ), 0xB0C59BFF);
        }
        WorldDraw.end();
    }

    /** Your feet's height this frame (moving smoothly between ticks, as the camera does). */
    private static double feetY(Minecraft mc) {
        return net.minecraft.util.Mth.lerp(FrameInfo.partialTick, mc.player.yo, mc.player.getY());
    }

    /** A box's twelve edges, each a thin bar seen from every side. */
    private static void boxEdges(org.joml.Matrix4f view, float x0, float y0, float z0, float x1, float y1, float z1, int c) {
        for (int a = 0; a < 2; a++) {
            for (int b = 0; b < 2; b++) {
                float ya = a == 0 ? y0 : y1, zb = b == 0 ? z0 : z1, xa = a == 0 ? x0 : x1, yb = b == 0 ? y0 : y1;
                bar(view, x0, ya, zb, x1, ya, zb, c);
                bar(view, xa, y0, zb, xa, y1, zb, c);
                bar(view, xa, yb, z0, xa, yb, z1, c);
            }
        }
    }

    /** An edge along one axis as two crossed strips, 0.12 blocks wide. */
    private static void bar(org.joml.Matrix4f view, float ax, float ay, float az, float bx, float by, float bz, int c) {
        float h = 0.06f;
        boolean alongX = ax != bx, alongY = ay != by;
        // Two directions across the bar.
        float ux = alongX ? 0f : h, uy = alongX ? h : 0f;
        float vy = alongX || alongY ? 0f : h, vz = alongX || alongY ? h : 0f;
        corner(view, ax - ux, ay - uy, az, c);
        corner(view, bx - ux, by - uy, bz, c);
        corner(view, bx + ux, by + uy, bz, c);
        corner(view, ax + ux, ay + uy, az, c);
        corner(view, ax, ay - vy, az - vz, c);
        corner(view, bx, by - vy, bz - vz, c);
        corner(view, bx, by + vy, bz + vz, c);
        corner(view, ax, ay + vy, az + vz, c);
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
