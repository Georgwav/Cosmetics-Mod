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
            Map.entry("hitboxes", "hitbox"),
            Map.entry("held_items", "hand"),
            Map.entry("crosshair", "cross"),
            Map.entry("zoom", "zoom"),
            Map.entry("camera", "eye"),
            Map.entry("movement", "run"),
            Map.entry("atmosphere", "sun"),
            Map.entry("fullbright", "bulb"),
            Map.entry("outline", "cube"),
            Map.entry("glint", "sparkle"),
            Map.entry("screen", "screen"),
            Map.entry("particles", "dots"),
            Map.entry("dropped_items", "item"),
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
            Map.entry("sounds", "speaker"),
            Map.entry("social", "people"),
            Map.entry("servers", "server"),
            Map.entry("hypixel", "star"),
            Map.entry("cosmetics", "cape"));

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
        return MODULES.getOrDefault(id, "cube");
    }
}
