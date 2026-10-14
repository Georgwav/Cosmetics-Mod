package io.github.georgwav.cosmetics.sol.theme;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ThemeParserTest {
    static final Path DIR = Path.of("src/main/resources/assets/cosmeticsmod/atmospheres");

    static List<ThemeRegistry.Source> bundled() throws IOException {
        Path dir = DIR;
        if (!Files.isDirectory(dir)) {
            dir = Path.of("../../").resolve(DIR);
        }
        List<ThemeRegistry.Source> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.sorted().toList()) {
                String name = f.getFileName().toString();
                out.add(new ThemeRegistry.Source(name.substring(0, name.length() - 5), Files.readString(f, StandardCharsets.UTF_8), true));
            }
        }
        return out;
    }

    @Test
    void allBundledThemesParseWithoutWarnings() throws IOException {
        List<String> warnings = new ArrayList<>();
        List<ThemeRegistry.Source> sources = bundled();
        assertEquals(13, sources.size(), "13 bundled themes");
        ThemeRegistry.load(sources, (id, msg) -> warnings.add(id + ": " + msg));
        assertEquals(List.of(), warnings);
        assertEquals("sakura_dusk", ThemeRegistry.all().get(0).id(), "built-in order");
        assertEquals("empty", ThemeRegistry.all().get(12).id());
        ThemeRegistry.Entry ember = ThemeRegistry.get("ember");
        assertEquals("ember_solstice", ember.id(), "prototype alias");
        assertEquals(0xFFFFB347, ember.base().ui().accent());
        assertEquals(0.042f, ember.base().sun().size(), 1e-6);
        assertEquals(0xFF5E0F06, ember.base().sky().top());
        assertEquals(0.55f, ember.base().fog().density(), 1e-6);
        assertEquals(0xFFFE8A2C, ember.base().sky().glow());
        assertNull(ThemeRegistry.get("monsoon").base().sun(), "Monsoon has no sun");
        assertTrue(ThemeRegistry.get("empty").base().vanillaSun());
        assertNotNull(ThemeRegistry.get("grace").base().sigil());
        assertEquals(7, ThemeRegistry.get("grace").base().mist().bands());
        assertEquals(2, ThemeRegistry.get("aurora").base().aurora().length);
        assertEquals(3, ThemeRegistry.get("nebula").base().nebula().size());
    }

    @Test
    void keyframesDifferFromTheShowPhase() throws IOException {
        ThemeRegistry.load(bundled(), (id, msg) -> fail(id + ": " + msg));
        ThemeRegistry.Entry ember = ThemeRegistry.get("ember_solstice");
        assertSame(ember.base(), ember.phase(ThemeRegistry.Phase.DUSK), "the show phase is the file itself");
        assertNotEquals(ember.base().sky().top(), ember.phase(ThemeRegistry.Phase.NIGHT).sky().top());
        assertEquals(0.8f, ember.particleScale()[ThemeRegistry.Phase.NIGHT.ordinal()], 1e-6);
        // A keyframe only overrides what it names.
        assertEquals(ember.base().sun().size(), ember.phase(ThemeRegistry.Phase.NIGHT).sun().size(), 1e-6);
    }

    @Test
    void partialFileMergesOverDefaults() {
        List<String> warnings = new ArrayList<>();
        ThemeSpec s = ThemeParser.parse("x", "{\"sky\":{\"top\":\"#112233\"}}", ThemeParser.DEFAULTS, warnings::add);
        assertEquals(0xFF112233, s.sky().top());
        assertEquals(ThemeParser.DEFAULTS.sky().middle(), s.sky().middle());
        assertEquals(List.of(), warnings);
    }

    @Test
    void badValuesFallBackAndWarn() {
        List<String> warnings = new ArrayList<>();
        ThemeSpec s = ThemeParser.parse("x", "{\"sky\":{\"top\":\"not a colour\"},\"fog\":{\"density\":7},"
                + "\"particles\":[[\"unicorn\",3,[\"#fff\"],[1,2]]],\"mystery\":1}", ThemeParser.DEFAULTS, warnings::add);
        assertEquals(ThemeParser.DEFAULTS.sky().top(), s.sky().top());
        assertTrue(s.fog().density() <= 1f);
        assertEquals(0, s.particles().size());
        assertTrue(warnings.size() >= 2, warnings.toString());
    }

    @Test
    void garbageGivesDefaultsWithoutThrowing() {
        List<String> warnings = new ArrayList<>();
        ThemeSpec s = ThemeParser.parse("junk", "{{{ not json", ThemeParser.DEFAULTS, warnings::add);
        assertEquals("junk", s.id());
        assertEquals(ThemeParser.DEFAULTS.sky().top(), s.sky().top());
        assertEquals(1, warnings.size());
        ThemeRegistry.Entry e = ThemeRegistry.parse(new ThemeRegistry.Source("j", "[]", false), warnings::add);
        assertNotNull(e.base());
    }

    @Test
    void nullRemovesTheSun() {
        ThemeSpec s = ThemeParser.parse("x", "{\"sun\":null}", ThemeParser.DEFAULTS, m -> fail(m));
        assertNull(s.sun());
    }

    @Test
    void fogDistancesFollowDensity() {
        ThemeSpec thick = ThemeParser.parse("x", "{\"fog\":{\"density\":0.85}}", ThemeParser.DEFAULTS, m -> fail(m));
        ThemeSpec thin = ThemeParser.parse("x", "{\"fog\":{\"density\":0.2}}", ThemeParser.DEFAULTS, m -> fail(m));
        assertTrue(thick.fog().end() < thin.fog().end());
        assertEquals(1.15f - 0.8f * 0.85f, thick.fog().end(), 1e-5);
        assertTrue(thick.fog().start() < thick.fog().end());
    }
}
