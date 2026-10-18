package io.github.georgwav.cosmetics.sol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Profiles;
import io.github.georgwav.cosmetics.sol.theme.Customisation;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.util.AtomicFiles;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sol profiles (schema 2): everything a setup is, under a name. The modules (as {@code modules.json} has them), the
 * pinned modules, the theme with its accent and the player's edits per theme, the cosmetics worn (and the outfit
 * they are), skin and cape (ProfileWear). Old
 * profiles (schema 1, modules only) are migrated when read and written back. Switching profiles puts all of it
 * back; any change is saved into the active profile (a moment after the last change, and when the game closes).
 */
public final class SolProfiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final long SAVE_DELAY_MS = 300L;

    /** The active profile's extras (what isn't in the modules), mirrored here while it's active. */
    private static final Set<String> pinned = new LinkedHashSet<>(Migrations.DEFAULT_PINS);
    private static final Map<String, Customisation> customs = new LinkedHashMap<>();
    private static String outfit;
    /** The profile's cosmetics as it was read (null: a profile from before cosmetics were recorded). */
    private static List<String> equipped;
    /** What the cosmetics worn looked like at the last look, to see a change in the Wardrobe. */
    private static int wornHash;
    private static boolean wornSeen;
    private static String skinKey;
    private static boolean skinSlim;
    private static String cape;
    private static JsonObject unknown = new JsonObject();

    private static volatile long saveAt = -1L;
    private static boolean switching;

    private SolProfiles() {
    }

    public static Path folder() {
        return MainFolder.clientFolder().resolve("profiles");
    }

    /** The profiles: the four starting ones in their order first (as the prototype lists them), then the rest A–Z. */
    public static List<String> names() {
        List<String> all = Profiles.names();
        List<String> out = new java.util.ArrayList<>();
        for (String d : FirstLaunch.ORDER) {
            if (all.contains(d)) {
                out.add(d);
            }
        }
        for (String n : all) {
            if (!out.contains(n)) {
                out.add(n);
            }
        }
        return out;
    }

    public static String active() {
        return SolConfig.get().activeProfile;
    }

    /** The theme the active profile uses (read from its file when Sol starts). */
    public static String activeTheme() {
        JsonObject p = read(active());
        if (p != null) {
            adopt(p);
            JsonObject theme = p.getAsJsonObject("theme");
            if (theme != null && theme.has("id") && theme.get("id").isJsonPrimitive()) {
                return theme.get("id").getAsString();
            }
        }
        return "deep_field";
    }

    /** Reads a profile, migrating it (and saving the migrated file) when it's older; null when there is none. */
    public static JsonObject read(String name) {
        Path path = folder().resolve(name + ".json");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            JsonObject p = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (Migrations.needsMigration(p)) {
                p = Migrations.migrateProfile(p);
                AtomicFiles.writeString(path, GSON.toJson(p));
                CosmeticsMod.LOGGER.info("[Sol] Profile '{}' moved to schema {}", name, Migrations.PROFILE_SCHEMA);
            }
            return p;
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't read the profile {}", path, e);
            return null;
        }
    }

    /** A server's host as profiles keep it: lower case, without the default port. */
    public static String host(String address) {
        if (address == null) {
            return "";
        }
        String h = address.trim().toLowerCase(java.util.Locale.ROOT);
        return h.endsWith(":25565") ? h.substring(0, h.length() - 6) : h;
    }

    /** Switches to a profile: modules, theme, accent, edits, pins. False when it can't be read. */
    public static boolean switchTo(String name) {
        JsonObject p = read(name);
        if (p == null) {
            return false;
        }
        flush();
        switching = true;
        try {
            SolConfig.get().activeProfile = name;
            SolConfig.save();
            adopt(p);
            Modules.apply(p.getAsJsonObject("modules"));
            applyTheme(p);
            ProfileWear.wearCosmetics(outfit, equipped);
            wornSeen = false;
            ProfileWear.wearSkin(skinKey);
            ProfileWear.wearCape(cape);
        } finally {
            switching = false;
        }
        return true;
    }

    /** Takes the profile's extras (pins, edits, outfit, skin, cape) into memory. */
    private static void adopt(JsonObject p) {
        pinned.clear();
        if (p.has("pinned") && p.get("pinned").isJsonArray()) {
            for (JsonElement e : p.getAsJsonArray("pinned")) {
                String id = e.isJsonPrimitive() ? Migrations.pin(e.getAsString()) : null;
                if (id != null) {
                    pinned.add(id);
                }
            }
        }
        customs.clear();
        JsonObject theme = p.has("theme") && p.get("theme").isJsonObject() ? p.getAsJsonObject("theme") : new JsonObject();
        if (theme.has("custom") && theme.get("custom").isJsonObject()) {
            for (Map.Entry<String, JsonElement> e : theme.getAsJsonObject("custom").entrySet()) {
                customs.put(e.getKey(), Customisation.fromJson(e.getValue()));
            }
        }
        JsonObject cos = p.has("cosmetics") && p.get("cosmetics").isJsonObject() ? p.getAsJsonObject("cosmetics") : null;
        outfit = cos != null && cos.has("outfit") && cos.get("outfit").isJsonPrimitive() ? cos.get("outfit").getAsString() : null;
        equipped = null;
        if (cos != null && cos.has("equipped") && cos.get("equipped").isJsonArray()) {
            equipped = new ArrayList<>();
            for (JsonElement e : cos.getAsJsonArray("equipped")) {
                if (e.isJsonPrimitive() && !equipped.contains(e.getAsString())) {
                    equipped.add(e.getAsString());
                }
            }
        }
        JsonObject skin = p.has("skin") && p.get("skin").isJsonObject() ? p.getAsJsonObject("skin") : null;
        skinKey = skin != null && skin.has("key") && skin.get("key").isJsonPrimitive() ? skin.get("key").getAsString() : null;
        skinSlim = skin != null && skin.has("slim") && skin.get("slim").getAsBoolean();
        cape = p.has("cape") && p.get("cape").isJsonPrimitive() ? p.get("cape").getAsString() : null;
        unknown = p.deepCopy();
        // "servers" too: server profiles were dropped, so an old list of them isn't written back.
        for (String known : new String[]{"schema", "modules", "pinned", "theme", "cosmetics", "skin", "cape", "servers"}) {
            unknown.remove(known);
        }
    }

    private static void applyTheme(JsonObject p) {
        JsonObject theme = p.has("theme") && p.get("theme").isJsonObject() ? p.getAsJsonObject("theme") : new JsonObject();
        String id = theme.has("id") && theme.get("id").isJsonPrimitive() ? theme.get("id").getAsString() : "deep_field";
        Integer accent = theme.has("accent") && theme.get("accent").isJsonPrimitive()
                ? Colors.tryParse(theme.get("accent").getAsString()) : null;
        ThemeState.setAccent(accent);
        ThemeState.setCustomisation(customs.getOrDefault(id, Customisation.NONE));
        ThemeState.setTheme(id, true);
    }

    /** The active profile as JSON, from what is live now. */
    public static JsonObject snapshot() {
        JsonObject p = unknown.deepCopy();
        p.addProperty("schema", Migrations.PROFILE_SCHEMA);
        p.add("modules", Modules.toJson());
        JsonArray pins = new JsonArray();
        pinned.forEach(pins::add);
        p.add("pinned", pins);
        JsonObject theme = new JsonObject();
        theme.addProperty("id", ThemeState.themeId());
        Integer accent = ThemeState.accentOverride();
        if (accent != null) {
            theme.addProperty("accent", Colors.hex(accent));
        } else {
            theme.add("accent", JsonNull.INSTANCE);
        }
        customs.put(ThemeState.themeId(), ThemeState.customisation());
        JsonObject custom = new JsonObject();
        customs.forEach((k, v) -> {
            if (!v.isNone()) {
                custom.add(k, v.toJson());
            }
        });
        theme.add("custom", custom);
        p.add("theme", theme);
        JsonObject cos = new JsonObject();
        outfit = ProfileWear.outfitWorn();
        equipped = ProfileWear.equipped();
        if (outfit != null) {
            cos.addProperty("outfit", outfit);
        } else {
            cos.add("outfit", JsonNull.INSTANCE);
        }
        JsonArray wearing = new JsonArray();
        equipped.forEach(wearing::add);
        cos.add("equipped", wearing);
        p.add("cosmetics", cos);
        JsonObject skin = new JsonObject();
        if (skinKey != null) {
            skin.addProperty("key", skinKey);
        } else {
            skin.add("key", JsonNull.INSTANCE);
        }
        skin.addProperty("slim", skinSlim);
        p.add("skin", skin);
        if (cape != null) {
            p.addProperty("cape", cape);
        } else {
            p.add("cape", JsonNull.INSTANCE);
        }
        return p;
    }

    /** Writes a profile file as {@code name}. */
    public static boolean write(String name, JsonObject profile) {
        Path path = folder().resolve(name + ".json");
        try {
            Files.createDirectories(path.getParent());
            AtomicFiles.writeString(path, GSON.toJson(profile));
            io.github.georgwav.cosmetics.client.Profiles.forget();
            return true;
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't save the profile {}", path, e);
            return false;
        }
    }

    /** Something in the active profile changed: it's saved a moment after the last change. */
    public static void changed() {
        if (!switching) {
            saveAt = System.currentTimeMillis() + SAVE_DELAY_MS;
        }
    }

    /** Called every tick: notes a change in the cosmetics worn, and saves when the delay has passed. */
    public static void tick() {
        int worn = io.github.georgwav.cosmetics.config.CosmeticsConfig.get().localEquipped().hashCode();
        if (!wornSeen || worn != wornHash) {
            if (wornSeen) {
                changed();
            }
            wornHash = worn;
            wornSeen = true;
        }
        long at = saveAt;
        if (at > 0L && System.currentTimeMillis() >= at) {
            flush();
        }
    }

    /** Saves the active profile now if it changed. */
    public static void flush() {
        if (saveAt > 0L) {
            saveAt = -1L;
            write(active(), snapshot());
        }
    }

    // ------------------------------------------------------------------ pins and theme edits

    /** What the profile popover shows of a saved profile: its theme, its own accent (or null) and modules on. */
    public record Summary(String theme, String accent, int modulesOn) {
    }

    public static Summary summary(String name) {
        JsonObject p = read(name);
        String accent = null;
        int on = 0;
        if (p != null) {
            try {
                JsonObject theme = p.has("theme") && p.get("theme").isJsonObject() ? p.getAsJsonObject("theme") : null;
                if (theme != null && theme.has("accent") && theme.get("accent").isJsonPrimitive()) {
                    accent = theme.get("accent").getAsString();
                }
                JsonObject modules = p.has("modules") && p.get("modules").isJsonObject() ? p.getAsJsonObject("modules") : null;
                if (modules != null) {
                    for (var e : modules.entrySet()) {
                        if (e.getValue().isJsonObject() && e.getValue().getAsJsonObject().has("enabled")
                                && e.getValue().getAsJsonObject().get("enabled").getAsBoolean()) {
                            on++;
                        }
                    }
                }
            } catch (RuntimeException e) {
                // A hand-edited file with odd values: show what could be read.
            }
        }
        return new Summary(themeOf(name), accent, on);
    }

    /** The theme a saved profile uses (its file's theme id), or the fallback theme. */
    public static String themeOf(String name) {
        JsonObject p = read(name);
        if (p != null && p.has("theme") && p.get("theme").isJsonObject() && p.getAsJsonObject("theme").has("id")) {
            try {
                return p.getAsJsonObject("theme").get("id").getAsString();
            } catch (RuntimeException e) {
                return io.github.georgwav.cosmetics.sol.theme.ThemeRegistry.FALLBACK;
            }
        }
        return io.github.georgwav.cosmetics.sol.theme.ThemeRegistry.FALLBACK;
    }

    public static Set<String> pinned() {
        return pinned;
    }

    public static void setPinned(String moduleId, boolean on) {
        if (on ? pinned.add(moduleId) : pinned.remove(moduleId)) {
            changed();
        }
    }

    public static void setTheme(String id) {
        ThemeState.setCustomisation(customs.getOrDefault(id, Customisation.NONE));
        ThemeState.setTheme(id, true);
        changed();
    }

    public static void setCustomisation(Customisation c) {
        customs.put(ThemeState.themeId(), c);
        ThemeState.setCustomisation(c);
        changed();
    }

    /** Gives every theme the night sky layers of the theme in use (the Night sky page). */
    public static void layersEverywhere() {
        Map<String, Boolean> layers = ThemeState.customisation().layers();
        for (io.github.georgwav.cosmetics.sol.theme.ThemeRegistry.Entry e : io.github.georgwav.cosmetics.sol.theme.ThemeRegistry.all()) {
            customs.put(e.id(), customs.getOrDefault(e.id(), Customisation.NONE).withLayers(layers));
        }
        changed();
    }

    public static void setAccent(Integer accent) {
        ThemeState.setAccent(accent);
        changed();
    }

    public static String skinKey() {
        return skinKey;
    }

    public static void setSkin(String key, boolean slim) {
        skinKey = key;
        skinSlim = slim;
        changed();
    }

    public static String cape() {
        return cape;
    }

    public static void setCape(String id) {
        cape = id;
        changed();
    }

    /** Saves what's live as a new profile and makes it active. */
    /** A new profile's name: "Profile 1", "Profile 2", ...: the first not taken. */
    public static String newName() {
        String base = net.minecraft.network.chat.Component.translatable("sol.menu.new_profile", "").getString().strip();
        return io.github.georgwav.cosmetics.client.Profiles.nextName(base, names());
    }

    public static String saveAs(String name) {
        if (name == null) {
            return null;
        }
        String clean = name.trim().replaceAll("[^\\p{L}\\p{N} _-]", "").replaceAll("\\s+", " ").trim();
        if (clean.isEmpty()) {
            return null;
        }
        if (write(clean, snapshot())) {
            SolConfig.get().activeProfile = clean;
            SolConfig.save();
            return clean;
        }
        return null;
    }
}
