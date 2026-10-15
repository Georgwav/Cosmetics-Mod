package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.config.Secrets;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.music.Covers;
import io.github.georgwav.cosmetics.sol.music.Karaoke;
import io.github.georgwav.cosmetics.sol.music.Lyrics;
import io.github.georgwav.cosmetics.sol.music.MusicService;
import io.github.georgwav.cosmetics.sol.music.NowPlaying;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.Locale;

/**
 * Music: what's playing (cover, title, artist and album, progress, the controls, and the lyrics around the line
 * being sung with the word lighting up) and where it comes from (Cider with its app token, the system's player,
 * Spotify), with the album and lyric switches.
 */
final class MusicSection implements Section {
    private final TextField token = new TextField(200, t -> {
        Secrets.put("ciderToken", t.strip());
        MusicService.tokenChanged();
    }).secret();

    private final TextField clientId = new TextField(64, t -> {
        SolConfig.get().music.spotifyClientId = t.strip();
        SolConfig.save();
    });

    @Override
    public void opened() {
        token.set(Secrets.get("ciderToken"));
        token.focus(false);
        clientId.set(SolConfig.get().music.spotifyClientId);
        clientId.focus(false);
    }

    @Override
    public boolean typed(String text) {
        return token.typed(text) || clientId.typed(text);
    }

