package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Music behind the Music module: starts and stops the sources, picks the one to show (the one playing; the one
 * shown stays while it plays), and on each new song finds its lyrics (Apple's through Cider, then LRCLIB's synced
 * or plain lyrics, cached on disk) and its cover (decoded off the render thread by {@link Covers}). Everything it
 * publishes is an immutable snapshot in a volatile field: the render thread reads, never waits.
 */
public final class MusicService {
    /** LRCLIB's address (a dev property points tests at a mock). */
    static final String LRCLIB = System.getProperty("cosmeticsmod.lrclib", "https://lrclib.net");

    private static final Map<String, MusicSource> SOURCES = new LinkedHashMap<>();
    private static final Map<String, NowPlaying> latest = new ConcurrentHashMap<>();
    private static volatile NowPlaying now;
    private static volatile Lyrics lyrics = Lyrics.NONE;
    private static volatile String lyricsFor;
    private static volatile boolean lyricsDone;
    private static volatile String problem;
    private static boolean running;

    static {
        add(new CiderSource());
        add(new SpotifySource());
        add(new MprisSource());
    }

    private MusicService() {
    }

    private static void add(MusicSource source) {
        SOURCES.put(source.id(), source);
    }

    private static java.util.function.Predicate<String> wanted = id -> true;

    /** Starts the sources the player wants ({@code on} by source id). */
    public static synchronized void start(java.util.function.Predicate<String> on) {
        wanted = on;
        if (running) {
            return;
        }
        running = true;
        problem = null;
        for (MusicSource s : SOURCES.values()) {
            if (on.test(s.id())) {
                s.start();
            }
        }
    }

    /** The player switched a source on or off. */
    public static synchronized void sourcesChanged(java.util.function.Predicate<String> on) {
        wanted = on;
        if (!running) {
            return;
        }
        for (MusicSource s : SOURCES.values()) {
            if (on.test(s.id())) {
                s.start();
            } else {
                s.stop();
                latest.remove(s.id());
            }
        }
        now = choose(now);
    }

    public static synchronized void stop() {
        if (!running) {
            return;
        }
        running = false;
        for (MusicSource s : SOURCES.values()) {
            s.stop();
        }
        latest.clear();
        now = null;
        lyrics = Lyrics.NONE;
        lyricsFor = null;
        Covers.clear();
    }

    public static boolean running() {
        return running;
    }

    /** What's playing now (the chosen source's last report), or null. */
    public static NowPlaying now() {
        return now;
    }

    /** The lyrics of the song now ({@link Lyrics#NONE} until they're found, or when there are none). */
    public static Lyrics lyrics() {
        NowPlaying n = now;
        return n != null && n.key().equals(lyricsFor) ? lyrics : Lyrics.NONE;
    }

    /** "Title · Artist" of the song now (for status lines), or null. */
    public static String line() {
        NowPlaying n = now;
        if (n == null) {
            return null;
        }
        return n.artist() == null || n.artist().isBlank() ? n.title() : n.title() + " \u00B7 " + n.artist();
    }

    /** Whether the lyrics of the song now are still being looked for. */
    public static boolean lyricsPending() {
        NowPlaying n = now;
        return n != null && n.key().equals(lyricsFor) && !lyricsDone;
    }

    /** The player changed Cider's app token: try again, and say so again if it's still refused. */
    public static void tokenChanged() {
        problem = null;
        if (SOURCES.get("cider") instanceof CiderSource c) {
            c.tokenChanged();
        }
    }

    /** Whether Cider's live events (Socket.IO) have come in. */
    public static boolean ciderEvents() {
        return SOURCES.get("cider") instanceof CiderSource c && c.heardEvents();
    }

    /**
     * Signs in to Spotify (IO thread; the browser opens): says how it went, and starts listening when it worked.
     */
    public static void signInSpotify() {
        SolExecutors.IO.execute(() -> {
            String problemKey = SpotifyAuth.signIn(SolConfig.get().music.spotifyClientId);
            Notifications.post(net.minecraft.network.chat.Component.translatable(problemKey == null ? "sol.notify.spotify_signed_in"
                    : problemKey).getString());
        });
    }

    /** Whether a source is connected to its player. */
    public static boolean connected(String sourceId) {
        MusicSource s = SOURCES.get(sourceId);
        return running && s != null && s.connected();
    }

