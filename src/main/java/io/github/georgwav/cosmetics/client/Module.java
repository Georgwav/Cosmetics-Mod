package io.github.georgwav.cosmetics.client;

import com.google.gson.JsonObject;
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
    /** Icon texture name under textures/gui/hub. */
    public final String icon;
    final List<Setting<?>> settings = new ArrayList<>();
    /** Switches the module on and off; unbound until you bind it. */
    KeyMapping toggleKey;
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

    public String name() {
        return Component.translatable("cosmeticsmod.module." + id).getString();
    }

    public String note() {
        return Component.translatable("cosmeticsmod.module." + id + ".note").getString();
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

    /** Extra state kept between sessions (Fullbright: your own brightness, to put back). */
    protected void saveState(JsonObject state) {
    }

    protected void loadState(JsonObject state) {
    }

    void loadEnabled(boolean on) {
        enabled = on;
    }
}
