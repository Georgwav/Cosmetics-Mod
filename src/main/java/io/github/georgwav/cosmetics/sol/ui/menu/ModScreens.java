package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Other mods' own screens, opened from the Mods section (back from them comes back to the menu):
 * - a mod's settings, as Mod Menu's "Configure" opens them;
 * - the game's Key Binds screen (Controlling adds its search to it);
 * - Screenshot Viewer's screen;
 * - Mod Menu's list of mods.
 * All by reflection: none of these mods is needed to build or run.
 */
final class ModScreens {
    private ModScreens() {
    }

    /** Whether Mod Menu has a settings screen for each mod asked about (asked once; the menu asks every frame). */
    private static final java.util.Map<String, Boolean> HAS_SETTINGS = new java.util.HashMap<>();

    /** Whether Mod Menu can open a settings screen for the mod (it has the call since 11.0.3). */
    static boolean hasSettings(String modId) {
        if (!Compat.MOD_MENU) {
            return false;
        }
        return HAS_SETTINGS.computeIfAbsent(modId, id -> {
            try {
                return (boolean) Class.forName("com.terraformersmc.modmenu.ModMenu")
                        .getMethod("hasConfigScreen", String.class).invoke(null, id);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                return true;
            }
        });
    }

    /** A mod's own settings screen; false when there is none to open. */
    static boolean openSettings(Screen parent, String modId) {
        try {
            Object screen = Class.forName("com.terraformersmc.modmenu.ModMenu")
                    .getMethod("getConfigScreen", String.class, Screen.class).invoke(null, modId, parent);
            if (screen instanceof Screen s) {
                ScreenOpener.open(s);
                return true;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("Couldn't open the settings of {}", modId, e);
        }
        return false;
    }

    static boolean openKeyBinds(Screen parent) {
        return open("net.minecraft.client.gui.screens.options.controls.KeyBindsScreen", parent, true);
    }

    static boolean openScreenshots(Screen parent) {
        return open("io.github.lgatodu47.screenshot_viewer.screen.manage_screenshots.ManageScreenshotsScreen", parent, false);
    }

    static boolean openModList(Screen parent) {
        return open("com.terraformersmc.modmenu.gui.ModsScreen", parent, false);
    }

    /** A screen made from its parent (and the game's options, for the key binds). */
    private static boolean open(String className, Screen parent, boolean withOptions) {
        try {
            Class<?> type = Class.forName(className);
            Screen screen = withOptions
                    ? (Screen) type.getConstructor(Screen.class, net.minecraft.client.Options.class).newInstance(parent, Minecraft.getInstance().options)
                    : (Screen) type.getConstructor(Screen.class).newInstance(parent);
            ScreenOpener.open(screen);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("Couldn't open {}", className, e);
            return false;
        }
    }
}
