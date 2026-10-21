package io.github.georgwav.cosmetics.devtools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.styles.Mixer;
import io.github.georgwav.cosmetics.sol.styles.StyleSetup;
import io.github.georgwav.cosmetics.sol.styles.Styles;
import io.github.georgwav.cosmetics.sol.styles.StylesIndex;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The Styles phase of the Sol scenario (B.4): the made-up test packs (tools/styles-packs, put in the game's
 * resourcepacks by ci/mcci.py) mounted; the full pack as the base; the crystal, the swords and the armour from the
 * red pack, fire and stone from the blue one; a scene with each of them in front of you; a colour changed live; the
 * sun from the sky pack; the Styles tab on Tools (the full packs folded, then open), on the Crystal, and Your look.
 * Each apply's path and time, the scan's and the tab's opening time go to {@code cosmeticsmod-styles-results.json}.
 * The setup and the scene are put back after.
 */
final class StylesScenario {
    static final String RED = "rp:Sol Test Red.zip", BLUE = "rp:Sol Test Blue.zip", SKY = "rp:Sol Test Sky.zip", FULL = "rp:Sol Test Full.zip",
            FULL2 = "rp:Sol Test Full Two.zip";
    /** How long a shot may wait for its apply (a reload under the software renderer can take a while), in ticks. */
    private static final int MAX_WAIT = 1800;

    private static StyleSetup before;
    private static net.minecraft.core.BlockPos scene;
    private static int waited;
    private static final JsonArray applies = new JsonArray();
    private static final List<String> problems = new ArrayList<>();

    private StylesScenario() {
    }

    static boolean isStyles(String what) {
        return what.startsWith("styles_") || what.startsWith("menu_styles");
    }

    /** Before a shot ({@code what} without "ui_"): its setup; the setup put back after the last Styles shot. */
    static void stage(String what) {
        if (!isStyles(what)) {
            if (before != null) {
                Styles.set(before);
                before = null;
            }
            return;
        }
        waited = 0;
        StylesIndex.Snapshot idx = Styles.index();
        if (before == null) {
            before = Styles.setup();
            if (idx == null || idx.get(RED) == null || idx.get(BLUE) == null || idx.get(FULL) == null) {
                problems.add("the Styles test packs aren't there (tools/styles-packs makes them)");
            }
        }
        StyleSetup mixed = StyleSetup.EMPTY.withBase(FULL)
                .with("crystal", StyleSetup.Choice.DEFAULT.withSource("pack:" + RED))
                .with("swords", StyleSetup.Choice.DEFAULT.withSource("pack:" + RED))
                .with("armor", StyleSetup.Choice.DEFAULT.withSource("pack:" + RED))
                .with("fire", StyleSetup.Choice.DEFAULT.withSource("pack:" + BLUE))
                .with("stone", StyleSetup.Choice.DEFAULT.withSource("pack:" + BLUE));
        switch (what) {
            case "styles_world", "menu_styles_tools", "menu_styles_tools_full", "menu_styles_look", "menu_styles_editor" -> Styles.set(mixed);
            case "menu_styles_tree" -> Styles.set(mixed.withLeaf("textures/block/amethyst_block.png", "pack:" + FULL2));
            case "styles_live" -> Styles.set(mixed.with("crystal", StyleSetup.Choice.DEFAULT.withSource("pack:" + RED)
                    .withColour(StyleSetup.Paint.RECOLOUR, 0, 1f, 1f, 0x33DDFF)));
            case "menu_styles_crystal" -> Styles.set(mixed.with("crystal", mixed.choice("crystal").withExtra("core", "theme")));
            case "styles_sky", "styles_packsky" -> Styles.set(mixed.with("sky", StyleSetup.Choice.DEFAULT.withSource("pack:" + SKY)));
            default -> {
            }
        }
    }

    /** Whether the shot must wait: the change is still being applied (or reloading). */
    static boolean waiting(String what) {
        if (!isStyles(what) || waited >= MAX_WAIT) {
            return false;
        }
        boolean busy = Styles.applying() || io.github.georgwav.cosmetics.sol.render.Mc.overlay() != null || Styles.index() == null;
        if (busy) {
            waited++;
            if (waited == MAX_WAIT) {
                problems.add(what + ": the change wasn't applied after " + MAX_WAIT / 20 + " s");
            }
        }
        return busy;
    }

