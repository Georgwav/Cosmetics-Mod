package io.github.georgwav.cosmetics.emote;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.Reader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every emote shipped in the jar parses without warnings, has a name, and poses the player sensibly. */
class BundledEmotesTest {
    @Test
    void allBundledEmotesLoad() throws Exception {
        URL marker = getClass().getClassLoader().getResource("assets/cosmeticsmod/emotes/wave.json");
        assertNotNull(marker, "bundled emotes not on the test classpath");
        Path dir = Path.of(marker.toURI()).getParent();
        JsonObject lang;
        try (Reader reader = Files.newBufferedReader(dir.getParent().resolve("lang/en_us.json"))) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertEquals(17, files.size(), "the in-game test expects 17 bundled emotes");
        for (Path file : files) {
            String name = file.getFileName().toString();
            String id = "cosmeticsmod:" + name.substring(0, name.length() - ".json".length());
            List<String> warnings = new ArrayList<>();
            List<Emote> emotes;
            try (Reader reader = Files.newBufferedReader(file)) {
                emotes = EmoteParser.parse(id, reader, warnings::add);
            }
            assertEquals(List.of(), warnings, id);
            assertEquals(1, emotes.size(), id);
            Emote emote = emotes.get(0);
            assertEquals(id, emote.id());
            assertFalse(emote.bones().isEmpty(), id + " moves nothing");
            assertTrue(lang.has("emote." + id.replace(':', '.')), id + " has no name in en_us.json");
            if (emote.sound() != null) {
                // A bundled emote's sound is bundled too: its sounds.json entry and the file it names.
                String ns = emote.sound().substring(0, emote.sound().indexOf(':'));
                String event = emote.sound().substring(ns.length() + 1);
                Path assets = dir.getParent().getParent();
                JsonObject sounds;
                try (Reader reader = Files.newBufferedReader(assets.resolve(ns + "/sounds.json"))) {
                    sounds = JsonParser.parseReader(reader).getAsJsonObject();
                }
                assertTrue(sounds.has(event), id + " plays " + emote.sound() + ", which no bundled sounds.json has");
                for (var entry : sounds.getAsJsonObject(event).getAsJsonArray("sounds")) {
                    String sound = entry.isJsonObject() ? entry.getAsJsonObject().get("name").getAsString() : entry.getAsString();
                    String ogg = sound.substring(sound.indexOf(':') + 1) + ".ogg";
                    assertTrue(Files.exists(assets.resolve(sound.substring(0, sound.indexOf(':')) + "/sounds/" + ogg)),
                            id + "'s sound file " + sound + " is missing");
                }
            }
            for (String prop : emote.props()) {
                // A prop is a bundled cosmetic marked "prop": true.
                Path cosmetic = dir.getParent().resolve("cosmetics/" + prop.substring(prop.indexOf(':') + 1) + ".json");
                assertTrue(Files.exists(cosmetic), id + " brings " + prop + ", which isn't a bundled cosmetic");
                try (Reader reader = Files.newBufferedReader(cosmetic)) {
                    assertTrue(JsonParser.parseReader(reader).getAsJsonObject().get("prop").getAsBoolean(),
                            prop + " isn't marked \"prop\": true");
                }
            }

            // Pose it all the way through: every number stays finite.
            float step = 0.05f;
            for (float t = 0f; t <= emote.length() + 0.5f; t += step) {
                float[][] parts = new float[EmotePose.PARTS][];
                float[][] rest = {{0, 0, 0}, {0, 0, 0}, {-5, 2, 0}, {5, 2, 0}, {-1.9f, 12, 0}, {1.9f, 12, 0}};
                for (int p = 0; p < EmotePose.PARTS; p++) {
                    parts[p] = new float[]{rest[p][0], rest[p][1], rest[p][2], 0, 0, 0};
                }
                EmotePose.apply(emote, emote.timeAt(t), emote.weightAt(t), parts, rest);
                for (float[] part : parts) {
                    for (float v : part) {
                        assertTrue(Float.isFinite(v), id + " at " + t);
                    }
                }
            }
        }
    }
}
