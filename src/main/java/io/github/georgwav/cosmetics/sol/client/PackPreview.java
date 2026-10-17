package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.packs.PackSelectionModel;

import java.util.List;
import java.util.Locale;

/**
 * Resource pack folders' search and previews in the pack screen: the pack under the mouse shown larger beside it
 * (its icon at twice the size, its name and its whole description), found through the lists' own rows; and,
 * before 1.21.9 (where the game added its own), a search that keeps only the packs whose name or description has
 * what you typed.
 */
public final class PackPreview {
    /** The shot's preview: the first pack shown, at a fixed place (for the test scenario). */
    public static boolean forShot;
    /** What the pack screen's search asks (before 1.21.9). */
    private static String query = "";
    /** Previews drawn, search boxes added and packs searched (for the self-test). */
    public static int previews, searchBoxes, searched;

    private PackPreview() {
    }

    public static boolean on() {
        return SolConfig.get().packFolders;
    }

    public static void search(String text) {
        query = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    public static void clearSearch() {
        query = "";
    }

    /** Whether a pack stays in the lists for the search. */
    public static boolean matches(PackSelectionModel.Entry pack) {
        if (query.isEmpty() || !on()) {
            return true;
        }
        searched++;
        return pack.getTitle().getString().toLowerCase(Locale.ROOT).contains(query)
                || pack.getDescription().getString().toLowerCase(Locale.ROOT).contains(query);
    }

    /** The pack under the mouse in the pack screen's lists, or null. */
    private static PackSelectionModel.Entry under(net.minecraft.client.gui.screens.Screen screen, int mouseX, int mouseY) {
        for (var child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.screens.packs.TransferableSelectionList list) {
                for (var entry : list.children()) {
                    if (entry instanceof io.github.georgwav.cosmetics.mixin.PackEntryAccess access
                            && (forShot || entry.isMouseOver(mouseX, mouseY))) {
                        return access.cosmeticsmod$pack();
                    }
                }
            }
        }
        return null;
    }

    /** Draws the preview of the pack under the mouse beside it (over the pack screen; the overlay calls it each frame). */
    public static void draw(Canvas g, net.minecraft.client.gui.screens.Screen screen, int mouseX, int mouseY) {
        if (!on()) {
            return;
        }
        int width = screen.width, height = screen.height;
        if (forShot) {
            mouseX = width / 4;
            mouseY = 60;
        }
        PackSelectionModel.Entry pack = under(screen, mouseX, mouseY);
        if (pack == null) {
            return;
        }
        previews++;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Tokens tk = ThemeState.tokens();
        int w = 220, icon = 64, pad = 8;
        List<net.minecraft.network.chat.FormattedText> lines = font.getSplitter().splitLines(pack.getExtendedDescription(), w - icon - pad * 3,
                net.minecraft.network.chat.Style.EMPTY);
        int h = Math.max(icon, 12 + lines.size() * 10) + pad * 2;
        int x = mouseX + 14, y = mouseY + 10;
        if (x + w > width - 4) {
            x = mouseX - 14 - w;
        }
        y = Math.max(4, Math.min(y, height - h - 4));
        g.nextLayer();
        g.fill(x, y, x + w, y + h, Colors.withAlpha(tk.panel, 0.94f));
        g.outline(x, y, x + w, y + h, Colors.withAlpha(tk.rim, 0.75f));
        g.blit(pack.getIconTexture(), x + pad, y + pad, icon, icon, 64);
        int tx = x + pad * 2 + icon, ty = y + pad;
        g.text(font, font.substrByWidth(pack.getTitle(), w - icon - pad * 3).getString(), tx, ty, 0xFFFFFFFF, true);
        for (int i = 0; i < lines.size(); i++) {
            g.text(font, lines.get(i).getString(), tx, ty + 12 + i * 10, 0xFFB8B8B8, false);
        }
    }
}
