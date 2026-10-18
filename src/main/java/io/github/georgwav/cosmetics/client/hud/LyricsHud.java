package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.music.Lyrics;
import io.github.georgwav.cosmetics.sol.music.NowPlaying;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Lyrics: the karaoke line of the song playing, on its own, wherever you put it (above the hotbar by default)
 * instead of under the Music card. Sung words white, the word being sung filling in the accent, and the next line
 * under it if you want it. Needs the Music module for the song.
 */
public final class LyricsHud extends HudModule {
    private final Setting.Toggle nextLine = add(new Setting.Toggle("next_line", false).group("look"));
    private final Setting.Choice align = add(new Setting.Choice("align", "centre", List.of("left", "centre", "right")).group("look"));

    private static final float NEXT_H = 9f, GAP = 2f;

    public LyricsHud() {
        super("lyrics", "mic", 0.5f, 1f, 0f, -98f);
    }

    /** The line being sung and the song, or null when there's nothing to sing. */
    private static int line(NowPlaying n, Lyrics l) {
        return n == null || l == null ? -1 : MusicHud.lineIndex(n, l);
    }

    private static String nextText(Lyrics l, int i) {
        return i + 1 < l.lines().size() ? l.lines().get(i + 1).text() : "";
    }

    // ---------------------------------------------------------------- the plain look

    @Override
    protected int plainWidth(Minecraft mc) {
        NowPlaying n = MusicHud.song();
        Lyrics l = n == null ? null : MusicHud.lyrics(n);
        int i = line(n, l);
        if (i < 0) {
            return 0;
        }
        int w = mc.font.width(l.lines().get(i).text());
        if (nextLine.get()) {
            w = Math.max(w, mc.font.width(nextText(l, i)));
        }
        return w + 6;
    }

    @Override
    protected int plainHeight(Minecraft mc) {
        NowPlaying n = MusicHud.song();
        int i = line(n, n == null ? null : MusicHud.lyrics(n));
        return i < 0 ? 0 : nextLine.get() ? 25 : 15;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        NowPlaying n = MusicHud.song();
        Lyrics l = n == null ? null : MusicHud.lyrics(n);
        int i = line(n, l);
        if (i < 0) {
            return;
        }
        int w = plainWidth(mc);
        String text = l.lines().get(i).text();
        g.text(mc.font, text, x(w, mc.font.width(text)), 4, 0xFFFFFFFF, true);
        if (nextLine.get()) {
            String next = nextText(l, i);
            g.text(mc.font, next, x(w, mc.font.width(next)), 14, 0x99FFFFFF, true);
        }
    }

    private int x(int room, int width) {
        return switch (align.get()) {
            case "left" -> 3;
            case "right" -> room - 3 - width;
            default -> (room - width) / 2;
        };
    }

    // ---------------------------------------------------------------- Sol UI

    @Override
    protected int solWidth(Minecraft mc) {
        NowPlaying n = MusicHud.song();
        Lyrics l = n == null ? null : MusicHud.lyrics(n);
        int i = line(n, l);
        if (i < 0) {
            return 0;
        }
        return (int) Math.ceil(MusicHud.KARAOKE_W);
    }

    @Override
    protected int solHeight(Minecraft mc) {
        NowPlaying n = MusicHud.song();
        int i = line(n, n == null ? null : MusicHud.lyrics(n));
        return i < 0 ? 0 : (int) Math.ceil(13f + (nextLine.get() ? GAP + NEXT_H : 0f));
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        NowPlaying n = MusicHud.song();
        Lyrics l = n == null ? null : MusicHud.lyrics(n);
        int i = line(n, l);
        if (i < 0) {
            return;
        }
        float room = MusicHud.KARAOKE_W;
        SolFont f = HudKit.text(g);
        float lw = Math.min(room, g.width(f, l.lines().get(i).text()) + 12f);
        MusicHud.karaoke(g, tk, n, l, i, place(room, lw), 0f, room);
        if (nextLine.get()) {
            String next = g.fit(HudKit.small(g), nextText(l, i), room - 12f);
            float nw = g.width(HudKit.small(g), next);
            g.textMid(HudKit.small(g), next, place(room, nw + 12f) + 6f, 13f + GAP + NEXT_H / 2f, 0x8CFFFFFF);
        }
    }

    /** Where something {@code width} wide starts in {@code room}, by the alignment. */
    private float place(float room, float width) {
        return switch (align.get()) {
            case "left" -> 0f;
            case "right" -> room - width;
            default -> (room - width) / 2f;
        };
    }
}
