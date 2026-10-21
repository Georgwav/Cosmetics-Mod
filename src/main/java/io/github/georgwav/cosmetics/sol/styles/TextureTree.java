package io.github.georgwav.cosmetics.sol.styles;

import java.util.List;
import java.util.Locale;

/**
 * Where every texture of the game sits in Styles' tree ("More"): by the part table's group first, then by its folder
 * and name, so every texture has a place even when no part names it. Branches are "trunk/branch" ids; the menu
 * names them through the language file (sol.styles.tree.*). Plain Java, so it is unit-tested.
 */
public final class TextureTree {
    /** The trunks, in the menu's order. */
    public static final List<String> TRUNKS = List.of("combat", "tools", "armour", "blocks", "items", "mobs", "gui", "particles", "sky", "fonts", "other");

    private TextureTree() {
    }

    /** The branch a path sits on ("blocks/stone", "gui/hotbar", "combat"...). */
    public static String branch(String path, PartTable table) {
        for (PartTable.Part p : table.partsOf(path)) {
            if (!p.group().equals("world") && !p.group().equals("screen")) {
                return p.group();
            }
        }
        return byFolder(path);
    }

    /** The branch from the folder and the name alone. */
    static String byFolder(String path) {
        String p = path.toLowerCase(Locale.ROOT);
        int slash = p.lastIndexOf('/');
        String name = slash >= 0 ? p.substring(slash + 1) : p;
        if (p.startsWith("textures/block/") || p.startsWith("models/block/") || p.startsWith("blockstates/")) {
            return "blocks/" + blockKind(name);
        }
        if (p.startsWith("textures/item/") || p.startsWith("models/item/") || p.startsWith("items/")) {
            return "items";
        }
        if (p.startsWith("textures/entity/equipment/") || p.startsWith("textures/models/armor/") || p.startsWith("equipment/")) {
            return "armour";
        }
        if (p.startsWith("textures/entity/")) {
            return "mobs";
        }
        if (p.startsWith("textures/gui/")) {
            return "gui/" + guiKind(p, name);
        }
        if (p.startsWith("textures/particle/") || p.startsWith("particles/")) {
            return "particles";
        }
        if (p.startsWith("textures/environment/") || p.startsWith("optifine/sky/") || p.startsWith("fabricskyboxes:")) {
            return "sky/" + skyKind(p, name);
        }
        if (p.startsWith("font/") || p.startsWith("textures/font/")) {
            return "fonts";
        }
        return "other";
    }

    private static String blockKind(String name) {
        if (has(name, "glass")) {
            return "glass";
        }
        if (has(name, "water", "lava")) {
            return "liquids";
        }
        if (has(name, "redstone", "repeater", "comparator", "piston", "observer", "hopper", "dispenser", "dropper", "lever",
                "button", "rail", "tnt", "target", "daylight_detector", "sculk_sensor")) {
            return "redstone";
        }
        if (has(name, "stone", "ore", "deepslate", "granite", "diorite", "andesite", "cobble", "brick", "tuff", "calcite",
                "basalt", "blackstone", "netherrack", "obsidian", "bedrock")) {
            return "stone";
        }
        if (has(name, "plank", "log", "wood", "stripped", "bamboo", "crimson_stem", "warped_stem")) {
            return "wood";
        }
        if (has(name, "leaves", "grass", "flower", "sapling", "vine", "fern", "mushroom", "crop", "wheat", "kelp", "seagrass",
                "rose", "tulip", "dandelion", "poppy", "orchid", "allium", "bluet", "daisy", "lily", "moss", "azalea", "carrots",
                "potatoes", "beetroots", "melon", "pumpkin", "cactus", "sugar_cane", "bush", "dripleaf", "roots")) {
            return "plants";
        }
        return "other";
    }

    private static String guiKind(String p, String name) {
        if (p.startsWith("textures/gui/container/") || p.startsWith("textures/gui/sprites/container/")) {
            return "containers";
        }
        if (has(name, "hotbar") || p.endsWith("widgets.png")) {
            return "hotbar";
        }
        if (p.startsWith("textures/gui/sprites/hud/")) {
            return "icons";
        }
        if (p.startsWith("textures/gui/sprites/widget/")) {
            return "buttons";
        }
        if (p.startsWith("textures/gui/title/")) {
            return "title";
        }
        return "other";
    }

    private static String skyKind(String p, String name) {
        if (has(name, "sun")) {
            return "sun";
        }
        if (has(name, "moon") || p.contains("/moon/")) {
            return "moon";
        }
        if (has(name, "cloud")) {
            return "clouds";
        }
        if (has(name, "rain")) {
            return "rain";
        }
        if (has(name, "snow")) {
            return "snow";
        }
        return "sky";
    }

    private static boolean has(String name, String... words) {
        for (String w : words) {
            if (name.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
