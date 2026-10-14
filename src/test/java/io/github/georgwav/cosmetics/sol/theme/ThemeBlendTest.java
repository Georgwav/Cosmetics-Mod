package io.github.georgwav.cosmetics.sol.theme;

import io.github.georgwav.cosmetics.sol.util.Colors;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ThemeBlendTest {
    @Test
    void weightsAddUpToOneEverywhere() {
        float[] w = new float[4];
        for (float alt = -90f; alt <= 90f; alt += 0.5f) {
            for (boolean morning : new boolean[]{true, false}) {
                Keyframes.weights(alt, morning, w);
                assertEquals(1f, w[0] + w[1] + w[2] + w[3], 1e-5, "at " + alt);
            }
        }
    }

    @Test
    void phasesAtTheirMoments() {
        float[] w = new float[4];
        Keyframes.weights(60f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.DAY.ordinal()], 1e-6);
        Keyframes.weights(-40f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.NIGHT.ordinal()], 1e-6);
        Keyframes.weights(3f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.DUSK.ordinal()], 1e-6);
        Keyframes.weights(3f, true, w);
        assertEquals(1f, w[ThemeRegistry.Phase.DAWN.ordinal()], 1e-6);
        // The Sun lab's sunset height is fully dusk.
        Keyframes.weights(0.18f * 38f, false, w);
        assertTrue(w[ThemeRegistry.Phase.DUSK.ordinal()] > 0.97f);
    }

    @Test
    void altitudeFollowsTheGamesSun() {
        assertEquals(90f, Keyframes.altitude(0f), 1e-3);
        assertEquals(0f, Keyframes.altitude(0.25f), 1e-3);
        assertEquals(-90f, Keyframes.altitude(0.5f), 1e-3);
        assertTrue(Keyframes.morning(0.8f));
        assertFalse(Keyframes.morning(0.2f));
        assertEquals(0.5f, Keyframes.quantise(0.5011f), 1e-6);
    }

    @Test
    void blendIsTheShowPhaseAtTheShowMoment() throws IOException {
        ThemeRegistry.load(ThemeParserTest.bundled(), (id, m) -> fail(m));
        ThemeRegistry.Entry ember = ThemeRegistry.get("ember_solstice");
        float[] w = {0f, 0f, 1f, 0f};
        Tokens t = new Tokens();
        Keyframes.blend(ember, w, null, t);
        assertEquals(ember.base().sky().top(), t.skyTop);
        assertEquals(ember.base().fog().color(), t.fogColor);
        assertEquals(ember.base().ui().accent(), t.accent);
        assertEquals(Colors.onColour(t.accent), t.onAccent);
        Keyframes.blend(ember, w, 0xFFFF4D4D, t);
        assertEquals(0xFFFF4D4D, t.accent, "profile accent override");
    }

    @Test
    void customisationMatchesThePrototype() throws IOException {
        ThemeRegistry.load(ThemeParserTest.bundled(), (id, m) -> fail(m));
        ThemeSpec eclipse = ThemeRegistry.get("eclipse").base();
        Customisation c = Customisation.NONE.withHue(40f).withSat(1.2f).withDensity(0.5f).withFog(0.8f);
        ThemeSpec out = c.apply(eclipse);
        assertEquals(Colors.shiftHue(eclipse.sky().top(), 40f, 1.2f), out.sky().top());
        assertEquals(Colors.shiftHue(eclipse.ui().accent(), 40f, 1.2f), out.ui().accent());
        assertEquals(eclipse.ui().panel(), out.ui().panel(), "the glass stays");
        assertEquals(0.8f, out.fog().density(), 1e-6);
        assertEquals(Math.round(eclipse.particles().get(0).count() * 0.5f), out.particles().get(0).count());
        assertSame(eclipse, Customisation.NONE.apply(eclipse));
        assertEquals(c, Customisation.fromJson(c.toJson()), "round trip");
        assertEquals(Customisation.NONE, Customisation.fromJson(null));
    }

    @Test
    void matchHueSnapsToFiveDegrees() {
        float dh = Customisation.matchHue(0xFFFF4B2B, 0xFF3B82FF);
        assertEquals(0f, dh % 5f, 1e-4);
        assertTrue(dh >= -180f && dh <= 180f);
    }

    @Test
    void tokensBlendEndpoints() {
        Tokens a = new Tokens();
        Tokens b = new Tokens();
        a.accent = 0xFF000000;
        b.accent = 0xFFFFFFFF;
        a.fogEnd = 0.2f;
        b.fogEnd = 1f;
        Tokens o = new Tokens();
        o.blend(a, b, 0f);
        assertEquals(a.accent, o.accent);
        o.blend(a, b, 1f);
        assertEquals(b.accent, o.accent);
        assertEquals(1f, o.fogEnd, 1e-6);
    }
}
