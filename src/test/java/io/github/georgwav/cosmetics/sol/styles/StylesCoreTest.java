package io.github.georgwav.cosmetics.sol.styles;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sol Styles' core (B.4): this version's part table against its own assets, which parts a pack can supply, CRC
 * matching against vanilla, pack classification, the texture tree, the mix plan (the same for the same input), model
 * dependencies, extruded models, forced-opaque blocks and share codes. Every pack is made here; none is real.
 */
class StylesCoreTest {
    @TempDir
    Path tmp;

    // ------------------------------------------------------------------ the part table

    @Test
    void everyPartPathExistsInThisVersionsAssets() {
        PartTable t = PartTable.get();
        assertFalse(t.parts().isEmpty(), "the build writes this version's part table");
        for (PartTable.Part p : t.parts()) {
            assertFalse(p.paths().isEmpty(), p.id() + " has files on this version");
            for (String path : p.paths()) {
                assertNotNull(StylesCoreTest.class.getResource("/assets/minecraft/" + path), p.id() + ": " + path + " is in the game");
            }
            if (p.icon() != null) {
                assertTrue(p.paths().contains(p.icon()) || StylesCoreTest.class.getResource("/assets/minecraft/" + p.icon()) != null,
                        p.id() + "'s icon exists");
            }
        }
        // Bauloo's tabs each have a part, in its order.
        assertEquals(List.of("crystal", "anchor", "armor", "tools", "inventory", "fire", "sky", "deepslate", "stone", "snow"), t.tabs());
        for (String tab : t.tabs()) {
            assertFalse(t.inTab(tab).isEmpty(), tab + " has parts");
        }
        assertTrue(t.part("crystal").off() && !t.part("stone").off(), "blocks never go off");
    }

    @Test
    void everyVanillaTextureLandsInTheTree() throws IOException {
        Path jar = VanillaIndex.locate();
        assertNotNull(jar, "the game's assets are on the test classpath");
        VanillaIndex v = java.nio.file.Files.isDirectory(jar) ? VanillaIndex.ofFolder(jar) : VanillaIndex.ofZip(jar);
        assertTrue(v.textures() > 1000, "vanilla has its textures: " + v.textures());
        PartTable t = PartTable.get();
        int other = 0;
        for (String path : v.all().keySet()) {
            if (!VanillaIndex.isTexture(path)) {
                continue;
            }
            String b = TextureTree.branch(path, t);
            assertNotNull(b);
            assertTrue(TextureTree.TRUNKS.contains(b.split("/")[0]), path + " -> " + b);
            other += b.equals("other") ? 1 : 0;
        }
        assertTrue(other < v.textures() / 10, "most textures have a real branch (" + other + " in other)");
    }

    // ------------------------------------------------------------------ scanning

    @Test
    void scanIgnoresVanillaCopiesAndClassifiesPacks() throws IOException {
        Map<String, byte[]> game = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            game.put("textures/block/b" + i + ".png", StylesTestPacks.png(16, 16, 0xFF808080 + i));
        }
        game.put("textures/item/diamond_sword.png", StylesTestPacks.png(16, 16, 0xFF55FFFF));
        VanillaIndex vanilla = StylesTestPacks.vanilla(game);