    /** The last problem a source had (a lang key), or null. */
    public static String problem() {
        return problem;
    }

    /** A source reports its song ({@code null}: nothing there now). Any thread. */
    static synchronized void report(MusicSource source, NowPlaying song) {
        if (!running || !wanted.test(source.id())) {
            return;
        }
        if (song == null) {
            latest.remove(source.id());
        } else {
            latest.put(source.id(), song);
        }
        NowPlaying before = now;
        NowPlaying chosen = choose(before);
        now = chosen;
        if (chosen != null && !chosen.sameSong(before)) {
            songChanged(chosen);
        }
    }

    /** The one shown keeps its place while it plays; else the first one playing; else the one shown, paused; else any. */
    private static NowPlaying choose(NowPlaying before) {
        NowPlaying same = before == null ? null : latest.get(before.source());
        if (same != null && same.playing()) {
            return same;
        }
        for (MusicSource s : SOURCES.values()) {
            NowPlaying n = latest.get(s.id());
            if (n != null && n.playing()) {
                return n;
            }
        }
        if (same != null) {
            return same;
        }
        for (MusicSource s : SOURCES.values()) {
            NowPlaying n = latest.get(s.id());
            if (n != null) {
                return n;
            }
        }
        return null;
    }

    /** A source can't do its job (a lang key): told once per run. Any thread. */
    static void problem(String langKey) {
        if (langKey.equals(problem)) {
            return;
        }
        problem = langKey;
        Notifications.post(net.minecraft.network.chat.Component.translatable(langKey).getString());
    }

    private static MusicSource source(NowPlaying n) {
        return SOURCES.get(n.source());
    }

    public static void playPause() {
        NowPlaying n = now;
        if (n != null) {
            source(n).playPause();
        }
    }

    public static void next() {
        NowPlaying n = now;
        if (n != null) {
            source(n).next();
        }
    }

    public static void previous() {
        NowPlaying n = now;
        if (n != null) {
            source(n).previous();
        }
    }

    // ---------------------------------------------------------------- per song

    private static void songChanged(NowPlaying song) {
        lyrics = Lyrics.NONE;
        lyricsFor = song.key();
        lyricsDone = false;
        MusicSource source = source(song);
        SolExecutors.IO.execute(() -> findLyrics(song, source));
        Covers.want(song.artworkUrl());
    }

    private static void findLyrics(NowPlaying song, MusicSource source) {
        Lyrics found = LyricsCache.read(song.key());
        if (found == null) {
            found = fetchLyrics(song, source);
        }
        if (song.key().equals(lyricsFor)) {
            lyrics = found == null ? Lyrics.NONE : found;
            lyricsDone = true;
        }
    }

    /** Apple's (syllable, then line) through the source, then LRCLIB synced, then LRCLIB plain. */
    private static Lyrics fetchLyrics(NowPlaying song, MusicSource source) {
        try {
            String ttml = source == null ? null : source.appleLyricsTtml(song);
            if (ttml != null) {
                Lyrics l = TtmlParser.parse(ttml, "apple");
                if (!l.isEmpty()) {
                    LyricsCache.write(song.key(), "ttml", "apple", ttml);
                    return l;
                }
            }
            String[] lrc = lrclib(song);
            if (lrc != null) {
                Lyrics l = lrc[0].equals("lrc") ? LrcParser.parse(lrc[1], "lrclib") : LrcParser.plain(lrc[1], "lrclib");
                if (!l.isEmpty()) {
                    LyricsCache.write(song.key(), lrc[0], "lrclib", lrc[1]);
                    return l;
                }
            }
        } catch (RuntimeException e) {
            CosmeticsMod.LOGGER.debug("[Sol] Lyrics lookup failed", e);
        }
        return null;
    }

