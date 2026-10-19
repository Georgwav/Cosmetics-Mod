package io.github.georgwav.cosmetics.melee;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.georgwav.cosmetics.model.CosmeticBaker;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.model.CosmeticParser;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GunpackMountTest {
    @Test
    void aGunpackFolderBecomesCosmeticsModelsAndSounds(@TempDir Path dir) throws IOException {
        StandInKnife.write(dir);
        GunpackMount.Result[] r = new GunpackMount.Result[1];
        Path pack = GunpackMount.mount(dir, r);
        assertNotNull(pack);
        assertEquals(1, r[0].cosmetics().size(), r[0].problems().toString());
        String ns = StandInKnife.NAMESPACE;
        Path assets = pack.resolve("assets").resolve(ns);
        JsonObject c = JsonParser.parseString(Files.readString(assets.resolve("cosmetics/melee/standin.json"))).getAsJsonObject();
        assertEquals("melee", c.get("slot").getAsString());
        assertEquals(ns + ":textures/melee/standin.png", c.get("texture").getAsString());
        assertEquals(ns + ":textures/melee/slot/standin.png", c.get("icon").getAsString());
        JsonObject melee = c.getAsJsonObject("melee");
        assertTrue(Files.exists(assets.resolve(melee.get("model").getAsString().substring(ns.length() + 1))));
        assertTrue(Files.exists(assets.resolve(melee.get("animations").getAsString().substring(ns.length() + 1))));
        assertTrue(Files.exists(assets.resolve("textures/melee/standin.png")));
        assertTrue(Files.exists(assets.resolve("sounds/melee/standin/swing.ogg")));
        JsonObject sounds = JsonParser.parseString(Files.readString(assets.resolve("sounds.json"))).getAsJsonObject();
        assertEquals(ns + ":melee/standin/swing",
                sounds.getAsJsonObject("melee/standin/swing").getAsJsonArray("sounds").get(0).getAsJsonObject().get("name").getAsString());
        assertTrue(Files.exists(pack.resolve("pack.mcmeta")));
        assertTrue(Files.exists(dir.resolve(GunpackMount.OVERRIDES)), "the overrides file is written with the defaults");
    }

    @Test
    void aZipOfSeveralPacksMountsEachAndSkipsWhatTheOverridesSay(@TempDir Path dir, @TempDir Path work) throws IOException {
        StandInKnife.write(work);
        // A second pack: the same files under another folder and namespace.
        Path second = work.resolve("Second").resolve("assets").resolve("other");
        try (Stream<Path> s = Files.walk(work.resolve("StandIn").resolve("assets").resolve(StandInKnife.NAMESPACE))) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                Path to = second.resolve(work.resolve("StandIn").resolve("assets").resolve(StandInKnife.NAMESPACE).relativize(p));
                Files.createDirectories(to.getParent());
                Files.copy(p, to);
            }
        }
        try (OutputStream out = Files.newOutputStream(dir.resolve("Knives.zip")); ZipOutputStream zip = new ZipOutputStream(out);
             Stream<Path> s = Files.walk(work)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(work.relativize(p).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(p));
                zip.closeEntry();
            }
        }
        Files.writeString(dir.resolve(GunpackMount.OVERRIDES), "{\"skip\":[\"other:standin\"],\"knives\":{\""
                + StandInKnife.NAMESPACE + ":standin\":{\"name\":\"Test Blade | Shiny\",\"hand_scale\":0.8}}}");
        GunpackMount.Result[] r = new GunpackMount.Result[1];
        Path pack = GunpackMount.mount(dir, r);
        assertEquals(1, r[0].cosmetics().size(), "the skipped pack's knife isn't mounted: " + r[0].cosmetics());
        JsonObject c = JsonParser.parseString(Files.readString(pack.resolve("assets").resolve(StandInKnife.NAMESPACE)
                .resolve("cosmetics/melee/standin.json"))).getAsJsonObject();
        assertEquals("Test Blade | Shiny", c.get("name").getAsString());
        assertEquals("Test Blade", c.get("group").getAsString(), "a finish groups under its knife");
        assertEquals(0.8, c.getAsJsonObject("melee").get("hand_scale").getAsDouble(), 1e-9);
        // Mounting again with nothing changed leaves the pack as it is.
        long before = Files.getLastModifiedTime(pack.resolve("pack.mcmeta")).toMillis();
        assertNotNull(GunpackMount.mount(dir, r));
        assertEquals(before, Files.getLastModifiedTime(pack.resolve("pack.mcmeta")).toMillis());
    }

    @Test
    void theMountedCosmeticLoadsAsAKnife(@TempDir Path dir) throws IOException {
        StandInKnife.write(dir);
        Path pack = GunpackMount.mount(dir, null);
        String ns = StandInKnife.NAMESPACE;
        JsonObject json = JsonParser.parseString(Files.readString(pack.resolve("assets").resolve(ns)
                .resolve("cosmetics/melee/standin.json"))).getAsJsonObject();
        CosmeticDefinition d = CosmeticParser.parse(ns + ":melee/standin", json);
        assertEquals(CosmeticSlot.MELEE, d.slot());
        assertTrue(d.parts().isEmpty(), "drawn by the knife renderer, not baked");
        assertEquals(0, d.options(), "no colour, size or place options");
        assertEquals(ns + ":textures/melee/slot/standin.png", d.icon());
        CosmeticDefinition.Melee m = d.melee();
        assertNotNull(m);
        assertTrue(m.model().startsWith(ns + ":knives/models/"), m.model());
        assertEquals(ns, m.sounds());
        assertEquals(1f, m.handScale(), 1e-6f);
        assertEquals(CosmeticDefinition.Melee.RANDOM, m.angel());
        assertNotNull(m.thirdRight());
        assertEquals(1f, m.thirdRight()[6], 1e-6f);
        assertEquals(0, CosmeticBaker.bake(d).layers().length);
    }

    @Test
    void aKnifeNeedsItsBlockAndOnlyAKnifeHasOne() {
        assertThrows(JsonParseException.class, () -> CosmeticParser.parse("t:a",
                JsonParser.parseString("{\"slot\":\"melee\",\"texture\":\"t:x.png\"}").getAsJsonObject()));
        assertThrows(JsonParseException.class, () -> CosmeticParser.parse("t:a", JsonParser.parseString(
                "{\"slot\":\"head\",\"texture\":\"t:x.png\",\"melee\":{\"model\":\"t:m.json\",\"animations\":\"t:a.json\"},"
                        + "\"parts\":[{\"cubes\":[{\"from\":[0,0,0],\"to\":[1,1,1],\"uv\":[0,0]}]}]}").getAsJsonObject()));
        assertThrows(JsonParseException.class, () -> CosmeticParser.parse("t:a", JsonParser.parseString(
                "{\"slot\":\"melee\",\"texture\":\"t:x.png\",\"melee\":{\"model\":\"t:m.json\",\"animations\":\"t:a.json\","
                        + "\"angel\":\"both\"}}").getAsJsonObject()));
    }

    @Test
    void anEmptyFolderMountsNothing(@TempDir Path dir) throws IOException {
        assertNull(GunpackMount.mount(dir, null));
        assertNull(GunpackMount.mount(dir, null), "and still nothing when nothing changed");
    }

    @Test
    void namesDefaultToGeorgsTable() {
        JsonObject d = GunpackMount.defaults();
        assertEquals("Dark Star", d.getAsJsonObject("knives").getAsJsonObject("delta_wt:annihil").get("name").getAsString());
        assertEquals("Huntsman Knife", d.getAsJsonObject("knives").getAsJsonObject("cs2_wt:cs2_tactical").get("name").getAsString());
        assertEquals("delta_wt:null_annihil", d.getAsJsonArray("skip").get(0).getAsString());
        assertEquals("Cobra Vanguard", GunpackMount.prettify("cobra_vanguard"));
    }
}
