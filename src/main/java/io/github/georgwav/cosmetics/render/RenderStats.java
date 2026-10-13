package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.CosmeticsMod;

/**
 * Counters read by the smoke test and the benchmark. Timing is only collected when the benchmark is
 * running ({@link #TIMING} is a constant, so the JIT removes the timing code otherwise).
 *
 * <p>Render thread only.
 */
public final class RenderStats {
    public static final boolean TIMING = Boolean.getBoolean(CosmeticsMod.MOD_ID + ".benchmark");
    /** Benchmark stress factor: draw every cosmetic this many times, to simulate a crowded server. */
    public static final int COPIES = Math.max(1, Integer.getInteger(CosmeticsMod.MOD_ID + ".benchmark.copies", 1));

    /** Main-pass layer calls for the local player; with one player in view this is the frame count. */
    public static long localPlayerFrames;
    /** Meshes handed to a vertex consumer (one per cosmetic per render mode). */
    public static long meshesDrawn;
    /** Meshes drawn while Iris was rendering its shadow map. */
    public static long shadowPassMeshes;
    /** Player layers skipped because the slot was claimed by something else (vanilla cape, Figura, ...). */
    public static long deferredToOtherMods;
    /** Player layers skipped in the shadow pass because the user turned shadows off. */
    public static long skippedShadowPass;
    /** Held items drawn as a cosmetic (a shield cosmetic in place of the shield). */
    public static long heldDrawn;
    /** Nanoseconds spent in our layer and our vertex emission (benchmark only). */
    public static long nanos;

    private RenderStats() {
    }

    public static void reset() {
        localPlayerFrames = 0;
        meshesDrawn = 0;
        shadowPassMeshes = 0;
        deferredToOtherMods = 0;
        skippedShadowPass = 0;
        heldDrawn = 0;
        nanos = 0;
    }
}
