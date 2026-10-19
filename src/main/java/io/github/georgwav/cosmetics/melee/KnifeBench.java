package io.github.georgwav.cosmetics.melee;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Random;

/**
 * A knife's own cost per frame, measured steady (test runs): its behaviour and pose, and every vertex of its faces and
 * arms worked out and handed to a consumer that only adds them up, frame after frame at 60 fps through draws, light
 * and heavy attacks and inspects, after a warm-up so the JIT has done its work. What the game then does with the
 * vertices (and its own per-draw bookkeeping) is the game's and is left out. Render thread.
 */
public final class KnifeBench {
    private KnifeBench() {
    }

    /** Milliseconds and bytes allocated per frame for {@code knife}: {ms, bytes}. */
    public static double[] run(Knife knife, int warmUp, int frames) {
        KnifeView v = new KnifeView(knife, new Random(7), new KnifeBehaviour.Sounds() {
            @Override
            public void play(String key, long owner) {
            }

            @Override
            public void stop(long owner) {
            }
        });
        Sum sink = new Sum();
        PoseStack.Pose pose = new PoseStack().last();
        float[] local = {1f / 16f, 0, 0, 0, 0, 1f / 16f, 0, -4f, 0, 0, 1f / 16f, 0};
        float[] t = {0f};
        for (int i = 0; i < warmUp; i++) {
            step(v, sink, pose, local, t, i);
        }
        long bytes = KnifeRenderer.allocated(), start = System.nanoTime();
        for (int i = 0; i < frames; i++) {
            step(v, sink, pose, local, t, warmUp + i);
        }
        long nanos = System.nanoTime() - start;
        bytes = KnifeRenderer.allocated() - bytes;
        if (sink.sum == 42.4242) {
            // Never true; keeps the sums alive so the work can't be skipped.
            System.out.print("");
        }
        return new double[]{nanos / 1.0e6 / frames, (double) bytes / frames};
    }

    private static void step(KnifeView v, Sum sink, PoseStack.Pose pose, float[] local, float[] t, int i) {
        t[0] += 1f / 60f;
        switch (i % 240) {
            case 0 -> v.behaviour.equip(t[0], 0.625f, true, 0);
            case 60 -> v.behaviour.attack(t[0], false, true, 0.625f, 0.3f, true);
            case 100 -> v.behaviour.attack(t[0], true, true, 0.625f, 0.3f, true);
            case 150 -> v.behaviour.inspect(t[0]);
            default -> {
            }
        }
        v.frame(t[0], true);
        KnifeRenderer.emitPass(pose, sink, KnifeRenderer.NORMAL, v.pose, v.knife.solid, 0xF000F0, false, local);
        KnifeRenderer.emitPass(pose, sink, KnifeRenderer.LIT, v.pose, v.knife.solid, 0xF000F0, false, local);
        KnifeRenderer.emitPass(pose, sink, KnifeRenderer.ARMS, v.pose, v.knife.solid, 0xF000F0, false, local);
    }

    /** Adds up what it's given (the methods without @Override are only on some Minecraft versions). */
    private static final class Sum implements VertexConsumer {
        double sum;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            sum += x + y + z;
            return this;
        }

        /** One call a vertex, as the game's own buffer takes the knife's vertices. */
        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx,
                              float ny, float nz) {
            sum += x + y + z + color + u + v + light + nx + ny + nz;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            sum += r;
            return this;
        }

        public VertexConsumer setColor(int argb) {
            sum += argb;
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            sum += u + v;
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            sum += u;
            return this;
        }

        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            sum += x + y + z;
            return this;
        }

        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}
