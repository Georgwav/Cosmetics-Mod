package io.github.georgwav.cosmetics.sol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.sol.util.AtomicFiles;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sol's client-wide settings ({@code <main folder>/client/sol.json}): the ones that don't belong to a profile.
 * Written atomically; a file from a newer Sol keeps its unknown fields only in memory (they are not lost on read,
 * but are rewritten in this schema). Never throws on a bad file: bad values fall back to defaults.
 */
public final class SolConfig {
    public static final int SCHEMA = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public int schema = SCHEMA;
    public boolean firstLaunchDone;
    public String activeProfile = "Deep Field";
    public boolean solUi = true;
    /** "full" or "calm" (reduced motion). */
    public String motion = "full";
    public boolean menuBlur = true;
    public boolean searchNeedsCtrl = true;
    public Music music = new Music();
    /** Who does a job a kept mod also does: mod id → "sol" or "mod". */
    public Map<String, String> overlaps = new LinkedHashMap<>();
    public Updates updates = new Updates();
    public String discordAppId = "";
    /** Set while Atmosphere has paused the shader pack, so it is put back even after a crash. */
    public boolean irisPausedByAtmosphere;
    /** The parts of the game's HUD switched back from Sol UI's look to the game's own, by id (SolHud.Part). */
    public java.util.List<String> solUiOff = new java.util.ArrayList<>();
    /** Resource packs in subfolders of resourcepacks show up too. */
    public boolean packFolders = true;
    /** The launcher skin (its texture key) and the Mojang cape id (or "none") Sol last put on an account, and its UUID. */
    public String wornSkin;
    public String wornCape;
    public String wornAccount;

    public static final class Music {
        public String ciderHost = "127.0.0.1";
        public int ciderPort = 10767;
        public String spotifyClientId = "";
        public boolean visualizers = true;
        /** The UI takes the album cover's colours while a song plays. */
        public boolean albumUi = true;
        /** The world's sky takes the album's colours too (the Album theme). */
        public boolean albumSky = false;
        /** The current lyric line in the HUD. */
        public boolean lyricsHud = true;
        /** Per-song lyric timing nudges in ms, by song key. */
        public Map<String, Integer> lyricsOffsetMs = new LinkedHashMap<>();
    }

    public static final class Updates {
        public String repo = "georgwav/cosmetics-mod";
        public boolean check = true;
        /** When updates were last looked for (ms); once a day. */
        public long lastCheck;
        /** A downloaded update's version and notes, shown once it has taken over (then cleared). */
        public String notesVersion;
        public String notes;
    }

    private static SolConfig current;
    private static Path override;

    private SolConfig() {
    }

    public static synchronized SolConfig get() {
        if (current == null) {
            current = load(file());
        }
        return current;
    }

    /** Tests point the config at their own file. */
    public static synchronized void useFile(Path file) {
        override = file;
        current = null;
    }

    public static Path file() {
        return override != null ? override : MainFolder.clientFolder().resolve("sol.json");
    }

    public static boolean exists() {
        return Files.isRegularFile(file());
    }

    static SolConfig load(Path file) {
        if (!Files.isRegularFile(file)) {
            return new SolConfig();
        }
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            SolConfig cfg = GSON.fromJson(json, SolConfig.class);
            return cfg == null ? new SolConfig() : cfg.sanitize();
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't read {}, using defaults", file, e);
            return new SolConfig();
        }
    }

    SolConfig sanitize() {
        schema = SCHEMA;
        if (activeProfile == null || activeProfile.isBlank()) activeProfile = "Deep Field";
        if (!"calm".equals(motion)) motion = "full";
        if (music == null) music = new Music();
        if (music.ciderHost == null || music.ciderHost.isBlank()) music.ciderHost = "127.0.0.1";
        if (music.ciderPort <= 0 || music.ciderPort > 65535) music.ciderPort = 10767;
        if (music.spotifyClientId == null) music.spotifyClientId = "";
        if (music.lyricsOffsetMs == null) music.lyricsOffsetMs = new LinkedHashMap<>();
        if (overlaps == null) overlaps = new LinkedHashMap<>();
        if (updates == null) updates = new Updates();
        if (updates.repo == null || updates.repo.isBlank()) updates.repo = "georgwav/cosmetics-mod";
        if (discordAppId == null) discordAppId = "";
        if (solUiOff == null) solUiOff = new java.util.ArrayList<>();
        return this;
    }

    public static synchronized void save() {
        SolConfig cfg = get();
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            AtomicFiles.writeString(file, GSON.toJson(cfg));
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't save {}", file, e);
        }
    }

    public boolean calmMotion() {
        return "calm".equals(motion);
    }
}
