package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * A Styles setup as text to share: its choices and colours, versioned and compressed ("SOL1-" then URL-safe base64 of
 * deflated JSON). Packs are named by their name and file size, never by where they are, so the code works on another
 * computer; on import the packs that aren't there fall back to vanilla, and the result says which. Plain Java, so it
 * is unit-tested.
 */
public final class ShareCode {
    static final String PREFIX = "SOL1-";
    /** The longest code read (a code is a few hundred characters; anything far longer isn't one). */
    static final int MAX = 64 * 1024;

    private ShareCode() {
    }

    /** What a pack is called and how big it is, to find it on another computer. */
    public record PackRef(String name, long size) {
    }

    /** An imported setup, and the packs it named that aren't here. */
    public record Imported(StyleSetup setup, List<String> missing) {
    }

    /** The code for a setup; {@code refs} says each pack key's name and size. */
    public static String encode(StyleSetup setup, Map<String, PackRef> refs) {
        JsonObject body = new JsonObject();
        body.addProperty("v", 1);
        // Pack keys become small numbers into a table of names and sizes.
        Map<String, Integer> ids = new HashMap<>();
        JsonArray packs = new JsonArray();
        for (String key : setup.packsUsed()) {
            PackRef r = refs.get(key);
            if (r == null) {
                continue;
            }
            ids.put(key, packs.size());
            JsonArray p = new JsonArray();
            p.add(r.name());
            p.add(r.size());
            packs.add(p);
        }
        body.add("packs", packs);
        JsonObject s = setup.withSort("pack").withFullOpen(false).toJson();
        s.remove("favourites");
        s.remove("sort");
        s.remove("fullOpen");
        body.add("setup", rewrite(s, key -> ids.containsKey(key) ? "#" + ids.get(key) : null));
        byte[] json = body.toString().getBytes(StandardCharsets.UTF_8);
        Deflater d = new Deflater(Deflater.BEST_COMPRESSION);
        d.setInput(json);
        d.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        while (!d.finished()) {
            out.write(buf, 0, d.deflate(buf));
        }
        d.end();
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray());
    }

    /**
     * The setup in a code; {@code find} finds a pack here by name and size (its key, or null when it isn't here).
     * Null for text that isn't a code.
     */
    public static Imported decode(String code, java.util.function.Function<PackRef, String> find) {
        if (code == null) {
            return null;
        }
        code = code.strip();
        if (!code.startsWith(PREFIX) || code.length() > MAX) {
            return null;
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(code.substring(PREFIX.length()));
            Inflater inf = new Inflater();
            inf.setInput(raw);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            while (!inf.finished()) {
                int n = inf.inflate(buf);
                if (n == 0 && (inf.needsInput() || inf.needsDictionary())) {
                    break;
                }
                out.write(buf, 0, n);
                if (out.size() > 1 << 20) {
                    inf.end();
                    return null;
                }
            }
            inf.end();
            JsonObject body = JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonObject();
            if (!body.has("v") || body.get("v").getAsInt() != 1) {
                return null;
            }
            List<String> keys = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (JsonElement e : body.getAsJsonArray("packs")) {
                JsonArray p = e.getAsJsonArray();
                PackRef ref = new PackRef(p.get(0).getAsString(), p.get(1).getAsLong());
                String key = find.apply(ref);
                keys.add(key);
                if (key == null) {
                    missing.add(ref.name());
                }
            }
            JsonObject s = rewrite(body.getAsJsonObject("setup"), id -> {
                if (!id.startsWith("#")) {
                    return null;
                }
                int i = Integer.parseInt(id.substring(1));
                return i >= 0 && i < keys.size() ? keys.get(i) : null;
            });
            return new Imported(StyleSetup.fromJson(s), missing);
        } catch (IllegalArgumentException | IllegalStateException | DataFormatException | ClassCastException
                 | UnsupportedOperationException | NullPointerException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    /**
     * The setup's JSON with every pack reference mapped ("pack:<x>" to "pack:<map(x)>"); a reference the map has no
     * answer for falls back to vanilla (and a base to none).
     */
    private static JsonObject rewrite(JsonObject s, java.util.function.Function<String, String> map) {
        JsonObject o = s.deepCopy();
        if (o.has("base")) {
            String b = map.apply(o.get("base").getAsString());
            if (b == null) {
                o.remove("base");
            } else {
                o.addProperty("base", b);
            }
        }
        if (o.has("parts")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("parts").entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                String src = c.has("source") ? c.get("source").getAsString() : StyleSetup.VANILLA;
                if (src.startsWith("pack:")) {
                    String k = map.apply(src.substring(5));
                    c.addProperty("source", k == null ? StyleSetup.VANILLA : "pack:" + k);
                }
            }
        }
        if (o.has("leaves")) {
            JsonObject leaves = o.getAsJsonObject("leaves");
            for (String path : new ArrayList<>(leaves.keySet())) {
                String src = leaves.get(path).getAsString();
                if (src.startsWith("pack:")) {
                    String k = map.apply(src.substring(5));
                    if (k == null) {
                        leaves.remove(path);
                    } else {
                        leaves.addProperty(path, "pack:" + k);
                    }
                }
            }
        }
        return o;
    }
}
