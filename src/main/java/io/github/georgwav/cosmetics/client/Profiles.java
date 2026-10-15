package io.github.georgwav.cosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Profiles: every module's settings saved under a name (for PvP, for building, ...), in {@code profiles/} of the
 * client's folder, so every instance has them. Loading one puts all of it back.
 */
public final class Profiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Profiles() {
    }

    private static Path folder() {
        return MainFolder.clientFolder().resolve("profiles");
    }

    private static List<String> listed;
    private static long listedAt;
    private static Path listedFolder;

    /**
     * The saved profiles' names, A to Z. Menus ask every frame, so the folder is listed at most once a second (and
     * again at once after a profile is saved or deleted here).
     */
    public static synchronized List<String> names() {
        Path folder = folder();
        long now = System.currentTimeMillis();
        if (listed != null && now - listedAt < 1000L && folder.equals(listedFolder)) {
            return new ArrayList<>(listed);
        }
        List<String> names = new ArrayList<>();
        if (Files.isDirectory(folder)) {
            try (Stream<Path> files = Files.list(folder)) {
                files.map(f -> f.getFileName().toString()).filter(n -> n.endsWith(".json"))
                        .map(n -> n.substring(0, n.length() - 5)).sorted(String.CASE_INSENSITIVE_ORDER).forEach(names::add);
            } catch (IOException e) {
                CosmeticsMod.LOGGER.warn("Couldn't list the profiles in {}", folder, e);
            }
        }
        listed = names;
        listedAt = now;
        listedFolder = folder;
        return new ArrayList<>(names);
    }

    /** A profile was saved or deleted: list the folder again next time. */
    public static synchronized void forget() {
        listed = null;
    }

    /** A file name from what was typed: letters, digits, spaces, dashes and underscores. */
    static String clean(String name) {
        String cleaned = name.trim().replaceAll("[^\\p{L}\\p{N} _-]", "").replaceAll("\\s+", " ").trim();
        return cleaned.length() > 40 ? cleaned.substring(0, 40).trim() : cleaned;
    }

    /** "Profile 1", "Profile 2", ...: the first name not among {@code existing}. */
    public static String nextName(String base, List<String> existing) {
        List<String> taken = existing.stream().map(n -> n.toLowerCase(Locale.ROOT)).toList();
        for (int i = 1; ; i++) {
            String name = base + " " + i;
            if (!taken.contains(name.toLowerCase(Locale.ROOT))) {
                return name;
            }
        }
    }

    /** Saves every module's settings as {@code name}; returns the name it was saved as, or null. */
    public static String save(String name) {
        String file = clean(name);
        if (file.isEmpty()) {
            return null;
        }
        JsonObject root = new JsonObject();
        root.add("modules", Modules.toJson());
        Path path = folder().resolve(file + ".json");
        try {
            Files.createDirectories(path.getParent());
            io.github.georgwav.cosmetics.sol.util.AtomicFiles.writeString(path, GSON.toJson(root));
            forget();
            return file;
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("Couldn't save the profile {}", path, e);
            return null;
        }
    }

    /** Puts a saved profile's settings back; false when it couldn't be read. */
    public static boolean load(String name) {
        Path path = folder().resolve(name + ".json");
        try {
            JsonObject root = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            Modules.apply(root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject());
            return true;
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("Couldn't read the profile {}", path, e);
            return false;
        }
    }

    public static void delete(String name) {
        try {
            Files.deleteIfExists(folder().resolve(name + ".json"));
            forget();
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("Couldn't delete the profile {}", name, e);
        }
    }
}
