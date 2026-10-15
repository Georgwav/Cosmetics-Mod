package io.github.georgwav.cosmetics.sol.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CiderTest {
    static final String NOW = """
            {"status":"ok","info":{"albumName":"Skin","durationInMillis":193633,"name":"Like Water (feat. MNDR)",
             "artistName":"Flume","currentPlaybackTime":2.066576,
             "artwork":{"width":600,"height":600,"url":"https://is1-ssl.mzstatic.com/image/thumb/a/3555.jpg/{w}x{h}bb.jpg"},
             "playParams":{"id":"i.abc","kind":"song","catalogId":"1719861213"}}}""";

    @Test
    void nowPlayingReadsTheSongAndItsCatalogId() {
        NowPlaying n = CiderJson.nowPlaying(NOW, true);
        assertNotNull(n);
        assertEquals("Like Water (feat. MNDR)", n.title());
        assertEquals("Flume", n.artist());
        assertEquals("Skin", n.album());
        assertEquals(193633, n.durationMs());
        assertEquals(2067, n.positionMs());
        assertEquals("1719861213", n.catalogId(), "a library song's catalog id");
        assertEquals("cider:1719861213", n.key());
        assertTrue(n.artworkUrl().endsWith("/300x300bb.jpg"));
        assertTrue(n.playing());
    }

    @Test
    void missingFieldsAndJunkAreSafe() {
        assertNull(CiderJson.nowPlaying("{\"status\":\"ok\",\"info\":{}}", true));
        assertNull(CiderJson.nowPlaying("not json", true));
        NowPlaying bare = CiderJson.nowPlaying("{\"info\":{\"name\":\"X\",\"playParams\":{\"id\":\"123\"}}}", false);
        assertEquals("123", bare.catalogId(), "a numeric id is the catalog id");
        assertNull(bare.artworkUrl());
        assertTrue(CiderJson.isPlaying("{\"status\":\"ok\",\"is_playing\":true}"));
        assertFalse(CiderJson.isPlaying("{}"));
    }

    @Test
    void playbackEventsAndTimes() {
        String[] e = CiderJson.playbackEvent("{\"type\":\"playbackStatus.playbackTimeDidChange\",\"data\":{\"isPlaying\":false,\"currentPlaybackTime\":61.5}}");
        assertEquals("playbackStatus.playbackTimeDidChange", e[0]);
        long[] t = CiderJson.playbackTime(e[1]);
        assertEquals(61500, t[0]);
        assertEquals(0, t[1]);
        assertNull(CiderJson.playbackTime("{}"));
    }

    @Test
    void socketIoFraming() {
        assertEquals("40", SocketIo.reply("0{\"sid\":\"abc\",\"pingInterval\":25000}"));
        assertEquals("3", SocketIo.reply("2"));
        assertNull(SocketIo.reply("40"));
        String[] ev = SocketIo.event("42[\"API:Playback\",{\"type\":\"x\",\"data\":{\"a\":1}}]");
        assertEquals("API:Playback", ev[0]);
        assertTrue(ev[1].contains("\"type\":\"x\""));
        assertNull(SocketIo.event("40"));
        assertNull(SocketIo.event("42not json"));
    }

    @Test
    void positionCarriesForwardWhilePlaying() {
        NowPlaying n = new NowPlaying("t", "k", "a", "b", "c", 10_000, 1_000, 0L, true, null, null);
        assertEquals(3_000, n.position(2_000_000_000L));
        assertEquals(10_000, n.position(60_000_000_000L), "never past the end");
        NowPlaying paused = n.withPosition(4_000, false);
        assertEquals(4_000, paused.position(paused.atNanos() + 5_000_000_000L));
    }
}
