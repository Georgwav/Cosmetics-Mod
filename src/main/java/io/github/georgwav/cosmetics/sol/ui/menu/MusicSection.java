package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * Music: what's playing (cover, title, progress and the current lyric line) and where it comes from (Cider, Spotify,
 * the system's player), with the album and lyric switches.
 */
final class MusicSection implements Section {
    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        SolConfig.Music cfg = SolConfig.get().music;
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f), mono = g.font(SolFont.Family.MONO, 12f);
        String title = SolMenu.tr("sol.section.music");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        g.textMid(mono, SolMenu.tr("sol.menu.music.count"), x + g.width(h2, title) + 14f, headY, 0x8CFFFFFF);

        // Now playing.
        float cy = y + 42f, cw = 400f, ch = 146f;
        g.glow(x, cy, cw, ch, 16f, 26f, Colors.withAlpha(tk.glow, 0.16f));
        g.round(x, cy, cw, ch, 16f, tk.panel);
        g.stroke(x, cy, cw, ch, 16f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(x, cy, cw, ch, 16f, 0x38FFFFFF);
        float ax = x + 14f, ay = cy + 14f;
        g.roundV(ax, ay, 96f, 96f, 10f, Colors.mix(tk.skyTop, 0xFF000000, 0.2f), tk.glow);
        Icons.draw(g, "note", ax + 30f, ay + 30f, 36f, 0xCCFFFFFF, Icons.FILL, 0);
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 16f), s = g.font(SolFont.Family.MEDIUM, 12f);
        g.text(b, SolMenu.tr("sol.menu.music.nothing"), ax + 110f, ay + 26f, 0xFFFFFFFF);
        g.text(s, SolMenu.tr("sol.menu.music.start"), ax + 110f, ay + 46f, 0x99FFFFFF);
        g.round(ax + 110f, ay + 66f, cw - 14f - 110f - 14f, 4f, 2f, 0x24FFFFFF);
        g.rect(x + 14f, cy + ch - 34f, cw - 28f, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        g.text(g.font(SolFont.Family.SEMIBOLD, 14f), SolMenu.tr("sol.menu.music.lyrics"), x + 14f, cy + ch - 12f, 0x66FFFFFF);

        // Sources and switches.
        float lx = x + cw + 22f, lw = w - cw - 22f, colW = (lw - 24f) / 2f;
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        String[] labels = {SolMenu.tr("sol.menu.music.cider"), SolMenu.tr("sol.menu.music.spotify"),
                SolMenu.tr("sol.menu.music.album_ui"), SolMenu.tr("sol.menu.music.album_sky"),
                SolMenu.tr("sol.menu.music.lyrics_hud"), SolMenu.tr("sol.menu.music.visualizers")};
        for (int i = 0; i < labels.length; i++) {
            float rx = lx + (i % 2) * (colW + 24f), ry = cy + 13f + (i / 2) * 34f, right = rx + colW;
            g.textMid(f, g.fit(f, labels[i], colW - 110f), rx, ry, 0xFFFFFFFF);
            switch (i) {
                case 0 -> ui.pill(tk, right, ry, SolMenu.tr("sol.menu.music.not_connected"), false);
                case 1 -> ui.button(tk, right, ry, SolMenu.tr("sol.menu.music.sign_in"), false,
                        () -> menu.toast(SolMenu.tr("sol.notify.spotify_setup")));
                case 2 -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.albumUi, () -> set(() -> cfg.albumUi = !cfg.albumUi));
                case 3 -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.albumSky, () -> set(() -> cfg.albumSky = !cfg.albumSky));
                case 4 -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.lyricsHud, () -> set(() -> cfg.lyricsHud = !cfg.lyricsHud));
                default -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.visualizers, () -> set(() -> cfg.visualizers = !cfg.visualizers));
            }
        }
    }

    private static void set(Runnable change) {
        change.run();
        SolConfig.save();
    }
}
