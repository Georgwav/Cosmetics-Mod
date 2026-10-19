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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void aSolFinishIsMadeFromItsKnifeWithTheSameModelAndAnimations(@TempDir Path dir) throws IOException {
        StandInKnife.write(dir);
        String ns = StandInKnife.NAMESPACE;
        Files.writeString(dir.resolve(GunpackMount.OVERRIDES), "{\"knives\":{\"" + ns + ":standin_ruby\":{\"name\":\"Test Blade | Ruby\","
                + "\"finish_of\":\"" + ns + ":standin\",\"finish\":\"ruby\"}}}");
        GunpackMount.Result[] r = new GunpackMount.Result[1];
        Path pack = GunpackMount.mount(dir, r);
        assertEquals(java.util.List.of(ns + ":melee/standin", ns + ":melee/standin_ruby"), r[0].cosmetics(), r[0].problems().toString());
        Path assets = pack.resolve("assets").resolve(ns);
        JsonObject base = JsonParser.parseString(Files.readString(assets.resolve("cosmetics/melee/standin.json"))).getAsJsonObject();
        JsonObject ruby = JsonParser.parseString(Files.readString(assets.resolve("cosmetics/melee/standin_ruby.json"))).getAsJsonObject();
        assertEquals("Test Blade", ruby.get("group").getAsString(), "the finish groups under its knife");
        assertEquals(base.getAsJsonObject("melee").get("model"), ruby.getAsJsonObject("melee").get("model"));
        assertEquals(base.getAsJsonObject("melee").get("animations"), ruby.getAsJsonObject("melee").get("animations"));
        assertEquals(ns + ":textures/melee/sol_finish/standin_ruby.png", ruby.get("texture").getAsString());
        assertEquals(ns + ":textures/melee/sol_finish/slot/standin_ruby.png", ruby.get("icon").getAsString());
        byte[] made = Files.readAllBytes(assets.resolve("textures/melee/sol_finish/standin_ruby.png"));
        assertTrue(made.length > 0);
        assertTrue(!java.util.Arrays.equals(made, Files.readAllBytes(assets.resolve("textures/melee/standin.png"))), "recoloured");
        assertTrue(Files.exists(assets.resolve("textures/melee/sol_finish/slot/standin_ruby.png")));
    }

    @Test
    void aFinishOfAKnifeThatIsntThereOrOfAnUnknownKindIsLeftOut(@TempDir Path dir) throws IOException {
        StandInKnife.write(dir);
        String ns = StandInKnife.NAMESPACE;
        Files.writeString(dir.resolve(GunpackMount.OVERRIDES), "{\"knives\":{\"" + ns + ":a\":{\"finish_of\":\"" + ns
                + ":standin\",\"finish\":\"gold\"},\"" + ns + ":b\":{\"finish_of\":\"" + ns + ":nothing\",\"finish\":\"ruby\"}}}");
        GunpackMount.Result[] r = new GunpackMount.Result[1];
        GunpackMount.mount(dir, r);
        assertEquals(java.util.List.of(ns + ":melee/standin"), r[0].cosmetics());
        assertEquals(1, r[0].problems().size(), r[0].problems().toString());
    }

    @Test
    void aPackCanPlayAnotherPacksAnimationsAndSounds(@TempDir Path dir) throws IOException {
        StandInKnife.write(dir);
        String ns = StandInKnife.NAMESPACE;
        // A pack of its own model and pictures, playing the stand-in's animations: no animations of its own.
        Path own = dir.resolve("Own").resolve("assets").resolve("mine");
        Path standIn = dir.resolve("StandIn").resolve("assets").resolve(ns);
        for (String f : new String[]{"geo_models/melee/standin_geo.json", "textures/melee/standin.png", "textures/melee/slot/standin.png"}) {
            Path to = own.resolve(f.replace("standin", "blade"));
            Files.createDirectories(to.getParent());
            Files.copy(standIn.resolve(f), to);
        }
        Files.createDirectories(own.resolve("display/melee"));
        Files.writeString(own.resolve("display/melee/blade.json"), "{\"model\":\"mine:melee/blade_geo\",\"animation\":\""
                + ns + ":melee/standin\",\"texture\":\"mine:melee/blade\",\"slot_texture\":\"mine:melee/slot/blade\"}");
        GunpackMount.Result[] r = new GunpackMount.Result[1];
        Path pack = GunpackMount.mount(dir, r);
        assertTrue(r[0].cosmetics().contains("mine:melee/blade"), r[0].cosmetics() + " " + r[0].problems());
        JsonObject melee = JsonParser.parseString(Files.readString(pack.resolve("assets/mine/cosmetics/melee/blade.json")))
                .getAsJsonObject().getAsJsonObject("melee");
        String anims = melee.get("animations").getAsString();
        assertTrue(anims.startsWith(ns + ":"), anims);
        assertTrue(Files.exists(pack.resolve("assets").resolve(ns).resolve(anims.substring(ns.length() + 1))));
        assertEquals(ns, melee.get("sounds").getAsString(), "the animations' sound keys are their pack's");
        assertTrue(melee.get("model").getAsString().startsWith("mine:"));
    }

    @Test
    void aPersonalBuildsKnivesAreUnpackedOnlyWhenTheyChangeAndRemovedWithoutThem(@TempDir Path dir) throws IOException {
        Path zip = dir.resolve(GunpackMount.BUNDLED).resolve("knives.zip");
        GunpackMount.unpackBundled(dir, new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(zip));
        Files.setLastModifiedTime(zip, java.nio.file.attribute.FileTime.fromMillis(1000));
        GunpackMount.unpackBundled(dir, new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertEquals(1000, Files.getLastModifiedTime(zip).toMillis(), "the same knives aren't written again");
        GunpackMount.unpackBundled(dir, new java.io.ByteArrayInputStream(new byte[]{4, 5}));
        assertArrayEquals(new byte[]{4, 5}, Files.readAllBytes(zip));
        GunpackMount.unpackBundled(dir, null);
        assertTrue(!Files.exists(zip), "a jar without knives leaves none behind");
    }

    @Test
    void bundledKnivesMountAndThePlayersOwnCopyWins(@TempDir Path dir, @TempDir Path work) throws IOException {
        StandInKnife.write(work);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes); Stream<Path> s = Files.walk(work)) {
            for (Path p : s.filter(Files::isRegularFile).toList()) {
                zip.putNextEntry(new ZipEntry(work.relativize(p).toString().replace('\\', '/')));
                zip.write(Files.readAllBytes(p));
                zip.closeEntry();
            }
        }
        GunpackMount.unpackBundled(dir, new java.io.ByteArrayInputStream(bytes.toByteArray()));
        GunpackMount.Result[] r = new GunpackMount.Result[1];
        Path pack = GunpackMount.mount(dir, r);
        String ns = StandInKnife.NAMESPACE;
        assertEquals(java.util.List.of(ns + ":melee/standin"), r[0].cosmetics(), r[0].problems().toString());
        JsonObject melee = JsonParser.parseString(Files.readString(pack.resolve("assets").resolve(ns)
                .resolve("cosmetics/melee/standin.json"))).getAsJsonObject().getAsJsonObject("melee");
        assertTrue(melee.get("pack").getAsString().startsWith(GunpackMount.BUNDLED + "/"), melee.toString());
        // The player drops the same pack in themselves: one knife, theirs.
        StandInKnife.write(dir);
        pack = GunpackMount.mount(dir, r);
        assertEquals(java.util.List.of(ns + ":melee/standin"), r[0].cosmetics());
        melee = JsonParser.parseString(Files.readString(pack.resolve("assets").resolve(ns)
                .resolve("cosmetics/melee/standin.json"))).getAsJsonObject().getAsJsonObject("melee");
        assertTrue(melee.get("pack").getAsString().startsWith("StandIn/"), melee.toString());
    }

    @Test
    void newDefaultsReachAPlayersOlderOverridesWhichStillWin() {
        JsonObject own = JsonParser.parseString("{\"skip\":[\"x:y\"],\"knives\":{\"wtyj:desire_blade\":{\"hand_scale\":0.9},"
                + "\"delta_wt:annihil\":{\"name\":\"Mine\"}}}").getAsJsonObject();
        JsonObject m = GunpackMount.merged(own);
        JsonObject knives = m.getAsJsonObject("knives");
        assertEquals(0.9, knives.getAsJsonObject("wtyj:desire_blade").get("hand_scale").getAsDouble(), 1e-9);
        assertEquals("VCT Lock In", knives.getAsJsonObject("wtyj:desire_blade").get("name").getAsString());
        assertEquals("Mine", knives.getAsJsonObject("delta_wt:annihil").get("name").getAsString());
        assertEquals("cs2_wt:cs2_skeleton", knives.getAsJsonObject("cs2_wt:cs2_skeleton_ruby").get("finish_of").getAsString());
        assertEquals(0.72, knives.getAsJsonObject("sol:sol_xenohunter").get("hand_scale").getAsDouble(), 1e-9);
        assertTrue(m.getAsJsonArray("skip").toString().contains("x:y"));
        assertTrue(m.getAsJsonArray("skip").toString().contains("delta_wt:null_annihil"));
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
