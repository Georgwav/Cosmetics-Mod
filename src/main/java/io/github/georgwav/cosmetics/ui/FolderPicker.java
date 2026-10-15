package io.github.georgwav.cosmetics.ui;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** The system's folder picker: LWJGL's tinyfd, which the game ships, looked up by name to stay optional. */
public final class FolderPicker {
    private FolderPicker() {
    }

    /**
     * Asks for a folder, starting in {@code start}: the one picked, or null when cancelled.
     *
     * @throws UnsupportedOperationException when this system has no folder picker
     */
    public static Path pick(String title, Path start) {
        try {
            Class<?> dialogs = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Object picked = dialogs.getMethod("tinyfd_selectFolderDialog", CharSequence.class, CharSequence.class)
                    .invoke(null, title, start != null ? start.toString() : "");
            return picked instanceof String path && !path.isBlank() ? Path.of(path) : null;
        } catch (ReflectiveOperationException | InvalidPathException | LinkageError e) {
            throw new UnsupportedOperationException("No folder picker", e);
        }
    }
}
