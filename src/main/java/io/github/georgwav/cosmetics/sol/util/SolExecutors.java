package io.github.georgwav.cosmetics.sol.util;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sol's background threads, all daemons: IO (network and files, two threads) and CPU (decoding, k-means, bakes,
 * one thread). The render thread never waits on them; their results are published as snapshots. An uncaught
 * failure is logged once and never reaches the game.
 */
public final class SolExecutors {
    public static final ExecutorService IO = Executors.newFixedThreadPool(2, factory("Sol IO"));
    public static final ExecutorService CPU = Executors.newSingleThreadExecutor(factory("Sol CPU"));

    private SolExecutors() {
    }

    private static ThreadFactory factory(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(() -> {
                try {
                    r.run();
                } catch (RuntimeException | LinkageError e) {
                    LogOnce.warn("executor:" + name + ":" + e.getClass().getName(), "{} task failed: {}", name, e.toString());
                }
            }, name + " " + n.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        };
    }

    /** Runs on the CPU thread; a failure switches {@code feature} off. */
    public static void cpu(Feature feature, Runnable task) {
        CPU.execute(() -> {
            try {
                task.run();
            } catch (RuntimeException | LinkageError e) {
                Guard.fail(feature, e);
            }
        });
    }
}
