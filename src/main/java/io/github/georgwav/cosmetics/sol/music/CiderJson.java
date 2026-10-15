package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Cider's replies, read leniently (pure; unit tested): {@code /now-playing}'s {@code info} (an Apple Music song with
 * Cider's playback fields), {@code /is-playing}, and the Socket.IO playback events.
 */
public final class CiderJson {
    /** The cover size Sol asks for. */
    static final int COVER = 300;

    private CiderJson() {
    }

    /** {@code /now-playing}'s reply (or an event's attributes object) as what's playing; null without a song. */
    public static NowPlaying nowPlaying(String json, boolean playing) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject info = root.has("info") && root.get("info").isJsonObject() ? root.getAsJsonObject("info") : root;
            if (info.has("attributes") && info.get("attributes").isJsonObject()) {
                info = info.getAsJsonObject("attributes");
            }
            return fromInfo(info, playing);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static NowPlaying fromInfo(JsonObject info, boolean playing) {
        String name = string(info, "name");
        if (name == null || name.isBlank()) {
            return null;
        }
        String artist = orEmpty(string(info, "artistName"));
        String album = orEmpty(string(info, "albumName"));
        long duration = (long) number(info, "durationInMillis", 0);
        long position = Math.round(number(info, "currentPlaybackTime", 0) * 1000.0);
        String artwork = null;
        if (info.has("artwork") && info.get("artwork").isJsonObject()) {
            String url = string(info.getAsJsonObject("artwork"), "url");
            if (url != null) {
                artwork = url.replace("{w}", Integer.toString(COVER)).replace("{h}", Integer.toString(COVER));
            }
        }
        // The catalog id: playParams.catalogId for a library song, else its id.
        String catalogId = null, id = null;
        if (info.has("playParams") && info.get("playParams").isJsonObject()) {
            JsonObject pp = info.getAsJsonObject("playParams");
            catalogId = string(pp, "catalogId");
            id = string(pp, "id");
            if (catalogId == null && id != null && id.chars().allMatch(Character::isDigit)) {
                catalogId = id;
            }
        }
        String key = "cider:" + (catalogId != null ? catalogId : id != null ? id : (name + "|" + artist));
        return new NowPlaying("cider", key, name, artist, album, duration, position, System.nanoTime(), playing, artwork, catalogId);
    }

    /** {@code /is-playing}'s reply. */
    public static boolean isPlaying(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.has("is_playing") && root.get("is_playing").getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * A Socket.IO {@code API:Playback} event's payload ({@code {"type": ..., "data": ...}}): the type, and the data as
     * JSON text.
     */
    public static String[] playbackEvent(String payload) {
        try {
            JsonObject o = JsonParser.parseString(payload).getAsJsonObject();
            String type = string(o, "type");
            JsonElement data = o.get("data");
            return new String[]{type == null ? "" : type, data == null ? "null" : data.toString()};
        } catch (RuntimeException e) {
            return new String[]{"", "null"};
        }
    }

    /** A {@code playbackTimeDidChange} event's data: {position ms, playing 1/0}, or null. */
    public static long[] playbackTime(String data) {
        try {
            JsonObject o = JsonParser.parseString(data).getAsJsonObject();
            double t = number(o, "currentPlaybackTime", -1);
            if (t < 0) {
                return null;
            }
            boolean playing = !o.has("isPlaying") || o.get("isPlaying").getAsBoolean();
            return new long[]{Math.round(t * 1000.0), playing ? 1 : 0};
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    static double number(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }
}
