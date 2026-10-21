package io.github.georgwav.cosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.hud.ArmourItemsHud;
import io.github.georgwav.cosmetics.client.hud.CoordinatesHud;
import io.github.georgwav.cosmetics.client.hud.HudModule;
import io.github.georgwav.cosmetics.client.hud.Keystrokes;
import io.github.georgwav.cosmetics.client.hud.PotionEffectsHud;
import io.github.georgwav.cosmetics.client.hud.StatsHud;
import io.github.georgwav.cosmetics.client.hud.TargetHud;
import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.client.modules.BlockOutline;
import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import io.github.georgwav.cosmetics.client.modules.Crosshair;
import io.github.georgwav.cosmetics.client.modules.CrystalPvp;
import io.github.georgwav.cosmetics.client.modules.Fullbright;
import io.github.georgwav.cosmetics.client.modules.Movement;
import io.github.georgwav.cosmetics.client.modules.SolChat;
import io.github.georgwav.cosmetics.client.modules.CameraTweaks;
import io.github.georgwav.cosmetics.client.modules.PvpTweaks;
import io.github.georgwav.cosmetics.client.modules.HeldItems;
import io.github.georgwav.cosmetics.client.modules.ScreenTweaks;
import io.github.georgwav.cosmetics.client.modules.Hitboxes;
import io.github.georgwav.cosmetics.client.modules.Nametags;
import io.github.georgwav.cosmetics.client.modules.SolUiModule;
import io.github.georgwav.cosmetics.client.modules.CosmeticsModule;
import io.github.georgwav.cosmetics.client.modules.Performance;
import io.github.georgwav.cosmetics.client.modules.ParticleTweaks;
import io.github.georgwav.cosmetics.client.modules.Screenshots;
import io.github.georgwav.cosmetics.client.modules.PvpEffects;
import io.github.georgwav.cosmetics.client.modules.WorldOverlays;
import io.github.georgwav.cosmetics.client.modules.InventoryTweaks;
import io.github.georgwav.cosmetics.client.modules.Servers;
import io.github.georgwav.cosmetics.client.modules.Social;
import io.github.georgwav.cosmetics.client.modules.GlintModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * The client's modules: made and given their keys at startup, saved to their own file, and ticked. Everything is
 * off and unbound until switched on.
 */
public final class Modules {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Where the game's own HUD parts were moved, kept with the modules (and in profiles). */
    private static final String VANILLA_HUD = "vanilla_hud";

