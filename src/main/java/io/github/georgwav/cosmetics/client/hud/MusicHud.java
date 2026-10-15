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
 * Music: what's playing from Cider or the system's player. Sol UI shows the prototype's now-playing card (cover,
 * title, artist, progress and time) with the karaoke line under it (sung words white, the word being sung filling
 * in the accent); the plain look writes lines. While on it also lays the album's colours over the theme
 * (Settings: album colours for the UI, the album sky).
 */
public final class MusicHud extends TextHud {
    private final Setting.Toggle cider = add(new Setting.Toggle("cider", true));
    private final Setting.Toggle spotify = add(new Setting.Toggle("spotify", true));
    private final Setting.Toggle systemPlayer = add(new Setting.Toggle("system_player", true));
    private final Setting.Toggle cover = add(new Setting.Toggle("cover", true));
    private final Setting.Toggle progress = add(new Setting.Toggle("progress", true));
    /** The visualizers, one switch each (Settings › Music turns them all off). */
    private final java.util.Map<String, Setting.Toggle> viz = new java.util.LinkedHashMap<>();

    {
        for (String v : new String[]{"stars", "eq", "aurora", "rings", "album_sun", "notes", "breathe"}) {
            viz.put(v, add(new Setting.Toggle("viz_" + v, true)));
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
        ThemeState.setAlbum(c.key() + (sky ? "|sky" : ""), spec -> AlbumTheme.apply(spec, p, sky), true);
    }

    private static NowPlaying song() {
        NowPlaying n = MusicService.now();
        return n == null && preview() ? SAMPLE : n;
    }

    private static Lyrics lyrics(NowPlaying n) {
        return n == SAMPLE ? SAMPLE_LYRICS : MusicService.lyrics();
    }

    /** The line being sung (index), or -1. */
    private static int lineIndex(NowPlaying n, Lyrics l) {
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
        int i = lineIndex(n, l);
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

    private float cardHeight() {
        return PAD * 2f + ART;
    }

    private float lyricWidth(NowPlaying n, Lyrics l, int i) {
        if (i < 0) {
            return 0f;
        }
        String text = l.lines().get(i).text();
        return Math.min(CARD_W * 1.6f, HudKit.measure(SolFont.Family.MEDIUM, HudKit.TEXT, text, scale()) + 12f);
    }

    @Override
    protected int solWidth(Minecraft mc) {
        NowPlaying n = song();
        if (n == null) {
            return 0;
        }
        Lyrics l = lyrics(n);
        return (int) Math.ceil(Math.max(CARD_W, lyricWidth(n, l, lineIndex(n, l))));
    }

    @Override
    protected int solHeight(Minecraft mc) {
        NowPlaying n = song();
        if (n == null) {
            return 0;
        }
        return (int) Math.ceil(cardHeight() + (lineIndex(n, lyrics(n)) >= 0 ? GAP + LYRIC_H : 0f));
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        NowPlaying n = song();
        if (n == null) {
            return;
        }
        // Lined up with the screen edge it's pinned to: the lyric under the card grows away from it.
        boolean rightSide = anchorX > 0.5f;
        float w = solWidth(mc), x0 = rightSide ? w - CARD_W : 0f;
        float ch = cardHeight();
        HudKit.card(g, tk, x0, 0f, CARD_W, ch, HudKit.CARD_R);
        float ax = x0 + PAD, ay = PAD;
        Covers.Cover c = n == SAMPLE ? null : Covers.current();
        if (cover.get() && c != null && Covers.small() != null) {
            g.glowSprite(ax + ART / 2f, ay + ART / 2f, ART * 0.7f, ART * 0.7f, Colors.withAlpha(c.palette().glow(), 0.35f));
            g.image(Covers.small(), ax, ay, ART, ART, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        } else {
            g.roundV(ax, ay, ART, ART, 4f, Colors.mix(tk.skyTop, 0xFF000000, 0.2f), tk.glow);
            Icons.draw(g, "note", ax + ART * 0.25f, ay + ART * 0.25f, ART * 0.5f, 0xCCFFFFFF, Icons.FILL, 0);
        }
        float tx = ax + ART + 6f, room = x0 + CARD_W - PAD - tx;
        SolFont title = g.font(SolFont.Family.SEMIBOLD, HudKit.TEXT), small = HudKit.small(g);
        SolFont mono = g.font(SolFont.Family.MONO, HudKit.SMALL);
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
        Lyrics l = lyrics(n);
        int i = lineIndex(n, l);
        if (i >= 0) {
            karaoke(g, tk, n, l, i, rightSide, w, ch + GAP);
        }
    }

    private static void drawState(Gfx g, Tokens tk, NowPlaying n, float cx, float cy) {
        if (!n.playing()) {
            g.rect(cx - 2f, cy - 2.5f, 1.2f, 5f, 0x99FFFFFF);
            g.rect(cx + 0.8f, cy - 2.5f, 1.2f, 5f, 0x99FFFFFF);
            return;
        }
        double t = System.nanoTime() / 1e9;
        for (int b = 0; b < 3; b++) {
            float h = 1.5f + 3.5f * (float) (0.5 + 0.5 * Math.sin(t * (5.0 + b * 1.7) + b * 2.1));
            g.rect(cx - 2.5f + b * 2f, cy + 2.5f - h, 1.2f, h, tk.accent);
        }
    }

    /** The karaoke line in a chip under the card: sung words white, the current word filling in the accent. */
    private static void karaoke(Gfx g, Tokens tk, NowPlaying n, Lyrics l, int i, boolean rightSide, float room, float y) {
        Lyrics.Line line = l.lines().get(i);
        SolFont f = HudKit.text(g);
        float maxW = CARD_W * 1.6f;
        String text = line.text();
        float tw = Math.min(g.width(f, text), maxW - 12f), w = tw + 12f, x = rightSide ? room - w : 0f;
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
