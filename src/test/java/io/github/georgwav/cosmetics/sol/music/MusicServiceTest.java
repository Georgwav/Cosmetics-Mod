package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.theme.ThemeParser;
import io.github.georgwav.cosmetics.sol.theme.ThemeSpec;
import io.github.georgwav.cosmetics.sol.util.Colors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MusicServiceTest {
    @Test
    void lrclibPicksSyncedThenPlainAndSkipsInstrumentals() {
        assertArrayEquals(new String[]{"lrc", "[00:01.00] hi"},
                MusicService.pickLyrics(JsonParser.parseString("{\"syncedLyrics\":\"[00:01.00] hi\",\"plainLyrics\":\"hi\"}")));
        assertArrayEquals(new String[]{"plain", "hi"},
                MusicService.pickLyrics(JsonParser.parseString("{\"syncedLyrics\":null,\"plainLyrics\":\"hi\"}")));
        assertNull(MusicService.pickLyrics(JsonParser.parseString("{\"instrumental\":true,\"plainLyrics\":\"x\"}")));
        assertNull(MusicService.pickLyrics(JsonParser.parseString("{\"syncedLyrics\":\"\",\"plainLyrics\":\" \"}")));
        assertNull(MusicService.pickLyrics(JsonParser.parseString("[]")));
    }

    @Test
    void lyricsCacheNamesAreStableAndShort() {
        String a = MusicService.LyricsCache.hash("cider:123"), b = MusicService.LyricsCache.hash("cider:124");
        assertEquals(20, a.length());
        assertEquals(a, MusicService.LyricsCache.hash("cider:123"));
        assertNotEquals(a, b);
    }

    @Test
    void mprisLinesBecomeSongs() {
        NowPlaying n = MprisSource.parse("Playing\tSong\tArtist\tAlbum\t200000000\t5000000\tfile:///tmp/a.png");
        assertNotNull(n);
        assertEquals("Song", n.title());
        assertEquals(200_000L, n.durationMs());
        assertTrue(n.playing());
        assertEquals("file:///tmp/a.png", n.artworkUrl());
        assertNull(MprisSource.parse("Stopped\t\t\t\t\t\t"));
        assertNull(MprisSource.parse("garbage"));
    }

    @Test
    void coversAreAveragedSquaresWithRoundCorners() {
        // 4 x 2: the square middle is columns 1..2; left half red, right half blue.
        int[] src = {0xFF000000, 0xFFFF0000, 0xFF0000FF, 0xFF000000, 0xFF000000, 0xFFFF0000, 0xFF0000FF, 0xFF000000};
        int[] one = Covers.scale(src, 4, 1, 0, 2, 1);
        assertEquals(0xFF7F007F, one[0]);
        int[] two = Covers.scale(src, 4, 1, 0, 2, 2);
        assertEquals(0xFFFF0000, two[0]);
        assertEquals(0xFF0000FF, two[1]);
        int[] solid = new int[32 * 32];
        java.util.Arrays.fill(solid, 0xFF336699);
        int[] r = Covers.round(solid, 32, 6f);
        assertEquals(0, r[0] >>> 24, "the corner is clear");
        assertEquals(255, r[16 * 32 + 16] >>> 24, "the middle is opaque");
        assertEquals(255, r[16 * 32] >>> 24, "the middle of an edge is opaque");
        assertEquals(0x336699, r[0] & 0xFFFFFF, "colour is kept");
    }

    @Test
    void coverPaletteComesWithTheCover() {
        int[] px = new int[64 * 48];
        java.util.Arrays.fill(px, 0xFFE0405A);
        Covers.Cover c = Covers.fromPixels("k", px, 64, 48);
        assertEquals(Covers.LARGE * Covers.LARGE, c.large().length);
        assertEquals(Covers.SMALL * Covers.SMALL, c.small().length);
        assertTrue(Colors.r(c.palette().accent()) > 180);
    }

    @Test
    void albumColoursOverlayTheUiAndOptionallyTheSky() {
        ThemeSpec base = ThemeParser.DEFAULTS;
        AlbumTheme.Palette p = new AlbumTheme.Palette(0xFFFF2E88, 0xFF1FC8B4, 0xFF0B1030, 0xFFFF9CC4);
        ThemeSpec ui = AlbumTheme.apply(base, p, false);
        assertEquals(0xFF2E88, ui.ui().accent() & 0xFFFFFF);
        assertEquals(Colors.a(base.ui().panel()), Colors.a(ui.ui().panel()), "the theme's glass darkness stays");
        assertEquals(0x0B1030, ui.ui().panel() & 0xFFFFFF);
        assertEquals(base.sky(), ui.sky(), "the sky is left alone");
        ThemeSpec sky = AlbumTheme.apply(base, p, true);
        assertEquals(0xFF2E88, sky.sky().glow() & 0xFFFFFF);
        assertNotEquals(base.fog().color(), sky.fog().color());
        assertEquals(0xFF2E88, sky.sun().halo() & 0xFFFFFF, "the sun's halo takes the album's colour");
        assertEquals(Colors.mix(0xFFFF2E88, 0xFFFFFFFF, 0.55f) & 0xFFFFFF, sky.light().tint() & 0xFFFFFF, "and the light");
    }

    @Test
    void albumColoursReachTheParticlesAndTheEclipse() throws java.io.IOException {
        ThemeSpec eclipse = bundled("eclipse");
        AlbumTheme.Palette p = new AlbumTheme.Palette(0xFFD8D8D8, 0xFFB0B0B0, 0xFF101010, 0xFFF0F0F0);
        ThemeSpec grey = AlbumTheme.apply(eclipse, p, true);
        assertEquals(0xD8D8D8, grey.eclipse().corona() & 0xFFFFFF, "a grey cover's eclipse burns grey, not orange");
        for (ThemeSpec.Emitter e : grey.particles()) {
            for (int c : e.colors()) {
                assertTrue(Colors.toHsl(c)[1] < 0.15f, e.type() + " particles take the cover's grey: " + Integer.toHexString(c));
            }
        }
    }

    private static ThemeSpec bundled(String id) throws java.io.IOException {
        java.nio.file.Path f = java.nio.file.Path.of("src/main/resources/assets/cosmeticsmod/atmospheres", id + ".json");
        if (!java.nio.file.Files.exists(f)) {
            f = java.nio.file.Path.of("../../").resolve(f);
        }
        return ThemeParser.parse(id, java.nio.file.Files.readString(f), ThemeParser.DEFAULTS, w -> {
        });
    }
}
