package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.client.modules.SolUiModule;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Sol UI's tweaks to the game's own HUD parts (SolUiModule's settings): the tab list's ping in milliseconds, its
 * heads, size and player count; the scoreboard, boss bars and titles shown or not and their size; the scoreboard's
 * red numbers. The mixins call these; each part is drawn by the game as usual, in a scaled transform.
 */
public final class HudTweaks {
    /** Times each tweak was used (for the self-test): ping text, sizes, and parts left out. */
    public static int pingsWritten, tabsScaled, sidebarsScaled, bossesScaled, titlesScaled;
    public static int sidebarsHidden, bossesHidden, titlesHidden;

    private HudTweaks() {
    }

    private static float scale(double percent) {
        return (float) (percent / 100.0);
    }

    /** The tab list's size (1: the game's). */
    public static float tabScale() {
        SolUiModule m = SolUiModule.tweaks();
        return m == null ? 1f : scale(m.tabScale.get());
    }

    /** The screen width the tab list lays itself out in, so it fits the screen at its size. */
    public static int tabWidth(int screenWidth) {
        float s = tabScale();
        return s == 1f ? screenWidth : Math.round(screenWidth / s);
    }

    /** Starts drawing the tab list at its size (true when the transform needs undoing). */
    public static boolean tabIn(Canvas g) {
        float s = tabScale();
        if (s == 1f) {
            return false;
        }
        tabsScaled++;
        g.push();
        g.scale(s, s);
        return true;
    }

    /** Whether the tab list shows everyone's head (the game shows them only on servers in online mode). */
    public static boolean tabHeads(boolean game) {
        SolUiModule m = SolUiModule.tweaks();
        return game || m != null && m.tabHeads.get();
    }

    /** Whether the ping is written in milliseconds. */
    public static boolean pingText() {
        SolUiModule m = SolUiModule.tweaks();
        return m != null && "ms".equals(m.tabPing.get());
    }

    /** The room a row keeps for the ping (the game's 13 for its bars). */
    public static int pingRoom(int game) {
        return pingText() ? Math.max(game, Minecraft.getInstance().font.width("9999ms") + 3) : game;
    }

    /** Writes a player's ping where the game draws its bars: right-aligned, coloured from green to red. */
    public static void ping(Canvas g, int width, int x, int y, PlayerInfo info) {
        pingsWritten++;
        int ms = info.getLatency();
        String text = ms < 0 ? "?" : Math.min(ms, 9999) + "ms";
        int colour = ms < 0 ? 0xFFAAAAAA : ms < 150 ? 0xFF55FF55 : ms < 300 ? 0xFFFFFF55 : ms < 600 ? 0xFFFFAA00 : 0xFFFF5555;
        var font = Minecraft.getInstance().font;
        g.text(font, text, x + width - font.width(text) - 1, y, colour, true);
    }

    /** The tab list's header with how many are online under it (the game's header when the count is off). */
    public static Component header(Component game) {
        SolUiModule m = SolUiModule.tweaks();
        var connection = Minecraft.getInstance().getConnection();
        if (m == null || !m.tabCount.get() || connection == null) {
            return game;
        }
        Component count = Component.translatable("sol.hud.tab.online", connection.getListedOnlinePlayers().size())
                .withStyle(net.minecraft.ChatFormatting.GRAY);
        return game == null ? count : Component.empty().append(game).append("\n").append(count);
    }

    /** Whether the scoreboard's red numbers are hidden. */
    public static boolean hidesScoreNumbers() {
        SolUiModule m = SolUiModule.tweaks();
        return m != null && !m.scoreNumbers.get();
    }

    /** Whether the scoreboard is hidden. */
    public static boolean hidesScoreboard() {
        SolUiModule m = SolUiModule.tweaks();
        boolean hides = m != null && !m.scoreboard.get();
        if (hides) {
            sidebarsHidden++;
        }
        return hides;
    }

    /** Starts drawing the scoreboard at its size, from the screen's right edge (true when it needs undoing). */
    public static boolean scoreboardIn(Canvas g, int screenWidth, int screenHeight) {
        SolUiModule m = SolUiModule.tweaks();
        float s = m == null ? 1f : scale(m.scoreboardScale.get());
        if (s == 1f) {
            return false;
        }
        sidebarsScaled++;
        around(g, s, screenWidth, screenHeight / 2f);
        return true;
    }

    /** Whether boss bars are hidden. */
    public static boolean hidesBossBars() {
        SolUiModule m = SolUiModule.tweaks();
        boolean hides = m != null && !m.bossBars.get();
        if (hides) {
            bossesHidden++;
        }
        return hides;
    }

    /** Starts drawing the boss bars at their size, from the top middle (true when it needs undoing). */
    public static boolean bossIn(Canvas g, int screenWidth) {
        SolUiModule m = SolUiModule.tweaks();
        float s = m == null ? 1f : scale(m.bossScale.get());
        if (s == 1f) {
            return false;
        }
        bossesScaled++;
        around(g, s, screenWidth / 2f, 0f);
        return true;
    }

    /** Whether titles (and subtitles) are hidden. */
    public static boolean hidesTitles() {
        SolUiModule m = SolUiModule.tweaks();
        boolean hides = m != null && !m.titles.get();
        if (hides) {
            titlesHidden++;
        }
        return hides;
    }

    /** Starts drawing the title at its size, from the screen's middle (true when it needs undoing). */
    public static boolean titleIn(Canvas g, int screenWidth, int screenHeight) {
        SolUiModule m = SolUiModule.tweaks();
        float s = m == null ? 1f : scale(m.titleScale.get());
        if (s == 1f) {
            return false;
        }
        titlesScaled++;
        around(g, s, screenWidth / 2f, screenHeight / 2f);
        return true;
    }

    private static void around(Canvas g, float s, float x, float y) {
        g.push();
        g.translate(x, y);
        g.scale(s, s);
        g.translate(-x, -y);
    }
}
