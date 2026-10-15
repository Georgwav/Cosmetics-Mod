package io.github.georgwav.cosmetics.sol.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Reads a crash report (pure; unit tested): its description, the exception line and the stack's classes, and
 * guesses which mod it came from: the first frame (in the exception and its causes) that isn't Java, Minecraft, Mixin
 * or Fabric's own, as the mod whose jar holds that class; or a mixin handler's mod id ({@code handler$...$modid$...}).
 */
public final class CrashReports {
    /** A parsed report: what it says, the exception line, and the classes of its frames in order. */
    public record Report(String time, String description, String exception, List<String> frames, List<String> handlerMods) {
    }

    private static final String[] PLATFORM = {"java.", "javax.", "jdk.", "sun.", "com.sun.", "net.minecraft.", "com.mojang.",
            "net.fabricmc.", "org.spongepowered.", "org.lwjgl.", "io.netty.", "com.google.", "it.unimi.", "org.apache.",
            "org.slf4j.", "org.joml.", "com.llamalad7.mixinextras.", "cpw.", "oshi.", "kotlin.", "scala."};

    private CrashReports() {
    }

    public static Report parse(String text) {
        String time = "", description = "", exception = "";
        List<String> frames = new ArrayList<>(), handlers = new ArrayList<>();
        String[] lines = text.split("\r?\n");
        boolean inStack = false;
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            String t = l.strip();
            if (t.startsWith("Time: ") && time.isEmpty()) {
                time = t.substring(6).strip();
            } else if (t.startsWith("Description: ") && description.isEmpty()) {
                description = t.substring(13).strip();
                // The exception follows after a blank line.
                for (int j = i + 1; j < lines.length && j < i + 4; j++) {
                    String e = lines[j].strip();
                    if (!e.isEmpty()) {
                        exception = e;
                        inStack = true;
                        i = j;
                        break;
                    }
                }
            } else if (inStack) {
                if (t.startsWith("at ")) {
                    String frame = t.substring(3);
                    int paren = frame.indexOf('(');
                    String method = paren < 0 ? frame : frame.substring(0, paren);
                    // "module//class.method" or "class.method"
                    int slash = method.lastIndexOf('/');
                    if (slash >= 0) {
                        method = method.substring(slash + 1);
                    }
                    int dot = method.lastIndexOf('.');
                    if (dot > 0) {
                        frames.add(method.substring(0, dot));
                        String name = method.substring(dot + 1);
                        String mod = handlerMod(name);
                        if (mod != null && !handlers.contains(mod)) {
                            handlers.add(mod);
                        }
                    }
                } else if (t.startsWith("Caused by:") || t.startsWith("...")) {
                    continue;
                } else if (t.startsWith("A detailed walkthrough") || t.startsWith("-- ")) {
                    inStack = false;
                }
            }
        }
        return new Report(time, description, exception, List.copyOf(frames), List.copyOf(handlers));
    }

    /** A mixin handler's mod id ({@code handler$zza000$modid$name}, {@code redirect$...$modid$name}), or null. */
    static String handlerMod(String method) {
        String[] parts = method.split("\\$");
        if (parts.length >= 4 && (parts[0].equals("handler") || parts[0].equals("redirect") || parts[0].equals("modify")
                || parts[0].equals("localvar") || parts[0].equals("wrapOperation") || parts[0].equals("wrapMethod")
                || parts[0].startsWith("mixinextras"))) {
            String id = parts[2];
            return id.matches("[a-z][a-z0-9_-]{1,63}") ? id : null;
        }
        return null;
    }

    public static boolean platform(String className) {
        for (String p : PLATFORM) {
            if (className.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The mod it most likely came from: the first non-platform frame whose class a mod holds ({@code modOfClass}: class
     * name → mod id or null), else the first mixin handler's mod; null when it's all the game's own.
     */
    public static String guess(Report r, Function<String, String> modOfClass) {
        for (String frame : r.frames()) {
            if (platform(frame)) {
                continue;
            }
            String mod = modOfClass.apply(frame);
            if (mod != null && !mod.equals("minecraft") && !mod.equals("java")) {
                return mod;
            }
        }
        for (String m : r.handlerMods()) {
            if (!m.equals("fabric") && !m.startsWith("fabric-")) {
                return m.toLowerCase(Locale.ROOT);
            }
        }
        return null;
    }
}
