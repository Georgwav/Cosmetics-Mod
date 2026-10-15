package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.config.Secrets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

/**
 * Spotify sign-in with PKCE (no client secret: the player's own app's Client ID): the browser opens Spotify's
 * consent page, Spotify sends the browser back to {@code http://127.0.0.1:43117/callback} (served once by
 * {@link LoopbackServer}), and the code is traded for tokens. The refresh token is kept in {@link Secrets}; the
 * access token only in memory and renewed before it runs out. Network calls never on the render thread.
 */
public final class SpotifyAuth {
    public static final int PORT = 43117;
    public static final String REDIRECT = "http://127.0.0.1:" + PORT + "/callback";
    static final String SCOPES = "user-read-playback-state user-read-currently-playing user-modify-playback-state user-library-modify";
    static final String ACCOUNTS = System.getProperty("cosmeticsmod.spotifyAccounts", "https://accounts.spotify.com");
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~";
    private static final SecureRandom RANDOM = new SecureRandom();

    private static volatile String accessToken;
    private static volatile long expiresAt;

    private SpotifyAuth() {
    }

    /** A code verifier: 64 characters from the unreserved set (RFC 7636 allows 43 to 128). */
    static String verifier() {
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < 64; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /** The S256 challenge: base64url (no padding) of the verifier's SHA-256. */
    static String challenge(String verifier) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String authorizeUrl(String clientId, String challenge, String state) {
        return ACCOUNTS + "/authorize?" + Http.form("client_id", clientId, "response_type", "code", "redirect_uri", REDIRECT,
                "code_challenge_method", "S256", "code_challenge", challenge, "scope", SCOPES, "state", state);
    }

    public static boolean signedIn() {
        return !Secrets.get("spotifyRefresh").isBlank();
    }

    public static void signOut() {
        Secrets.put("spotifyRefresh", "");
        accessToken = null;
    }

    /**
     * The whole sign-in (IO thread): opens the browser, waits up to three minutes for Spotify to send it back, and
     * trades the code. Returns null when it worked, else a lang key saying why not.
     */
    public static String signIn(String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return "sol.notify.spotify_client_id";
        }
        String verifier = verifier(), state = verifier().substring(0, 16);
        String url = authorizeUrl(clientId.strip(), challenge(verifier), state);
        LoopbackServer.Result r;
        try (LoopbackServer server = LoopbackServer.open(PORT)) {
            if (!io.github.georgwav.cosmetics.sol.util.Browser.open(url)) {
                return "sol.notify.spotify_browser";
            }
            r = server.await(180_000L);
        } catch (java.io.IOException e) {
            return "sol.notify.spotify_port";
        }
        if (r == null || r.code() == null || !state.equals(r.state())) {
            return "sol.notify.spotify_refused";
        }
        Http.Reply reply = Http.send("POST", ACCOUNTS + "/api/token", Http.form("grant_type", "authorization_code", "code", r.code(),
                "redirect_uri", REDIRECT, "client_id", clientId.strip(), "code_verifier", verifier),
                "application/x-www-form-urlencoded", Map.of());
        return take(reply) ? null : "sol.notify.spotify_refused";
    }

    /** A token reply: keeps the access token and the (new) refresh token. */
    static boolean take(Http.Reply reply) {
        if (!reply.ok()) {
            return false;
        }
        try {
            JsonObject o = JsonParser.parseString(reply.body()).getAsJsonObject();
            accessToken = o.get("access_token").getAsString();
            expiresAt = System.currentTimeMillis() + (o.has("expires_in") ? o.get("expires_in").getAsLong() : 3600L) * 1000L;
            if (o.has("refresh_token") && !o.get("refresh_token").isJsonNull()) {
                Secrets.put("spotifyRefresh", o.get("refresh_token").getAsString());
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** A usable access token (renewed when it has under a minute left), or null when signed out or refused. IO thread. */
    static String token(String clientId) {
        if (accessToken != null && System.currentTimeMillis() < expiresAt - 60_000L) {
            return accessToken;
        }
        String refresh = Secrets.get("spotifyRefresh");
        if (refresh.isBlank() || clientId == null || clientId.isBlank()) {
            return null;
        }
        Http.Reply reply = Http.send("POST", ACCOUNTS + "/api/token", Http.form("grant_type", "refresh_token",
                "refresh_token", refresh, "client_id", clientId.strip()), "application/x-www-form-urlencoded", Map.of());
        if (take(reply)) {
            return accessToken;
        }
        if (reply.status() == 400 || reply.status() == 401) {
            // Revoked: sign in again.
            signOut();
        }
        return null;
    }

    /** The token went bad early (a 401): the next call renews it. */
    static void expire() {
        expiresAt = 0L;
    }
}