    /** The scene in front of you (facing west): stone, fire on netherrack, a crystal on obsidian, an armour stand in diamond. */
    static void scene(Minecraft mc, Consumer<List<String>> run) {
        if (mc.player == null || scene != null) {
            return;
        }
        var p = mc.player.blockPosition();
        scene = p;
        int x = p.getX(), y = p.getY(), z = p.getZ();
        List<String> c = new ArrayList<>();
        c.add(String.format(Locale.ROOT, "fill %d %d %d %d %d %d stone", x - 7, y, z + 2, x - 7, y + 1, z + 3));
        c.add(String.format(Locale.ROOT, "setblock %d %d %d netherrack", x - 6, y, z - 3));
        c.add(String.format(Locale.ROOT, "setblock %d %d %d fire", x - 6, y + 1, z - 3));
        c.add(String.format(Locale.ROOT, "setblock %d %d %d obsidian", x - 6, y, z));
        c.add(String.format(Locale.ROOT, "summon end_crystal %.1f %.1f %.1f {ShowBottom:0b}", x - 5.5, y + 1.0, z + 0.5));
        c.add(String.format(Locale.ROOT, "summon armor_stand %.1f %.1f %.1f {Rotation:[-90f,0f]}", x - 4.5, (double) y, z + 2.5));
        for (String[] s : new String[][]{{"armor.head", "diamond_helmet"}, {"armor.chest", "diamond_chestplate"},
                {"armor.legs", "diamond_leggings"}, {"armor.feet", "diamond_boots"}}) {
            c.add("item replace entity @e[type=armor_stand,limit=1,sort=nearest] " + s[0] + " with " + s[1]);
        }
        c.add("item replace entity @s weapon.mainhand with diamond_sword");
        run.accept(c);
    }

    /** At the shot: what the apply did, and whether it did what it should. */
    static void shot(String what) {
        if (!isStyles(what)) {
            return;
        }
        JsonObject a = new JsonObject();
        a.addProperty("shot", what);
        a.addProperty("path", Styles.lastApply);
        a.addProperty("ms", Styles.lastApplyMillis);
        a.addProperty("liveWrites", Styles.liveWrites);
        a.addProperty("reloads", Styles.reloads);
        a.addProperty("mixFiles", Styles.plan().files().size());
        applies.add(a);
        if (what.equals("styles_world")) {
            Mixer.Source crystal = Styles.plan().files().get("textures/entity/end_crystal/end_crystal.png");
            if (!(crystal instanceof Mixer.FromPack fp) || !fp.pack().equals(RED)) {
                problems.add(what + ": the crystal isn't the red pack's (" + crystal + ")");
            }
            if (Styles.reloads == 0) {
                problems.add(what + ": the base pack never reloaded");
            }
            // The mix over the base in the game's pack list (the parts picked win over the base's).
            List<String> ids = new ArrayList<>(Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
            if (ids.indexOf(Styles.MIX_ID) < ids.indexOf(Styles.BASE_ID)) {
                problems.add(what + ": the Styles mix is under the base pack (" + ids + ")");
            }
        }
        if (what.equals("styles_packsky") && io.github.georgwav.cosmetics.sol.atmosphere.PackSky.drawn == 0) {
            problems.add(what + ": the sky pack's skybox wasn't drawn in Sol's sky");
        }
        if (what.equals("styles_live") && !"live".equals(Styles.lastApply)) {
            problems.add(what + ": the colour change wasn't written live (" + Styles.lastApply + ")");
        }
    }

    /** After the scenario: the scene taken away, the setup put back, the results written. */
    static List<String> finish(Minecraft mc, Consumer<List<String>> run) {
        if (scene != null) {
            int x = scene.getX(), y = scene.getY(), z = scene.getZ();
            run.accept(List.of("kill @e[type=end_crystal]", "kill @e[type=armor_stand]",
                    String.format(Locale.ROOT, "fill %d %d %d %d %d %d air", x - 7, y, z - 3, x - 4, y + 2, z + 3)));
            scene = null;
        }
        if (before != null) {
            Styles.set(before);
            before = null;
        }
        if (applies.isEmpty() && problems.isEmpty()) {
            return List.of();
        }
        JsonObject out = new JsonObject();
        Styles.stats().forEach((k, v) -> out.addProperty(k, String.valueOf(v)));
        out.add("applies", applies);
        JsonArray pr = new JsonArray();
        problems.forEach(pr::add);
        out.add("problems", pr);
        try {
            Files.writeString(mc.gameDirectory.toPath().resolve("cosmeticsmod-styles-results.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(out));
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't write the Styles results: {}", e.toString());
        }
        CosmeticsMod.LOGGER.info("[Sol] Styles scenario: {}", out);
        return List.copyOf(problems);
    }
}
