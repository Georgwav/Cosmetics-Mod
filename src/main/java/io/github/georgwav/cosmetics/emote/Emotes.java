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
                    "dab", "facepalm", "salute", "shrug", "bow", "nod", "head_shake", "spin", "t_pose", "sit")
            .map(name -> CosmeticsMod.MOD_ID + ":" + name).toList();

    private Emotes() {
    }

    public static void reload(ResourceManager manager) {
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
        emotes = Collections.unmodifiableMap(ordered);
        CosmeticsMod.LOGGER.info("Loaded {} emote(s)", ordered.size());
    }

    public static Collection<Emote> all() {
        return emotes.values();
    }

    public static Emote get(String id) {
        return emotes.get(id);
    }

    /** The emotes on the wheel, in order: the ones picked in the Wardrobe, or all of them when none are picked. */
    public static List<Emote> wheel() {
        List<String> picked = CosmeticsConfig.get().emoteWheel;
        if (picked.isEmpty()) {
            return List.copyOf(emotes.values());
        }
        List<Emote> wheel = new ArrayList<>();
        for (String id : picked) {
            Emote emote = emotes.get(id);
            if (emote != null) {
                wheel.add(emote);
            }
        }
        return wheel;
    }

    /** Whether {@code id} is on the wheel. */
    public static boolean onWheel(String id) {
        List<String> picked = CosmeticsConfig.get().emoteWheel;
        return picked.isEmpty() ? emotes.containsKey(id) : picked.contains(id);
    }

    /** Puts {@code id} on the wheel or takes it off. */
    public static void setOnWheel(String id, boolean on) {
        List<String> picked = CosmeticsConfig.get().emoteWheel;
        if (picked.isEmpty()) {
            // "All of them" until now: start from the full list.
            picked.addAll(emotes.keySet());
        }
        picked.remove(id);
        if (on) {
            picked.add(id);
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
