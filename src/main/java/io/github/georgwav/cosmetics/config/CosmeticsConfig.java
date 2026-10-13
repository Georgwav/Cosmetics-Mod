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
 * User settings, stored as {@code config/cosmeticsmod.json}. Read once on startup; the render path
 * only reads plain fields, so it never touches the file system or allocates.
 */
public final class CosmeticsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CosmeticsConfig instance = new CosmeticsConfig();

    /** Master switch. */
    public boolean enabled = true;

    /**
     * Cosmetics cast shadows with Iris shaderpacks by default. Turn this off to skip cosmetics in the
     * Iris shadow pass for extra FPS.
     */
    public boolean renderInShadowPass = true;

    /**
     * What happens when another mod already uses a slot for a player. Everything defaults to
     * {@link SlotPriority#DEFER}: we never override another mod's cape or model unless you say so.
     */
    public Map<CosmeticSlot, SlotPriority> slotPriority = defaultPriorities();

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
        loaded.fillDefaults();
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

    private void fillDefaults() {
        if (slotPriority == null) {
            slotPriority = defaultPriorities();
        }
        // Unknown slot or priority names in a hand-edited file deserialize to null; drop them.
        slotPriority.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            slotPriority.putIfAbsent(slot, SlotPriority.DEFER);
        }
        if (equipped == null) {
            equipped = new LinkedHashMap<>();
        }
    }

    private static Map<CosmeticSlot, SlotPriority> defaultPriorities() {
        Map<CosmeticSlot, SlotPriority> map = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            map.put(slot, SlotPriority.DEFER);
        }
        return map;
    }

    private static Map<String, List<String>> defaultEquipped() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("local", new ArrayList<>());
        return map;
    }
}