    public static final Fullbright FULLBRIGHT = new Fullbright();
    public static final Movement MOVEMENT = new Movement();
    public static final Crosshair CROSSHAIR = new Crosshair();
    public static final BlockOutline OUTLINE = new BlockOutline();
    public static final Keystrokes KEYSTROKES = new Keystrokes();
    public static final StatsHud STATS = new StatsHud();
    public static final CoordinatesHud COORDINATES = new CoordinatesHud();
    public static final ArmourItemsHud ARMOUR_ITEMS = new ArmourItemsHud();
    public static final PotionEffectsHud POTION_EFFECTS = new PotionEffectsHud();
    public static final TargetHud TARGET = new TargetHud();
    public static final SolChat SOL_CHAT = new SolChat();
    public static final CameraTweaks CAMERA = new CameraTweaks();
    public static final PvpTweaks PVP_TWEAKS = new PvpTweaks();
    public static final HeldItems HELD_ITEMS = new HeldItems();
    public static final PunchyModule PUNCHY = new PunchyModule();
    public static final ScreenTweaks SCREEN = new ScreenTweaks();
    public static final Hitboxes HITBOXES = new Hitboxes();
    public static final Nametags NAMETAGS = new Nametags();
    public static final SolUiModule SOL_UI = new SolUiModule();
    public static final CosmeticsModule COSMETICS = new CosmeticsModule();
    public static final Performance PERFORMANCE = new Performance();
    public static final ParticleTweaks PARTICLES = new ParticleTweaks();
    public static final Screenshots SCREENSHOTS = new Screenshots();
    public static final PvpEffects PVP_EFFECTS = new PvpEffects();
    public static final CrystalPvp CRYSTAL_PVP = new CrystalPvp();
    public static final WorldOverlays WORLD_OVERLAYS = new WorldOverlays();
    public static final InventoryTweaks INVENTORY = new InventoryTweaks();
    public static final Servers SERVERS = new Servers();
    public static final Social SOCIAL = new Social();
    public static final GlintModule GLINT = new GlintModule();
    public static final io.github.georgwav.cosmetics.client.hud.MusicHud MUSIC = new io.github.georgwav.cosmetics.client.hud.MusicHud();
    public static final io.github.georgwav.cosmetics.client.hud.LyricsHud LYRICS = new io.github.georgwav.cosmetics.client.hud.LyricsHud();
    public static final io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere ATMOSPHERE =
            new io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere();
    public static final io.github.georgwav.cosmetics.sol.fx.effects.KillEffects KILL_EFFECTS =
            new io.github.georgwav.cosmetics.sol.fx.effects.KillEffects();
    public static final io.github.georgwav.cosmetics.sol.fx.effects.HitEffects HIT_EFFECTS =
            new io.github.georgwav.cosmetics.sol.fx.effects.HitEffects();
    public static final io.github.georgwav.cosmetics.sol.fx.effects.Totems TOTEMS = new io.github.georgwav.cosmetics.sol.fx.effects.Totems();
    public static final io.github.georgwav.cosmetics.sol.fx.effects.Trails TRAILS = new io.github.georgwav.cosmetics.sol.fx.effects.Trails();
    public static final io.github.georgwav.cosmetics.sol.fx.effects.Target TARGET_MARK = new io.github.georgwav.cosmetics.sol.fx.effects.Target();
    public static final io.github.georgwav.cosmetics.sol.fx.effects.JumpFx JUMP_FX = new io.github.georgwav.cosmetics.sol.fx.effects.JumpFx();
    public static final io.github.georgwav.cosmetics.sol.fx.effects.You YOU = new io.github.georgwav.cosmetics.sol.fx.effects.You();
    public static final io.github.georgwav.cosmetics.client.hud.ComboHud COMBO = new io.github.georgwav.cosmetics.client.hud.ComboHud();
    public static final io.github.georgwav.cosmetics.client.hud.KillFeedHud KILL_FEED =
            new io.github.georgwav.cosmetics.client.hud.KillFeedHud();

    private static final List<Module> ALL = List.of(ATMOSPHERE, CAMERA, PVP_TWEAKS, PVP_EFFECTS, KILL_EFFECTS, HIT_EFFECTS, TOTEMS, TARGET_MARK, CRYSTAL_PVP, JUMP_FX, TRAILS, YOU, HITBOXES, HELD_ITEMS, PUNCHY,
            NAMETAGS, SCREEN, PARTICLES, GLINT, FULLBRIGHT, CROSSHAIR, OUTLINE, MOVEMENT, SOL_UI, KEYSTROKES, STATS,
            COORDINATES, ARMOUR_ITEMS, POTION_EFFECTS, TARGET, COMBO, KILL_FEED, MUSIC, LYRICS, SOL_CHAT, WORLD_OVERLAYS, INVENTORY, PERFORMANCE, SCREENSHOTS,
            SOCIAL, SERVERS, COSMETICS);
    private static final List<HudModule> HUD = List.of(KEYSTROKES, STATS, COORDINATES, ARMOUR_ITEMS, POTION_EFFECTS, TARGET, COMBO, KILL_FEED, MUSIC, LYRICS);

    private static boolean loading;
    private static boolean started;

    private Modules() {
    }

    /** Whether the saved modules are being read (switching them on then isn't something you did). */
    public static boolean loading() {
        return loading;
    }

    public static List<Module> all() {
        return ALL;
    }

    /** The modules drawn on the HUD. */
    public static List<HudModule> hud() {
        return HUD;
    }

