package io.github.georgwav.cosmetics.client.skins;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Changes your skin on your Minecraft account (Mojang's profile API, as the launcher does): the PNG and whether
 * its arms are slim, sent with the game's access token. Others see it once you rejoin a server.
 */
public final class SkinUploader {
    private static final URI SKINS = URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    private SkinUploader() {
    }

    /** Sends it; completes with the response's status (200 when it took). */
    public static CompletableFuture<Integer> upload(String accessToken, byte[] png, boolean slim) {
        String boundary = "cosmeticsmod-" + Long.toHexString(System.nanoTime());
        ByteArrayOutputStream body = new ByteArrayOutputStream(png.length + 512);
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"variant\"\r\n\r\n"
                + (slim ? "slim" : "classic") + "\r\n");
        write(body, "--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n");
        body.writeBytes(png);
        write(body, "\r\n--" + boundary + "--\r\n");
        HttpRequest request = HttpRequest.newBuilder(SKINS)
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.discarding()).thenApply(HttpResponse::statusCode);
    }

    private static void write(ByteArrayOutputStream out, String text) {
        out.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }
}
