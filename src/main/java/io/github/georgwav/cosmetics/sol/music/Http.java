package io.github.georgwav.cosmetics.sol.music;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/** Sol's one HTTP client for music (2 s to connect, 4 s for a reply), and small helpers. Never on the render thread. */
public final class Http {
    public static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
            .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NORMAL).build();
    static final String AGENT = "SolClient/0.1 (https://github.com/georgwav/cosmetics-mod)";

    private Http() {
    }

    /** A GET's status and body; status -1 when it couldn't be reached. */
    public static Reply get(String url, Map<String, String> headers) {
        return send(HttpRequest.newBuilder(URI.create(url)).GET(), headers);
    }

    public static Reply post(String url, String json, Map<String, String> headers) {
        return send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.ofString(json))
                .header("Content-Type", "application/json"), headers);
    }

    /** A request with any method and body ({@code type} its content type; null body: none). */
    public static Reply send(String method, String url, String body, String type, Map<String, String> headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).method(method,
                body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null && type != null) {
            b.header("Content-Type", type);
        }
        return send(b, headers);
    }

    /** A form body ({@code a=1&b=2}) from pairs, encoded. */
    public static String form(String... pairs) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            sb.append(java.net.URLEncoder.encode(pairs[i], java.nio.charset.StandardCharsets.UTF_8)).append('=')
                    .append(java.net.URLEncoder.encode(pairs[i + 1], java.nio.charset.StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public static byte[] bytes(String url) {
        try {
            HttpResponse<byte[]> r = CLIENT.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(6))
                    .header("User-Agent", AGENT).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            return r.statusCode() / 100 == 2 ? r.body() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static Reply send(HttpRequest.Builder b, Map<String, String> headers) {
        try {
            b.timeout(Duration.ofSeconds(4)).header("User-Agent", AGENT);
            headers.forEach(b::header);
            HttpResponse<String> r = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString());
            return new Reply(r.statusCode(), r.body());
        } catch (IOException | RuntimeException e) {
            return new Reply(-1, "");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Reply(-1, "");
        }
    }

    public record Reply(int status, String body) {
        public boolean ok() {
            return status / 100 == 2;
        }
    }
}
