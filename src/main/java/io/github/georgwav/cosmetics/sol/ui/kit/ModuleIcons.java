package io.github.georgwav.cosmetics.sol.ui.kit;

import java.util.Map;

/**
 * Which Sol line icon each module, menu section and kept mod shows (the Sol menu prototype's MODS and STOPS
 * tables; Sounds, Servers and Cosmetics, which the prototype doesn't show, get icons drawn the same way). Every
 * module has an icon of its own. Pure data, so a test checks every name is in the icon set.
 */
public final class ModuleIcons {
    /** Module id -> icon. */
    public static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("pvp_tweaks", "sword"),
            Map.entry("pvp_effects", "burst"),
            Map.entry("crystal_pvp", "spark"),
            Map.entry("target_hud", "lookat"),
            Map.entry("hitboxes", "hitbox"),
            Map.entry("held_items", "hand"),
            Map.entry("punchy", "flame"),
            Map.entry("crosshair", "cross"),
            Map.entry("camera", "eye"),
            Map.entry("movement", "run"),
            Map.entry("atmosphere", "sun"),
            Map.entry("fullbright", "bulb"),
            Map.entry("outline", "cube"),
            Map.entry("glint", "sparkle"),
            Map.entry("screen", "screen"),
            Map.entry("particles", "dots"),
            Map.entry("world_overlays", "grid"),
            Map.entry("nametags", "tag"),
            Map.entry("sol_ui", "heart"),
            Map.entry("sol_chat", "chat"),
            Map.entry("stats", "bars"),
            Map.entry("coordinates", "compass"),
            Map.entry("armour_items", "shield"),
            Map.entry("potion_effects", "potion"),
            Map.entry("keystrokes", "key"),
            Map.entry("music", "note"),
            Map.entry("inventory", "chest"),
            Map.entry("performance", "bolt"),
            Map.entry("screenshots", "camera"),
            Map.entry("social", "people"),
            Map.entry("servers", "server"),
            Map.entry("cosmetics", "cape"));

    /**
     * The modules that keep their own ids until they move into the ones above: each shows the icon of the module it
     * becomes (toggle sprint is Movement, Coordinates, Armour & Items, Potion Effects), or one of its own where it
     * becomes part of one (time and weather in Atmosphere, FPS, ping and clock as Stats lines).
     */
    public static final Map<String, String> EARLIER = Map.of(
            "toggle_sprint", "run",
            "coords", "compass",
            "armor", "shield",
            "effects", "potion",
            "time", "moon",
            "weather", "cloud",
            "fps", "gauge",
            "ping", "signal",
            "clock", "clock");

    /** Kept mods shown among the modules -> icon. */
    public static final Map<String, String> KEPT = Map.of(
            "xaeros-minimap", "map",
            "simple-voice-chat", "mic",
            "jade", "lookat");

    /** Full-menu stops and quick-menu sections -> icon. */
    public static final Map<String, String> SECTIONS = Map.of(
            "modules", "grid4",
            "hud", "hud",
            "world", "sun",
            "wardrobe", "shirt",
            "music", "note",
            "mods", "puzzle",
            "settings", "gear");

    private ModuleIcons() {
    }

    /** The icon for a module id (the cube for one the table doesn't know, e.g. a module added by an addon). */
    public static String module(String id) {
        String icon = MODULES.get(id);
        return icon != null ? icon : EARLIER.getOrDefault(id, "cube");
    }
}
