package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Screenshots: each screenshot copied to the clipboard as an image as it's saved (through the system's own tools:
 * PowerShell on Windows, osascript on macOS, wl-copy or xclip on Linux), with a toast. The gallery of your
 * screenshots is a page of its own.
 */
public final class Screenshots extends Module {
    private final Setting.Toggle copy = add(new Setting.Toggle("copy", true));

    public Screenshots() {
        super("screenshots", Category.TOOLS, "camera");
    }

    /** The game saved a screenshot: copies the newest one in the screenshots folder (off the render thread). */
    public static void saved() {
        Screenshots s = Modules.SCREENSHOTS;
        if (!s.enabled() || !s.copy.get()) {
            return;
        }
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
        Thread t = new Thread(() -> {
            Path newest = newest(dir);
            if (newest != null && copy(newest.toFile())) {
                Minecraft.getInstance().execute(() -> Notifications.post(
                        Component.translatable("sol.notify.screenshot_copied").getString()));
            }
        }, "Sol screenshot copy");
        t.setDaemon(true);
        t.start();
    }

    private static Path newest(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(p -> p.toString().endsWith(".png"))
                    .max((a, b) -> Long.compare(a.toFile().lastModified(), b.toFile().lastModified())).orElse(null);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Puts a PNG on the clipboard with the system's tool; false if none worked. */
    public static boolean copy(File png) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String path = png.getAbsolutePath();
        List<List<String>> tries;
        if (os.contains("win")) {
            tries = List.of(List.of("powershell", "-NoProfile", "-STA", "-Command",
                    "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; [System.Windows.Forms.Clipboard]::SetImage("
                            + "[System.Drawing.Image]::FromFile('" + path.replace("'", "''") + "'))"));
        } else if (os.contains("mac")) {
            tries = List.of(List.of("osascript", "-e", "set the clipboard to (read (POSIX file \"" + path.replace("\"", "\\\"")
                    + "\") as «class PNGf»)"));
        } else {
            tries = List.of(List.of("wl-copy", "--type", "image/png"), List.of("xclip", "-selection", "clipboard", "-t", "image/png", "-i", path));
        }
        for (List<String> cmd : tries) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
                if (cmd.get(0).equals("wl-copy")) {
                    pb.redirectInput(png);
                }
                Process p = pb.start();
                if (p.waitFor() == 0 || cmd.get(0).equals("wl-copy") && p.isAlive()) {
                    return true;
                }
            } catch (IOException e) {
                // That tool isn't installed; try the next.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        CosmeticsMod.LOGGER.info("[Sol] No clipboard tool for images found (tried {})", tries);
        return false;
    }
}
