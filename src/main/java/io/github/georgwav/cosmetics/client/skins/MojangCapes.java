package io.github.georgwav.cosmetics.client.skins;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Your Mojang capes, as the launcher's cape picker shows them (Mojang's profile API, with the game's access
 * token): which capes the account owns and which one is worn, wearing another, or hiding it.
 */
public final class MojangCapes {
    /** A cape the account owns: its id, its name (Mojang's alias), its texture's URL, and whether it is worn. */
    public record Cape(String id, String alias, String url, boolean active) {
    }

    /** The answer to a listing: the response's status (200 when it worked) and the capes. */
    public record Listing(int status, List<Cape> capes) {
    }

    private static final URI PROFILE = URI.create("https://api.minecraftservices.com/minecraft/profile");
    private static final URI ACTIVE = URI.create("https://api.minecraftservices.com/minecraft/profile/capes/active");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    private MojangCapes() {
    }

    public static CompletableFuture<Listing> list(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(PROFILE)
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(r -> new Listing(r.statusCode(), r.statusCode() == 200 ? parse(r.body()) : List.of()));
    }

    /** Wears the cape with this id; completes with the response's status (200 when it took). */
    public static CompletableFuture<Integer> wear(String accessToken, String capeId) {
        JsonObject body = new JsonObject();
        body.addProperty("capeId", capeId);
        HttpRequest request = HttpRequest.newBuilder(ACTIVE)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
    }

    /** Hides the cape (none worn); completes with the response's status. */
    public static CompletableFuture<Integer> hide(String accessToken) {
        HttpRequest request = HttpRequest.newBuilder(ACTIVE)
                .header("Authorization", "Bearer " + accessToken)
                .timeout(Duration.ofSeconds(15))
                .DELETE()
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
    }

    /** A cape's texture (a PNG); Mojang's texture URLs are plain http, read over https. */
    public static CompletableFuture<byte[]> texture(String url) {
        String secure = url.startsWith("http://") ? "https://" + url.substring("http://".length()) : url;
        HttpRequest request = HttpRequest.newBuilder(URI.create(secure)).timeout(Duration.ofSeconds(15)).GET().build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(r -> {
            if (r.statusCode() != 200) {
                throw new IllegalStateException("cape texture: status " + r.statusCode());
            }
            return r.body();
        });
    }

    /** The capes in a profile response ({@code capes: [{id, state, url, alias}]}); anything missing is skipped. */
    public static List<Cape> parse(String json) {
        List<Cape> out = new ArrayList<>();
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject() || !root.getAsJsonObject().has("capes") || !root.getAsJsonObject().get("capes").isJsonArray()) {
            return out;
        }
        JsonArray capes = root.getAsJsonObject().getAsJsonArray("capes");
        for (JsonElement e : capes) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject c = e.getAsJsonObject();
            String id = string(c, "id"), url = string(c, "url");
            if (id == null || url == null) {
                continue;
            }
            String alias = string(c, "alias");
            out.add(new Cape(id, alias != null ? alias : id, url, "ACTIVE".equals(string(c, "state"))));
        }
        return out;
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }
}
