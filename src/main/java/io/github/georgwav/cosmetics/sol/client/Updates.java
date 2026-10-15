package io.github.georgwav.cosmetics.sol.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.config.Secrets;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;

/**
 * Updates from the repository's GitHub Releases (D11): once a day (and on request) the latest release is asked for;
 * a newer one with a jar for this Minecraft version is offered with a notification. Installing downloads it next to
 * the running jar as {@code .pending}; when the game closes the old jar makes way for it (on Windows a small
 * command does it once the game has let go of the file). A private repository needs a token in Sol's secrets.
 */
public final class Updates {
    /** A release: its version (tag without "v"), the jar's file name and download address, and whether it's the API's. */
    public record Release(String version, String name, String notes, String asset, String url, boolean api) {
    }

    private static volatile Release available;
    private static volatile String state = "idle";
    private static volatile Path pending;

    private Updates() {
    }

    /** idle, checking, current, available, downloading, staged, failed, private (needs a token). */
    public static String state() {
        return state;
    }

    public static Release available() {
        return available;
    }

    /** This jar's version ("0.1.0" of "0.1.0+mc1.21.11"). */
    public static String current() {
        String v = FabricLoader.getInstance().getModContainer(CosmeticsMod.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
        int plus = v.indexOf('+');
        return plus < 0 ? v : v.substring(0, plus);
    }

    static String minecraft() {
        return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    /** Whether version {@code a} is newer than {@code b} (dotted numbers; a missing part counts as 0). */
    static boolean newer(String a, String b) {
        String[] x = a.replaceFirst("^[vV]", "").split("[.-]"), y = b.replaceFirst("^[vV]", "").split("[.-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? number(x[i]) : 0, q = i < y.length ? number(y[i]) : 0;
            if (p != q) {
                return p > q;
            }
        }
        return false;
    }

    private static int number(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D.*$", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** A release reply's jar for this Minecraft version ({@code solclient-<v>+mc<mc>.jar}), as a release; or null. */
    static Release parse(String json, String mc) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String tag = o.has("tag_name") ? o.get("tag_name").getAsString() : null;
        if (tag == null || !o.has("assets")) {
            return null;
        }
        String version = tag.replaceFirst("^[vV]", "");
        for (JsonElement e : o.getAsJsonArray("assets")) {
            JsonObject a = e.getAsJsonObject();
            String name = a.get("name").getAsString();
            if (name.endsWith("+mc" + mc + ".jar") && (name.startsWith("solclient-") || name.startsWith("cosmeticsmod-"))) {
                String browser = a.has("browser_download_url") ? a.get("browser_download_url").getAsString() : null;
                String apiUrl = a.has("url") ? a.get("url").getAsString() : null;
                String notes = o.has("body") && !o.get("body").isJsonNull() ? o.get("body").getAsString() : "";
                String title = o.has("name") && !o.get("name").isJsonNull() ? o.get("name").getAsString() : tag;
                boolean useApi = apiUrl != null && !Secrets.get("githubToken").isBlank();
                return new Release(version, title, notes, name, useApi ? apiUrl : browser, useApi);
            }
        }
        return null;
    }

    private static HttpRequest.Builder request(String url, String accept) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "SolClient/" + current()).header("Accept", accept);
        String token = Secrets.get("githubToken");
        if (!token.isBlank()) {
            b.header("Authorization", "Bearer " + token.strip());
        }
        return b;
    }

