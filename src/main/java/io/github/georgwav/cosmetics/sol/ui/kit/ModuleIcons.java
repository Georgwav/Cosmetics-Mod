package io.github.georgwav.cosmetics.sol.ui.kit;

import java.util.Map;

/**
 * Which pixel icon each module, menu tab and kept mod shows (the pixel UI prototype's MODS table, and icons drawn on
 * the same grid for the ones it doesn't show). Pure data, so a test checks every name is in the icon set.
 */
public final class ModuleIcons {
    /** Module id -> icon. */
    public static final Map<String, String> MODULES = Map.ofEntries(
            Map.entry("pvp_tweaks", "sword"),
            Map.entry("pvp_effects", "bolt"),
            Map.entry("crystal_pvp", "crystal"),
            Map.entry("target_hud", "card"),
            Map.entry("hitboxes", "hitbox"),
            Map.entry("held_items", "shield"),
            Map.entry("punchy", "fist"),
            Map.entry("crosshair", "cross"),
            Map.entry("camera", "eye"),
            Map.entry("movement", "run"),
            Map.entry("atmosphere", "sun"),
            Map.entry("fullbright", "bulb"),
            Map.entry("outline", "cube"),
            Map.entry("glint", "spark"),
            Map.entry("screen", "screen"),
            Map.entry("particles", "dots"),
            Map.entry("world_overlays", "chunk"),
            Map.entry("nametags", "tag"),
            Map.entry("sol_ui", "solsq"),
            Map.entry("sol_chat", "chat"),
            Map.entry("stats", "bars"),
            Map.entry("coordinates", "pin"),
            Map.entry("armour_items", "armour"),
            Map.entry("potion_effects", "potion"),
            Map.entry("keystrokes", "keys"),
            Map.entry("music", "note"),
            Map.entry("lyrics", "lines"),
            Map.entry("inventory", "chest"),
            Map.entry("performance", "gauge"),
            Map.entry("screenshots", "cam"),
            Map.entry("social", "people2"),
            Map.entry("servers", "server"),
            Map.entry("cosmetics", "crown"),
            Map.entry("kill_effects", "skull"),
            Map.entry("hit_effects", "burst"),
            Map.entry("combo", "combo"),
            Map.entry("kill_feed", "feed"),
            Map.entry("totems", "totem"),
            Map.entry("target", "aim"),
            Map.entry("jump_fx", "jump"),
            Map.entry("trails", "trail"),
            Map.entry("you", "person"),
            Map.entry("world_fx", "globe"));

    /**
     * The modules that keep their own ids until they move into the ones above: each shows the icon of the module it
     * becomes (toggle sprint is Movement, Coordinates, Armour & Items, Potion Effects), or one of its own where it
     * becomes part of one (time and weather in Atmosphere, FPS, ping and clock as Stats lines).
     */
    public static final Map<String, String> EARLIER = Map.of(
            "toggle_sprint", "run",
            "coords", "pin",
            "armor", "armour",
            "effects", "potion",
            "time", "clock",
            "weather", "cloud",
            "fps", "gauge",
            "ping", "signal",
            "clock", "clock");

    /** Kept mods shown among the modules -> icon. */
    public static final Map<String, String> KEPT = Map.of(
            "xaeros-minimap", "map",
            "simple-voice-chat", "mic",
            "jade", "lookat");

    /** Full-menu tabs and quick-menu sections -> icon. */
    public static final Map<String, String> SECTIONS = Map.ofEntries(
            Map.entry("modules", "grid"),
            Map.entry("hud", "hud"),
            Map.entry("world", "mount"),
            Map.entry("presets", "sun"),
            Map.entry("wardrobe", "shirt"),
            Map.entry("styles", "brush"),
            Map.entry("emotes", "run"),
            Map.entry("music", "note"),
            Map.entry("mods", "box"),
            Map.entry("settings", "gear"));

    private ModuleIcons() {
    }

    /** The icon for a module id (the box for one the table doesn't know, e.g. a module added by an addon). */
    public static String module(String id) {
        String icon = MODULES.get(id);
        return icon != null ? icon : EARLIER.getOrDefault(id, "box");
    }
}
