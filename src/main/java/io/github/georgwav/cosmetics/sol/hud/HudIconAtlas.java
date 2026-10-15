package io.github.georgwav.cosmetics.sol.hud;

/**
 * The Sol HUD's pixel icons, baked into one 64 x 64 picture of 9 x 9 cells (the game's HUD sprites are 9 x 9 too):
 * the prototype's heart, shank and armour point, plus an air bubble and a mount heart, in every state the game's HUD
 * shows. Pure Java (an ARGB array), so it is unit-tested; {@link HudIcons} uploads it.
 *
 * Colours follow the brief: hearts in the theme's accent, shanks in its glow, armour light silver. The kinds of
 * heart that mean something keep their own colours (poison, wither, frozen, absorption), and so does hunger's green.
 * Every icon has a dark outline; an empty part shows a faint fill.
 */
public final class HudIconAtlas {
    public static final int SIZE = 64;
    public static final int CELL = 9;

    /** The cells, in order; each is at ({@code ordinal % 7 * 9}, {@code ordinal / 7 * 9}). */
    public enum Icon {
        HEART_CONTAINER, HEART_CONTAINER_BLINK,
        HEART_FULL, HEART_HALF, POISON_FULL, POISON_HALF, WITHER_FULL, WITHER_HALF, FROZEN_FULL, FROZEN_HALF,
        ABSORB_FULL, ABSORB_HALF, BLINK_FULL, BLINK_HALF, HARDCORE_MARK,
        FOOD_EMPTY, FOOD_FULL, FOOD_HALF, HUNGER_EMPTY, HUNGER_FULL, HUNGER_HALF, SATURATION_RIM,
        ARMOR_EMPTY, ARMOR_FULL, ARMOR_HALF,
        AIR_FULL, AIR_POPPING, AIR_EMPTY,
        MOUNT_CONTAINER, MOUNT_FULL, MOUNT_HALF;

        public int u() {
            return ordinal() % 7 * CELL;
        }

        public int v() {
            return ordinal() / 7 * CELL;
        }
    }

    // The prototype's icons (o outline, # fill, + highlight, b bone), and Sol's bubble.
    static final String[] HEART = {".oo.oo.", "o+#o##o", "o#####o", ".o###o.", "..o#o..", "...o..."};
    static final String[] SHANK = {".....oo", "....obo", "...o#o.", "..o+#o.", ".o###o.", "o####o.", ".oooo.."};
    static final String[] ARMOR = {"oo...oo", "o+o.o#o", "o#####o", ".o###o.", ".o###o.", ".o###o.", ".ooooo."};
    static final String[] BUBBLE = {"..ooo..", ".o+##o.", "o+####o", "o#####o", "o#####o", ".o###o.", "..ooo.."};

    static final int OUTLINE = 0xB8080610;
    static final int EMPTY = 0x24FFFFFF;
    static final int BONE = 0xFFF4EFE2;
    static final int ARMOR_FILL = 0xFFE9EDF5;
    static final int POISON = 0xFF9CB833;
    static final int WITHER = 0xFF2A2A2E;
    static final int FROZEN = 0xFF8FD8FF;
    static final int ABSORB = 0xFFFFCF3A;
    static final int HUNGER = 0xFF7FA03A;

    private HudIconAtlas() {
    }

