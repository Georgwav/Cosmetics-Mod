package io.github.georgwav.cosmetics.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.hud.ArmorHud;
import io.github.georgwav.cosmetics.client.hud.ClockHud;
import io.github.georgwav.cosmetics.client.hud.CoordsHud;
import io.github.georgwav.cosmetics.client.hud.EffectsHud;
import io.github.georgwav.cosmetics.client.hud.FpsHud;
import io.github.georgwav.cosmetics.client.hud.HudModule;
import io.github.georgwav.cosmetics.client.hud.Keystrokes;
import io.github.georgwav.cosmetics.client.hud.PingHud;
import io.github.georgwav.cosmetics.client.modules.Fullbright;
import io.github.georgwav.cosmetics.client.modules.TimeChanger;
import io.github.georgwav.cosmetics.client.modules.ToggleSprint;
import io.github.georgwav.cosmetics.client.modules.WeatherChanger;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

/**
 * The client's modules: made and given their keys at startup, saved to their own file, and ticked. Everything is
 * off and unbound until switched on.
 */
public final class Modules {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final Fullbright FULLBRIGHT = new Fullbright();
    public static final ToggleSprint TOGGLE_SPRINT = new ToggleSprint();
    public static final WeatherChanger WEATHER = new WeatherChanger();
    public static final TimeChanger TIME = new TimeChanger();
    public static final Keystrokes KEYSTROKES = new Keystrokes();
    public static final FpsHud FPS = new FpsHud();
    public static final PingHud PING = new PingHud();
    public static final ClockHud CLOCK = new ClockHud();
    public static final CoordsHud COORDS = new CoordsHud();
    public static final ArmorHud ARMOR = new ArmorHud();
    public static final EffectsHud EFFECTS = new EffectsHud();

    private static final List<Module> ALL = List.of(FULLBRIGHT, TOGGLE_SPRINT, WEATHER, TIME, KEYSTROKES, FPS, PING,
            CLOCK, COORDS, ARMOR, EFFECTS);
    private static final List<HudModule> HUD = List.of(KEYSTROKES, FPS, PING, CLOCK, COORDS, ARMOR, EFFECTS);

    private static boolean loading;
    private static boolean started;

    private Modules() {
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
    public static void init(Function<String, KeyMapping> registerKey) {
        for (Module m : ALL) {
            m.toggleKey = registerKey.apply("key.cosmeticsmod.module." + m.id);
        }
        load();
    }

    /** Every client tick: keys, then the modules that are on. */
    public static void tick(Minecraft mc) {
        if (!started && mc.options != null) {
            // Modules left on last time are switched on once the game is up.
            started = true;
            for (Module m : ALL) {
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

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(CosmeticsMod.MOD_ID + "-client.json");
    }

    private static void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        loading = true;
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject modules = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
            for (Module m : ALL) {
                if (!modules.has(m.id)) {
                    continue;
                }
                JsonObject saved = modules.getAsJsonObject(m.id);
                m.loadEnabled(saved.has("enabled") && saved.get("enabled").getAsBoolean());
                JsonObject values = saved.has("settings") ? saved.getAsJsonObject("settings") : new JsonObject();
                for (Setting<?> s : m.settings) {
                    JsonElement v = values.get(s.id);
                    if (v != null) {
                        s.load(v);
                    }
                }
                m.loadState(saved.has("state") ? saved.getAsJsonObject("state") : new JsonObject());
            }
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("Couldn't read {}; client modules start off", file, e);
        } finally {
            loading = false;
        }
    }

    public static void save() {
        if (loading) {
            return;
        }
        JsonObject root = new JsonObject();
        JsonObject modules = new JsonObject();
        for (Module m : ALL) {
            JsonObject saved = new JsonObject();
            saved.addProperty("enabled", m.enabled());
            JsonObject values = new JsonObject();
            for (Setting<?> s : m.settings) {
                values.add(s.id, s.save());
            }
            saved.add("settings", values);
            JsonObject state = new JsonObject();
            m.saveState(state);
            if (!state.isEmpty()) {
                saved.add("state", state);
            }
            modules.add(m.id, saved);
        }
        root.add("modules", modules);
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("Couldn't save {}", file, e);
        }
    }
}
