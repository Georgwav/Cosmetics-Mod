package io.github.georgwav.cosmetics.sol.ui;

import io.github.georgwav.cosmetics.sol.ui.quick.Corona;
import io.github.georgwav.cosmetics.sol.ui.quick.QuickLight;
import io.github.georgwav.cosmetics.sol.util.Colors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuickMenuMathsTest {
    private static final int WHITE_GLOW = 0xFFFFFFFF;

    @Test
    void noonLightsTheTopWithTheNoonKey() {
        QuickLight l = new QuickLight();
        l.update(-90f, 640f, 106f, 0f, WHITE_GLOW);
        // The noon key's sky top is #d6a654, at the prototype's .62.
        assertEquals(0xD6, Colors.r(l.skyTop));
        assertEquals(0xA6, Colors.g(l.skyTop));
        assertEquals(0x54, Colors.b(l.skyTop));
        assertEquals(Math.round(0.62f * 255f), Colors.a(l.skyTop));
        assertEquals(1f, l.lit, 1e-4f);
        assertEquals(0f, l.stars, 1e-4f);
        // Short shadow at noon, pointing down the dial.
        assertEquals(90f, l.shadowAngle, 1e-4f);
        assertEquals(0.62f, l.shadowLength, 1e-3f);
    }

    @Test
    void midnightHasStarsAndTotalityIsBlack() {
        QuickLight l = new QuickLight();
        l.update(90f, 640f, 530f, 0f, WHITE_GLOW);
        assertEquals(1f, l.stars, 1e-4f);
        l.update(90f, 640f, 318f, 1f, WHITE_GLOW);
        assertEquals(0x04, Colors.r(l.skyTop));
        assertEquals(0x0C, Colors.b(l.skyTop));
        assertEquals(0f, l.lit, 1e-4f);
        assertEquals(0f, l.shadow, 1e-4f);
        // In totality the horizon glow centres and widens all the way round.
        assertEquals(640f, l.hx, 1e-3f);
        assertEquals(970f, l.hw, 1e-3f);
    }

    @Test
    void keysBlendHalfwayBetweenTheirAngles() {
        QuickLight l = new QuickLight();
        l.update(-75f, 0f, 0f, 0f, WHITE_GLOW);
        // Halfway between noon (#d6a654) and -60 (#b98a4a).
        assertEquals((0xD6 + 0xB9) / 2f, Colors.r(l.skyTop), 1f);
        // -180 and 180 are the same dawn key, so the dial has no seam: the same light at both, and nearly the
        // same just either side of it.
        QuickLight a = new QuickLight(), b = new QuickLight();
        a.update(-180f, 0f, 0f, 0f, WHITE_GLOW);
        b.update(180f, 0f, 0f, 0f, WHITE_GLOW);
        assertEquals(a.skyTop, b.skyTop);
        a.update(-179.9f, 0f, 0f, 0f, WHITE_GLOW);
        b.update(179.9f, 0f, 0f, 0f, WHITE_GLOW);
        assertEquals(Colors.r(a.skyTop), Colors.r(b.skyTop), 1);
        assertEquals(Colors.g(a.skyTop), Colors.g(b.skyTop), 1);
        assertEquals(Colors.b(a.skyTop), Colors.b(b.skyTop), 1);
    }

    @Test
    void brightWorldsGetAHeavierVeil() {
        assertEquals(0.3f, QuickLight.veil(0xFF060608), 1e-4f);
        assertEquals(0.6f, QuickLight.veil(0xFFFFFFFF), 1e-4f);
    }

    @Test
    void theCoronaHugsTheDiscAndGrowsWithTheEclipse() {
        Corona c = new Corona();
        c.draw(1f, 0f, Corona.color(0xFFFE8A2C), 0f, 0f, 0f);
        int mid = Corona.N / 2;
        // Inside your disc nothing is drawn (you cover the sun).
        assertEquals(0, c.argb[mid * Corona.N + mid]);
        // Just outside the rim it is bright; far out it fades.
        int rim = c.argb[mid * Corona.N + mid + (int) (Corona.DISC / Corona.CELL) + 2];
        int far = c.argb[mid * Corona.N + Corona.N - 2];
        assertTrue(Colors.a(rim) > Colors.a(far), "brighter at the rim than far out");
        int lit = 0;
        for (int v : c.argb) {
            lit += Colors.a(v) > 0 ? 1 : 0;
        }
        Corona weak = new Corona();
        weak.draw(0.2f, 0f, Corona.color(0xFFFE8A2C), 0f, 0f, 0f);
        int weakLit = 0;
        for (int v : weak.argb) {
            weakLit += Colors.a(v) > 0 ? 1 : 0;
        }
        assertTrue(lit > weakLit, "a fuller eclipse lights more of the corona");
    }

    @Test
    void theStreamerReachesTowardThePickedPetal() {
        Corona plain = new Corona(), ray = new Corona();
        plain.draw(1f, 0f, 0xFFEEF2FF, 0f, 0f, 0f);
        ray.draw(1f, 0f, 0xFFEEF2FF, 0f, 1f, 0f);
        // Straight right of the disc (0°), 20 cells out: the streamer makes it brighter there.
        int k = (Corona.N / 2) * Corona.N + Corona.N / 2 + 20;
        assertTrue(Colors.a(ray.argb[k]) > Colors.a(plain.argb[k]));
    }
}
