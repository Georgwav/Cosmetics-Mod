package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.CosmeticsMod;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sol's 12 x 12 pixel icons (sol/pixel_icons.txt): the pixel UI prototype's set and the rest drawn on the same grid.
 * Each is a bitmap in a 12 x 12 cell, a smaller one centred (rounded down). Pure data: {@link Icons} draws them, and
 * the tests read them here.
 */
public final class PixelIcons {
    public static final int CELL = 12;

    /** The names the line icons had before, and the pixel icon each now is. */
    private static final Map<String, String> ALIASES = Map.of("grid4", "grid", "wings", "wing", "puzzle", "box", "close", "x",
            "camera", "cam", "key", "keys", "hand", "fist");

    private static Map<String, boolean[]> icons;

    private PixelIcons() {
    }

    /** Every icon by name: 144 cells, row by row. */
    public static synchronized Map<String, boolean[]> all() {
        if (icons == null) {
            try (InputStream in = PixelIcons.class.getResourceAsStream("/assets/cosmeticsmod/sol/pixel_icons.txt")) {
                icons = in == null ? Map.of() : parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException e) {
                CosmeticsMod.LOGGER.warn("[Sol] Couldn't read the pixel icons", e);
                icons = Map.of();
            }
        }
        return icons;
    }

    /** Reads the icon file's text: "name row row ...", "#" comments. */
    public static Map<String, boolean[]> parse(String text) {
        Map<String, boolean[]> out = new LinkedHashMap<>();
        for (String line : text.split("\n")) {
            line = line.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            int rows = parts.length - 1, cols = 0;
            for (int i = 1; i < parts.length; i++) {
                cols = Math.max(cols, parts[i].length());
            }
            if (rows < 1 || rows > CELL || cols > CELL) {
                throw new IllegalArgumentException("icon " + parts[0] + " is " + cols + " x " + rows);
            }
            boolean[] cells = new boolean[CELL * CELL];
            int ox = (CELL - cols) / 2, oy = (CELL - rows) / 2;
            for (int r = 0; r < rows; r++) {
                String row = parts[r + 1];
                for (int c = 0; c < row.length(); c++) {
                    if (row.charAt(c) == '#') {
                        cells[(oy + r) * CELL + ox + c] = true;
                    }
                }
            }
            out.put(parts[0], cells);
        }
        return Collections.unmodifiableMap(out);
    }

    /** The icon's own name for a name a screen asks for (an older line icon's name works too), or null. */
    public static String resolve(String name) {
        if (name == null) {
            return null;
        }
        Map<String, boolean[]> all = all();
        if (all.containsKey(name)) {
            return name;
        }
        String alias = ALIASES.get(name);
        return alias != null && all.containsKey(alias) ? alias : null;
    }

    public static boolean has(String name) {
        return resolve(name) != null;
    }
}
