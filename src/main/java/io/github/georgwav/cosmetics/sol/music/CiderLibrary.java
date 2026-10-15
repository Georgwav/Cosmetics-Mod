package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Cider's library for the Music page (pure parsing; unit tested): what's up next in the queue, recently played,
 * the player's playlists, and a catalog search, as small items to show and play. The calls run on IO threads.
 */
public final class CiderLibrary {
    /**
     * Something to play: its kind (songs, albums, playlists, library-playlists, stations), id, title, a second line,
     * the cover's address (sized), and Apple Music's own path for it (Cider plays by that path).
     */
    public record Item(String kind, String id, String title, String subtitle, String artworkUrl, String href) {
    }

    public static final int THUMB = 96;

    private CiderLibrary() {
    }

    /** {@code /playback/queue}: the songs after the one playing ({@code currentKey}'s id skipped). */
    static List<Item> queue(String json, String currentId, int max) {
        List<Item> out = new ArrayList<>();
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonArray items = root.isJsonArray() ? root.getAsJsonArray()
                    : root.isJsonObject() && root.getAsJsonObject().has("data") && root.getAsJsonObject().get("data").isJsonArray()
                    ? root.getAsJsonObject().getAsJsonArray("data") : new JsonArray();
            boolean afterCurrent = currentId == null;
            List<Item> all = new ArrayList<>();
            for (JsonElement e : items) {
                Item i = item(e);
                if (i == null) {
                    continue;
                }
                if (!afterCurrent && i.id().equals(currentId)) {
                    afterCurrent = true;
                    continue;
                }
                all.add(i);
                if (afterCurrent) {
                    out.add(i);
                }
            }
            // Cider's queue may start at the song playing or not list it at all: then it's all up next.
            List<Item> pick = afterCurrent && currentId != null ? out : all;
            return pick.size() > max ? List.copyOf(pick.subList(0, max)) : List.copyOf(pick);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** An Apple Music API reply ({@code data: [...]}) as items. */
    static List<Item> data(String json, int max) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                root = root.getAsJsonObject("data");
            }
            List<Item> out = new ArrayList<>();
            if (root.has("data") && root.get("data").isJsonArray()) {
                for (JsonElement e : root.getAsJsonArray("data")) {
                    Item i = item(e);
                    if (i != null && out.size() < max) {
                        out.add(i);
                    }
                }
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** A catalog search's reply ({@code results: {songs: {data}, albums: {data}, playlists: {data}}}): songs first. */
    static List<Item> search(String json, int max) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("data") && root.get("data").isJsonObject()) {
                root = root.getAsJsonObject("data");
            }
            JsonObject results = root.has("results") && root.get("results").isJsonObject() ? root.getAsJsonObject("results") : new JsonObject();
            List<Item> out = new ArrayList<>();
            for (String kind : new String[]{"songs", "albums", "playlists"}) {
                if (!results.has(kind) || !results.get(kind).isJsonObject()) {
                    continue;
                }
                JsonObject group = results.getAsJsonObject(kind);
                if (!group.has("data") || !group.get("data").isJsonArray()) {
                    continue;
                }
                for (JsonElement e : group.getAsJsonArray("data")) {
                    Item i = item(e);
                    if (i != null && out.size() < max) {
                        out.add(i);
                    }
                }
            }
            return List.copyOf(out);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    static Item item(JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        JsonObject a = o.has("attributes") && o.get("attributes").isJsonObject() ? o.getAsJsonObject("attributes") : o;
        String name = CiderJson.string(a, "name");
        if (name == null || name.isBlank()) {
            return null;
        }
        String id = CiderJson.string(o, "id");
        String kind = CiderJson.string(o, "type");
        if (a.has("playParams") && a.get("playParams").isJsonObject()) {
            JsonObject pp = a.getAsJsonObject("playParams");
            if (id == null) {
                id = CiderJson.string(pp, "id");
            }
            if (kind == null) {
                String k = CiderJson.string(pp, "kind");
                kind = k == null ? null : k.endsWith("s") ? k : k + "s";
            }
        }
        if (id == null) {
            return null;
        }
        String sub = CiderJson.string(a, "artistName");
        if (sub == null) {
            sub = CiderJson.string(a, "curatorName");
        }
        String art = null;
        if (a.has("artwork") && a.get("artwork").isJsonObject()) {
            String url = CiderJson.string(a.getAsJsonObject("artwork"), "url");
            if (url != null) {
                art = url.replace("{w}", Integer.toString(THUMB)).replace("{h}", Integer.toString(THUMB));
            }
        }
        return new Item(kind == null ? "songs" : kind, id, name, sub == null ? "" : sub, art, CiderJson.string(o, "href"));
    }
}
