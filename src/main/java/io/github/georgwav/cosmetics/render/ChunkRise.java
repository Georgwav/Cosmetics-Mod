package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/**
 * World FX › Chunk rise: each chunk section the client gets rises from 8 blocks below into place over 0.6 s, easing
 * in, the first time it's drawn. The game's own draw of each section is moved down by {@link #offset} (ChunkRiseMixin):
 * smoothly where the game hands each section's place over as a float, in whole blocks where it hands it over as whole
 * numbers (1.21.11 and 26.x). Not on 1.21.5 (its sections' places are set where no hook reaches cleanly), nor with
 * Sodium (it draws the chunks its own way). Render thread only.
 */
public final class ChunkRise {
    public static final float DEPTH = 8f, SECONDS = 0.6f;
    /** Whether this version has the hook. */
    public static final boolean AVAILABLE =
            //? if >=1.21.5 && <1.21.6 {
            /*false;
            *///?} else {
            true;
            //?}

    /** When each section (by its packed section position) was first drawn, while it rises. */
    private static final Long2FloatOpenHashMap RISING = new Long2FloatOpenHashMap();
    /** Sections that have risen (until their chunk unloads). */
    private static final LongOpenHashSet RISEN = new LongOpenHashSet();
    /** For the in-game test: sections that started rising. */
    public static int started;

    private ChunkRise() {
    }

    /** Whether it works here: this version has the hook and Sodium isn't drawing the chunks. */
    public static boolean works() {
        return AVAILABLE && !io.github.georgwav.cosmetics.compat.Compat.SODIUM;
    }

    private static boolean on() {
        return Modules.WORLD_FX.enabled() && Modules.WORLD_FX.chunkRise.get();
    }

    /** How far down (blocks, 0 or less) the section at block origin (x, y, z) is drawn now. */
    public static float offset(int x, int y, int z) {
        if (!on()) {
            if (!RISEN.isEmpty() || !RISING.isEmpty()) {
                RISEN.clear();
                RISING.clear();
            }
            return 0f;
        }
        long key = key(x >> 4, y >> 4, z >> 4);
        if (RISEN.contains(key)) {
            return 0f;
        }
        float now = FrameInfo.time;
        float born = RISING.getOrDefault(key, Float.NaN);
        if (Float.isNaN(born)) {
            RISING.put(key, now);
            started++;
            return drop(0f);
        }
        float k = (now - born) / SECONDS;
        if (k >= 1f) {
            RISING.remove(key);
            RISEN.add(key);
            return 0f;
        }
        return drop(k);
    }

    /** How far down (blocks) a section is {@code k} of the way up: all the way at first, slowing into place. */
    public static float drop(float k) {
        float left = 1f - Math.max(0f, Math.min(1f, k));
        return -DEPTH * left * left;
    }

    /** {@link #offset} for a section whose place the game gives relative to the camera (as floats). */
    public static float offsetRelative(float x, float y, float z) {
        return offset((int) Math.round(x + FrameInfo.camX), (int) Math.round(y + FrameInfo.camY), (int) Math.round(z + FrameInfo.camZ));
    }

    /** {@link #offset} in whole blocks (where the game takes whole numbers). */
    public static int offsetBlocks(int x, int y, int z) {
        return Math.round(offset(x, y, z));
    }

    /** A chunk column unloaded: its sections rise again when it comes back. */
    public static void unload(int chunkX, int chunkZ) {
        if (RISEN.isEmpty() && RISING.isEmpty()) {
            return;
        }
        for (int sy = -8; sy < 24; sy++) {
            long key = key(chunkX, sy, chunkZ);
            RISEN.remove(key);
            RISING.remove(key);
        }
    }

    /** Leaving the world: everything rises again next time. */
    public static void clear() {
        RISEN.clear();
        RISING.clear();
    }

    private static long key(int sx, int sy, int sz) {
        return ((long) sx & 0x3FFFFFL) << 42 | ((long) sy & 0xFFFFFL) << 22 | ((long) sz & 0x3FFFFFL);
    }
}
