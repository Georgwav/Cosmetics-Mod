package io.github.georgwav.cosmetics.emote;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every emote in the active resource packs: {@code assets/<namespace>/emotes/*.json}, Blockbench (Bedrock)
 * animation files and Emotecraft emotes alike. A broken file is logged and skipped.
 */
public final class Emotes {
    private static final String DIRECTORY = "emotes";
    private static final String SUFFIX = ".json";
    private static volatile Map<String, Emote> emotes = Map.of();
    /** The bundled emotes in their place on the wheel: the most used first. */
    private static final List<String> BUNDLED_ORDER = java.util.stream.Stream.of("wave", "clap", "cheer", "dance",
                    "brazilian_dance", "dab", "facepalm", "salute", "shrug", "bow", "nod", "head_shake", "spin", "t_pose", "sit", "cloud_rider",
                    "cloud_sit")
            .map(name -> CosmeticsMod.MOD_ID + ":" + name).toList();

    private Emotes() {
    }

    /** Reads every emote, in wheel order; touches nothing the game is using (runs off its thread on a reload). */
    public static Map<String, Emote> prepare(ResourceManager manager) {
        Map<String, Emote> loaded = new LinkedHashMap<>();
        // `var`: the key type is ResourceLocation before 1.21.11 and Identifier after.
        for (var entry : manager.listResources(DIRECTORY, location -> location.getPath().endsWith(SUFFIX)).entrySet()) {
            String path = entry.getKey().getPath();
            String id = entry.getKey().getNamespace() + ":"
                    + path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length());
            String file = "assets/" + entry.getKey().getNamespace() + "/" + path;
            try (Reader reader = entry.getValue().openAsReader()) {
                for (Emote emote : EmoteParser.parse(id, reader,
                        warning -> CosmeticsMod.LOGGER.warn("Emote '{}' ({}): {}", id, file, warning))) {
                    loaded.put(emote.id(), emote);
                }
            } catch (Exception e) {
                CosmeticsMod.LOGGER.error("Skipping emote '{}' ({}): {}", id, file, e.toString());
            }
        }
        // The mod's own emotes first, in their wheel order, then the rest by id.
        List<Emote> sorted = new ArrayList<>(loaded.values());
        sorted.sort(java.util.Comparator.comparingInt((Emote e) -> {
            int own = BUNDLED_ORDER.indexOf(e.id());
            return own >= 0 ? own : BUNDLED_ORDER.size();
        }).thenComparing(Emote::id));
        Map<String, Emote> ordered = new LinkedHashMap<>();
        sorted.forEach(e -> ordered.put(e.id(), e));
        return Collections.unmodifiableMap(ordered);
    }

    /** Puts what {@link #prepare} read in place. */
    public static void apply(Map<String, Emote> loaded) {
        int before = emotes == null ? -1 : emotes.size();
        emotes = loaded;
        // Said once; a reload with the same emotes (Sol Styles applying a mix) isn't news.
        if (loaded.size() != before) {
            CosmeticsMod.LOGGER.info("Loaded {} emote(s)", loaded.size());
        } else {
            CosmeticsMod.LOGGER.debug("Loaded {} emote(s) again", loaded.size());
        }
    }

    public static Collection<Emote> all() {
        return emotes.values();
    }

    public static Emote get(String id) {
        return emotes.get(id);
    }

    /** A wheel's slots, {@link CosmeticsConfig#WHEEL_SLOTS} to a page: emote ids, "" for empty. Live; edit freely. */
    public static List<String> slots(boolean elytra) {
        CosmeticsConfig config = CosmeticsConfig.get();
        return elytra ? config.elytraWheelSlots : config.emoteWheelSlots;
    }

    /** How many pages a wheel has. */
    public static int pages(boolean elytra) {
        return Math.max(1, slots(elytra).size() / CosmeticsConfig.WHEEL_SLOTS);
    }

    /** The emote in a slot, or null when the slot is empty (or names an emote that isn't loaded). */
    public static Emote at(boolean elytra, int slot) {
        List<String> slots = slots(elytra);
        return slot >= 0 && slot < slots.size() ? emotes.get(slots.get(slot)) : null;
    }

    /** Puts {@code id} in a slot; null or "" empties it. */
    public static void assign(boolean elytra, int slot, String id) {
        List<String> slots = slots(elytra);
        if (slot >= 0 && slot < slots.size()) {
            slots.set(slot, id == null ? "" : id);
        }
    }

    /** Adds an empty page at the end; false when the wheel has as many as it may. */
    public static boolean addPage(boolean elytra) {
        List<String> slots = slots(elytra);
        if (slots.size() / CosmeticsConfig.WHEEL_SLOTS >= CosmeticsConfig.MAX_WHEEL_PAGES) {
            return false;
        }
        for (int i = 0; i < CosmeticsConfig.WHEEL_SLOTS; i++) {
            slots.add("");
        }
        return true;
    }

    /** Removes a page and its slots; the last page stays (emptied). */
    public static void removePage(boolean elytra, int page) {
        List<String> slots = slots(elytra);
        int from = page * CosmeticsConfig.WHEEL_SLOTS;
        if (from < 0 || from >= slots.size()) {
            return;
        }
        if (slots.size() <= CosmeticsConfig.WHEEL_SLOTS) {
            java.util.Collections.fill(slots, "");
            return;
        }
        slots.subList(from, from + CosmeticsConfig.WHEEL_SLOTS).clear();
    }

    /** Whether {@code id} is in one of a wheel's slots. */
    public static boolean onWheel(String id, boolean elytra) {
        return slots(elytra).contains(id);
    }

    /** Puts {@code id} in a wheel's first empty slot (on a new page when they are full), or takes it off. */
    public static void setOnWheel(String id, boolean elytra, boolean on) {
        List<String> slots = slots(elytra);
        if (!on) {
            slots.replaceAll(each -> each.equals(id) ? "" : each);
            return;
        }
        if (slots.contains(id)) {
            return;
        }
        int empty = slots.indexOf("");
        if (empty < 0 && addPage(elytra)) {
            empty = slots.indexOf("");
        }
        if (empty >= 0) {
            slots.set(empty, id);
        }
    }

    /**
     * An emote's display name: the translation {@code emote.<namespace>.<path>} if there is one, else the name in
     * its file, else its id made readable ("cheer_up" gives "Cheer Up").
     */
    public static String name(Emote emote) {
        String key = "emote." + emote.id().replace(':', '.').replace('/', '.');
        String translated = Component.translatable(key).getString();
        if (!translated.equals(key)) {
            return translated;
        }
        if (emote.name() != null && !emote.name().isBlank()) {
            return emote.name();
        }
        String path = emote.id().substring(emote.id().indexOf(':') + 1);
        path = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : path.split("[_\\-. ]+")) {
            if (!word.isEmpty()) {
                if (name.length() > 0) {
                    name.append(' ');
                }
                name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return name.toString();
    }
}
