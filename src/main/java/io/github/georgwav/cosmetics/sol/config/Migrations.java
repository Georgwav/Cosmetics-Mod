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
            for (String p : new String[]{"zoom", "crosshair", "glint", "atmosphere"}) {
                pinned.add(p);
            }
            out.add("pinned", pinned);
        }
        return out;
    }

    /** The modules block with moved ids renamed and time/weather folded into Atmosphere. */
    public static JsonObject migrateModules(JsonObject modules) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : modules.entrySet()) {
            String id = MODULE_IDS.getOrDefault(e.getKey(), e.getKey());
            if (id.equals("time") || id.equals("weather")) {
                continue;
            }
            // Don't overwrite a module the file already has under its new id.
            if (!out.has(id)) {
                out.add(id, e.getValue().deepCopy());
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
            if (time != null) {
                settings.addProperty("lock_time", enabled(time));
                JsonObject ts = object(time, "settings");
                if (ts != null && ts.has("time")) {
                    settings.add("time", ts.get("time").deepCopy());
                }
            }
            if (weather != null) {
                settings.addProperty("lock_weather", enabled(weather));
                JsonObject ws = object(weather, "settings");
                if (ws != null && ws.has("weather")) {
                    settings.add("weather", ws.get("weather").deepCopy());
                }
            }
        }
        return out;
    }

    private static JsonObject object(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonObject() ? o.getAsJsonObject(key) : null;
    }

    private static boolean enabled(JsonObject module) {
        return module.has("enabled") && module.get("enabled").isJsonPrimitive() && module.get("enabled").getAsBoolean();
    }
}
