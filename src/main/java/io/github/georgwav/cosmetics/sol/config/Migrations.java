package io.github.georgwav.cosmetics.sol.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;

/**
 * Brings saved profiles and module files up to the current schema. A file without {@code schema} is version 1:
 * the client branch's profile (or {@code modules.json}), a {@code modules} block only. Module ids that moved are
 * renamed and their settings kept; the time and weather modules become Atmosphere's time and weather locks.
 * Pure JSON in, JSON out: no game classes, unit tested.
 */
public final class Migrations {
    public static final int PROFILE_SCHEMA = 2;

    /** Old module id → new id, for modules that were renamed or merged. */
    public static final Map<String, String> MODULE_IDS = Map.of(
            "toggle_sprint", "movement",
            "coords", "coordinates",
            "armor", "armour_items",
            "effects", "potion_effects");

    private Migrations() {
    }

    /** Whether a profile needs migrating. */
    public static boolean needsMigration(JsonObject profile) {
        return !profile.has("schema") || !profile.get("schema").isJsonPrimitive()
                || profile.get("schema").getAsInt() < PROFILE_SCHEMA;
    }

    /** The modules pinned in a new profile. */
    public static final java.util.List<String> DEFAULT_PINS = java.util.List.of("crosshair", "glint", "atmosphere", "camera");

    /**
     * A saved pin as it reads now: the old default pin on Zoom (Zoomify zooms now) becomes Camera; pins on modules
     * the client no longer has are dropped (null).
     */
    public static String pin(String id) {
        return switch (id) {
            case "zoom" -> "camera";
            case "hypixel", "dropped_items", "sounds" -> null;
            default -> id;
        };
    }

    /** A copy of the profile in the current schema. Unknown fields are kept. */
    public static JsonObject migrateProfile(JsonObject old) {
        JsonObject out = old.deepCopy();
        out.addProperty("schema", PROFILE_SCHEMA);
        JsonObject modules = out.has("modules") && out.get("modules").isJsonObject() ? out.getAsJsonObject("modules")
                : new JsonObject();
        out.add("modules", migrateModules(modules));
        if (!out.has("theme") || !out.get("theme").isJsonObject()) {
            JsonObject theme = new JsonObject();
            theme.addProperty("id", "deep_field");
            theme.add("accent", com.google.gson.JsonNull.INSTANCE);
            theme.add("custom", new JsonObject());
            out.add("theme", theme);
        }
        if (!out.has("pinned") || !out.get("pinned").isJsonArray()) {
            com.google.gson.JsonArray pinned = new com.google.gson.JsonArray();
            for (String p : DEFAULT_PINS) {
                pinned.add(p);
            }
            out.add("pinned", pinned);
        }
        return out;
    }

    /** The modules block with moved ids renamed and time/weather folded into Atmosphere. */
    public static JsonObject migrateModules(JsonObject modules) {
        JsonObject out = new JsonObject();
        // Modules under their current ids first, then old ids only where the file has no current one: a file with
        // both keeps the current settings whatever order they're in.
        for (int pass = 0; pass < 2; pass++) {
            for (Map.Entry<String, JsonElement> e : modules.entrySet()) {
                boolean moved = MODULE_IDS.containsKey(e.getKey()) && !MODULE_IDS.get(e.getKey()).equals(e.getKey());
                if (moved != (pass == 1)) {
                    continue;
                }
                String id = MODULE_IDS.getOrDefault(e.getKey(), e.getKey());
                if (id.equals("time") || id.equals("weather")) {
                    continue;
                }
                if (!out.has(id)) {
                    out.add(id, e.getValue().deepCopy());
                }
            }
        }
        JsonObject atmosphere = out.has("atmosphere") && out.get("atmosphere").isJsonObject()
                ? out.getAsJsonObject("atmosphere") : null;
        JsonObject time = object(modules, "time");
        JsonObject weather = object(modules, "weather");
        if (time != null || weather != null) {
            if (atmosphere == null) {
                atmosphere = new JsonObject();
                atmosphere.addProperty("enabled", false);
                out.add("atmosphere", atmosphere);
            }
            JsonObject settings = atmosphere.has("settings") && atmosphere.get("settings").isJsonObject()
                    ? atmosphere.getAsJsonObject("settings") : new JsonObject();
            atmosphere.add("settings", settings);
            // A module that was on sets Atmosphere's lock; one that was off only fills what Atmosphere doesn't have
            // yet (a live file can have both, and Atmosphere's own lock stays).
            fold(settings, time, "lock_time", "time");
            fold(settings, weather, "lock_weather", "weather");
        }
        stats(modules, out);
        killEffects(out);
        return out;
    }

