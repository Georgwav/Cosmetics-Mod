package io.github.georgwav.cosmetics.sol.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelFont;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.quick.PixelWheel;
import io.github.georgwav.cosmetics.sol.ui.quick.WheelLabels;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pixel quick menu's wheel (A.7.1): every ring's labels fit inside their sectors in the real pixel font at the
 * four screen sizes it is checked at, and the canvas is drawn in whole pixels with gaps, edges, head and moon where
 * the geometry puts them.
 */
class QuickWheelTest {
    /** Screen pixels per design pixel at 1280 x 720, 1366 x 768, 1920 x 1080 and 2560 x 1440. */
    private static final float[] US = {1f, 1366f / 1280f, 1.5f, 2f};
    private static final String[][] MAIN = {{"hud", "-150"}, {"modules", "-90"}, {"world", "-30"}, {"music", "30"},
            {"settings", "90"}, {"mods", "150"}};
    private static final String[] ME = {"presets", "emotes", "wardrobe"};

    private static JsonObject lang() throws Exception {
        try (InputStream in = QuickWheelTest.class.getResourceAsStream("/assets/cosmeticsmod/lang/en_us.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    /** The pixel font as the game measures it at u screen pixels per design pixel. */
    private static WheelLabels.Measure measure(float u) {
        return new WheelLabels.Measure() {
            private int k(float size) {
                return Math.max(1, Math.min(16, Math.round(size * u / PixelFont.EM)));
            }

            @Override
            public float width(String text, float size) {
                return PixelFont.width(true, text) * k(size) / u;
            }

            @Override
            public float height(float size) {
                return PixelFont.CAP * k(size) / u + 3f;
            }

            @Override
            public float icon(float size) {
                return PixelIcons.CELL * Math.max(1, Math.round(size / PixelIcons.CELL * u)) / u;
            }
        };
    }

    /** Design pixels per art pixel: the wheel shows at whole screen pixels, round(2u) per art pixel. */
    private static float ud(float u) {
        return Math.max(1, Math.round(2f * u)) / u;
    }

    private static List<WheelLabels.Label> ring(float r0, float r1, float start, int n, float ud) {
        List<WheelLabels.Label> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new WheelLabels.Label(r0 * ud, r1 * ud, start + i * 360f / n, 180f / n, null));
        }
        return out;
    }

    private static void assertFits(List<WheelLabels.Label> labels, float u, String what) {
        boolean all = WheelLabels.fit(labels, measure(u), 640f, 330f, ud(u) / 2f);
        WheelLabels.Label l0 = labels.get(0);
        assertTrue(all, what + " at u " + u + " (room " + l0.room + ", " + l0.w + " x " + l0.h + ", small " + l0.small + ")");
        for (WheelLabels.Label l : labels) {
            assertTrue(WheelLabels.fits(l.room), what + " '" + l.text + "' has " + l.room + " px to spare at u " + u);
        }
    }

    @Test
    void mainRingLabelsFitWithIconAndName() throws Exception {
        JsonObject lang = lang();
        for (float u : US) {
            float ud = ud(u);
            List<WheelLabels.Label> labels = ring(PixelWheel.MAIN_A, PixelWheel.MAIN_B, -150f, 6, ud);
            for (int i = 0; i < 6; i++) {
                assertEquals(Float.parseFloat(MAIN[i][1]), labels.get(i).mid, 0.01f);
                labels.get(i).text = lang.get("sol.section." + MAIN[i][0]).getAsString();
            }
            assertFits(labels, u, "main ring");
            for (WheelLabels.Label l : labels) {
                assertFalse(l.row, "main labels stack icon over name (" + l.text + ")");
                assertTrue(l.size >= 10f, "main labels stay readable");
            }
        }
    }

    @Test
    void yourRingAndTheEmoteRingFit() throws Exception {
        JsonObject lang = lang();
        for (float u : US) {
            float ud = ud(u);
            List<WheelLabels.Label> me = ring(PixelWheel.MAIN_A, PixelWheel.ME_B, -90f, 3, ud);
            for (int i = 0; i < 3; i++) {
                me.get(i).text = lang.get("sol.section." + ME[i]).getAsString();
            }
            assertFits(me, u, "your ring");
            // The emote wheel: eight slots, pictures where an emote is, the plus and "Empty" where none is.
            List<WheelLabels.Label> emotes = ring(PixelWheel.EM_A, PixelWheel.EM_B, -90f, 8, ud);
            for (int i = 0; i < 8; i++) {
                WheelLabels.Label l = emotes.get(i);
                if (i % 2 == 0) {
                    l.picW = 44f;
                    l.picH = 44f;
                } else {
                    l.text = lang.get("sol.quick.wheel.empty_slot").getAsString();
                }
            }
            assertFits(emotes, u, "emote ring");
            // Your own presets: the plus to save one comes last on the second ring, wherever that puts it.
            for (int n = 1; n <= 11; n++) {
                WheelLabels.Label plus = ring(PixelWheel.P2A, PixelWheel.P2B, -90f, n, ud).get(n - 1);
                plus.bits = PixelIcons.all().get("plus");
                assertFits(List.of(plus), u, "the save plus after " + (n - 1) + " presets");
            }
        }
    }

    @Test
    void segmentsAreFoundByTheirAngle() {
        PixelWheel.Ring r = new PixelWheel.Ring(58f, 110f, -150f);
        for (int i = 0; i < 6; i++) {
            r.segs.add(new PixelWheel.Seg());
        }
        for (int i = 0; i < 6; i++) {
            assertEquals(i, r.at(r.mid(i)));
            assertEquals(i, r.at(r.mid(i) + 29.5f));
            assertEquals(i, r.at(r.mid(i) - 29.5f + 360f));
        }
        assertEquals(1, r.at(-90f), "Modules at the top");
    }

    @Test
    void wheelIsDrawnInWholePixels() {
        PixelWheel w = new PixelWheel();
        PixelTokens p = new PixelTokens().set(0xFFFFB347, 0xFF5E0F06, 0xFFFE8A2C, false);
        w.colours(p);
        List<PixelWheel.Ring> rings = new ArrayList<>();
        PixelWheel.Ring main = new PixelWheel.Ring(PixelWheel.MAIN_A, PixelWheel.MAIN_B, -150f);
        for (int i = 0; i < 6; i++) {
            main.segs.add(new PixelWheel.Seg());
        }
        rings.add(main);
        // Noon: the sun at the top of the dial, Modules pointed at.
        w.draw(rings, 1f, 0, 1, false, true, -90f, 0f, false, 0f);
        int c = (int) PixelWheel.WC, mid = Math.round((PixelWheel.MAIN_A + PixelWheel.MAIN_B) / 2f);
        assertTrue(w.buf[c * PixelWheel.WN + c] >>> 24 >= 250, "your head's disc is (all but) solid");
        // Down the middle of a segment there's slab; on the line between two segments there's the gap.
        int at = px(w, -30f, mid);
        assertNotEquals(0, at >>> 24, "segment");
        assertEquals(0, px(w, 0f, mid) >>> 24, "the gap between World and Music");
        // The hot segment's outline is in the accent.
        assertEquals(p.acc, px(w, -90f, PixelWheel.MAIN_A + 0.6f));
        // The sun sits on the dial at the top.
        assertEquals(0xFFFFF6DC, w.buf[(c - (int) PixelWheel.RT) * PixelWheel.WN + c], "the sun's core on the dial");
        // Only a few levels of see-through: pixel art, no anti-aliased edges.
        Set<Integer> alphas = new HashSet<>();
        for (int v : w.buf) {
            alphas.add(v >>> 24);
        }
        assertTrue(alphas.size() <= 8, "alpha levels " + alphas);

        // Night: the moon replaces the sun (a square with a lit top row), no core.
        w.draw(rings, 1f, -1, -1, false, true, 90f, 0f, true, 0f);
        int my = c + (int) PixelWheel.RT;
        assertEquals(0xFFE8EEFC, w.buf[my * PixelWheel.WN + c], "the moon's face");
        assertEquals(0xFFFFFFFF, w.buf[(my - 5) * PixelWheel.WN + c], "its lit top row");

        // The eclipse: no sun on the dial, the corona round your head.
        w.draw(rings, 0f, -1, -1, true, false, -90f, 1f, false, 0f);
        assertNotEquals(0xFFFFF6DC, w.buf[(c - (int) PixelWheel.RT) * PixelWheel.WN + c], "the sun has gone");
        int lit = 0;
        for (int a = 0; a < 360; a += 5) {
            lit += px(w, a, PixelWheel.HR + 3f) >>> 24 != 0 ? 1 : 0;
        }
        assertTrue(lit > 36, "the corona burns round your head (" + lit + " of 72)");
    }

    @Test
    void redrawIsQuick() {
        PixelWheel w = new PixelWheel();
        w.colours(new PixelTokens().set(0xFFFFB347, 0xFF5E0F06, 0xFFFE8A2C, false));
        List<PixelWheel.Ring> rings = new ArrayList<>();
        for (float[] r : new float[][]{{PixelWheel.P1A, PixelWheel.P1B, 12}, {PixelWheel.P2A, PixelWheel.P2B, 11},
                {PixelWheel.P3A, PixelWheel.P3B, 4}}) {
            PixelWheel.Ring ring = new PixelWheel.Ring(r[0], r[1], -90f);
            for (int i = 0; i < r[2]; i++) {
                PixelWheel.Seg s = new PixelWheel.Seg();
                PixelWheel.skyTones(s, 0xFF5E0F06, 0xFFC2461A, 0xFFFE8A2C, false);
                ring.segs.add(s);
            }
            rings.add(ring);
        }
        for (int i = 0; i < 40; i++) {
            w.draw(rings, 1f, 1, i % 11, false, false, -90f, 0.7f, false, i / 12f);
        }
        // The best of three batches: a shared machine's scheduler can stall any one of them.
        int n = 40;
        double ms = Double.MAX_VALUE;
        long t;
        for (int batch = 0; batch < 3; batch++) {
            t = System.nanoTime();
            for (int i = 0; i < n; i++) {
                w.draw(rings, 1f, 1, i % 11, false, false, -90f, 0.7f, false, i / 12f);
            }
            ms = Math.min(ms, (System.nanoTime() - t) / 1e6 / n);
        }
        // A frame of the eclipse turning (the rings unchanged, the hot one moving) against a full redraw.
        t = System.nanoTime();
        for (int i = 0; i < n; i++) {
            rings.get(0).segs.get(0).current = i % 2 == 0;
            w.draw(rings, 1f, 1, i % 11, false, false, -90f, 0.7f, false, i / 12f);
        }
        double full = (System.nanoTime() - t) / 1e6 / n;
        System.out.printf("[QuickWheelTest] presets wheel with the corona: %.3f ms a frame, %.3f ms with the rings redrawn%n", ms, full);
        assertTrue(ms < 1.0, "a frame took " + ms + " ms");
    }

    /** The pixel at an angle (degrees) and radius (art pixels) from the wheel's middle. */
    private static int px(PixelWheel w, float deg, float r) {
        double a = Math.toRadians(deg);
        int x = (int) Math.floor(PixelWheel.WC + r * Math.cos(a)), y = (int) Math.floor(PixelWheel.WC + r * Math.sin(a));
        return w.buf[y * PixelWheel.WN + x];
    }
}
