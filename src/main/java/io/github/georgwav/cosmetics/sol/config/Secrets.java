package io.github.georgwav.cosmetics.sol.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.sol.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/**
 * Tokens Sol keeps for the player (Cider's app token, Spotify's refresh token, a GitHub token for updates):
 * {@code <main folder>/client/secrets.json}, readable by the owner only where the system allows it, and never put
 * into a profile.
 */
public final class Secrets {
    private static JsonObject values;
    private static Path override;

    private Secrets() {
    }

    public static synchronized void useFile(Path file) {
        override = file;
        values = null;
    }

    private static Path file() {
        return override != null ? override : MainFolder.clientFolder().resolve("secrets.json");
    }

    private static JsonObject values() {
        if (values == null) {
            values = new JsonObject();
            Path f = file();
            if (Files.isRegularFile(f)) {
                try {
                    values = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
                } catch (IOException | RuntimeException e) {
                    CosmeticsMod.LOGGER.warn("[Sol] Couldn't read the secrets file; starting without saved tokens");
                }
            }
        }
        return values;
    }

    public static synchronized String get(String key) {
        JsonObject v = values();
        return v.has(key) && v.get(key).isJsonPrimitive() ? v.get(key).getAsString() : "";
    }

    public static synchronized void put(String key, String value) {
        JsonObject v = values();
        if (value == null || value.isEmpty()) {
            v.remove(key);
        } else {
            v.addProperty(key, value);
        }
        Path f = file();
        try {
            Files.createDirectories(f.getParent());
            AtomicFiles.writeString(f, v.toString());
            try {
                Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException | IOException ignored) {
                // Windows: the user's profile folder is already private to them.
            }
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't save the secrets file", e);
        }
    }
}
