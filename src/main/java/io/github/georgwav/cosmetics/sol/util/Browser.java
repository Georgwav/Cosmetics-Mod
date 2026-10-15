package io.github.georgwav.cosmetics.sol.util;

import java.io.IOException;
import java.util.Locale;

/** Opens an address in the player's browser (as the game does: the system's own opener). */
public final class Browser {
    private Browser() {
    }

    /** True if the opener started. */
    public static boolean open(String url) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String[] cmd = os.contains("win") ? new String[]{"rundll32", "url.dll,FileProtocolHandler", url}
                : os.contains("mac") ? new String[]{"open", url} : new String[]{"xdg-open", url};
        try {
            new ProcessBuilder(cmd).start();
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
