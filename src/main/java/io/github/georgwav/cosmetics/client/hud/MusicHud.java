package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.music.AlbumTheme;
import io.github.georgwav.cosmetics.sol.music.Covers;
import io.github.georgwav.cosmetics.sol.music.Karaoke;
import io.github.georgwav.cosmetics.sol.music.Lyrics;
import io.github.georgwav.cosmetics.sol.music.MusicService;
import io.github.georgwav.cosmetics.sol.music.NowPlaying;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Music: what's playing from Cider or the system's player. Sol UI shows a now-playing card in one of four styles
 * (the prototype's card with cover, title, artist, progress and time; a big cover; a slim pill; the cover alone)
 * with the karaoke line under it (sung words white, the word being sung filling in the accent), unless the lyrics are
 * off or have their own place (the Lyrics module); the plain look writes lines. While on it also lays the album's
 * colours over the theme (Settings: album colours for the UI, the album sky).
 */
public final class MusicHud extends TextHud {
    private final Setting.Toggle cider = add(new Setting.Toggle("cider", true).group("sources"));
    private final Setting.Toggle spotify = add(new Setting.Toggle("spotify", true).group("sources"));
    private final Setting.Toggle systemPlayer = add(new Setting.Toggle("system_player", true).group("sources"));
    private final Setting.Toggle cover = add(new Setting.Toggle("cover", true).group("look"));
    private final Setting.Toggle progress = add(new Setting.Toggle("progress", true).group("look"));
    private final Setting.Choice style = add(new Setting.Choice("style", "card", List.of("card", "big_art", "compact", "art")).group("look"));
    /** The karaoke line under the card (the Lyrics module shows it somewhere else instead). */
    private final Setting.Toggle lyricsLine = add(new Setting.Toggle("lyrics", true).group("look"));
    /** The visualizers, one switch each (Settings › Music turns them all off). */
    private final java.util.Map<String, Setting.Toggle> viz = new java.util.LinkedHashMap<>();

    {
        for (String v : new String[]{"stars", "eq", "aurora", "rings", "album_sun", "notes", "breathe"}) {
            viz.put(v, add(new Setting.Toggle("viz_" + v, true).group("visualizers")));
        }
    }

    private static final NowPlaying SAMPLE = new NowPlaying("sample", "sample", "Golden Hour", "JVKE", "this is what ____ feels like",
            209_000L, 74_000L, 0L, false, null, null);
    private static final Lyrics SAMPLE_LYRICS = new Lyrics(List.of(new Lyrics.Line(70_000L, 78_000L,
            "It's golden hour, you're my golden hour", List.of(new Lyrics.Word(70_000L, 71_000L, "It's "),
            new Lyrics.Word(71_000L, 72_000L, "golden "), new Lyrics.Word(72_000L, 73_000L, "hour, "),
            new Lyrics.Word(73_000L, 74_500L, "you're "), new Lyrics.Word(74_500L, 75_500L, "my "),
            new Lyrics.Word(75_500L, 76_500L, "golden "), new Lyrics.Word(76_500L, 78_000L, "hour")), null, false)),
            true, "sample");

    public MusicHud() {
        super("music", "note", 0f, 0f, 4f, 80f);
        // Media keys reach Cider or Spotify through the system already; these are the game's own (unbound).
        addKey("play_pause");
        addKey("next_track");
        addKey("previous_track");
        addKey("like");
    }

    /** Whether visualizer {@code id} (stars, eq, aurora, rings, album_sun, notes, breathe) is on. */
    public boolean viz(String id) {
        Setting.Toggle t = viz.get(id);
        return t != null && t.get();
    }

    public boolean sourceOn(String id) {
        return switch (id) {
            case "cider" -> cider.get();
            case "spotify" -> spotify.get();
            case "mpris" -> systemPlayer.get();
            default -> true;
        };
    }

    @Override
    protected void onEnable(Minecraft mc) {
        MusicService.start(this::sourceOn);
    }

    @Override
    protected void onDisable(Minecraft mc) {
        MusicService.stop();
        ThemeState.setAlbum(null, null, true);
    }

    @Override
    protected void onSettingsChanged(Minecraft mc) {
        MusicService.sourcesChanged(this::sourceOn);
    }

    @Override
    protected void tick(Minecraft mc) {
        albumColours();
        if (pressed("play_pause")) {
            MusicService.playPause();
        }
        if (pressed("next_track")) {
            MusicService.next();
        }
        if (pressed("previous_track")) {
            MusicService.previous();
        }
        if (pressed("like")) {
            MusicService.like();
        }
    }

    private boolean pressed(String name) {
        var k = key(name);
        boolean any = false;
        while (k != null && k.consumeClick()) {
            any = true;
        }
        return any;
    }

    /** Lays the cover's colours over the theme while a song with a cover is there (and the player wants it). */
    public static void albumColours() {
        SolConfig.Music cfg = SolConfig.get().music;
        Covers.Cover c = MusicService.now() != null ? Covers.current() : null;
        if (c == null || !cfg.albumUi) {
            ThemeState.setAlbum(null, null, true);
            return;
        }
        AlbumTheme.Palette p = c.palette();
        boolean sky = cfg.albumSky;
        ThemeState.setAlbum(c.key() + (sky ? "|sky" : ""), e -> AlbumTheme.apply(e, p, sky), true);
    }

    static NowPlaying song() {
        NowPlaying n = MusicService.now();
        return n == null && preview() ? SAMPLE : n;
    }

    static Lyrics lyrics(NowPlaying n) {
        return n == SAMPLE ? SAMPLE_LYRICS : MusicService.lyrics();
    }

    /** The line being sung (index), or -1. */
    static int lineIndex(NowPlaying n, Lyrics l) {
        if (!SolConfig.get().music.lyricsHud || l.isEmpty()) {
            return -1;
        }
        return Karaoke.line(l, n.position(), MusicService.offset(n));
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        NowPlaying n = MusicService.now();
        if (n == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        out.add((n.playing() ? "♪ " : "‖ ") + n.title() + (n.artist() == null || n.artist().isBlank() ? "" : " — " + n.artist()));
        if (progress.get() && n.durationMs() > 0) {
            out.add(time(n.position()) + " / " + time(n.durationMs()));
        }
        Lyrics l = lyrics(n);
        int i = ownLyrics() ? lineIndex(n, l) : -1;
        if (i >= 0) {
            out.add(l.lines().get(i).text());
        }
        return out;
    }

    @Override
    protected List<String> sample(Minecraft mc) {
        return List.of("♪ " + SAMPLE.title() + " — " + SAMPLE.artist(), "1:14 / 3:29", SAMPLE_LYRICS.lines().get(0).text());
    }

    static String time(long ms) {
        long s = Math.max(0L, ms / 1000L);
        return String.format(Locale.ROOT, "%d:%02d", s / 60L, s % 60L);
    }

    // ---------------------------------------------------------------- Sol UI

    private static final float CARD_W = 150f, PAD = 4f, ART = 26f, LYRIC_H = 13f, GAP = 3f;
    private static final float BIG_W = 178f, BIG_ART = 52f, PILL_W = 150f, PILL_H = 15f, COVER = 52f;

    /** Whether the karaoke line goes under the card (on, and not shown by the Lyrics module instead). */
    private boolean ownLyrics() {
        return lyricsLine.get() && !io.github.georgwav.cosmetics.client.Modules.LYRICS.enabled();
    }

    private float cardWidth() {
        return switch (style.get()) {
            case "big_art" -> BIG_W;
            case "compact" -> PILL_W;
            case "art" -> COVER;
            default -> CARD_W;
        };
    }

    private float cardHeight() {
        return switch (style.get()) {
            case "big_art" -> PAD * 2f + BIG_ART;
            case "compact" -> PILL_H;
            case "art" -> COVER + (progress.get() ? 5f : 0f);
            default -> PAD * 2f + ART;
        };
    }

    private float lyricWidth(NowPlaying n, Lyrics l, int i) {
        if (i < 0) {
            return 0f;
        }
        String text = l.lines().get(i).text();
        return Math.min(KARAOKE_W, HudKit.measure(SolFont.Family.MEDIUM, HudKit.TEXT, text, scale()) + 12f);
    }

    @Override
    protected int solWidth(Minecraft mc) {
        NowPlaying n = song();
        if (n == null) {
            return 0;
        }
        Lyrics l = lyrics(n);
        return (int) Math.ceil(Math.max(cardWidth(), ownLyrics() ? lyricWidth(n, l, lineIndex(n, l)) : 0f));
    }

    @Override
    protected int solHeight(Minecraft mc) {
        NowPlaying n = song();
        if (n == null) {
            return 0;
        }
        return (int) Math.ceil(cardHeight() + (ownLyrics() && lineIndex(n, lyrics(n)) >= 0 ? GAP + LYRIC_H : 0f));
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        NowPlaying n = song();
        if (n == null) {
            return;
        }
        // Lined up with the screen edge it's pinned to: the lyric under the card grows away from it.
        boolean rightSide = anchorX > 0.5f;
        float cw = cardWidth(), w = solWidth(mc), x0 = rightSide ? w - cw : 0f;
        float ch = cardHeight();
        Covers.Cover c = n == SAMPLE ? null : Covers.current();
        switch (style.get()) {
            case "big_art" -> big(g, tk, n, c, x0, ch);
            case "compact" -> pill(g, tk, n, c, x0);
            case "art" -> coverOnly(g, tk, n, c, x0);
            default -> card(g, tk, n, c, x0, ch);
        }
        Lyrics l = lyrics(n);
        int i = ownLyrics() ? lineIndex(n, l) : -1;
        if (i >= 0) {
            float lw = Math.min(KARAOKE_W, g.width(HudKit.text(g), l.lines().get(i).text()) + 12f);
            karaoke(g, tk, n, l, i, rightSide ? w - lw : 0f, ch + GAP, KARAOKE_W);
        }
    }

    /** The cover, or the note on the theme's colours when there's none. */
    private void art(Gfx g, Tokens tk, Covers.Cover c, float x, float y, float size, float r) {
        if (cover.get() && c != null && Covers.small() != null) {
            HudKit.glow(g, x, y, size, size, Colors.withAlpha(c.palette().glow(), 0.3f));
            g.image(Covers.small(), x, y, size, size, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        } else {
            // The theme's sky in three hard bands, top to bottom.
            int top = Colors.mix(tk.skyTop, 0xFF000000, 0.2f);
            HudKit.box(g, x, y, size, size, top);
            HudKit.box(g, x, y + Math.round(size * 0.5f), size, size - Math.round(size * 0.5f), Colors.mix(top, tk.glow, 0.5f));
            HudKit.box(g, x, y + Math.round(size * 0.8f), size, size - Math.round(size * 0.8f), tk.glow);
            Icons.draw(g, "note", x + size * 0.25f, y + size * 0.25f, size * 0.5f, 0xCCFFFFFF, Icons.FILL, 0);
        }
    }

    /** The prototype's card: cover, title, artist, progress and time. */
    private void card(Gfx g, Tokens tk, NowPlaying n, Covers.Cover c, float x0, float ch) {
        HudKit.card(g, tk, x0, 0f, CARD_W, ch, HudKit.CARD_R);
        float ax = x0 + PAD, ay = PAD;
        art(g, tk, c, ax, ay, ART, 4f);
        float tx = ax + ART + 6f, room = x0 + CARD_W - PAD - tx;
        SolFont title = g.font(SolFont.Family.SEMIBOLD, HudKit.TEXT), small = HudKit.small(g);
        SolFont mono = g.font(SolFont.Family.PIXEL_BOLD, HudKit.SMALL);
        g.textMid(title, g.fit(title, n.title(), room - 8f), tx, ay + 5f, 0xFFFFFFFF);
        // Playing: a little equaliser in the accent; paused: two bars.
        drawState(g, tk, n, x0 + CARD_W - PAD - 5f, ay + 5f);
        if (n.artist() != null && !n.artist().isBlank()) {
            g.textMid(small, g.fit(small, n.artist(), room), tx, ay + 13f, 0xA8FFFFFF);
        }
        if (progress.get() && n.durationMs() > 0) {
            String t = time(n.position());
            float tw = g.width(mono, t);
            HudKit.bar(g, tx, ay + ART - 3.5f, room - tw - 4f, 2f, n.progress(), tk.accent);
            g.textMid(mono, t, tx + room - tw, ay + ART - 2.5f, 0x99FFFFFF);
        }
    }

    /** A big cover beside larger text: the album as the point of it. */
    private void big(Gfx g, Tokens tk, NowPlaying n, Covers.Cover c, float x0, float ch) {
        HudKit.card(g, tk, x0, 0f, BIG_W, ch, HudKit.CARD_R + 1f);
        float ax = x0 + PAD, ay = PAD;
        art(g, tk, c, ax, ay, BIG_ART, 5f);
        float tx = ax + BIG_ART + 7f, room = x0 + BIG_W - PAD - 2f - tx;
        SolFont title = g.font(SolFont.Family.SEMIBOLD, 8.5f), small = g.font(SolFont.Family.MEDIUM, 6.5f);
        SolFont mono = g.font(SolFont.Family.PIXEL_BOLD, HudKit.SMALL);
        g.textMid(title, g.fit(title, n.title(), room - 8f), tx, ay + 8f, 0xFFFFFFFF);
        drawState(g, tk, n, x0 + BIG_W - PAD - 5f, ay + 8f);
        if (n.artist() != null && !n.artist().isBlank()) {
            g.textMid(small, g.fit(small, n.artist(), room), tx, ay + 19f, 0xB8FFFFFF);
        }
        if (n.album() != null && !n.album().isBlank()) {
            SolFont tiny = HudKit.small(g);
            g.textMid(tiny, g.fit(tiny, n.album(), room), tx, ay + 28f, 0x80FFFFFF);
        }
        if (progress.get() && n.durationMs() > 0) {
            String t = time(n.position()), d = time(n.durationMs());
            float by = ay + BIG_ART - 9f;
            HudKit.bar(g, tx, by, room, 2.2f, n.progress(), tk.accent);
            g.textMid(mono, t, tx, by + 6f, 0x99FFFFFF);
            g.textRight(mono, d, tx + room, by + 6f, 0x66FFFFFF);
        }
    }

    /** A slim pill: a small cover, title and artist on one line, the progress along its foot. */
    private void pill(Gfx g, Tokens tk, NowPlaying n, Covers.Cover c, float x0) {
        HudKit.card(g, tk, x0, 0f, PILL_W, PILL_H, PILL_H / 2f);
        float s = PILL_H - 5f;
        art(g, tk, c, x0 + 3f, 2.5f, s, 2f);
        float tx = x0 + 3f + s + 4f, room = x0 + PILL_W - 8f - tx;
        SolFont f = HudKit.text(g), small = HudKit.small(g);
        String title = g.fit(f, n.title(), room * 0.62f);
        g.textMid(f, title, tx, PILL_H / 2f, 0xFFFFFFFF);
        float aw = g.width(f, title) + 4f;
        if (n.artist() != null && !n.artist().isBlank() && aw < room) {
            g.textMid(small, g.fit(small, n.artist(), room - aw), tx + aw, PILL_H / 2f, 0x99FFFFFF);
        }
        drawState(g, tk, n, x0 + PILL_W - 7f, PILL_H / 2f);
        if (progress.get() && n.durationMs() > 0) {
            float bw = PILL_W - PILL_H;
            HudKit.bar(g, x0 + Math.round(PILL_H / 2f), PILL_H - 2f, bw, 1f, n.progress(), tk.accent);
        }
    }

    /** The cover alone, with the equaliser in its corner and the progress under it. */
    private void coverOnly(Gfx g, Tokens tk, NowPlaying n, Covers.Cover c, float x0) {
        if (HudKit.glass > 0f) {
            HudKit.glow(g, x0, 0f, COVER, COVER, Colors.withAlpha(tk.glow, 0.3f * Math.min(1f, HudKit.glass)));
        }
        art(g, tk, c, x0, 0f, COVER, 5f);
        HudKit.box(g, x0 + COVER - 12f, COVER - 10f, 10f, 8f, 0x99000000);
        drawState(g, tk, n, x0 + COVER - 7f, COVER - 6f);
        if (progress.get() && n.durationMs() > 0) {
            HudKit.bar(g, x0, COVER + 2.5f, COVER, 2f, n.progress(), tk.accent);
        }
    }

    private static void drawState(Gfx g, Tokens tk, NowPlaying n, float cx, float cy) {
        if (!n.playing()) {
            HudKit.box(g, (float) Math.floor(cx - 2f), (float) Math.floor(cy - 2.5f), 1f, 5f, 0x99FFFFFF);
            HudKit.box(g, (float) Math.floor(cx + 1f), (float) Math.floor(cy - 2.5f), 1f, 5f, 0x99FFFFFF);
            return;
        }
        double t = System.nanoTime() / 1e9;
        for (int b = 0; b < 3; b++) {
            float h = Math.round(1.5f + 3.5f * (float) (0.5 + 0.5 * Math.sin(t * (5.0 + b * 1.7) + b * 2.1)));
            float bottom = (float) Math.floor(cy + 2.5f);
            HudKit.box(g, (float) Math.floor(cx - 2.5f) + b * 2f, bottom - h, 1f, h, tk.accent);
        }
    }

    /** How wide the karaoke line's chip can grow. */
    static final float KARAOKE_W = CARD_W * 1.6f;

    /**
     * The karaoke line in a chip with its left at {@code x}: sung words white, the current word filling in the
     * accent. Also the Lyrics module's.
     */
    static void karaoke(Gfx g, Tokens tk, NowPlaying n, Lyrics l, int i, float x, float y, float maxW) {
        Lyrics.Line line = l.lines().get(i);
        SolFont f = HudKit.text(g);
        String text = line.text();
        float tw = Math.min(g.width(f, text), maxW - 12f), w = tw + 12f;
        HudKit.card(g, tk, x, y, w, LYRIC_H, LYRIC_H / 2f);
        float cy = y + LYRIC_H / 2f, tx = x + 6f;
        g.clip(x + 4f, y, x + w - 4f, y + LYRIC_H);
        long pos = n.position(), off = MusicService.offset(n);
        if (line.words().isEmpty()) {
            g.textMid(f, g.fit(f, text, maxW - 12f), tx, cy, 0xFFFFFFFF);
        } else {
            int cur = Karaoke.word(l, i, pos, off);
            float wx = tx;
            for (int k = 0; k < line.words().size(); k++) {
                String word = line.words().get(k).text();
                float ww = g.width(f, word);
                if (k < cur) {
                    g.textMid(f, word, wx, cy, 0xFFFFFFFF);
                } else if (k == cur) {
                    float p = Karaoke.wordProgress(l, i, k, pos, off);
                    g.textMid(f, word, wx, cy, 0x73FFFFFF);
                    g.clip(wx, y, wx + ww * p, y + LYRIC_H);
                    g.textMid(f, word, wx, cy, tk.accent);
                    g.endClip();
                } else {
                    g.textMid(f, word, wx, cy, 0x73FFFFFF);
                }
                wx += ww;
            }
        }
        g.endClip();
    }
}
