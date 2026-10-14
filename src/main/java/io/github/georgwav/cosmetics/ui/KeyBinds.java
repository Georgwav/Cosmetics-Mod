package io.github.georgwav.cosmetics.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * The mod's key binds. They are the game's own key mappings (Options → Controls → Key Binds), which the settings
 * screen edits too, so changing a key in either place changes it in both: it is one setting, saved in the game's
 * options.
 */
public final class KeyBinds {
    /** Opens the settings screen. */
    public static KeyMapping open;
    /** Opens the emote wheel (held: release to play). */
    public static KeyMapping emotes;

    private KeyBinds() {
    }

    /** Binds {@code mapping} to {@code key} ({@link InputConstants#UNKNOWN} for none), as the Controls screen does. */
    static void bind(KeyMapping mapping, InputConstants.Key key) {
        mapping.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }

    /** Whether another key bind (the game's or any mod's) uses the same key. */
    static boolean conflicts(KeyMapping mapping) {
        if (mapping.isUnbound()) {
            return false;
        }
        for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
            if (other != mapping && mapping.same(other)) {
                return true;
            }
        }
        return false;
    }

    /** A keyboard key, by its key code, or by its scan code when it has none (as the game reads it). */
    static InputConstants.Key keyboard(int keyCode, int scanCode) {
        return keyCode == InputConstants.UNKNOWN.getValue()
                ? InputConstants.Type.SCANCODE.getOrCreate(scanCode)
                : InputConstants.Type.KEYSYM.getOrCreate(keyCode);
    }

    static InputConstants.Key mouse(int button) {
        return InputConstants.Type.MOUSE.getOrCreate(button);
    }

    /**
     * For the in-game test: binds the emote wheel to G as the settings screen does, checks that the game's own key
     * bind (the one in Controls) changed with it, then puts the default back. Returns what went wrong, if anything.
     */
    public static java.util.List<String> selfTest() {
        java.util.List<String> problems = new java.util.ArrayList<>();
        KeyMapping mapping = emotes;
        if (mapping == null) {
            problems.add("the emote wheel key isn't registered");
            return problems;
        }
        if (!java.util.Arrays.asList(Minecraft.getInstance().options.keyMappings).contains(mapping)) {
            problems.add("the emote wheel key isn't one of the game's key binds (Controls)");
        }
        bind(mapping, keyboard(InputConstants.KEY_G, 0));
        if (!mapping.saveString().equals("key.keyboard.g")) {
            problems.add("binding the emote wheel to G gave " + mapping.saveString());
        }
        bind(mapping, mapping.getDefaultKey());
        if (!mapping.isDefault()) {
            problems.add("the emote wheel key didn't go back to its default");
        }
        return problems;
    }

    /** The key's name as the game shows it ("B", "Mouse Button 4", "Not Bound"). */
    public static String name(KeyMapping mapping) {
        return mapping == null ? "" : mapping.getTranslatedKeyMessage().getString();
    }
}