    /** LRCLIB's lyrics for the song: {"lrc"|"plain", text}, or null. The exact match first, then a search. */
    static String[] lrclib(NowPlaying song) {
        if (song.title() == null || song.title().isBlank()) {
            return null;
        }
        StringBuilder q = new StringBuilder(LRCLIB).append("/api/get?track_name=").append(enc(song.title()))
                .append("&artist_name=").append(enc(song.artist()));
        if (song.album() != null && !song.album().isBlank()) {
            q.append("&album_name=").append(enc(song.album()));
        }
        if (song.durationMs() > 0) {
            q.append("&duration=").append(Math.round(song.durationMs() / 1000.0));
        }
        Http.Reply r = Http.get(q.toString(), Map.of());
        String[] picked = r.ok() ? pickLyrics(JsonParser.parseString(r.body())) : null;
        if (picked != null) {
            return picked;
        }
        Http.Reply s = Http.get(LRCLIB + "/api/search?track_name=" + enc(song.title()) + "&artist_name=" + enc(song.artist()), Map.of());
        if (!s.ok()) {
            return null;
        }
        JsonElement list = JsonParser.parseString(s.body());
        if (!list.isJsonArray()) {
            return null;
        }
        String[] plain = null;
        for (JsonElement e : list.getAsJsonArray()) {
            String[] p = pickLyrics(e);
            if (p != null && p[0].equals("lrc")) {
                return p;
            }
            if (p != null && plain == null) {
                plain = p;
            }
        }
        return plain;
    }

    /** One LRCLIB record's lyrics: synced if it has them, else plain; null for none (or instrumental). */
    static String[] pickLyrics(JsonElement e) {
        if (e == null || !e.isJsonObject()) {
            return null;
        }
        JsonObject o = e.getAsJsonObject();
        if (o.has("instrumental") && o.get("instrumental").isJsonPrimitive() && o.get("instrumental").getAsBoolean()) {
            return null;
        }
        String synced = str(o, "syncedLyrics"), plain = str(o, "plainLyrics");
        return synced != null && !synced.isBlank() ? new String[]{"lrc", synced}
                : plain != null && !plain.isBlank() ? new String[]{"plain", plain} : null;
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    /** The per-song lyric timing nudge (ms) the player set, by song key. */
    public static long offset(NowPlaying n) {
        Integer o = n == null ? null : SolConfig.get().music.lyricsOffsetMs.get(n.key());
        return o == null ? 0L : o;
    }

    /**
     * Found lyrics kept under {@code <main>/client/cache/lyrics}, one file per song (named by a hash of its key): the
     * kind ({@code ttml}, {@code lrc}, {@code plain}) and source on the first line, the text after.
     */
    static final class LyricsCache {
        private LyricsCache() {
        }

        static Path file(String key) {
            return MainFolder.clientFolder().resolve("cache").resolve("lyrics").resolve(hash(key) + ".txt");
        }

        static Lyrics read(String key) {
            try {
                Path f = file(key);
                if (!Files.isRegularFile(f)) {
                    return null;
                }
                String all = Files.readString(f, StandardCharsets.UTF_8);
                int nl = all.indexOf('\n');
                if (nl < 0) {
                    return null;
                }
                String[] head = all.substring(0, nl).split(" ", 2);
                String text = all.substring(nl + 1), source = head.length > 1 ? head[1] : "cache";
                Lyrics l = switch (head[0]) {
                    case "ttml" -> TtmlParser.parse(text, source);
                    case "lrc" -> LrcParser.parse(text, source);
                    case "plain" -> LrcParser.plain(text, source);
                    default -> null;
                };
                return l == null || l.isEmpty() ? null : l;
            } catch (java.io.IOException | RuntimeException e) {
                return null;
            }
        }

        static void write(String key, String kind, String source, String text) {
            try {
                Path f = file(key);
                Files.createDirectories(f.getParent());
                io.github.georgwav.cosmetics.sol.util.AtomicFiles.writeString(f, kind + " " + source + "\n" + text);
            } catch (java.io.IOException | RuntimeException e) {
                CosmeticsMod.LOGGER.debug("[Sol] Couldn't cache lyrics", e);
            }
        }

        static String hash(String key) {
            try {
                byte[] d = java.security.MessageDigest.getInstance("SHA-1").digest(key.getBytes(StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 10; i++) {
                    sb.append(String.format(Locale.ROOT, "%02x", d[i]));
                }
                return sb.toString();
            } catch (java.security.NoSuchAlgorithmException e) {
                return Integer.toHexString(key.hashCode());
            }
        }
    }

    /** For tests and the scenario: the sources by id. */
    static List<String> sourceIds() {
        return List.copyOf(SOURCES.keySet());
    }
}
