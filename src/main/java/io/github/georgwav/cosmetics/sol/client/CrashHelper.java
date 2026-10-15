package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.sol.config.SolConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * After a crash: finds the newest crash report the player hasn't been shown (from the last two days), reads it, and
 * guesses the mod behind it by which mod's jar holds the classes in its stack. Files only; no network.
 */
public final class CrashHelper {
    private static final long RECENT = 2L * 24 * 60 * 60 * 1000;

    /** A crash to show: the report file, what it says, the mod it points at (id, or null for the game itself). */
    public record Crash(Path file, CrashReports.Report report, String modId, String modName, Path modJar) {
    }

    private CrashHelper() {
    }

    public static Path folder() {
        return FabricLoader.getInstance().getGameDir().resolve("crash-reports");
    }

    /** The newest unseen report, read (IO thread), or null. */
    public static Crash unseen() {
        long since = Math.max(SolConfig.get().lastCrashSeen, System.currentTimeMillis() - RECENT);
        Path newest = null;
        long newestTime = since;
        try (Stream<Path> files = Files.list(folder())) {
            for (Path p : (Iterable<Path>) files::iterator) {
                long t = p.toFile().lastModified();
                if (p.getFileName().toString().endsWith(".txt") && t > newestTime) {
                    newest = p;
                    newestTime = t;
                }
            }
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (newest == null) {
            return null;
        }
        try {
            String text = Files.readString(newest, StandardCharsets.UTF_8);
            CrashReports.Report r = CrashReports.parse(text);
            String mod = CrashReports.guess(r, CrashHelper::modOfClass);
            String name = null;
            Path jar = null;
            if (mod != null) {
                ModContainer c = FabricLoader.getInstance().getModContainer(mod).orElse(null);
                if (c != null) {
                    name = c.getMetadata().getName();
                    jar = c.getOrigin().getKind() == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH
                            ? c.getOrigin().getPaths().get(0) : null;
                }
            }
            return new Crash(newest, r, mod, name == null ? mod : name, jar);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Marks this report as shown. */
    public static void seen(Crash c) {
        SolConfig.get().lastCrashSeen = c.file().toFile().lastModified();
        SolConfig.save();
    }

    /** The mod whose jar holds a class, or null. */
    static String modOfClass(String className) {
        String path = className.replace('.', '/') + ".class";
        int nested = path.indexOf('$');
        if (nested > 0) {
            path = path.substring(0, nested) + ".class";
        }
        for (ModContainer c : FabricLoader.getInstance().getAllMods()) {
            String id = c.getMetadata().getId();
            if (id.equals("minecraft") || id.equals("java") || id.equals("fabricloader")) {
                continue;
            }
            try {
                if (c.findPath(path).isPresent()) {
                    // A jar inside another mod's jar belongs to that mod.
                    return c.getContainingMod().map(p -> p.getMetadata().getId()).orElse(id);
                }
            } catch (RuntimeException e) {
                // Skip it.
            }
        }
        return null;
    }

    /** Turns a mod off for the next start (its jar renamed to .jar.disabled); true if it worked. */
    public static boolean disable(Path jar) {
        if (jar == null || !Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
            return false;
        }
        try {
            Files.move(jar, jar.resolveSibling(jar.getFileName() + ".disabled"));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
