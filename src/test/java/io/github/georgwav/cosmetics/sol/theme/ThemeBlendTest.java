package io.github.georgwav.cosmetics.sol.theme;

import io.github.georgwav.cosmetics.sol.util.Colors;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ThemeBlendTest {
    private static final int N = ThemeRegistry.Phase.values().length;

    @Test
    void weightsAddUpToOneEverywhere() {
        float[] w = new float[N];
        for (float alt = -90f; alt <= 90f; alt += 0.25f) {
            for (boolean morning : new boolean[]{true, false}) {
                Keyframes.weights(alt, morning, w);
                float sum = 0f;
                for (float v : w) {
                    sum += v;
                }
                assertEquals(1f, sum, 1e-5, "at " + alt);
            }
        }
    }

    @Test
    void phasesAtTheirMoments() {
        float[] w = new float[N];
        Keyframes.weights(50f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.NOON.ordinal()], 1e-6);
        Keyframes.weights(17f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.AFTERNOON.ordinal()], 1e-6);
        Keyframes.weights(17f, true, w);
        assertEquals(1f, w[ThemeRegistry.Phase.MORNING.ordinal()], 1e-6);
        Keyframes.weights(3f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.SUNSET.ordinal()], 1e-6);
        Keyframes.weights(3f, true, w);
        assertEquals(1f, w[ThemeRegistry.Phase.SUNRISE.ordinal()], 1e-6);
        Keyframes.weights(-8f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.BLUEHOUR.ordinal()], 1e-6);
        Keyframes.weights(-8f, true, w);
        assertEquals(1f, w[ThemeRegistry.Phase.PREDAWN.ordinal()], 1e-6);
        Keyframes.weights(-40f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.NIGHT.ordinal()], 1e-6);
        assertEquals(1f, Keyframes.night(w), 1e-6);
        // The Sun lab's sunset height is fully sunset.
        Keyframes.weights(6.7f, false, w);
        assertEquals(1f, w[ThemeRegistry.Phase.SUNSET.ordinal()], 1e-6);
    }

    @Test
    void altitudeFollowsTheSkySphere() {
        Keyframes.latitude = 0f;
        assertEquals(90f, Keyframes.altitude(0f), 1e-3);
        assertEquals(-90f, Keyframes.altitude(0.5f), 1e-3);
        Keyframes.latitude = 34f;
        assertEquals(56f, Keyframes.altitude(0f), 1e-3, "noon: 90° less the latitude");
        assertEquals(0f, Keyframes.altitude(0.25f), 1e-3, "sunset where the game has it");
        assertTrue(Keyframes.morning(0.8f));
        assertFalse(Keyframes.morning(0.2f));
        assertEquals(0.5f, Keyframes.quantise(0.5011f), 1e-6);
    }

    @Test
    void blendIsTheShowPhaseAtTheShowMoment() throws IOException {
        ThemeRegistry.load(ThemeParserTest.bundled(), (id, m) -> fail(m));
        ThemeRegistry.Entry ember = ThemeRegistry.get("ember_solstice");
        float[] w = new float[N];
        w[ThemeRegistry.Phase.SUNSET.ordinal()] = 1f;
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
    void aPartsColourIsExactAtTheShowpieceAndFollowsTheThemeElsewhere() throws IOException {
        ThemeRegistry.load(ThemeParserTest.bundled(), (id, m) -> fail(m));
        ThemeRegistry.Entry eclipse = ThemeRegistry.get("eclipse");
        int blue = 0xFF2A5BFF;
        Customisation c = Customisation.NONE.withColour("sky_top", blue).withColour("night_top", 0xFF101830)
                .withColour("corona", 0xFF66CCFF).withAmount("bloom", 1.5f).withAmount("moon_size", 2f).withMoonLook("silver");
        ThemeRegistry.Entry out = ThemeState.customised(eclipse, c);
        assertEquals(blue, out.base().sky().top(), "exact at the showpiece");
        assertEquals(0xFF66CCFF, out.base().eclipse().corona());
        ThemeSpec night = out.phase(ThemeRegistry.Phase.NIGHT);
        assertEquals(0xFF101830, night.sky().top(), "the night's own pick at night");
        ThemeSpec sunset = out.phase(ThemeRegistry.Phase.SUNSET), sunset0 = eclipse.phase(ThemeRegistry.Phase.SUNSET);
        assertEquals(Colors.toHsl(blue)[0], Colors.toHsl(sunset.sky().top())[0], 0.03f, "the day's hue at sunset too");
        assertEquals(Colors.toHsl(sunset0.sky().top())[2] < Colors.toHsl(eclipse.base().sky().top())[2],
                Colors.toHsl(sunset.sky().top())[2] < Colors.toHsl(blue)[2], "as much darker or lighter as the theme's own");
        assertEquals(eclipse.base().grade().bloom() * 1.5f, out.base().grade().bloom(), 1e-5);
        assertEquals(eclipse.base().moon().size() * 2f, out.base().moon().size(), 1e-6);
        assertEquals("silver", out.phase(ThemeRegistry.Phase.NIGHT).night().moon(), "the picked moon look");
        assertEquals(c, Customisation.fromJson(c.toJson()), "round trip");
        assertTrue(c.withColour("sky_top", null).withColour("night_top", null).withColour("corona", null)
                .withAmount("bloom", 1f).withAmount("moon_size", 1f).withMoonLook(null).isNone(), "taken back is no edit at all");
    }

    @Test
    void recolourKeepsTheOrderOfLightAndDark() {
        int from = 0xFF8A3020, to = 0xFF2050C0;
        assertEquals(to, Colors.recolour(from, from, to));
        int darker = Colors.recolour(0xFF451810, from, to), lighter = Colors.recolour(0xFFD06048, from, to);
        assertTrue(Colors.toHsl(darker)[2] < Colors.toHsl(to)[2] && Colors.toHsl(to)[2] < Colors.toHsl(lighter)[2]);
        assertEquals(0x80000000, Colors.recolour(0x80451810, from, to) & 0xFF000000, "alpha kept");
    }

    @Test
    void nightLayersAreAllOnUntilATurnsOffAndSaveWithTheTheme() {
        for (String layer : Customisation.LAYERS) {
            assertTrue(Customisation.NONE.layerOn(layer), layer + " on by default");
        }
        Customisation c = Customisation.NONE.withLayer("meteors", false).withLayer("names", false);
        assertFalse(c.layerOn("meteors"));
        assertFalse(c.layerOn("names"));
        assertTrue(c.layerOn("stars"));
        com.google.gson.JsonObject json = c.toJson();
        assertFalse(json.getAsJsonObject("night").getAsJsonObject("layers").get("meteors").getAsBoolean(), "saved as night.layers");
        assertEquals(c, Customisation.fromJson(json), "round trip");
        assertTrue(c.withLayer("meteors", true).withLayer("names", true).isNone(), "back on is no edit at all");
        // Unknown layers, and layers saved as on, are dropped.
        com.google.gson.JsonObject odd = com.google.gson.JsonParser.parseString(
                "{\"night\":{\"layers\":{\"dragons\":false,\"stars\":true,\"comet\":false}}}").getAsJsonObject();
        assertEquals(Customisation.NONE.withLayer("comet", false), Customisation.fromJson(odd));
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
