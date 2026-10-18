package io.github.georgwav.cosmetics.sol.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.music.Http;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The mod browser's Modrinth: search Fabric mods or resource packs for this Minecraft version, and install one (its
 * newest compatible file, checked against Modrinth's SHA-1; a mod with the mods it requires) into this instance's
 * folder: the game's own folder, so the launcher's instance (Threadrinth's, or .minecraft). Mods load at the next
 * start; packs show in the pack screen at once. Parsing is pure and unit tested; the calls run on IO threads.
 */
public final class Modrinth {
    /** What to look for: Fabric mods (into mods) or resource packs (into resourcepacks). */
    public enum Kind {
        MOD("mod", "mods", "fabric"), PACK("resourcepack", "resourcepacks", "minecraft");

        final String projectType;
        public final String folder;
        final String loader;

        Kind(String projectType, String folder, String loader) {
            this.projectType = projectType;
            this.folder = folder;
            this.loader = loader;
        }
    }

    static final String API = System.getProperty("cosmeticsmod.modrinth", "https://api.modrinth.com/v2");

    /** A search hit. */
    public record Hit(String id, String slug, String title, String author, String description, String icon, long downloads) {
    }

    /** A version's file to download, and the projects it needs. */
    public record Download(String url, String fileName, String sha1, List<String> requires) {
    }

    private Modrinth() {
    }

    private static Map<String, String> headers() {
        String v = FabricLoader.getInstance().getModContainer("cosmeticsmod").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("0");
        return Map.of("User-Agent", "georgwav/cosmetics-mod/" + v + " (Sol Client)");
    }

    static String minecraft() {
        return FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("");
    }

    /** The search address: Fabric mods (or resource packs) for {@code mc}; empty query lists the most downloaded. */
    static String searchUrl(String query, String mc, Kind kind) {
        String facets = (kind == Kind.MOD ? "[[\"categories:fabric\"]," : "[") + "[\"versions:" + mc + "\"],[\"project_type:"
                + kind.projectType + "\"]]";
        return API + "/search?limit=24&index=" + (query.isBlank() ? "downloads" : "relevance") + "&query="
                + URLEncoder.encode(query.strip(), StandardCharsets.UTF_8) + "&facets=" + URLEncoder.encode(facets, StandardCharsets.UTF_8);
    }

    static List<Hit> hits(String json) {
        List<Hit> out = new ArrayList<>();
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (!o.has("hits")) {
            return out;
        }
        for (JsonElement e : o.getAsJsonArray("hits")) {
            JsonObject h = e.getAsJsonObject();
            out.add(new Hit(str(h, "project_id"), str(h, "slug"), str(h, "title"), str(h, "author"), str(h, "description"),
                    str(h, "icon_url"), h.has("downloads") ? h.get("downloads").getAsLong() : 0L));
        }
        return out;
    }

    /** The first (newest) version in a {@code /project/{id}/version} reply: its primary file and required projects. */
    static Download pick(String json) {
        JsonArray versions = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement v : versions) {
            JsonObject version = v.getAsJsonObject();
            JsonObject file = null;
            for (JsonElement f : version.getAsJsonArray("files")) {
                JsonObject fo = f.getAsJsonObject();
                if (file == null || fo.has("primary") && fo.get("primary").getAsBoolean()) {
                    file = fo;
                }
            }
            if (file == null) {
                continue;
            }
            List<String> requires = new ArrayList<>();
            if (version.has("dependencies")) {
                for (JsonElement d : version.getAsJsonArray("dependencies")) {
                    JsonObject dep = d.getAsJsonObject();
                    if ("required".equals(str(dep, "dependency_type")) && str(dep, "project_id") != null) {
                        requires.add(str(dep, "project_id"));
                    }
                }
            }
            String sha1 = file.has("hashes") ? str(file.getAsJsonObject("hashes"), "sha1") : null;
            return new Download(str(file, "url"), str(file, "filename"), sha1, requires);
        }
        return null;
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    /** Searches (IO thread); null when Modrinth can't be reached. */
    public static List<Hit> search(String query, Kind kind) {
        Http.Reply r = Http.get(searchUrl(query, minecraft(), kind), headers());
        try {
            return r.ok() ? hits(r.body()) : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Whether a project looks installed (its slug is a loaded mod's id, or its file is in the mods folder). */
    public static boolean installed(Hit hit, Set<String> downloaded) {
        return downloaded.contains(hit.id()) || hit.slug() != null && FabricLoader.getInstance().isModLoaded(hit.slug().replace('-', '_'))
                || hit.slug() != null && FabricLoader.getInstance().isModLoaded(hit.slug());
    }

    /** This instance's folder (where its mods and resourcepacks folders are). */
    public static Path instance() {
        return FabricLoader.getInstance().getGameDir();
    }

    /**
     * Installs a project (IO thread) into this instance's mods or resourcepacks folder, a mod with the mods it
     * requires; returns the files written, or throws with a reason. Mods already loaded are skipped.
     */
    public static List<String> install(String projectId, Kind kind, Set<String> seen) throws IOException, InterruptedException {
        List<String> written = new ArrayList<>();
        if (!seen.add(projectId)) {
            return written;
        }
        String mc = minecraft();
        String url = API + "/project/" + projectId + "/version?loaders=" + URLEncoder.encode("[\"" + kind.loader + "\"]", StandardCharsets.UTF_8)
                + "&game_versions=" + URLEncoder.encode("[\"" + mc + "\"]", StandardCharsets.UTF_8);
        Http.Reply r = Http.get(url, headers());
        if (!r.ok()) {
            throw new IOException("no version for " + mc);
        }
        Download d = pick(r.body());
        if (d == null || d.url() == null || d.fileName() == null || d.fileName().contains("/") || d.fileName().contains("\\")) {
            throw new IOException("no file");
        }
        Path mods = instance().resolve(kind.folder);
        Files.createDirectories(mods);
        Path target = mods.resolve(d.fileName());
        if (!Files.exists(target)) {
            Path part = mods.resolve(d.fileName() + ".part");
            HttpResponse<InputStream> resp = Http.CLIENT.send(HttpRequest.newBuilder(URI.create(d.url())).timeout(Duration.ofSeconds(60))
                    .header("User-Agent", headers().get("User-Agent")).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() / 100 != 2) {
                throw new IOException("download status " + resp.statusCode());
            }
            try (InputStream in = resp.body()) {
                Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
            }
            if (d.sha1() != null && !d.sha1().equalsIgnoreCase(sha1(part))) {
                Files.deleteIfExists(part);
                throw new IOException("checksum");
            }
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
            written.add(d.fileName());
        }
        for (String dep : kind == Kind.MOD ? d.requires() : List.<String>of()) {
            Http.Reply pr = Http.get(API + "/project/" + dep, headers());
            String slug = pr.ok() ? str(JsonParser.parseString(pr.body()).getAsJsonObject(), "slug") : null;
            if (slug != null && (FabricLoader.getInstance().isModLoaded(slug) || FabricLoader.getInstance().isModLoaded(slug.replace('-', '_'))
                    || slug.equals("fabric-api") && FabricLoader.getInstance().isModLoaded("fabric-api"))) {
                continue;
            }
            written.addAll(install(dep, kind, seen));
        }
        return written;
    }

    static String sha1(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
