package io.github.georgwav.cosmetics.sol.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PkceTest {
    @Test
    void verifierHasTheRightLengthAndCharacters() {
        for (int i = 0; i < 20; i++) {
            String v = SpotifyAuth.verifier();
            assertEquals(64, v.length());
            assertTrue(v.matches("[A-Za-z0-9._~-]+"), v);
        }
        assertNotEquals(SpotifyAuth.verifier(), SpotifyAuth.verifier());
    }

    @Test
    void challengeMatchesRfc7636() {
        // RFC 7636 appendix B.
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
                SpotifyAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"));
    }

    @Test
    void authorizeUrlCarriesEverything() {
        String url = SpotifyAuth.authorizeUrl("abc123", "CH", "st");
        assertTrue(url.startsWith(SpotifyAuth.ACCOUNTS + "/authorize?"));
        assertTrue(url.contains("client_id=abc123"));
        assertTrue(url.contains("code_challenge_method=S256"));
        assertTrue(url.contains("code_challenge=CH"));
        assertTrue(url.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A43117%2Fcallback"));
        assertTrue(url.contains("state=st"));
        assertTrue(url.contains("scope=user-read-playback-state+user-read-currently-playing+user-modify-playback-state"));
    }

    @Test
    void loopbackTakesTheCallbackOnly() {
        LoopbackServer.Result r = LoopbackServer.parse("GET /callback?code=AQB%2Fx&state=s1 HTTP/1.1");
        assertNotNull(r);
        assertEquals("AQB/x", r.code());
        assertEquals("s1", r.state());
        LoopbackServer.Result denied = LoopbackServer.parse("GET /callback?error=access_denied&state=s1 HTTP/1.1");
        assertNull(denied.code());
        assertEquals("access_denied", denied.error());
        assertNull(LoopbackServer.parse("GET /favicon.ico HTTP/1.1"));
        assertNull(LoopbackServer.parse("POST /callback HTTP/1.1"));
    }

    @Test
    void loopbackServesOneRequest() throws Exception {
        try (LoopbackServer s = LoopbackServer.open(0)) {
            int port = s.port();
            Thread browser = new Thread(() -> {
                try {
                    java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("http://127.0.0.1:" + port + "/callback?code=c&state=t")).build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding());
                } catch (Exception ignored) {
                    // The test fails below.
                }
            });
            browser.start();
            LoopbackServer.Result r = s.await(5000L);
            browser.join(5000L);
            assertNotNull(r);
            assertEquals("c", r.code());
            assertEquals("t", r.state());
        }
    }

    @Test
    void spotifyNowPlayingParses() {
        String json = "{\"is_playing\":true,\"progress_ms\":42000,\"item\":{\"id\":\"t1\",\"name\":\"Song\",\"duration_ms\":180000,"
                + "\"artists\":[{\"name\":\"A\"},{\"name\":\"B\"}],\"album\":{\"name\":\"Al\",\"images\":[{\"url\":\"u640\",\"width\":640},"
                + "{\"url\":\"u300\",\"width\":300},{\"url\":\"u64\",\"width\":64}]}}}";
        NowPlaying n = SpotifySource.parse(json);
        assertNotNull(n);
        assertEquals("spotify:t1", n.key());
        assertEquals("A, B", n.artist());
        assertEquals(42_000L, n.positionMs());
        assertEquals("u300", n.artworkUrl());
        assertTrue(n.playing());
        assertNull(SpotifySource.parse("{\"is_playing\":false,\"item\":null}"));
        assertNull(SpotifySource.parse("not json"));
    }
}