    public static Module byId(String id) {
        for (Module m : ALL) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    /** At startup: registers each module's key (unbound, through {@code registerKey}) and reads the saved state. */
    public static void init(java.util.function.BiFunction<String, Integer, KeyMapping> registerKey) {
        for (Module m : ALL) {
            m.toggleKey = registerKey.apply("key.cosmeticsmod.module." + m.id, -1);
            for (String name : new java.util.ArrayList<>(m.keys.keySet())) {
                m.keys.put(name, registerKey.apply("key.cosmeticsmod.module." + m.id + "." + name, m.keyDefaults.getOrDefault(name, -1)));
            }
        }
        load();
    }

    /** Every client tick: keys, then the modules that are on. */
    public static void tick(Minecraft mc) {
        if (!started && mc.options != null) {
            // Modules left on last time are switched on once the game is up.
            started = true;
            for (Module m : ALL) {
                m.startup(mc);
                if (m.enabled()) {
                    m.onEnable(mc);
                }
            }
        }
        for (Module m : ALL) {
            while (m.toggleKey != null && m.toggleKey.consumeClick()) {
                m.setEnabled(!m.enabled());
            }
            if (m.enabled()) {
                m.tick(mc);
            }
        }
    }

    /** Around the game saving its options (options.txt): a module can put the player's own value back meanwhile. */
    public static void beforeOptionsSave() {
        Minecraft mc = Minecraft.getInstance();
        for (Module m : ALL) {
            if (m.enabled()) {
                m.beforeOptionsSave(mc);
            }
        }
    }

    public static void afterOptionsSave() {
        Minecraft mc = Minecraft.getInstance();
        for (Module m : ALL) {
            if (m.enabled()) {
                m.afterOptionsSave(mc);
            }
        }
    }

    /** This instance's file: which main folder (and the modules, when the main folder can't be written). */
    private static Path instanceFile() {
        return FabricLoader.getInstance().getConfigDir().resolve(CosmeticsMod.MOD_ID + "-client.json");
    }

    /** The modules' file in the main folder, which every instance shares. */
    private static Path sharedFile() {
        return MainFolder.clientFolder().resolve("modules.json");
    }

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void load() {
        Path file = instanceFile();
        loading = true;
        try {
            JsonObject root = Files.isRegularFile(file) ? read(file) : new JsonObject();
            if (root.has("mainFolder") && root.get("mainFolder").isJsonPrimitive()) {
                try {
                    MainFolder.chosen = Path.of(root.get("mainFolder").getAsString());
                } catch (InvalidPathException e) {
                    MainFolder.chosen = null;
                }
            }
            // The main folder's modules when it has them; else this instance's (moved there on the next save).
            Path shared = sharedFile();
            if (Files.isRegularFile(shared)) {
                file = shared;
                root = read(shared);
            }
            // Older files (the client branch's ids, the Time, Weather, FPS, Ping and Clock modules) read as today's.
            JsonObject modules = io.github.georgwav.cosmetics.sol.config.Migrations.migrateModules(
                    root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject());
            if (modules.has(VANILLA_HUD) && modules.get(VANILLA_HUD).isJsonObject()) {
                VanillaHud.load(modules.getAsJsonObject(VANILLA_HUD));
            }
            for (Module m : ALL) {
                if (!modules.has(m.id)) {
                    // A module new since the file was written starts as it does on a fresh install.
                    m.loadEnabled(m.onByDefault());
                }
                if (modules.has(m.id)) {
                    JsonObject saved = modules.getAsJsonObject(m.id);
                    m.loadEnabled(saved.has("enabled") && saved.get("enabled").getAsBoolean());
                    loadSettings(m, saved);
                    int seen = defaultsSeen(saved);
                    if (m.turnOnOnce() || m.onByDefault() && seen < m.onByDefaultSince()) {
                        m.loadEnabled(true);
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("Couldn't read {}; client modules start off", file, e);
        } finally {
            loading = false;
        }
    }

    /**
     * The version of the defaults settings are saved with: 2 made the theme's colour every colour's default (and
     * turned on the modules that show it). Saved settings still at an older default take the new one, once.
     */
    public static final int DEFAULTS = 2;

    /** The defaults' version a module's saved settings were written with (1 before it was recorded). */
    private static int defaultsSeen(JsonObject saved) {
        return saved.has("defaults") && saved.get("defaults").isJsonPrimitive() ? saved.get("defaults").getAsInt() : 1;
    }

    private static void loadSettings(Module m, JsonObject saved) {
        JsonObject values = saved.has("settings") ? saved.getAsJsonObject("settings") : new JsonObject();
        m.migrate(values);
        boolean older = defaultsSeen(saved) < DEFAULTS;
        for (Setting<?> s : m.settings) {
            JsonElement v = values.get(s.id);
            if (v != null) {
                s.loadSaved(v, older);
            }
        }
        m.loadState(saved.has("state") ? saved.getAsJsonObject("state") : new JsonObject());
    }

    /** Every module: on or off, its settings, and its own state (a HUD module's place). */
    public static JsonObject toJson() {
        JsonObject modules = new JsonObject();
        for (Module m : ALL) {
            JsonObject saved = new JsonObject();
            saved.addProperty("enabled", m.enabled());
            saved.addProperty("defaults", DEFAULTS);
            JsonObject values = new JsonObject();
            for (Setting<?> s : m.settings) {
                JsonElement value = s.save();
                if (value != null) {
                    values.add(s.id, value);
                }
            }
            saved.add("settings", values);
            JsonObject state = new JsonObject();
            m.saveState(state);
            if (!state.isEmpty()) {
                saved.add("state", state);
            }
            modules.add(m.id, saved);
        }
        modules.add(VANILLA_HUD, VanillaHud.save());
        return modules;
    }

    /**
     * After the main folder changed: the modules saved in it when it has them (another instance's), else this
     * instance's go into it.
     */
    public static void mainFolderChanged() {
        Path shared = sharedFile();
        try {
            if (Files.isRegularFile(shared)) {
                JsonObject root = read(shared);
                if (root.has("modules") && root.get("modules").isJsonObject()) {
                    apply(root.getAsJsonObject("modules"));
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("Couldn't read {}; keeping this instance's modules", shared, e);
        }
        save();
    }

    /**
     * Puts modules as {@link #toJson()} wrote them (a profile): their settings, then each switched on or off the way
     * a click would, so what they change in the game follows. Modules it doesn't mention stay as they are.
     */
    public static void apply(JsonObject given) {
        Minecraft mc = Minecraft.getInstance();
        JsonObject modules = io.github.georgwav.cosmetics.sol.config.Migrations.migrateModules(given);
        loading = true;
        try {
            if (modules.has(VANILLA_HUD) && modules.get(VANILLA_HUD).isJsonObject()) {
                VanillaHud.load(modules.getAsJsonObject(VANILLA_HUD));
            }
            for (Module m : ALL) {
                if (!modules.has(m.id) || !modules.get(m.id).isJsonObject()) {
                    continue;
                }
                JsonObject saved = modules.getAsJsonObject(m.id);
                loadSettings(m, saved);
                if (m.enabled()) {
                    m.onSettingsChanged(mc);
                }
                m.setEnabled(saved.has("enabled") && saved.get("enabled").getAsBoolean());
            }
        } finally {
            loading = false;
        }
        save();
    }

    /** When the next save is due (ms), or 0: changes are written once they settle, not once per slider step. */
    private static volatile long saveDue;

    /** Saves the modules a moment after the last change (see {@link #saveNow}). */
    public static void save() {
        if (loading) {
            return;
        }
        saveDue = System.currentTimeMillis() + 300L;
    }

    /** Each client tick: writes the modules when a change has settled. */
    public static void flushIfDue() {
        long due = saveDue;
        if (due != 0L && System.currentTimeMillis() >= due) {
            saveNow();
        }
    }

    /** When the game closes: anything not written yet is written now. */
    public static void flush() {
        if (saveDue != 0L) {
            saveNow();
        }
    }

    /**
     * Saves the modules into the main folder (shared by every instance; its client folder is made when missing),
     * or into this instance's file when the main folder can't be written. This instance's file always says which
     * main folder it uses. Render thread (it reads the modules).
     */
    public static void saveNow() {
        saveDue = 0L;
        if (loading) {
            return;
        }
        JsonObject modules = toJson();
        io.github.georgwav.cosmetics.sol.config.SolProfiles.changed();
        JsonObject instance = new JsonObject();
        if (MainFolder.chosen != null) {
            instance.addProperty("mainFolder", MainFolder.chosen.toString());
        }
        JsonObject root = new JsonObject();
        root.add("modules", modules);
        if (!write(sharedFile(), root)) {
            instance.add("modules", modules);
        }
        write(instanceFile(), instance);
    }

    private static boolean write(Path file, JsonObject root) {
        try {
            Files.createDirectories(file.getParent());
            // Whole or not at all: a crash mid-write never leaves half a file.
            io.github.georgwav.cosmetics.sol.util.AtomicFiles.writeString(file, GSON.toJson(root));
            return true;
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("Couldn't save {}", file, e);
            return false;
        }
    }
}