        // Half vanilla copies, one real change.
        Map<String, byte[]> half = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            half.put("textures/block/b" + i + ".png", game.get("textures/block/b" + i + ".png"));
        }
        half.put("textures/item/diamond_sword.png", StylesTestPacks.png(16, 16, 0xFFFF0000));
        PackInfo h = PackScanner.scan("rp:half", "Half", PackInfo.Origin.RESOURCE_PACK, null, StylesTestPacks.zip(tmp, "half", half), vanilla);
        assertEquals(10, h.copies(), "the copies of vanilla are seen as copies");
        assertEquals(1, h.changed());
        assertEquals(Set.of("textures/item/diamond_sword.png"), h.files().keySet(), "only what really changed counts");
        assertEquals(PackInfo.Kind.PART, h.kind());

        // Changes more than 40% of vanilla's textures: a full pack (a folder this time).
        Map<String, byte[]> full = new HashMap<>();
        for (int i = 0; i < 12; i++) {
            full.put("textures/block/b" + i + ".png", StylesTestPacks.png(16, 16, 0xFF00FF00 + i));
        }
        PackInfo f = PackScanner.scan("rp:full", "Full", PackInfo.Origin.RESOURCE_PACK, null, StylesTestPacks.folder(tmp, "full", full), vanilla);
        assertEquals(PackInfo.Kind.FULL, f.kind());

        // Only new files: an overlay.
        PackInfo o = PackScanner.scan("rp:sky", "Sky", PackInfo.Origin.RESOURCE_PACK, null,
                StylesTestPacks.zip(tmp, "sky", Map.of("optifine/sky/world0/sky1.properties", "source=./sky1.png".getBytes())), vanilla);
        assertEquals(PackInfo.Kind.OVERLAY, o.kind());
        assertEquals(PackInfo.Kind.EMPTY, PackScanner.scan("rp:copy", "Copy", PackInfo.Origin.RESOURCE_PACK, null,
                StylesTestPacks.zip(tmp, "copy", Map.of("textures/block/b1.png", game.get("textures/block/b1.png"))), vanilla).kind());
    }

    @Test
    void indexFindsPacksInGroupsAndReusesItsCache() throws IOException {
        VanillaIndex vanilla = StylesTestPacks.vanilla(Map.of("textures/item/totem_of_undying.png", StylesTestPacks.png(16, 16, 0xFFFFFF00)));
        Path rp = tmp.resolve("resourcepacks");
        Files.createDirectories(rp.resolve("PvP"));
        StylesTestPacks.zip(rp, "Red", Map.of("textures/item/totem_of_undying.png", StylesTestPacks.png(16, 16, 0xFFFF0000)));
        StylesTestPacks.zip(rp.resolve("PvP"), "Blue", Map.of("textures/item/totem_of_undying.png", StylesTestPacks.png(16, 16, 0xFF0000FF)));
        List<StylesIndex.Candidate> c = StylesIndex.candidates(null, rp, null);
        assertEquals(2, c.size(), "a group's packs are found too");
        StylesIndex.Snapshot s = StylesIndex.build(vanilla, c, Map.of());
        assertEquals(2, s.rescanned());
        Path cache = tmp.resolve("styles-index.json");
        StylesIndex.writeCache(cache, s, null);
        StylesIndex.Snapshot again = StylesIndex.build(vanilla, c, StylesIndex.readCache(cache));
        assertEquals(0, again.rescanned(), "unchanged packs come from the cache");
        assertEquals(s.byKey().keySet(), again.byKey().keySet());
        assertEquals(s.get("rp:Red.zip").files(), again.get("rp:Red.zip").files());
    }

    // ------------------------------------------------------------------ the mix

    @Test
    void mixIsTheSameForTheSameInputAndTakesWhatThePackHas() throws IOException {
        PartTable table = table();
        Map<String, byte[]> game = new LinkedHashMap<>();
        game.put("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFFAA00AA));
        game.put("textures/item/diamond_sword.png", StylesTestPacks.png(16, 16, 0xFF55FFFF));
        game.put("models/item/diamond_sword.json", "{\"parent\":\"item/handheld\",\"textures\":{\"layer0\":\"item/diamond_sword\"}}".getBytes());
        game.put("textures/block/stone.png", StylesTestPacks.png(16, 16, 0xFF777777));
        VanillaIndex vanilla = StylesTestPacks.vanilla(game);
        Map<String, byte[]> red = new HashMap<>();
        red.put("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFFFF0000));
        red.put("textures/item/diamond_sword.png", StylesTestPacks.png(16, 16, 0xFFFF2222));
        red.put("models/item/diamond_sword.json", "{\"parent\":\"item/red_parent\",\"textures\":{\"layer0\":\"item/red_blade\"}}".getBytes());
        red.put("models/item/red_parent.json", "{\"parent\":\"item/handheld\",\"elements\":[]}".getBytes());
        red.put("textures/item/red_blade.png", StylesTestPacks.png(16, 16, 0xFFEE1111));
        red.put("textures/block/stone.png", StylesTestPacks.png(16, 16, 0x00FFFFFF));
        PackInfo pack = PackScanner.scan("rp:red", "Red", PackInfo.Origin.RESOURCE_PACK, null, StylesTestPacks.zip(tmp, "red", red), vanilla);
        StyleSetup setup = StyleSetup.EMPTY
                .with("crystal", StyleSetup.Choice.DEFAULT.withSource("pack:rp:red"))
                .with("swords", StyleSetup.Choice.DEFAULT.withSource("pack:rp:red"))
                .with("stone", StyleSetup.Choice.DEFAULT.withSource("pack:rp:red"));
        Builder reader = new Builder(tmp.resolve("cache"), null);
        Mixer.Plan a = Mixer.plan(setup, table, k -> k.equals("rp:red") ? pack : null, vanilla, (p, path) -> text(reader, p, path), 0xFF8800);
        Mixer.Plan b = Mixer.plan(StyleSetup.fromJson(setup.toJson()), table, k -> k.equals("rp:red") ? pack : null, vanilla,
                (p, path) -> text(reader, p, path), 0xFF8800);
        assertEquals(a, b, "the same setup and packs give the same mix");
        assertInstanceOf(Mixer.FromPack.class, a.files().get("textures/entity/end_crystal/end_crystal.png"));
        // The model's parent and its texture come along from the pack.
        assertTrue(a.files().containsKey("models/item/red_parent.json"), "the parent chain comes along");
        assertTrue(a.files().containsKey("textures/item/red_blade.png"), "the textures it names come along");
        // The pack's see-through stone is made opaque again.
        Mixer.Source stone = a.files().get("textures/block/stone.png");
        assertInstanceOf(Mixer.Made.class, stone);
        assertEquals("keep_opaque", ((Mixer.Made) stone).op());
        // A pack that's gone: back to the base, said in the notes.
        Mixer.Plan gone = Mixer.plan(setup, table, k -> null, vanilla, (p, path) -> null, 0);
        assertTrue(gone.files().isEmpty());
        assertFalse(gone.notes().isEmpty());
        reader.close();
    }

    @Test
    void offAndBuiltinsAreMadeFromTheTextureUnderThem() throws IOException {
        PartTable table = table();
        Map<String, byte[]> game = new LinkedHashMap<>();
        game.put("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFFAA00AA));
        game.put("textures/block/stone.png", StylesTestPacks.png(16, 16, 0xFF777777));
        VanillaIndex vanilla = StylesTestPacks.vanilla(game);
        Path jar = StylesTestPacks.zip(tmp, "game", game);
        StyleSetup setup = StyleSetup.EMPTY
                .with("crystal", StyleSetup.Choice.DEFAULT.withSource(StyleSetup.OFF))
                .with("stone", StyleSetup.Choice.DEFAULT.withSource(StyleSetup.OFF).withColour(StyleSetup.Paint.RECOLOUR, 0, 1f, 1f, 0x2244FF));
        Mixer.Plan plan = Mixer.plan(setup, table, k -> null, vanilla, (p, path) -> null, 0);
        Mixer.Made crystal = (Mixer.Made) plan.files().get("textures/entity/end_crystal/end_crystal.png");
        assertEquals("off", crystal.op());
        try (Builder b = new Builder(tmp.resolve("cache"), jar)) {
            Pixels.Image img = Builder.decode(b.read(crystal));
            assertEquals(64, img.w());
            assertTrue(java.util.Arrays.stream(img.argb()).allMatch(p -> p >>> 24 == 0), "an invisible crystal");
            // Stone can't go off (a block): only its colour changes, and it stays opaque.
            Mixer.Made stone = (Mixer.Made) plan.files().get("textures/block/stone.png");
            Pixels.Image s = Builder.decode(b.read(stone));
            assertTrue(s.opaque(), "blocks stay opaque");
            assertTrue(Files.exists(b.cached(stone)), "made files are cached");
        }
    }

    // ------------------------------------------------------------------ the menu's tiles

    @Test
    void tilesComeInTheMenusOrderWithDuplicatesShownOnce() throws IOException {
        PartTable table = table();
        Map<String, byte[]> game = new LinkedHashMap<>();
        game.put("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFFAA00AA));
        for (int i = 0; i < 4; i++) {
            game.put("textures/block/b" + i + ".png", StylesTestPacks.png(16, 16, 0xFF808080 + i));
        }
        VanillaIndex vanilla = StylesTestPacks.vanilla(game);
        Map<String, byte[]> red = Map.of("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFFFF0000));
        PackInfo a = PackScanner.scan("rp:Red.zip", "Red", PackInfo.Origin.RESOURCE_PACK, null, StylesTestPacks.zip(tmp, "red", red), vanilla);
        PackInfo b = PackScanner.scan("rp:Red copy.zip", "Red copy", PackInfo.Origin.RESOURCE_PACK, null, StylesTestPacks.zip(tmp, "red2", red), vanilla);
        Map<String, byte[]> blue = Map.of("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFF0000FF));
        PackInfo mine = PackScanner.scan("user:crystal/blue", "Blue", PackInfo.Origin.USER, "crystal", StylesTestPacks.folder(tmp, "blue", blue), vanilla);
        // A full pack: every block and the crystal.
        Map<String, byte[]> full = new HashMap<>();
        for (int i = 0; i < 4; i++) {
            full.put("textures/block/b" + i + ".png", StylesTestPacks.png(16, 16, 0xFF00FF00 + i));
        }
        full.put("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFF00FF00));
        PackInfo f = PackScanner.scan("rp:Full.zip", "Full", PackInfo.Origin.RESOURCE_PACK, null, StylesTestPacks.zip(tmp, "full", full), vanilla);
        assertEquals(PackInfo.Kind.FULL, f.kind());
        Map<String, PackInfo> byKey = new LinkedHashMap<>();
        for (PackInfo p : List.of(a, b, mine, f)) {
            byKey.put(p.key(), p);
        }
        StylesIndex.Snapshot idx = new StylesIndex.Snapshot(vanilla, List.of(a, b, mine, f), byKey, 0L, 0);
        List<Choices.Tile> tiles = Choices.of(table.part("crystal"), idx, StyleSetup.EMPTY, 0x112233);
        // Your styles first, then the resource packs (the two identical ones once), Vanilla, Off, Sol's, the full pack last.
        assertEquals(Choices.Group.PART, tiles.get(0).group());
        assertEquals("Blue", tiles.get(0).label());
        assertEquals("Red", tiles.get(1).label());
        assertEquals(List.of("Red copy"), tiles.get(1).alsoIn(), "the same files in two packs: one tile, also in the other");
        assertEquals(Choices.Group.VANILLA, tiles.get(2).group());
        assertEquals(Choices.Group.OFF, tiles.get(3).group());
        assertEquals("builtin:see_through", tiles.get(4).source());
        assertEquals(Choices.Group.FULL, tiles.get(tiles.size() - 1).group());
        assertEquals(1, Choices.folded(tiles));
        // With the full pack as the base: it's the base tile, the game's own is Vanilla, and it isn't folded again.
        StyleSetup based = StyleSetup.EMPTY.withBase("rp:Full.zip");
        List<Choices.Tile> withBase = Choices.of(table.part("crystal"), idx, based, 0x112233);
        assertEquals(0, Choices.folded(withBase));
        Choices.Tile base = withBase.stream().filter(t -> t.group() == Choices.Group.BASE).findFirst().orElseThrow();
        assertEquals(StyleSetup.VANILLA, base.source());
        assertTrue(withBase.stream().anyMatch(t -> t.source().equals("game")));
        // A block can't go off and has no see-through.
        List<Choices.Tile> stone = Choices.of(table.part("stone"), idx, StyleSetup.EMPTY, 0);
        assertTrue(stone.stream().noneMatch(t -> t.group() == Choices.Group.OFF || t.source().equals("builtin:see_through")));
        // Sorting moves only the part packs: favourites first.
        List<Choices.Tile> sorted = Choices.sort(tiles, "favourites", t -> 2.0, t -> Integer.MAX_VALUE, Set.of(tiles.get(1).key()));
        assertEquals("Red", sorted.get(0).label());
        assertEquals(tiles.subList(2, tiles.size()), sorted.subList(2, sorted.size()));
        // The preview's other files come from the tile's pack, else the base, else the game.
        Choices.Tile redTile = tiles.get(1);
        assertInstanceOf(Mixer.FromPack.class, Choices.file(redTile, "textures/entity/end_crystal/end_crystal.png", table.part("crystal"), idx,
                StyleSetup.EMPTY, 0));
        assertInstanceOf(Mixer.FromGame.class, Choices.file(redTile, "textures/block/b0.png", table.part("crystal"), idx, StyleSetup.EMPTY, 0));
    }

    @Test
    void anchorPixelGlowAndFullChargeMark() throws IOException {
        PartTable table = new PartTable("test", List.of("anchor"), List.of("combat"), List.of(
                new PartTable.Part("anchor", "combat", "anchor", PartTable.Kind.BLOCK, false, true, false, false,
                        "textures/block/respawn_anchor_top.png", List.of("textures/block/respawn_anchor_top.png",
                        "textures/block/respawn_anchor_side4.png", "textures/block/respawn_anchor_side3.png"))));
        // A glow fading from bright to dim purple over a dark body.
        java.awt.image.BufferedImage an = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int v = 150 + x * 7;
                an.setRGB(x, y, y >= 4 && y < 12 ? 0xFF000000 | v / 2 << 16 | 0x10 << 8 | v : 0xFF201028);
            }
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(an, "png", bo);
        Map<String, byte[]> game = new LinkedHashMap<>();
        game.put("textures/block/respawn_anchor_top.png", bo.toByteArray());
        game.put("textures/block/respawn_anchor_side4.png", bo.toByteArray());
        game.put("textures/block/respawn_anchor_side3.png", bo.toByteArray());
        VanillaIndex vanilla = StylesTestPacks.vanilla(game);
        Path jar = StylesTestPacks.zip(tmp, "game", game);
        StyleSetup setup = StyleSetup.EMPTY.with("anchor", StyleSetup.Choice.DEFAULT.withExtra("pixel_glow", "true")
                .withExtra("mark", "true").withExtra("glow", "00ff00"));
        Mixer.Plan plan = Mixer.plan(setup, table, k -> null, vanilla, (p, path) -> null, 0);
        try (Builder b = new Builder(tmp.resolve("cache"), jar)) {
            Pixels.Image top = Builder.decode(b.read(plan.files().get("textures/block/respawn_anchor_top.png")));
            java.util.Set<Integer> glow = new java.util.HashSet<>();
            for (int y = 4; y < 12; y++) {
                for (int x = 0; x < 16; x++) {
                    glow.add(top.argb()[y * 16 + x]);
                }
            }
            assertTrue(glow.size() <= 4, "the glow is in at most four tones: " + glow.size());
            assertTrue(glow.size() >= 2, "and still steps from bright to dim");
            assertEquals(0xFF201028, top.argb()[0], "the dark body stays as it is");
            assertTrue(top.opaque(), "the anchor stays opaque");
            Pixels.Image full = Builder.decode(b.read(plan.files().get("textures/block/respawn_anchor_side4.png")));
            Pixels.Image three = Builder.decode(b.read(plan.files().get("textures/block/respawn_anchor_side3.png")));
            int edge = full.argb()[0 * 16 + 7];
            assertTrue((edge >> 8 & 255) > 200 && (edge >> 16 & 255) < 60, "the full side's frame is in the glow's colour");
            assertEquals(0xFF201028, three.argb()[0 * 16 + 7], "a side that isn't full has no frame");
            assertTrue(full.opaque(), "the marked side stays opaque");
        }
    }

    @Test
    void crystalFrameAndCoreAndAnchorGlowAreRecolouredApart() throws IOException {
        PartTable table = new PartTable("test", List.of("crystal", "anchor"), List.of("combat"), List.of(
                new PartTable.Part("crystal", "combat", "crystal", PartTable.Kind.ENTITY, true, true, false, false,
                        "textures/entity/end_crystal/end_crystal.png", List.of("textures/entity/end_crystal/end_crystal.png")),
                new PartTable.Part("anchor", "combat", "anchor", PartTable.Kind.BLOCK, false, true, false, false,
                        "textures/block/respawn_anchor_top.png", List.of("textures/block/respawn_anchor_top.png"))));
        Map<String, byte[]> game = new LinkedHashMap<>();
        game.put("textures/entity/end_crystal/end_crystal.png", StylesTestPacks.png(64, 32, 0xFF888888));
        // The anchor: dark body with a bright purple glow in the middle.
        java.awt.image.BufferedImage an = new java.awt.image.BufferedImage(16, 16, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                an.setRGB(x, y, x >= 6 && x < 10 && y >= 6 && y < 10 ? 0xFFB040FF : 0xFF201028);
            }
        }
        java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(an, "png", bo);
        game.put("textures/block/respawn_anchor_top.png", bo.toByteArray());
        VanillaIndex vanilla = StylesTestPacks.vanilla(game);
        Path jar = StylesTestPacks.zip(tmp, "game", game);
        StyleSetup setup = StyleSetup.EMPTY
                .with("crystal", StyleSetup.Choice.DEFAULT.withExtra("core", "ff0000"))
                .with("anchor", StyleSetup.Choice.DEFAULT.withExtra("glow", "00ff00"));
        Mixer.Plan plan = Mixer.plan(setup, table, k -> null, vanilla, (p, path) -> null, 0);
        try (Builder b = new Builder(tmp.resolve("cache"), jar)) {
            Pixels.Image crystal = Builder.decode(b.read(plan.files().get("textures/entity/end_crystal/end_crystal.png")));
            int frame = crystal.argb()[4 * 64 + 4], core = crystal.argb()[4 * 64 + 40];
            assertEquals(0xFF888888, frame, "the frame half keeps its colour");
            assertTrue((core >> 16 & 255) > (core & 255) + 40, "the core half turns red");
            Pixels.Image anchor = Builder.decode(b.read(plan.files().get("textures/block/respawn_anchor_top.png")));
            int glow = anchor.argb()[8 * 16 + 8], body = anchor.argb()[0];
            assertTrue((glow >> 8 & 255) > (glow >> 16 & 255) && (glow >> 8 & 255) > (glow & 255), "the charge glow turns green");
            assertEquals(0xFF201028, body, "the dark body stays as it is");
            assertTrue(anchor.opaque(), "the anchor stays opaque");
        }
    }

    @Test
    void solsOwnModelsTakeThePartsItemModels() throws IOException {
        PartTable table = table();
        Map<String, byte[]> game = new LinkedHashMap<>();
        game.put("textures/item/diamond_sword.png", StylesTestPacks.png(16, 16, 0xFF55FFFF));
        game.put("models/item/diamond_sword.json", "{}".getBytes());
        VanillaIndex vanilla = StylesTestPacks.vanilla(game);
        assertTrue(StyleModels.known("swords", "sol_sword"), "the mod ships its sword");
        StyleSetup setup = StyleSetup.EMPTY.with("swords", StyleSetup.Choice.DEFAULT.withShape(StyleSetup.Shape.MODEL, 1f, "sol_sword")
                .withColour(StyleSetup.Paint.RECOLOUR, 0, 1f, 1f, 0xFF2222));
        Mixer.Plan plan = Mixer.plan(setup, table, k -> null, vanilla, (p, path) -> null, 0);
        assertEquals(new Mixer.FromMod("styles/models/sol_sword.json"), plan.files().get("models/item/diamond_sword.json"));
        Mixer.Source tex = plan.files().get("cosmeticsmod:textures/item/styles/sol_sword.png");
        assertInstanceOf(Mixer.Made.class, tex, "the model's texture takes the part's colours");
        try (Builder b = new Builder(tmp.resolve("cache"), null)) {
            assertNotNull(b.read(new Mixer.FromMod("styles/models/sol_sword.json")), "Sol's model is read from the mod");
            Pixels.Image red = Builder.decode(b.read(tex));
            assertEquals(16, red.w());
        }
        // An unknown model changes nothing.
        StyleSetup unknown = StyleSetup.EMPTY.with("swords", StyleSetup.Choice.DEFAULT.withShape(StyleSetup.Shape.MODEL, 1f, "nope"));
        assertNull(Mixer.plan(unknown, table, k -> null, vanilla, (p, path) -> null, 0).files().get("models/item/diamond_sword.json"));
    }

    // ------------------------------------------------------------------ pictures and models

    @Test
    void keepOpaqueFillsClearPixels() {
        int[] px = {0xFF102030, 0x00000000, 0x80FF0000, 0x00000000};
        Pixels.Image out = Pixels.keepOpaque(new Pixels.Image(4, 1, px));
        assertTrue(out.opaque());
        assertEquals(0xFF102030, out.argb()[1], "a clear pixel takes the colour beside it");
    }

    @Test
    void lowFireKeepsFramesAndClearsTheTop() {
        int w = 16;
        int[] px = new int[w * w * 2];
        java.util.Arrays.fill(px, 0xFFFF8800);
        Pixels.Image out = Pixels.lowFire(new Pixels.Image(w, w * 2, px));
        assertEquals(w * 2, out.h(), "the animation's frames stay");
        assertEquals(0, out.argb()[3 * w + 5] >>> 24, "the top half is clear");
        assertEquals(255, out.argb()[(w - 1) * w + 5] >>> 24, "the bottom keeps the flames");
    }

    @Test
    void extrudedModelMergesRunsAndCountsQuads() {
        // A 16x16 texture with one solid row of 8 pixels and one lone pixel.
        int[] px = new int[16 * 16];
        for (int x = 4; x < 12; x++) {
            px[8 * 16 + x] = 0xFFFFFFFF;
        }
        px[2 * 16 + 2] = 0xFF00FF00;
        Extruder.Model m = Extruder.extrude(new Pixels.Image(16, 16, px), "minecraft:item/test", 1f, true);
        assertNotNull(m);
        assertEquals(2, m.json().getAsJsonArray("elements").size(), "a row of pixels is one box");
        // Each box: front, back, two ends, top and bottom.
        assertEquals(12, m.quads());
        assertNull(Extruder.extrude(new Pixels.Image(64, 64, new int[64 * 64]), "x", 1f, false), "big textures stay flat");
        JsonObject first = m.json().getAsJsonArray("elements").get(0).getAsJsonObject();
        assertEquals(2.0, first.getAsJsonArray("from").get(0).getAsDouble(), 1e-6);
    }

    @Test
    void modelDepsFollowTheParentChainAndListMissingTextures() {
        Map<String, String> pack = Map.of(
                "models/item/a.json", "{\"parent\":\"minecraft:item/b\",\"textures\":{\"layer0\":\"item/a_tex\"}}",
                "models/item/b.json", "{\"parent\":\"item/handheld\",\"textures\":{\"glow\":\"item/missing_tex\",\"x\":\"#layer0\"}}",
                "textures/item/a_tex.png", "png",
                "textures/item/a_tex.png.mcmeta", "{}");
        ModelDeps.Needs n = ModelDeps.walk("models/item/a.json", pack::get, p -> pack.containsKey(p) || p.equals("models/item/handheld.json"));
        assertTrue(n.files().containsAll(Set.of("models/item/a.json", "models/item/b.json", "textures/item/a_tex.png", "textures/item/a_tex.png.mcmeta")));
        assertEquals(Set.of("textures/item/missing_tex.png"), n.missing());
        // 1.21.4+ item definitions point at models.
        Map<String, String> items = Map.of("items/a.json", "{\"model\":{\"type\":\"minecraft:select\",\"cases\":[{\"model\":{\"type\":\"model\",\"model\":\"item/a\"}}]}}",
                "models/item/a.json", "{\"textures\":{\"layer0\":\"item/a\"}}");
        assertTrue(ModelDeps.walk("items/a.json", items::get, p -> true).files().contains("models/item/a.json"));
    }

    // ------------------------------------------------------------------ share codes

    @Test
    void shareCodesRoundTripAndListMissingPacks() {
        StyleSetup s = StyleSetup.EMPTY.withBase("rp:Bare Bones.zip")
                .with("crystal", StyleSetup.Choice.DEFAULT.withSource("pack:rp:red.zip").withColour(StyleSetup.Paint.RECOLOUR, 40, 0.8f, 1.2f, 0x00FFAA))
                .with("fire", StyleSetup.Choice.DEFAULT.withSource("builtin:low_fire"))
                .withLeaf("textures/item/apple.png", "pack:rp:blue.zip");
        Map<String, ShareCode.PackRef> refs = Map.of(
                "rp:Bare Bones.zip", new ShareCode.PackRef("Bare Bones", 1234),
                "rp:red.zip", new ShareCode.PackRef("red", 99),
                "rp:blue.zip", new ShareCode.PackRef("blue", 77));
        String code = ShareCode.encode(s, refs);
        assertTrue(code.startsWith("SOL1-") && code.length() < 600, code);
        // Here every pack is found (under other keys).
        ShareCode.Imported all = ShareCode.decode(code, r -> "rp:found/" + r.name());
        assertNotNull(all);
        assertEquals("rp:found/Bare Bones", all.setup().base());
        assertEquals("pack:rp:found/red", all.setup().choice("crystal").source());
        assertEquals(40, all.setup().choice("crystal").hue());
        assertEquals("builtin:low_fire", all.setup().choice("fire").source());
        assertTrue(all.missing().isEmpty());
        // Nothing found: vanilla, and the missing packs named.
        ShareCode.Imported none = ShareCode.decode(code, r -> null);
        assertNull(none.setup().base());
        assertEquals(StyleSetup.VANILLA, none.setup().choice("crystal").source());
        assertEquals(40, none.setup().choice("crystal").hue(), "colours come along without the pack");
        assertEquals(List.of("Bare Bones", "red", "blue"), none.missing());
        assertNull(ShareCode.decode("not a code", r -> null));
        assertNull(ShareCode.decode("SOL1-!!!", r -> null));
    }

    @Test
    void setupJsonRoundTrips() {
        StyleSetup s = StyleSetup.EMPTY.with("swords", StyleSetup.Choice.DEFAULT.withShape(StyleSetup.Shape.EXTRUDED, 2f, null)
                        .withExtra("glint", "true"))
                .withFavourite("swords|pack:rp:x", true).withSort("colour").withFullOpen(true);
        assertEquals(s, StyleSetup.fromJson(s.toJson()));
        assertEquals(StyleSetup.EMPTY, StyleSetup.fromJson(null));
        assertTrue(StyleSetup.EMPTY.with("crystal", StyleSetup.Choice.DEFAULT).isEmpty(), "a vanilla choice isn't kept");
    }

    // ------------------------------------------------------------------ helpers

    private static String text(Builder b, PackInfo p, String path) {
        try {
            byte[] d = b.read(Mixer.fromPack(p, path));
            return d == null ? null : new String(d, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    /** A small table: a crystal, swords and stone, as the real one has them. */
    private static PartTable table() {
        return new PartTable("test", List.of("crystal", "tools", "stone"), List.of("combat", "tools", "world"), List.of(
                new PartTable.Part("crystal", "combat", "crystal", PartTable.Kind.ENTITY, true, true, false, false,
                        "textures/entity/end_crystal/end_crystal.png", List.of("textures/entity/end_crystal/end_crystal.png")),
                new PartTable.Part("swords", "tools", "tools", PartTable.Kind.ITEM, false, true, true, false,
                        "textures/item/diamond_sword.png", List.of("models/item/diamond_sword.json", "textures/item/diamond_sword.png")),
                new PartTable.Part("stone", "world", "stone", PartTable.Kind.BLOCK, false, true, false, false,
                        "textures/block/stone.png", List.of("textures/block/stone.png"))));
    }
}
