package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The loaded knives, by cosmetic id. Made with the cosmetics on each reload, off the game's thread: each knife's model
 * and animations are read and measured once ({@link KnifeRig}), and shared by its finishes. A knife whose files can't
 * be read is reported like a broken cosmetic and left out. Pure Java.
 */
public final class KnifeRegistry {
    /** Opens a file in the resource packs by its location ({@code namespace:path}). */
    public interface Files {
        InputStream open(String location) throws IOException;
    }

    private static volatile Map<String, Knife> knives = Map.of();

    private KnifeRegistry() {
    }

    /** The knives for these cosmetics; those that failed go into {@code failed} as "id: why". */
    public static Map<String, Knife> prepare(Collection<CosmeticDefinition> definitions, Files files, List<String> failed) {
        Map<String, Object> rigs = new ConcurrentHashMap<>();
        List<CosmeticDefinition> melee = new ArrayList<>();
        for (CosmeticDefinition d : definitions) {
            if (d.melee() != null) {
                melee.add(d);
                rigs.putIfAbsent(key(d.melee()), d.melee());
            }
        }
        // Each model and its animations once, on every core.
        new ArrayList<>(rigs.keySet()).parallelStream().forEach(key -> {
            CosmeticDefinition.Melee m = (CosmeticDefinition.Melee) rigs.get(key);
            try {
                KnifeGeo geo = KnifeGeo.parse(json(files, m.model()), m.model());
                rigs.put(key, new KnifeRig(geo, KnifeClip.parseAll(json(files, m.animations()), geo)));
            } catch (Exception e) {
                rigs.put(key, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
        });
        Map<String, Knife> out = new java.util.LinkedHashMap<>();
        for (CosmeticDefinition d : melee) {
            Object rig = rigs.get(key(d.melee()));
            if (rig instanceof KnifeRig r) {
                out.put(d.id(), new Knife(d.id(), d.melee(), d.texture(), r));
            } else {
                failed.add(d.id() + ": " + rig);
            }
        }
        return out;
    }

    /** Puts the knives in place; on the game's thread. */
    public static void apply(Map<String, Knife> loaded) {
        knives = Map.copyOf(loaded);
    }

    /** The knife of a cosmetic, or null. */
    public static Knife get(String cosmeticId) {
        return knives.get(cosmeticId);
    }

    public static Collection<Knife> all() {
        return knives.values();
    }

    private static String key(CosmeticDefinition.Melee m) {
        return m.model() + "\n" + m.animations();
    }

    private static JsonObject json(Files files, String location) throws IOException {
        try (InputStream in = files.open(location); Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
