package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.config.Secrets;
import io.github.georgwav.cosmetics.sol.config.SolConfig;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Cider (the Apple Music client): its REST API on {@code localhost:10767} (the {@code apptoken} header when Cider asks
 * for one) and its Socket.IO playback events. While the socket is up the events drive the song and its time (with
 * a REST check every 5 s); without it, REST is asked every second. A closed Cider is tried again every 10 s.
 */
public final class CiderSource implements MusicSource {
    private ScheduledExecutorService loop;
    private volatile SocketIo socket;
    private volatile boolean socketUp, reachable, refusedOnce, heard;
    private long lastSocketTry, lastRest;
    private volatile NowPlaying last;
    private volatile String storefront;

    @Override
    public String id() {
        return "cider";
    }

    private static String base() {
        SolConfig.Music m = SolConfig.get().music;
        return "http://" + m.ciderHost + ":" + m.ciderPort + "/api/v1";
    }

    private static Map<String, String> auth() {
        String token = Secrets.get("ciderToken");
        return token == null || token.isBlank() ? Map.of() : Map.of("apptoken", token);
    }

    @Override
    public void start() {
        if (loop != null) {
            return;
        }
        loop = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Sol Cider");
            t.setDaemon(true);
            return t;
        });
        loop.scheduleWithFixedDelay(this::step, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (loop != null) {
            loop.shutdownNow();
            loop = null;
        }
        if (socket != null) {
            socket.close();
            socket = null;
        }
        socketUp = false;
        reachable = false;
    }

    @Override
    public boolean connected() {
        return reachable;
    }

    private void step() {
        try {
            long now = System.currentTimeMillis();
            if (!socketUp && now - lastSocketTry > 10_000L) {
                lastSocketTry = now;
                SolConfig.Music m = SolConfig.get().music;
                socket = new SocketIo(m.ciderHost, m.ciderPort, this::event, () -> socketUp = false);
                socket.connect(Http.CLIENT);
                socketUp = true;
            }
            if (!socketUp || now - lastRest > 5_000L) {
                lastRest = now;
                rest();
            }
        } catch (RuntimeException e) {
            CosmeticsMod.LOGGER.debug("[Sol] Cider step failed", e);
        }
    }

    private void rest() {
        Http.Reply r = Http.get(base() + "/playback/now-playing", auth());
        if (r.status() == 401 || r.status() == 403) {
            reachable = true;
            if (!refusedOnce) {
                refusedOnce = true;
                MusicService.problem("sol.notify.cider_token");
            }
            return;
        }
        if (!r.ok()) {
            reachable = false;
            if (r.status() == -1) {
                MusicService.report(this, null);
            }
            return;
        }
        reachable = true;
        boolean playing = CiderJson.isPlaying(Http.get(base() + "/playback/is-playing", auth()).body());
        NowPlaying n = CiderJson.nowPlaying(r.body(), playing);
        last = n;
        MusicService.report(this, n);
    }

    /** A Socket.IO event: Cider's playback changes. */
    private void event(String name, String data) {
        if (!"API:Playback".equals(name)) {
            return;
        }
        reachable = true;
        heard = true;
        String[] e = CiderJson.playbackEvent(data);
        switch (e[0]) {
            case "playbackStatus.nowPlayingItemDidChange" -> {
                NowPlaying n = CiderJson.nowPlaying(e[1], true);
                if (n != null) {
                    last = n;
                    MusicService.report(this, n);
                }
            }
            case "playbackStatus.playbackTimeDidChange" -> {
                long[] t = CiderJson.playbackTime(e[1]);
                NowPlaying n = last;
                if (t != null && n != null) {
                    last = n.withPosition(t[0], t[1] == 1);
                    MusicService.report(this, last);
                }
            }
            case "playbackStatus.playbackStateDidChange" -> {
                NowPlaying n = last;
                boolean playing = e[1].contains("\"playing\"");
                if (n != null) {
                    last = n.withPosition(n.position(), playing);
                    MusicService.report(this, last);
                }
            }
            default -> {
            }
        }
    }

    private void post(String path) {
        Thread t = new Thread(() -> Http.post(base() + path, "{}", auth()), "Sol Cider control");
        t.setDaemon(true);
        t.start();
    }

    /** Whether Cider's Socket.IO events have come in (since it started). */
    boolean heardEvents() {
        return heard;
    }

    /** A new app token: the next refusal is told again. */
    void tokenChanged() {
        refusedOnce = false;
    }

    @Override
    public void playPause() {
        post("/playback/playpause");
    }

    @Override
    public void next() {
        post("/playback/next");
    }

    @Override
    public void previous() {
        post("/playback/previous");
    }

    @Override
    public void like() {
        Thread t = new Thread(() -> {
            Http.post(base() + "/playback/set-rating", "{\"rating\":1}", auth());
            Http.post(base() + "/playback/add-to-library", "{}", auth());
        }, "Sol Cider control");
        t.setDaemon(true);
        t.start();
    }

    /** Apple Music's API through Cider ({@code amapi/run-v3}): the reply, or null. */
    private String runV3(String path) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        body.addProperty("path", path);
        Http.Reply r = Http.post(base() + "/amapi/run-v3", body.toString(), auth());
        return r.ok() ? r.body() : null;
    }

    /** The player's storefront (country), asked once: "us" until known. */
    private String storefront() {
        String sf = storefront;
        if (sf != null) {
            return sf;
        }
        String reply = runV3("/v1/me/storefront?limit=1");
        sf = "us";
        try {
            if (reply != null) {
                JsonObject o = JsonParser.parseString(reply).getAsJsonObject();
                sf = o.getAsJsonObject("data").getAsJsonArray("data").get(0).getAsJsonObject().get("id").getAsString();
                storefront = sf;
            }
        } catch (RuntimeException e) {
            // Keep "us".
        }
        return sf;
    }

    /**
     * The Music page's library row (IO thread): "queue" (up next), "recent", "playlists", or "search" for {@code query}.
     */
    java.util.List<CiderLibrary.Item> library(String tab, String query) {
        NowPlaying n = last;
        String currentId = n == null ? null : n.catalogId();
        return switch (tab) {
            case "queue" -> {
                Http.Reply r = Http.get(base() + "/playback/queue", auth());
                yield r.ok() ? CiderLibrary.queue(r.body(), currentId, 12) : java.util.List.of();
            }
            case "recent" -> data(runV3("/v1/me/recent/played?limit=10"));
            case "playlists" -> data(runV3("/v1/me/library/playlists?limit=25"));
            case "search" -> {
                if (query == null || query.isBlank()) {
                    yield java.util.List.of();
                }
                String reply = runV3("/v1/catalog/" + storefront() + "/search?term=" + java.net.URLEncoder.encode(query.strip(),
                        java.nio.charset.StandardCharsets.UTF_8) + "&types=songs,albums,playlists&limit=8");
                yield reply == null ? java.util.List.of() : CiderLibrary.search(reply, 12);
            }
            default -> java.util.List.of();
        };
    }

    private static java.util.List<CiderLibrary.Item> data(String reply) {
        return reply == null ? java.util.List.of() : CiderLibrary.data(reply, 12);
    }

    /** Plays an item from the library row: by Apple Music's path when it has one, else by kind and id. */
    void play(CiderLibrary.Item item) {
        com.google.gson.JsonObject body = new com.google.gson.JsonObject();
        String path;
        if (item.href() != null) {
            body.addProperty("href", item.href());
            path = "/playback/play-item-href";
        } else {
            body.addProperty("type", item.kind());
            body.addProperty("id", item.id());
            path = "/playback/play-item";
        }
        Thread t = new Thread(() -> Http.post(base() + path, body.toString(), auth()), "Sol Cider control");
        t.setDaemon(true);
        t.start();
    }

    /** Apple's lyrics through Cider's Apple Music passthrough: syllable lyrics first, then line lyrics. */
    @Override
    public String appleLyricsTtml(NowPlaying song) {
        if (song.catalogId() == null) {
            return null;
        }
        String storefront = storefront();
        for (String kind : new String[]{"syllable-lyrics", "lyrics"}) {
            String reply = runV3("/v1/catalog/" + storefront + "/songs/" + song.catalogId() + "/" + kind + "?l=en-US&platform=web");
            if (reply == null) {
                continue;
            }
            try {
                JsonObject o = JsonParser.parseString(reply).getAsJsonObject();
                String ttml = o.getAsJsonObject("data").getAsJsonArray("data").get(0).getAsJsonObject()
                        .getAsJsonObject("attributes").get("ttml").getAsString();
                if (ttml != null && !ttml.isBlank()) {
                    return ttml;
                }
            } catch (RuntimeException e) {
                // Try the next kind.
            }
        }
        return null;
    }
}