    /** The atlas for a theme: hearts in {@code accent}, shanks, air and mount hearts in {@code glow}. */
    public static int[] bake(int accent, int glow) {
        int[] px = new int[SIZE * SIZE];
        heart(px, Icon.HEART_CONTAINER, 0, Mode.EMPTY, OUTLINE);
        heart(px, Icon.HEART_CONTAINER_BLINK, 0, Mode.EMPTY, 0xE6FFFFFF);
        heart(px, Icon.HEART_FULL, accent, Mode.FULL, OUTLINE);
        heart(px, Icon.HEART_HALF, accent, Mode.HALF, OUTLINE);
        heart(px, Icon.POISON_FULL, POISON, Mode.FULL, OUTLINE);
        heart(px, Icon.POISON_HALF, POISON, Mode.HALF, OUTLINE);
        heart(px, Icon.WITHER_FULL, WITHER, Mode.FULL, 0xE6000000);
        heart(px, Icon.WITHER_HALF, WITHER, Mode.HALF, 0xE6000000);
        heart(px, Icon.FROZEN_FULL, FROZEN, Mode.FULL, OUTLINE);
        heart(px, Icon.FROZEN_HALF, FROZEN, Mode.HALF, OUTLINE);
        heart(px, Icon.ABSORB_FULL, ABSORB, Mode.FULL, OUTLINE);
        heart(px, Icon.ABSORB_HALF, ABSORB, Mode.HALF, OUTLINE);
        heart(px, Icon.BLINK_FULL, 0xFFFFFFFF, Mode.FULL, OUTLINE);
        heart(px, Icon.BLINK_HALF, 0xFFFFFFFF, Mode.HALF, OUTLINE);
        // Hardcore: two bright pixels over the heart's lobes, drawn over any heart.
        set(px, Icon.HARDCORE_MARK, 2, 1, 0xFFFFFFFF);
        set(px, Icon.HARDCORE_MARK, 5, 1, 0xFFFFFFFF);
        icon(px, Icon.FOOD_EMPTY, SHANK, 0, Mode.EMPTY, OUTLINE, 1, 1);
        icon(px, Icon.FOOD_FULL, SHANK, glow, Mode.FULL, OUTLINE, 1, 1);
        icon(px, Icon.FOOD_HALF, SHANK, glow, Mode.HALF, OUTLINE, 1, 1);
        icon(px, Icon.HUNGER_EMPTY, SHANK, 0, Mode.EMPTY, OUTLINE, 1, 1);
        icon(px, Icon.HUNGER_FULL, SHANK, HUNGER, Mode.FULL, OUTLINE, 1, 1);
        icon(px, Icon.HUNGER_HALF, SHANK, HUNGER, Mode.HALF, OUTLINE, 1, 1);
        // Saturation: a bright rim round the shank (its outline in white), drawn over a full one.
        rim(px, Icon.SATURATION_RIM, SHANK, 0xFFFFFFFF, 1, 1);
        icon(px, Icon.ARMOR_EMPTY, ARMOR, 0, Mode.EMPTY, OUTLINE, 1, 1);
        icon(px, Icon.ARMOR_FULL, ARMOR, ARMOR_FILL, Mode.FULL, OUTLINE, 1, 1);
        icon(px, Icon.ARMOR_HALF, ARMOR, ARMOR_FILL, Mode.HALF, OUTLINE, 1, 1);
        int air = mix(glow, 0xFFFFFFFF, 0.35f);
        icon(px, Icon.AIR_FULL, BUBBLE, air, Mode.FULL, OUTLINE, 1, 1);
        icon(px, Icon.AIR_POPPING, BUBBLE, mix(air, 0xFFFFFFFF, 0.6f), Mode.HALF, OUTLINE, 1, 1);
        icon(px, Icon.AIR_EMPTY, BUBBLE, 0, Mode.EMPTY, OUTLINE, 1, 1);
        heart(px, Icon.MOUNT_CONTAINER, 0, Mode.EMPTY, OUTLINE);
        heart(px, Icon.MOUNT_FULL, glow, Mode.FULL, OUTLINE);
        heart(px, Icon.MOUNT_HALF, glow, Mode.HALF, OUTLINE);
        return px;
    }

    enum Mode { FULL, HALF, EMPTY }

    private static void heart(int[] px, Icon cell, int fill, Mode mode, int outline) {
        icon(px, cell, HEART, fill, mode, outline, 1, 2);
    }

    /** One icon into its cell at (dx, dy): its fill (and highlight) in {@code fill}, or the faint empty fill. */
    static void icon(int[] px, Icon cell, String[] rows, int fill, Mode mode, int outline, int dx, int dy) {
        int cols = rows[0].length();
        int highlight = mix(fill, 0xFFFFFFFF, 0.55f);
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < cols; x++) {
                char ch = rows[y].charAt(x);
                int c;
                switch (ch) {
                    case 'o' -> c = outline;
                    case 'b' -> c = BONE;
                    case '#', '+' -> {
                        boolean empty = mode == Mode.EMPTY || (mode == Mode.HALF && x >= (cols + 1) / 2);
                        c = empty ? EMPTY : ch == '+' ? highlight : fill;
                    }
                    default -> {
                        continue;
                    }
                }
                set(px, cell, x + dx, y + dy, c);
            }
        }
    }

    /** Only an icon's outline, in {@code color}. */
    private static void rim(int[] px, Icon cell, String[] rows, int color, int dx, int dy) {
        for (int y = 0; y < rows.length; y++) {
            for (int x = 0; x < rows[y].length(); x++) {
                if (rows[y].charAt(x) == 'o') {
                    set(px, cell, x + dx, y + dy, color);
                }
            }
        }
    }

    private static void set(int[] px, Icon cell, int x, int y, int argb) {
        px[(cell.v() + y) * SIZE + cell.u() + x] = argb;
    }

    static int mix(int a, int b, float k) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
        int g = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
        int al = Math.round(((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * k);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }
}
