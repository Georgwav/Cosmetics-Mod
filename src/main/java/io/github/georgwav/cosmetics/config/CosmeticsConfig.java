package io.github.georgwav.cosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User settings, stored as {@code config/cosmeticsmod.json} and edited in game on the settings screen.
 * The render path only reads plain fields, so it never touches the file system or allocates.
 */
public final class CosmeticsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CosmeticsConfig instance = new CosmeticsConfig();

    // ---------------------------------------------------------------- general

    /** Master switch. */
    public boolean enabled = true;
    /** Draw the cosmetics you wear yourself (third person, inventory, mirrors...). */
    public boolean showOwnCosmetics = true;
    /** Draw other players' cosmetics. */
    public boolean showOtherPlayersCosmetics = true;

    // ---------------------------------------------------------------- display

    /** Per-slot visibility, e.g. hide every cape while keeping hats. */
    public Map<CosmeticSlot, Boolean> slotVisible = defaultVisibility();
    /** Hide hats and face cosmetics while a helmet or other head item is worn. */
    public boolean hideHeadwearWithHelmet = true;
    /** Hide capes while an elytra is worn, like the vanilla cape. */
    public boolean hideCapeWithElytra = true;
    /** Capes sway with movement like the vanilla cape. Off: they hang still. */
    public boolean capePhysics = true;
    /** How strongly capes react to movement, 0..2 (1 = vanilla). */
    public float capeSwing = 1.0f;
    /** Draw glowing (emissive) parts. */
    public boolean emissiveParts = true;
    /** Draw see-through (translucent) parts. */
    public boolean translucentParts = true;

    // ---------------------------------------------------------------- performance

    /** Players further away than this many blocks don't get cosmetics drawn. 0 = no limit. */
    public int maxRenderDistance = 64;
    /**
     * Cosmetics cast shadows with Iris shaderpacks by default. Turn this off to skip cosmetics in the
     * Iris shadow pass for extra FPS.
     */
    public boolean renderInShadowPass = true;
    /** In the Iris shadow pass, only players within this many blocks cast cosmetic shadows. 0 = no limit. */
    public int shadowDistance = 32;

    // ---------------------------------------------------------------- compatibility

    /**
     * What happens when another mod already uses a slot for a player. Everything defaults to
     * {@link SlotPriority#DEFER}: we never override another mod's cape or model unless you say so.
     */
    public Map<CosmeticSlot, SlotPriority> slotPriority = defaultPriorities();
    /** Players with a Figura avatar don't get our cosmetics in DEFER slots. */
    public boolean deferToFigura = true;

    // ---------------------------------------------------------------- wardrobe

    /**
     * Which cosmetics each player wears. Keys are player UUIDs, or {@code "local"} for whoever is
     * playing on this client. Values are cosmetic ids like {@code "cosmeticsmod:top_hat"}.
     */
    public Map<String, List<String>> equipped = defaultEquipped();

    public static CosmeticsConfig get() {
        return instance;
    }

    public SlotPriority priority(CosmeticSlot slot) {
        SlotPriority priority = slotPriority.get(slot);
        return priority != null ? priority : SlotPriority.DEFER;
    }

    public boolean slotVisible(CosmeticSlot slot) {
        Boolean visible = slotVisible.get(slot);
        return visible == null || visible;
    }

    /** The local player's equipped cosmetic ids (mutable; call {@code EquippedCosmetics.invalidate()} after changes). */
    public List<String> localEquipped() {
        return equipped.computeIfAbsent("local", k -> new ArrayList<>());
    }

    /** Puts every setting except the wardrobe back to its default. */
    public void resetSettings() {
        CosmeticsConfig defaults = new CosmeticsConfig();
        enabled = defaults.enabled;
        showOwnCosmetics = defaults.showOwnCosmetics;
        showOtherPlayersCosmetics = defaults.showOtherPlayersCosmetics;
        slotVisible = defaultVisibility();
        hideHeadwearWithHelmet = defaults.hideHeadwearWithHelmet;
        hideCapeWithElytra = defaults.hideCapeWithElytra;
        capePhysics = defaults.capePhysics;
        capeSwing = defaults.capeSwing;
        emissiveParts = defaults.emissiveParts;
        translucentParts = defaults.translucentParts;
        maxRenderDistance = defaults.maxRenderDistance;
        renderInShadowPass = defaults.renderInShadowPass;
        shadowDistance = defaults.shadowDistance;
        slotPriority = defaultPriorities();
        deferToFigura = defaults.deferToFigura;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(CosmeticsMod.MOD_ID + ".json");
    }

    public static void load() {
        Path path = path();
        CosmeticsConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                loaded = GSON.fromJson(reader, CosmeticsConfig.class);
            } catch (IOException | JsonParseException e) {
                CosmeticsMod.LOGGER.error("Could not read {}, using defaults", path, e);
            }
        }
        if (loaded == null) {
            loaded = new CosmeticsConfig();
        }
        loaded.sanitize();
        instance = loaded;
        save();
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            CosmeticsMod.LOGGER.error("Could not write {}", path, e);
        }
    }

    /** Repairs a hand-edited or older file: fills missing entries, clamps numbers into range. */
    void sanitize() {
        if (slotPriority == null) {
            slotPriority = defaultPriorities();
        }
        // Unknown slot or value names in a hand-edited file deserialize to null; drop them.
        slotPriority.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        if (slotVisible == null) {
            slotVisible = defaultVisibility();
        }
        slotVisible.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            slotPriority.putIfAbsent(slot, SlotPriority.DEFER);
            slotVisible.putIfAbsent(slot, true);
        }
        if (equipped == null) {
            equipped = new LinkedHashMap<>();
        }
        equipped.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        equipped.values().forEach(list -> list.removeIf(id -> id == null));
        if (!Float.isFinite(capeSwing)) {
            capeSwing = 1.0f;
        }
        capeSwing = Math.max(0f, Math.min(2f, capeSwing));
        maxRenderDistance = Math.max(0, Math.min(512, maxRenderDistance));
        shadowDistance = Math.max(0, Math.min(512, shadowDistance));
    }

    private static Map<CosmeticSlot, SlotPriority> defaultPriorities() {
        Map<CosmeticSlot, SlotPriority> map = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            map.put(slot, SlotPriority.DEFER);
        }
        return map;
    }

    private static Map<CosmeticSlot, Boolean> defaultVisibility() {
        Map<CosmeticSlot, Boolean> map = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            map.put(slot, true);
        }
        return map;
    }

    private static Map<String, List<String>> defaultEquipped() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("local", new ArrayList<>());
        return map;
    }
}
