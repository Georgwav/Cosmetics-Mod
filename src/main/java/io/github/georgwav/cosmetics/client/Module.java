package io.github.georgwav.cosmetics.client;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A feature of the client that can be switched on and off: off by default, with no key bound by default (the key
 * that switches it is one of the game's own key binds, in Controls too), and its own settings. Names come from the
 * language file: {@code cosmeticsmod.module.<id>} and {@code cosmeticsmod.module.<id>.note}.
 */
public abstract class Module {
    public enum Category { VIEW, INVENTORY, HUD, CHAT, WORLD, TOOLS }

    public final String id;
    public final Category category;
    /** The icon name it was made with; the Sol UI draws each module's own icon by its id (ModuleIcons). */
    public final String icon;
    final List<Setting<?>> settings = new ArrayList<>();
    /** Switches the module on and off; unbound until you bind it. */
    KeyMapping toggleKey;
    /** Its other keys (unbound until bound), by name: {@code key.cosmeticsmod.module.<id>.<name>}. */
    final java.util.Map<String, KeyMapping> keys = new java.util.LinkedHashMap<>();
    private boolean enabled;

    protected Module(String id, Category category, String icon) {
        this.id = id;
        this.category = category;
        this.icon = icon;
    }

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean on) {
        if (on == enabled) {
            return;
        }
        enabled = on;
        Minecraft mc = Minecraft.getInstance();
        if (on) {
            onEnable(mc);
        } else {
            onDisable(mc);
        }
        Modules.save();
    }

    public List<Setting<?>> settings() {
        return settings;
    }

    public KeyMapping toggleKey() {
        return toggleKey;
    }

    /** Whether this Minecraft version has it yet (some need hooks that newer versions changed). */
    public boolean available() {
        return true;
    }

    public String name() {
        return Component.translatable("cosmeticsmod.module." + id).getString();
    }

    public String note() {
        return Component.translatable("cosmeticsmod.module." + id + ".note").getString();
    }

    /** A setting's name: the module's own, or the one HUD modules share ({@code cosmeticsmod.module.hud.<setting>}). */
    public String settingName(Setting<?> setting) {
        return Component.translatable(settingKey(setting.id)).getString();
    }

    /** A setting's hint (one line under its name), or "" when it has none. */
    public String settingHint(Setting<?> setting) {
        String key = settingKey(setting.id) + ".hint";
        String text = Component.translatable(key).getString();
        return text.equals(key) ? "" : text;
    }

    /** A group's name on the module's page. */
    public static String groupName(String group) {
        return Component.translatable("cosmeticsmod.group." + group).getString();
    }

    /** One option of a choice setting. */
    public String optionName(Setting<?> setting, String option) {
        return Component.translatable(settingKey(setting.id) + "." + option).getString();
    }

    private String settingKey(String settingId) {
        String own = "cosmeticsmod.module." + id + "." + settingId;
        // A missing translation comes back as its key (26.2 removed I18n.exists).
        return !Component.translatable(own).getString().equals(own) ? own : "cosmeticsmod.module.hud." + settingId;
    }

    /** Height (design pixels) of a live preview the Mods page shows above the settings, or 0 for none. */
    public float previewHeight() {
        return 0f;
    }

    /** Draws that preview into a box of screen pixels, over the page's background for it. */
    public void drawPreview(Canvas g, int x0, int y0, int x1, int y1) {
    }

    /** Its other keys' default bindings (a GLFW key code), by name; unbound when missing. */
    final java.util.Map<String, Integer> keyDefaults = new java.util.HashMap<>();

    /** Declares another key (registered at startup, unbound); read it with {@link #key(String)}. */
    protected void addKey(String name) {
        keys.put(name, null);
    }

    /** Declares another key bound to {@code defaultKey} (a GLFW key code) until the player rebinds it. */
    protected void addKey(String name, int defaultKey) {
        keys.put(name, null);
        keyDefaults.put(name, defaultKey);
    }

    /** Whether it is on in a fresh install (most modules start off). */
    public boolean onByDefault() {
        return false;
    }

    /** Its other keys' names, in the order declared. */
    public java.util.Set<String> keyNames() {
        return keys.keySet();
    }

    /** One of its other keys, or null before startup. */
    public KeyMapping key(String name) {
        return keys.get(name);
    }

    /** Adds a setting; {@code onChange} runs whenever its value changes. */
    protected <S extends Setting<?>> S add(S setting) {
        settings.add(setting);
        setting.onChange = () -> {
            if (enabled) {
                onSettingsChanged(Minecraft.getInstance());
            }
        };
        return setting;
    }

    /** Once the game is up, on or off (before {@link #onEnable} for a module left on). */
    protected void onStartup(Minecraft mc) {
    }

    /** Switched on (also at startup when it was left on). */
    protected void onEnable(Minecraft mc) {
    }

    protected void onDisable(Minecraft mc) {
    }

    /** A setting changed while the module is on. */
    protected void onSettingsChanged(Minecraft mc) {
    }

    /** Every client tick while on. */
    protected void tick(Minecraft mc) {
    }

    /** Whether the game's own options would be saved with a value this module changed (see Fullbright). */
    protected void beforeOptionsSave(Minecraft mc) {
    }

    protected void afterOptionsSave(Minecraft mc) {
    }

    /**
     * Moves values saved by an older version to where they are now, before the settings read them (a colour name
     * that meant "no flash" becomes the flash switched off).
     */
    protected void migrate(JsonObject settings) {
    }

    /** Extra state kept between sessions (Fullbright: your own brightness, to put back). */
    protected void saveState(JsonObject state) {
    }

    protected void loadState(JsonObject state) {
    }

    void startup(Minecraft mc) {
        onStartup(mc);
    }

    void loadEnabled(boolean on) {
        enabled = on;
    }
}