    @Override
    public boolean key(int key, boolean ctrl) {
        return token.key(key, ctrl) || clientId.key(key, ctrl);
    }

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        SolConfig.Music cfg = SolConfig.get().music;
        NowPlaying n = MusicService.now();
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f), mono = g.font(SolFont.Family.MONO, 12f);
        String title = SolMenu.tr("sol.section.music");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        String state = n == null ? SolMenu.tr("sol.menu.music.count")
                : SolMenu.tr(n.playing() ? "sol.menu.music.playing" : "sol.menu.music.paused") + " · "
                + SolMenu.tr("sol.menu.music.source." + n.source());
        g.textMid(mono, state, x + g.width(h2, title) + 14f, headY, 0x8CFFFFFF);

        float cy = y + 42f, cw = 400f, ch = Math.min(206f, h - 46f);
        card(g, tk, x, cy, cw, ch);
        nowPlaying(g, ui, tk, n, x, cy, cw);
        lyrics(g, tk, n, x, cy + 124f, cw, ch - 124f);
        sources(menu, g, ui, tk, cfg, x + cw + 22f, cy, w - cw - 22f);
    }

    private static void card(Gfx g, Tokens tk, float x, float y, float w, float h) {
        g.glow(x, y, w, h, 16f, 26f, Colors.withAlpha(tk.glow, 0.16f));
        g.round(x, y, w, h, 16f, tk.panel);
        g.stroke(x, y, w, h, 16f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(x, y, w, h, 16f, 0x38FFFFFF);
    }

    private static void nowPlaying(Gfx g, Ui ui, Tokens tk, NowPlaying n, float x, float cy, float cw) {
        float ax = x + 14f, ay = cy + 14f, tx = ax + 110f, room = cw - 14f - 110f - 14f;
        Covers.Cover cover = n == null ? null : Covers.current();
        if (cover != null && Covers.large() != null) {
            g.glowSprite(ax + 48f, ay + 52f, 70f, 62f, Colors.withAlpha(cover.palette().glow(), 0.32f));
            g.image(Covers.large(), ax, ay, 96f, 96f, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        } else {
            g.roundV(ax, ay, 96f, 96f, 10f, Colors.mix(tk.skyTop, 0xFF000000, 0.2f), tk.glow);
            Icons.draw(g, "note", ax + 30f, ay + 30f, 36f, 0xCCFFFFFF, Icons.FILL, 0);
        }
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 16f), s = g.font(SolFont.Family.MEDIUM, 12f);
        SolFont mono = g.font(SolFont.Family.MONO, 11f);
        if (n == null) {
            g.text(b, SolMenu.tr("sol.menu.music.nothing"), tx, ay + 26f, 0xFFFFFFFF);
            boolean on = Modules.MUSIC.enabled();
            g.text(s, g.fit(s, SolMenu.tr(on ? "sol.menu.music.start" : "sol.menu.music.module_off"), room), tx, ay + 46f, 0x99FFFFFF);
            g.round(tx, ay + 60f, room, 4f, 2f, 0x24FFFFFF);
            return;
        }
        g.text(b, g.fit(b, n.title(), room), tx, ay + 18f, 0xFFFFFFFF);
        String by = n.artist() == null ? "" : n.artist();
        if (n.album() != null && !n.album().isBlank()) {
            by = by.isEmpty() ? n.album() : by + " · " + n.album();
        }
        g.text(s, g.fit(s, by, room), tx, ay + 36f, 0x99FFFFFF);
        float k = n.progress();
        g.round(tx, ay + 46f, room, 4f, 2f, 0x24FFFFFF);
        if (k > 0f) {
            g.glow(tx, ay + 46f, room * k, 4f, 2f, 4f, Colors.withAlpha(tk.accent, 0.35f));
            g.round(tx, ay + 46f, Math.max(4f, room * k), 4f, 2f, tk.accent);
        }
        g.textMid(mono, time(n.position()), tx, ay + 58f, 0x99FFFFFF);
        if (n.durationMs() > 0) {
            g.textRight(mono, time(n.durationMs()), tx + room, ay + 58f, 0x99FFFFFF);
        }
        float bx = tx + room / 2f - 13f;
        ui.tool(tk, bx - 34f, ay + 70f, "prev", false, MusicService::previous);
        ui.tool(tk, bx, ay + 70f, n.playing() ? "pause" : "play", true, MusicService::playPause);
        ui.tool(tk, bx + 34f, ay + 70f, "next", false, MusicService::next);
    }

    /** Three lines around the one being sung: the sung words white, the word being sung filling in the accent. */
    private static void lyrics(Gfx g, Tokens tk, NowPlaying n, float x, float y, float w, float h) {
        g.rect(x + 14f, y, w - 28f, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        SolFont dim = g.font(SolFont.Family.MEDIUM, 13f), big = g.font(SolFont.Family.SEMIBOLD, 15f);
        float lx = x + 18f, room = w - 36f, mid = y + h / 2f + 1f;
        if (n == null) {
            g.textMid(g.font(SolFont.Family.SEMIBOLD, 14f), SolMenu.tr("sol.menu.music.lyrics"), lx, mid, 0x66FFFFFF);
            return;
        }
        Lyrics l = MusicService.lyrics();
        if (l.isEmpty()) {
            g.textMid(dim, SolMenu.tr(MusicService.lyricsPending() ? "sol.menu.music.finding_lyrics" : "sol.menu.music.no_lyrics"),
                    lx, mid, 0x66FFFFFF);
            return;
        }
        long pos = n.position(), off = MusicService.offset(n);
        int i = Karaoke.line(l, pos, off);
        if (!l.synced()) {
            // Plain lyrics: the first lines, by how far through the song it is.
            i = Math.min(l.lines().size() - 1, (int) (n.progress() * l.lines().size()));
        }
        g.clip(x + 10f, y + 2f, x + w - 10f, y + h - 2f);
        String before = previous(l, i), after = next(l, i);
        if (before != null) {
            g.textMid(dim, g.fit(dim, before, room), lx, mid - 20f, 0x59FFFFFF);
        }
        if (i >= 0) {
            line(g, tk, l, i, pos, off, big, lx, mid, room);
        }
        if (after != null) {
            g.textMid(dim, g.fit(dim, after, room), lx, mid + 20f, 0x59FFFFFF);
        }
        g.endClip();
    }

    private static String previous(Lyrics l, int i) {
        for (int k = i - 1; k >= 0; k--) {
            if (!l.lines().get(k).background()) {
                return l.lines().get(k).text();
            }
        }
        return null;
    }

    private static String next(Lyrics l, int i) {
        for (int k = i + 1; k < l.lines().size(); k++) {
            if (!l.lines().get(k).background()) {
                return l.lines().get(k).text();
            }
        }
        return null;
    }

    private static void line(Gfx g, Tokens tk, Lyrics l, int i, long pos, long off, SolFont f, float x, float cy, float room) {
        Lyrics.Line line = l.lines().get(i);
        if (line.words().isEmpty() || !l.synced()) {
            g.textMid(f, g.fit(f, line.text(), room), x, cy, 0xFFFFFFFF);
            return;
        }
        int cur = Karaoke.word(l, i, pos, off);
        float wx = x;
        for (int k = 0; k < line.words().size() && wx < x + room; k++) {
            String word = line.words().get(k).text();
            float ww = g.width(f, word);
            if (k < cur) {
                g.textMid(f, word, wx, cy, 0xFFFFFFFF);
            } else {
                g.textMid(f, word, wx, cy, 0x66FFFFFF);
                if (k == cur) {
                    float p = Karaoke.wordProgress(l, i, k, pos, off);
                    g.clip(wx, cy - 12f, wx + ww * p, cy + 12f);
                    g.textMid(f, word, wx, cy, tk.accent);
                    g.endClip();
                }
            }
            wx += ww;
        }
    }

    private void sources(SolMenu menu, Gfx g, Ui ui, Tokens tk, SolConfig.Music cfg, float lx, float cy, float lw) {
        float colW = (lw - 24f) / 2f;
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        String[] labels = {SolMenu.tr("sol.menu.music.cider"), SolMenu.tr("sol.menu.music.system"),
                SolMenu.tr("sol.menu.music.album_ui"), SolMenu.tr("sol.menu.music.album_sky"),
                SolMenu.tr("sol.menu.music.lyrics_hud"), SolMenu.tr("sol.menu.music.visualizers"),
                SolMenu.tr("sol.menu.music.spotify")};
        for (int i = 0; i < labels.length; i++) {
            float rx = lx + (i % 2) * (colW + 24f), ry = cy + 13f + (i / 2) * 34f, right = rx + colW;
            g.textMid(f, g.fit(f, labels[i], colW - 110f), rx, ry, 0xFFFFFFFF);
            switch (i) {
                case 0 -> status(ui, tk, right, ry, MusicService.connected("cider"));
                case 1 -> status(ui, tk, right, ry, MusicService.connected("mpris"));
                case 2 -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.albumUi, () -> set(() -> cfg.albumUi = !cfg.albumUi));
                case 3 -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.albumSky, () -> set(() -> cfg.albumSky = !cfg.albumSky));
                case 4 -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.lyricsHud, () -> set(() -> cfg.lyricsHud = !cfg.lyricsHud));
                case 5 -> ui.smallSwitch(tk, right - 30f, ry - 9f, cfg.visualizers, () -> set(() -> cfg.visualizers = !cfg.visualizers));
                default -> {
                    if (io.github.georgwav.cosmetics.sol.music.SpotifyAuth.signedIn()) {
                        float bw = ui.button(tk, right, ry, SolMenu.tr("sol.menu.music.sign_out"), false,
                                io.github.georgwav.cosmetics.sol.music.SpotifyAuth::signOut);
                        status(ui, tk, right - bw - 8f, ry, MusicService.connected("spotify"));
                    } else {
                        ui.button(tk, right, ry, SolMenu.tr("sol.menu.music.sign_in"), !cfg.spotifyClientId.isBlank(),
                                () -> {
                                    if (cfg.spotifyClientId.isBlank()) {
                                        menu.toast(SolMenu.tr("sol.notify.spotify_client_id"));
                                    } else {
                                        menu.toast(SolMenu.tr("sol.notify.spotify_browser_opened"));
                                        MusicService.signInSpotify();
                                    }
                                });
                    }
                }
            }
        }
        // Cider's app token (Cider asks for one unless "require token" is off), and the Client ID of the player's own
        // Spotify app (Spotify gives none to share).
        float ty = cy + 13f + 4 * 34f + 4f;
        g.textMid(f, SolMenu.tr("sol.menu.music.token"), lx, ty, 0xFFFFFFFF);
        token.draw(g, ui, tk, lx, ty + 12f, colW, 32f, "key", SolMenu.tr("sol.menu.music.token_hint"), menu.dt());
        float sx = lx + colW + 24f;
        g.textMid(f, SolMenu.tr("sol.menu.music.client_id"), sx, ty, 0xFFFFFFFF);
        clientId.draw(g, ui, tk, sx, ty + 12f, colW, 32f, "key", SolMenu.tr("sol.menu.music.client_id_hint"), menu.dt());
        SolFont small = g.font(SolFont.Family.MEDIUM, 11f);
        g.text(small, g.fit(small, SolMenu.tr("sol.menu.music.cider_steps"), colW), lx, ty + 60f, 0x73FFFFFF);
        g.text(small, g.fit(small, SolMenu.tr("sol.menu.music.spotify_steps", io.github.georgwav.cosmetics.sol.music.SpotifyAuth.REDIRECT),
                colW), sx, ty + 60f, 0x73FFFFFF);
        if ("none".equals(io.github.georgwav.cosmetics.sol.music.AudioCapture.state()) && cfg.visualizers) {
            g.text(small, g.fit(small, SolMenu.tr("sol.menu.music.viz_none"), lw), lx, ty + 78f, Colors.withAlpha(Tokens.AMBER, 0.85f));
        }
    }

    private static void status(Ui ui, Tokens tk, float right, float cy, boolean on) {
        ui.pill(tk, right, cy, SolMenu.tr(on ? "sol.menu.music.connected" : "sol.menu.music.not_connected"), on);
    }

    private static String time(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
    }

    private static void set(Runnable change) {
        change.run();
        SolConfig.save();
    }
}
