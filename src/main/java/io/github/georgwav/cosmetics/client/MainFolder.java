package io.github.georgwav.cosmetics.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client's main folder. By default the game's own {@code .minecraft} folder (where the Minecraft Launcher keeps
 * it); it can be set to another one, such as Threadrinth's app folder (or the Modrinth App's it is built on). Either
 * way it is laid out the way Threadrinth lays out its folder: the client keeps its own things in {@code client/}
 * (created when first needed), and a Threadrinth folder's database ({@code app.db}) has the skins saved in the
 * launcher, which the Skins page shows.
 */
public final class MainFolder {
    /** The folder chosen in the settings, or null for {@code .minecraft}. */
    static Path chosen;
    private static Path launcher;
    private static boolean searched;

    private MainFolder() {
    }

    /** The main folder: the one chosen, else {@code .minecraft}. */
    public static Path folder() {
        if (chosen != null) {
            return chosen;
        }
        Path minecraft = minecraftFolder();
        return minecraft != null ? minecraft : FabricLoader.getInstance().getGameDir();
    }

    /** Whether it is the default ({@code .minecraft}), not one chosen. */
    public static boolean isDefault() {
        return chosen == null;
    }

    /** Where the client keeps its own things: {@code client/} in the main folder. */
    public static Path clientFolder() {
        return folder().resolve("client");
    }

    /** The main folder's Threadrinth database (saved skins), or null when it has none. */
    public static Path database() {
        Path db = folder().resolve("app.db");
        return Files.isRegularFile(db) ? db : null;
    }

    /** The folder chosen, or null for the default. */
    public static Path chosen() {
        return chosen;
    }

    /**
     * Sets the folder (null: back to {@code .minecraft}) and saves the choice; the modules saved in that folder
     * (by another instance) are taken over, else this instance's go into it.
     */
    public static void choose(Path folder) {
        chosen = folder;
        Modules.mainFolderChanged();
    }

    /**
     * Threadrinth's (or the Modrinth App's) app folder on this computer, or null: the one this game was started
     * from, or the one in the launcher's settings folder. Offered as a main folder, never used without asking.
     */
    public static Path launcherFolder() {
        if (!searched) {
            searched = true;
            launcher = find(FabricLoader.getInstance().getGameDir());
        }
        return launcher;
    }

    /**
     * {@code .minecraft}, where the Minecraft Launcher keeps it: {@code %APPDATA%\.minecraft} on Windows,
     * {@code ~/Library/Application Support/minecraft} on macOS, {@code ~/.minecraft} elsewhere.
     */
    public static Path minecraftFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        try {
            if (os.contains("win")) {
                String appData = System.getenv("APPDATA");
                return appData != null ? Path.of(appData, ".minecraft") : home != null ? Path.of(home, ".minecraft") : null;
            }
            if (os.contains("mac")) {
                return home != null ? Path.of(home, "Library", "Application Support", "minecraft") : null;
            }
            return home != null ? Path.of(home, ".minecraft") : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    static Path find(Path gameDir) {
        // Started by the launcher: <app folder>/profiles/<instance>.
        Path instances = gameDir.toAbsolutePath().normalize().getParent();
        if (instances != null && instances.getFileName() != null
                && instances.getFileName().toString().equals("profiles") && isAppFolder(instances.getParent())) {
            return instances.getParent();
        }
        for (Path settings : settingsFolders()) {
            // A moved app folder is written down in the settings folder.
            Path pointer = settings.resolve("app-folder.txt");
            try {
                if (Files.isRegularFile(pointer)) {
                    Path moved = Path.of(Files.readString(pointer, StandardCharsets.UTF_8).trim());
                    if (isAppFolder(moved)) {
                        return moved;
                    }
                }
            } catch (IOException | InvalidPathException ignored) {
                // An unreadable pointer: look on.
            }
            if (isAppFolder(settings)) {
                return settings;
            }
        }
        return null;
    }

    private static boolean isAppFolder(Path folder) {
        return folder != null && Files.isRegularFile(folder.resolve("app.db"));
    }

    /** The launchers' settings folders on this computer: Threadrinth's first, then the Modrinth App's. */
    private static List<Path> settingsFolders() {
        List<Path> folders = new ArrayList<>();
        String override = System.getenv("THESEUS_CONFIG_DIR");
        if (override != null && !override.isBlank()) {
            try {
                folders.add(Path.of(override));
            } catch (InvalidPathException ignored) {
                // Not a path: skip it.
            }
        }
        Path data = dataFolder();
        if (data != null) {
            folders.add(data.resolve("Threadrinth"));
            folders.add(data.resolve("ModrinthApp"));
        }
        return folders;
    }

    /** The operating system's folder for applications' data. */
    private static Path dataFolder() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        try {
            if (os.contains("win")) {
                String appData = System.getenv("APPDATA");
                return appData != null ? Path.of(appData) : null;
            }
            if (os.contains("mac")) {
                return home != null ? Path.of(home, "Library", "Application Support") : null;
            }
            String xdg = System.getenv("XDG_DATA_HOME");
            if (xdg != null && !xdg.isBlank()) {
                return Path.of(xdg);
            }
            return home != null ? Path.of(home, ".local", "share") : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
