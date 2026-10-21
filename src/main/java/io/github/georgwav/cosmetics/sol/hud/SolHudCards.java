package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.BossEvent;

/**
 * Sol UI's slabs under the HUD parts the game still lays out and writes itself: the boss bars, the scoreboard
 * sidebar, the tab list and the action bar. Their text (and the server's colours in it) stays the game's; Sol draws
 * the cards, bars and chips under it (the mixins hand over the game's own boxes). Each call is guarded: a failure
 * switches Sol UI off and the game's look comes back.
 */
public final class SolHudCards {
    private SolHudCards() {
    }

    /** The bar colours, brighter than the game's sprites; each still the server's choice. */
    private static int bossColor(BossEvent.BossBarColor color) {
        return switch (color) {
            case PINK -> 0xFFFF7AC8;
            case BLUE -> 0xFF5AB4FF;
            case RED -> 0xFFFF5A5A;
            case GREEN -> 0xFF6CF07A;
            case YELLOW -> 0xFFFFD84A;
            case PURPLE -> 0xFFB07CFF;
            case WHITE -> 0xFFF2F2F2;
        };
    }

    private static int notches(BossEvent.BossBarOverlay overlay) {
        return switch (overlay) {
            case NOTCHED_6 -> 6;
            case NOTCHED_10 -> 10;
            case NOTCHED_12 -> 12;
            case NOTCHED_20 -> 20;
            default -> 0;
        };
    }

    /**
     * A boss bar in place of the game's at (x, y), 182 x 5: a slab track filling in the bar's colour with a lit top,
     * the notches as gaps, and a slab under the name the game writes above it.
     */
    public static void bossBar(Canvas c, int x, int y, BossEvent event) {
        SolHud.DRAWN[SolHud.Part.BOSS.ordinal()]++;
        Guard.run(Feature.SOL_UI, () -> {
            Tokens tk = ThemeState.tokens();
            int col = bossColor(event.getColor());
            float k = Math.clamp(event.getProgress(), 0f, 1f);
            Minecraft mc = Minecraft.getInstance();
            int nameW = mc.font.width(event.getName());
            Gfx g = SolHud.begin(c);
            float cx = x + 91f;
            if (nameW > 0) {
                float nx = Math.round(cx - nameW / 2f - 5f);
                HudKit.slab(g, tk, nx, y - 11f, nameW + 10f, 11f, 0.7f);
            }
            HudKit.slab(g, tk, x, y, 182f, 5f, 1f);
            if (k > 0f) {
                float fw = Math.max(3f, Math.round(182f * k));
                HudKit.box(g, x + 1f, y + 1f, fw - 2f, 3f, col);
                HudKit.box(g, x + 1f, y + 1f, fw - 2f, 1f, 0x55FFFFFF);
            }
            int n = notches(event.getOverlay());
            for (int i = 1; i < n; i++) {
                HudKit.box(g, Math.round(x + 182f * i / n), y + 1f, 1f, 3f, 0x99000000);
            }
            SolHud.end(c);
        });
    }

    /** The scoreboard sidebar's card: from the title's top to the last line, the title set off by an accent line. */
    public static void sidebar(Canvas c, int x1, int top, int x2, int bottom, int titleBottom) {
        SolHud.DRAWN[SolHud.Part.SIDEBAR.ordinal()]++;
        Guard.run(Feature.SOL_UI, () -> {
            Tokens tk = ThemeState.tokens();
            Gfx g = SolHud.begin(c);
            float x = x1 - 2f, y = top - 2f, w = x2 - x1 + 4f, h = bottom - top + 4f;
            SolHud.glass(g, tk, x, y, w, h);
            HudKit.box(g, x + 2f, titleBottom, w - 4f, 1f, Colors.withAlpha(tk.accent, 0.55f));
            SolHud.end(c);
        });
    }

    /** A panel of the tab list (header, players or footer). */
    public static void tabPanel(Canvas c, int x1, int y1, int x2, int y2) {
        SolHud.DRAWN[SolHud.Part.TAB.ordinal()]++;
        Guard.run(Feature.SOL_UI, () -> {
            Tokens tk = ThemeState.tokens();
            Gfx g = SolHud.begin(c);
            SolHud.glass(g, tk, x1 - 1f, y1 - 1f, x2 - x1 + 2f, y2 - y1 + 2f);
            SolHud.end(c);
        });
    }

    /** One player's row in the tab list. */
    public static void tabRow(Canvas c, int x1, int y1, int x2, int y2) {
        Guard.run(Feature.SOL_UI, () -> {
            Gfx g = SolHud.begin(c);
            HudKit.box(g, x1, y1, x2 - x1, y2 - y1, 0x1AFFFFFF);
            SolHud.end(c);
        });
    }

    /** The action bar's chip under its text (the game's backdrop rectangle), fading with the text. */
    public static void chip(Canvas c, int x, int y, int width, int textColor) {
        SolHud.DRAWN[SolHud.Part.ACTION_BAR.ordinal()]++;
        Guard.run(Feature.SOL_UI, () -> {
            float a = ((textColor >>> 24) & 255) / 255f;
            if (a <= 0.02f || width <= 0) {
                return;
            }
            Tokens tk = ThemeState.tokens();
            Gfx g = SolHud.begin(c);
            g.alpha = a;
            HudKit.slab(g, tk, x - 6f, y - 3f, width + 12f, 14f, 0.9f);
            SolHud.end(c);
        });
    }

    /**
     * The chat's line backgrounds: the game's black at its own opacity becomes the theme's panel colour while Sol
     * UI draws the chat (its other fills, the scroll bar and the message tags, keep their colours).
     */
    public static int chatTint(int argb) {
        if ((argb & 0xFFFFFF) != 0 || !SolHud.draws(SolHud.Part.CHAT)) {
            return argb;
        }
        SolHud.DRAWN[SolHud.Part.CHAT.ordinal()]++;
        int a = Math.min(255, Math.round((argb >>> 24) * 1.15f));
        return (a << 24) | (ThemeState.tokens().panel & 0xFFFFFF);
    }
}