    /**
     * PvP Effects' kill effect (lightning or a burst) moved to Kill Effects: a file that had one on and no Kill Effects
     * yet gets Kill Effects on with that style, for the same victims.
     */
    static void killEffects(JsonObject out) {
        JsonObject pvp = object(out, "pvp_effects");
        if (pvp == null || out.has("kill_effects") || !pvp.has("settings") || !pvp.get("settings").isJsonObject()) {
            return;
        }
        JsonObject old = pvp.getAsJsonObject("settings");
        String effect = old.has("kill_effect") && old.get("kill_effect").isJsonPrimitive() ? old.get("kill_effect").getAsString() : "";
        if (!effect.equals("lightning") && !effect.equals("burst")) {
            return;
        }
        JsonObject kill = new JsonObject();
        kill.addProperty("enabled", pvp.has("enabled") && pvp.get("enabled").getAsBoolean());
        JsonObject settings = new JsonObject();
        settings.addProperty("kill_style", effect);
        boolean playersOnly = old.has("players_only") && old.get("players_only").getAsBoolean();
        settings.addProperty("kill_who", playersOnly ? "players" : "everything");
        if (old.has("kill_volume") && old.get("kill_volume").isJsonPrimitive()) {
            settings.add("kill_volume", old.get("kill_volume"));
        }
        kill.add("settings", settings);
        out.add("kill_effects", kill);
    }

    private static void fold(JsonObject settings, JsonObject old, String lock, String value) {
        if (old == null) {
            return;
        }
        boolean on = enabled(old);
        JsonObject os = object(old, "settings");
        if (on || !settings.has(lock)) {
            settings.addProperty(lock, on || settings.has(lock) && settings.get(lock).getAsBoolean());
        }
        if (os != null && os.has(value) && (on || !settings.has(value))) {
            settings.add(value, os.get(value).deepCopy());
        }
    }

    /** The FPS, Ping and Clock modules become lines of Stats: each line on where its module was. */
    static final String[] STATS = {"fps", "ping", "clock"};

    private static void stats(JsonObject modules, JsonObject out) {
        JsonObject first = null, firstOn = null;
        boolean any = false;
        for (String id : STATS) {
            out.remove(id);
            JsonObject m = object(modules, id);
            if (m == null) {
                continue;
            }
            any = true;
            if (first == null) {
                first = m;
            }
            if (firstOn == null && enabled(m)) {
                firstOn = m;
            }
        }
        if (!any || out.has("stats")) {
            return;
        }
        JsonObject base = firstOn != null ? firstOn : first;
        JsonObject stats = new JsonObject();
        stats.addProperty("enabled", firstOn != null);
        JsonObject settings = new JsonObject();
        JsonObject bs = object(base, "settings");
        if (bs != null) {
            for (String shared : new String[]{"scale", "backing", "shadow", "font", "color"}) {
                if (bs.has(shared)) {
                    settings.add(shared, bs.get(shared).deepCopy());
                }
            }
        }
        for (String id : STATS) {
            JsonObject m = object(modules, id);
            // Stats shows FPS by default; with the old modules it shows what was on (FPS when none was).
            settings.addProperty(id, m != null && enabled(m) || id.equals("fps") && firstOn == null);
        }
        JsonObject clock = object(modules, "clock");
        JsonObject cs = clock == null ? null : object(clock, "settings");
        if (cs != null && cs.has("format")) {
            settings.add("format", cs.get("format").deepCopy());
        }
        stats.add("settings", settings);
        JsonObject state = object(base, "state");
        if (state != null) {
            stats.add("state", state.deepCopy());
        }
        out.add("stats", stats);
    }

    private static JsonObject object(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static boolean enabled(JsonObject module) {
        return module.has("enabled") && module.get("enabled").isJsonPrimitive() && module.get("enabled").getAsBoolean();
    }
}
