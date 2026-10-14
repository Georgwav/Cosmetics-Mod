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
 * The client's main folder: the launcher's app folder (Threadrinth, or the Modrinth App it is built on), which holds
 * its database (saved skins) and its instances under {@code profiles/}; the client keeps its own things in
 * {@code client/} there. Set once in the client's settings, or found: the game running from
 * {@code <app folder>/profiles/<instance>}, or the launcher's settings folder (which points to a moved app folder).
 */
public final class MainFolder {
    /** The folder chosen in the settings, or null to find it. */
    static Path chosen;
    private static Path found;
    private static boolean searched;

    private MainFolder() {
    }

    /** The launcher's app folder, or null when there is none. */
    public static Path appFolder() {
        if (chosen != null) {
            return chosen;
        }
        if (!searched) {
            searched = true;
            found = find(FabricLoader.getInstance().getGameDir());
        }
        return found;
    }

    /** Where the client keeps its own things: {@code client/} in the app folder, else in this instance. */
    public static Path clientFolder() {
        Path app = appFolder();
        return app != null ? app.resolve("client") : FabricLoader.getInstance().getGameDir().resolve("cosmeticsmod");
    }

    /** The launcher's database, or null. */
    public static Path database() {
        Path app = appFolder();
        Path db = app == null ? null : app.resolve("app.db");
        return db != null && Files.isRegularFile(db) ? db : null;
    }

    public static Path chosen() {
        return chosen;
    }

    /** Sets the folder (null: find it again) and saves the choice. */
    public static void choose(Path folder) {
        chosen = folder;
        searched = false;
        Modules.save();
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
