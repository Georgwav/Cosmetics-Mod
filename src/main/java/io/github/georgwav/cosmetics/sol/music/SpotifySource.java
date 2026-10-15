package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.config.SolConfig;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Spotify through its Web API, once signed in ({@link SpotifyAuth}): what's playing every two seconds (the time is
 * carried forward between), and the controls (Premium only; Spotify refuses them otherwise, which is said once).
 */
public final class SpotifySource implements MusicSource {
    static final String API = System.getProperty("cosmeticsmod.spotifyApi", "https://api.spotify.com/v1");
    private ScheduledExecutorService loop;
    private volatile boolean connected;

    @Override
    public String id() {
        return "spotify";
    }

    private static String clientId() {
        return SolConfig.get().music.spotifyClientId;
    }

    @Override
    public void start() {
        if (loop != null) {
            return;
        }
        loop = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Sol Spotify");
            t.setDaemon(true);
            return t;
        });
        loop.scheduleWithFixedDelay(this::step, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (loop != null) {
            loop.shutdownNow();
            loop = null;
        }
        connected = false;
    }

    @Override
    public boolean connected() {
        return connected;
    }

    private void step() {
        try {
            if (!SpotifyAuth.signedIn()) {
                connected = false;
                return;
            }
            String token = SpotifyAuth.token(clientId());
            if (token == null) {
                connected = false;
                return;
            }
            Http.Reply r = Http.get(API + "/me/player/currently-playing", Map.of("Authorization", "Bearer " + token));
            if (r.status() == 401) {
                SpotifyAuth.expire();
                return;
            }
            connected = r.status() == 200 || r.status() == 204;
            if (r.status() == 204 || r.status() == 200 && r.body().isBlank()) {
                MusicService.report(this, null);
            } else if (r.ok()) {
                MusicService.report(this, parse(r.body()));
            }
        } catch (RuntimeException e) {
            io.github.georgwav.cosmetics.CosmeticsMod.LOGGER.debug("[Sol] Spotify step failed", e);
        }
    }

    /** {@code /me/player/currently-playing} as what's playing; null for nothing (or an episode/ad without a track). */
    static NowPlaying parse(String json) {
        try {
            JsonObject o = JsonParser.parseString(json).getAsJsonObject();
            if (!o.has("item") || !o.get("item").isJsonObject()) {
                return null;
            }
            JsonObject item = o.getAsJsonObject("item");
            String name = CiderJson.string(item, "name");
            if (name == null || name.isBlank()) {
                return null;
            }
            StringBuilder artists = new StringBuilder();
            if (item.has("artists") && item.get("artists").isJsonArray()) {
                for (JsonElement a : item.getAsJsonArray("artists")) {
                    String n = a.isJsonObject() ? CiderJson.string(a.getAsJsonObject(), "name") : null;
                    if (n != null) {
                        artists.append(artists.isEmpty() ? "" : ", ").append(n);
                    }
                }
            }
            String album = "", art = null;
            if (item.has("album") && item.get("album").isJsonObject()) {
                JsonObject al = item.getAsJsonObject("album");
                album = CiderJson.string(al, "name") == null ? "" : CiderJson.string(al, "name");
                art = image(al.has("images") && al.get("images").isJsonArray() ? al.getAsJsonArray("images") : null);
            }
            String id = CiderJson.string(item, "id");
            long duration = (long) CiderJson.number(item, "duration_ms", 0);
            long position = (long) CiderJson.number(o, "progress_ms", 0);
            boolean playing = o.has("is_playing") && o.get("is_playing").getAsBoolean();
            return new NowPlaying("spotify", "spotify:" + (id != null ? id : name + "|" + artists), name, artists.toString(), album,
                    duration, position, System.nanoTime(), playing, art, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The cover closest to 300 px (Spotify gives 640, 300 and 64). */
    private static String image(JsonArray images) {
        if (images == null) {
            return null;
        }
        String best = null;
        int bestD = Integer.MAX_VALUE;
        for (JsonElement e : images) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject i = e.getAsJsonObject();
            int w = (int) CiderJson.number(i, "width", 300);
            int d = Math.abs(w - Covers.LARGE * 2);
            if (CiderJson.string(i, "url") != null && d < bestD) {
                bestD = d;
                best = CiderJson.string(i, "url");
            }
        }
        return best;
    }

    private void control(String method, String path) {
        Thread t = new Thread(() -> {
            String token = SpotifyAuth.token(clientId());
            if (token == null) {
                return;
            }
            Http.Reply r = Http.send(method, API + path, "", "application/json", Map.of("Authorization", "Bearer " + token));
            if (r.status() == 403) {
                MusicService.problem("sol.notify.spotify_premium");
            }
            ScheduledExecutorService l = loop;
            if (l != null) {
                l.execute(this::step);
            }
        }, "Sol Spotify control");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void playPause() {
        NowPlaying n = MusicService.now();
        control("PUT", n != null && n.playing() ? "/me/player/pause" : "/me/player/play");
    }

    @Override
    public void next() {
        control("POST", "/me/player/next");
    }

    @Override
    public void previous() {
        control("POST", "/me/player/previous");
    }

    /** Saves the track to Liked Songs. */
    @Override
    public void like() {
        NowPlaying n = MusicService.now();
        if (n != null && n.key().startsWith("spotify:") && !n.key().contains("|")) {
            control("PUT", "/me/tracks?ids=" + n.key().substring("spotify:".length()));
        }
    }
}
