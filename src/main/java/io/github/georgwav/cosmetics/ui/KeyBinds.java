package io.github.georgwav.cosmetics.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}

/**
 * The mod's key binds. They are the game's own key mappings (Options → Controls → Key Binds), which the settings
 * screen edits too, so changing a key in either place changes it in both: it is one setting, saved in the game's
 * options.
 */
public final class KeyBinds {
    /** "Open Cosmetics": the Wardrobe (unbound by default, as on main). */
    public static KeyMapping open;
    /** The Sol quick menu (Right Shift). */
    public static KeyMapping quick;
    /** Opens the emote wheel (held: release to play). */
    public static KeyMapping emotes;

    private KeyBinds() {
    }

    /** Binds {@code mapping} to {@code key} ({@link InputConstants#UNKNOWN} for none), as the Controls screen does. */
    public static void bind(KeyMapping mapping, InputConstants.Key key) {
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

    // A keyboard key pressed: by its key code, or by its scan code when it has none (as the game reads it).
    // 26.3 keeps only key codes (one KEYBOARD type) and reads a pressed key itself.
    //? if >=26.3 {
    /*public static InputConstants.Key keyboard(KeyEvent event) {
        return InputConstants.getKey(event);
    }

    public static InputConstants.Key keyboard(int keyCode, int scanCode) {
        return InputConstants.Type.KEYBOARD.getOrCreate(keyCode);
    }
    *///?} elif >=1.21.9 {
    public static InputConstants.Key keyboard(KeyEvent event) {
        return keyboard(event.key(), event.scancode());
    }

    public static InputConstants.Key keyboard(int keyCode, int scanCode) {
        return keyCode == InputConstants.UNKNOWN.getValue()
                ? InputConstants.Type.SCANCODE.getOrCreate(scanCode)
                : InputConstants.Type.KEYSYM.getOrCreate(keyCode);
    }
    //?} else {
    /*public static InputConstants.Key keyboard(int keyCode, int scanCode) {
        return keyCode == InputConstants.UNKNOWN.getValue()
                ? InputConstants.Type.SCANCODE.getOrCreate(scanCode)
                : InputConstants.Type.KEYSYM.getOrCreate(keyCode);
    }
    *///?}

    public static InputConstants.Key mouse(int button) {
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

    public static KeyMapping quickMenu() {
        return quick;
    }

    /** Whether a key code pressed in a screen is this mapping's key. */
    public static boolean matches(KeyMapping mapping, int key) {
        if (mapping == null || mapping.isUnbound()) {
            return false;
        }
        //? if >=1.21.9 {
        return mapping.matches(new KeyEvent(key, 0, 0));
        //?} else {
        /*return mapping.matches(key, 0);
        *///?}
    }

    /**
     * The client branch had the hub on Right Shift under "Open Cosmetics". Sol's quick menu has its own key on Right
     * Shift and "Open Cosmetics" is unbound by default again: if the old key still sits on Right Shift, free it once,
     * so Right Shift opens the quick menu.
     */
    public static void migrateQuickKey() {
        if (open == null || quick == null) {
            return;
        }
        if (open.same(quick) && !quick.isUnbound()) {
            bind(open, InputConstants.UNKNOWN);
        }
    }

    /** The key's name as the game shows it ("B", "Mouse Button 4", "Not Bound"). */
    public static String name(KeyMapping mapping) {
        return mapping == null ? "" : mapping.getTranslatedKeyMessage().getString();
    }
}