    /** Looks for a newer release (IO thread); {@code tell}: a notification either way, else only when there's one. */
    public static void check(boolean tell) {
        state = "checking";
        SolExecutors.IO.execute(() -> {
            SolConfig cfg = SolConfig.get();
            cfg.updates.lastCheck = System.currentTimeMillis();
            SolConfig.save();
            try {
                String url = "https://api.github.com/repos/" + cfg.updates.repo + "/releases/latest";
                HttpResponse<String> r = io.github.georgwav.cosmetics.sol.music.Http.CLIENT.send(
                        request(url, "application/vnd.github+json").GET().build(), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() == 404) {
                    state = Secrets.get("githubToken").isBlank() ? "private" : "current";
                    tell(tell, state.equals("private") ? "sol.notify.update_private" : "sol.notify.update_current");
                    return;
                }
                if (r.statusCode() / 100 != 2) {
                    state = "failed";
                    tell(tell, "sol.notify.update_failed");
                    return;
                }
                Release rel = parse(r.body(), minecraft());
                if (rel != null && newer(rel.version(), current())) {
                    available = rel;
                    state = "available";
                    Notifications.post(Component.translatable("sol.notify.update_available", rel.version()).getString(),
                            Component.translatable("sol.notify.update_install").getString(), Updates::install);
                } else {
                    state = "current";
                    tell(tell, "sol.notify.update_current");
                }
            } catch (IOException | RuntimeException e) {
                state = "failed";
                tell(tell, "sol.notify.update_failed");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static void tell(boolean tell, String key) {
        if (tell) {
            Notifications.post(Component.translatable(key).getString());
        }
    }

    /** Once a day, at the title screen (when allowed). */
    public static void daily() {
        SolConfig cfg = SolConfig.get();
        if (cfg.updates.check && System.currentTimeMillis() - cfg.updates.lastCheck > 24L * 60 * 60 * 1000 && state.equals("idle")) {
            check(false);
        }
    }

    /** Downloads the release next to this jar as .pending; it takes over when the game closes. */
    public static void install() {
        Release rel = available;
        Path jar = ownJar();
        if (rel == null || jar == null) {
            Notifications.post(Component.translatable("sol.notify.update_failed").getString());
            return;
        }
        state = "downloading";
        SolExecutors.IO.execute(() -> {
            Path target = jar.resolveSibling(rel.asset() + ".pending");
            try {
                HttpResponse<InputStream> r = io.github.georgwav.cosmetics.sol.music.Http.CLIENT.send(
                        request(rel.url(), rel.api() ? "application/octet-stream" : "*/*").GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                if (r.statusCode() / 100 != 2) {
                    throw new IOException("status " + r.statusCode());
                }
                try (InputStream in = r.body()) {
                    Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                }
                if (Files.size(target) < 10_000L) {
                    throw new IOException("too small");
                }
                pending = target;
                state = "staged";
                Notifications.post(Component.translatable("sol.notify.update_staged", rel.version()).getString());
            } catch (IOException | RuntimeException e) {
                state = "failed";
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    // Nothing to clean.
                }
                Notifications.post(Component.translatable("sol.notify.update_failed").getString());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** The jar this mod runs from, or null (a development run). */
    static Path ownJar() {
        return FabricLoader.getInstance().getModContainer(CosmeticsMod.MOD_ID).flatMap(c -> c.getOrigin().getKind()
                        == net.fabricmc.loader.api.metadata.ModOrigin.Kind.PATH ? c.getOrigin().getPaths().stream().findFirst() : java.util.Optional.empty())
                .filter(p -> p.toString().endsWith(".jar")).orElse(null);
    }

    /**
     * When the game closes with an update downloaded, the new jar replaces this one. A download that couldn't take
     * over last time (a locked file, a full disk) is found again here and tried at this close.
     */
    public static void registerSwap() {
        Path own = ownJar();
        if (own != null && pending == null) {
            try (var files = Files.list(own.getParent())) {
                files.filter(f -> f.getFileName().toString().startsWith("solclient-") && f.getFileName().toString().endsWith(".jar.pending"))
                        .findFirst().ifPresent(f -> pending = f);
            } catch (IOException | RuntimeException e) {
                // No folder to look in: nothing waiting.
            }
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Path next = pending, jar = ownJar();
            if (next == null || jar == null || !Files.exists(next)) {
                return;
            }
            Path finalName = next.resolveSibling(next.getFileName().toString().replaceFirst("\\.pending$", ""));
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            try {
                if (windows) {
                    // The running jar is locked until the game has gone: a detached command waits a moment, then
                    // swaps, the new jar moving in only once the old one is gone (never two of the mod at once).
                    new ProcessBuilder("cmd", "/c", "ping -n 4 127.0.0.1 > nul & del /f /q \"" + jar + "\" && move /y \""
                            + next + "\" \"" + finalName + "\"").start();
                } else {
                    // The new jar in first, then the old one out: a failed move leaves the old jar working.
                    Files.move(next, finalName, StandardCopyOption.REPLACE_EXISTING);
                    if (!finalName.equals(jar)) {
                        Files.deleteIfExists(jar);
                    }
                }
            } catch (IOException | RuntimeException e) {
                // The next start keeps the old jar; the .pending one is tried again next time.
            }
        }, "Sol update"));
    }
}
