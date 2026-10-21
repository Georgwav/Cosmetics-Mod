package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.sol.util.AtomicFiles;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The menu's memory across profiles (client/styles-prefs.json): the tiles used lately (for "Recently used") and the
 * named Styles presets, each a whole setup you can put on any profile. The current setup itself lives in the profile.
 * Render thread; written on Sol's IO thread.
 */
public final class StylesPrefs {
    /** How many recently used tiles are remembered. */
    static final int RECENT = 64;

    private static final List<String> recent = new ArrayList<>();
    private static final Map<String, StyleSetup> presets = new LinkedHashMap<>();
    private static boolean loaded;

    private StylesPrefs() {
    }

    private static Path file() {
        return MainFolder.clientFolder().resolve("styles-prefs.json");
    }

    private static void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = file();
        if (!Files.isRegularFile(f)) {
            return;
        }
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
            if (o.get("recent") instanceof JsonArray a) {
                for (JsonElement e : a) {
                    if (recent.size() < RECENT) {
                        recent.add(e.getAsString());
                    }
                }
            }
            if (o.get("presets") instanceof JsonObject p) {
                for (Map.Entry<String, JsonElement> e : p.entrySet()) {
                    presets.put(e.getKey(), StyleSetup.fromJson(e.getValue()));
                }
            }
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Styles: couldn't read {}: {}", f, e.toString());
        }
    }

    /** A tile's place among the recently used (0 the latest), or MAX_VALUE. */
    public static int recent(String tileKey) {
        load();
        int i = recent.indexOf(tileKey);
        return i < 0 ? Integer.MAX_VALUE : i;
    }

    /** A tile was picked. */
    public static void used(String tileKey) {
        load();
        recent.remove(tileKey);
        recent.add(0, tileKey);
        while (recent.size() > RECENT) {
            recent.remove(recent.size() - 1);
        }
        save();
    }

    public static Map<String, StyleSetup> presets() {
        load();
        return java.util.Collections.unmodifiableMap(presets);
    }

    public static void savePreset(String name, StyleSetup s) {
        load();
        presets.put(name, s);
        save();
    }

    public static void removePreset(String name) {
        load();
        presets.remove(name);
        save();
    }

    private static void save() {
        JsonObject o = new JsonObject();
        JsonArray a = new JsonArray();
        recent.forEach(a::add);
        o.add("recent", a);
        JsonObject p = new JsonObject();
        presets.forEach((k, v) -> p.add(k, v.toJson()));
        o.add("presets", p);
        String text = o.toString();
        Path f = file();
        SolExecutors.IO.execute(() -> {
            try {
                Files.createDirectories(f.getParent());
                AtomicFiles.writeString(f, text);
            } catch (IOException e) {
                CosmeticsMod.LOGGER.warn("[Sol] Styles: couldn't write {}: {}", f, e.toString());
            }
        });
    }
}
