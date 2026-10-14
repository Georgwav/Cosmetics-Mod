package io.github.georgwav.cosmetics.sol.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Writes files so a crash or power cut never leaves half a file: the text goes to a temporary file next to it,
 * which then replaces the old one in one move.
 */
public final class AtomicFiles {
    private AtomicFiles() {
    }

    public static void writeString(Path file, String text) throws IOException {
        write(file, text.getBytes(StandardCharsets.UTF_8));
    }

    public static void write(Path file, byte[] bytes) throws IOException {
        Path dir = file.toAbsolutePath().getParent();
        if (dir != null) {
            Files.createDirectories(dir);
        }
        Path temp = Files.createTempFile(dir, file.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, bytes);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
