package io.github.georgwav.cosmetics.client;

import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.client.skins.OwnSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import java.util.ArrayList;
import java.util.List;
//? if >=26.3 {
/*import net.minecraft.client.ClientClockManager;
*///?}

/**
 * For the in-game test: switches the world features on (Atmosphere's thunder and midnight locks, fullbright with night vision), checks the
 * game shows them, then switches them off and checks everything went back.
 */
public final class ModulesSelfTest {
    private static double gammaBefore;

    private ModulesSelfTest() {
    }

    public static void switchOn(Minecraft mc) {
        gammaBefore = mc.options.gamma().get();
        // Atmosphere's time and weather locks (the Time and Weather modules moved there).
        setting(Modules.ATMOSPHERE, "weather").set("thunder");
        setting(Modules.ATMOSPHERE, "time").set(18000.0);
        setting(Modules.ATMOSPHERE, "lock_weather").set(true);
        setting(Modules.ATMOSPHERE, "lock_time").set(true);
        setting(Modules.FULLBRIGHT, "night_vision").set("always");
        Modules.FULLBRIGHT.setEnabled(true);
        // A diamond in the inventory's top row, for the hotbar rows key.
        command(mc, "item replace entity @s inventory.0 with minecraft:diamond");
        // World Overlays' structure outlines, with a made-up desert pyramid starting in the chunk next to you.
        overlaysWereOn = Modules.WORLD_OVERLAYS.enabled();
        structuresWereOn = ModulesSelfTest.<Boolean>setting(Modules.WORLD_OVERLAYS, "structures").get();
        ModulesSelfTest.<Boolean>setting(Modules.WORLD_OVERLAYS, "structures").set(true);
        Modules.WORLD_OVERLAYS.setEnabled(true);
        scansBefore = io.github.georgwav.cosmetics.client.modules.WorldOverlays.structureScans;
        plantPyramid(mc, true);
    }

    private static boolean overlaysWereOn;
    private static boolean structuresWereOn;
    private static int scansBefore;
    /** Where the made-up pyramid's box starts (its corner block), once planted. */
    private static volatile int pyramidX = Integer.MIN_VALUE, pyramidZ;
    private static java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, net.minecraft.world.level.levelgen.structure.StructureStart> startsBefore;
    private static java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, it.unimi.dsi.fastutil.longs.LongSet> referencesBefore;

    /**
     * On the world's server: a desert pyramid's start (with its one piece) in the chunk east of you and a reference
     * to it there, as world generation would leave them (the test world makes no structures); or, with
     * {@code plant} false, the chunk's own starts and references back.
     */
    private static void plantPyramid(Minecraft mc, boolean plant) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        var dimension = mc.level.dimension();
        int cx = (mc.player.blockPosition().getX() >> 4) + 1, cz = mc.player.blockPosition().getZ() >> 4;
        server.execute(() -> {
            var level = server.getLevel(dimension);
            var chunk = level == null ? null : level.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                return;
            }
            if (!plant) {
                if (startsBefore != null) {
                    chunk.setAllStarts(startsBefore);
                    chunk.setAllReferences(referencesBefore);
                    startsBefore = null;
                }
                return;
            }
            var structure = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .get(net.minecraft.world.level.levelgen.structure.BuiltinStructures.DESERT_PYRAMID).orElseThrow().value();
            startsBefore = new java.util.HashMap<>(chunk.getAllStarts());
            referencesBefore = new java.util.HashMap<>(chunk.getAllReferences());
            var piece = new net.minecraft.world.level.levelgen.structure.structures.DesertPyramidPiece(
                    net.minecraft.util.RandomSource.create(1L), cx << 4, cz << 4);
            var pos = new net.minecraft.world.level.ChunkPos(cx, cz);
            chunk.setStartForStructure(structure, new net.minecraft.world.level.levelgen.structure.StructureStart(structure, pos, 0,
                    new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(java.util.List.of(piece))));
            //? if >=26.1 {
            /*chunk.addReferenceForStructure(structure, pos.pack());
            *///?} else {
            chunk.addReferenceForStructure(structure, pos.toLong());
            //?}
            pyramidZ = piece.getBoundingBox().minZ();
            pyramidX = piece.getBoundingBox().minX();
        });
    }

    private static boolean inventoryWasOn;

    /** Midway, once the diamond is there: the hotbar rows key, so the diamond comes down to the hotbar. */
    public static void midway(Minecraft mc) {
        inventoryWasOn = Modules.INVENTORY.enabled();
        Modules.INVENTORY.setEnabled(true);
        rotated = io.github.georgwav.cosmetics.client.modules.InventoryTweaks.rotateRows(mc);
    }

    private static boolean rotated;

    /** Projectile trails on, and a snowball in your hand to throw. */
    public static void trailsOn(Minecraft mc) {
        ModulesSelfTest.<String>setting(Modules.COSMETICS, "projectile_trails").set("sparkle");
        command(mc, "item replace entity @s weapon.mainhand with minecraft:snowball 4");
    }

    /** Throws the snowball (as a right click would) once it has reached your hand; true when thrown. */
    public static boolean throwSnowball(Minecraft mc) {
        if (mc.gameMode == null || mc.player == null || !mc.player.getMainHandItem().is(net.minecraft.world.item.Items.SNOWBALL)) {
            return false;
        }
        mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        return true;
    }

    /** Whether the snowball left a trail; then the trails go off and the snowballs go. */
    public static java.util.List<String> trailsCheckAndOff(Minecraft mc) {
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (io.github.georgwav.cosmetics.client.modules.CosmeticsModule.trailsMade <= 0) {
            problems.add("the thrown snowball left no trail");
        }
        ModulesSelfTest.<String>setting(Modules.COSMETICS, "projectile_trails").reset();
        command(mc, "clear @s minecraft:snowball");
        return problems;
    }

    private static void command(Minecraft mc, String command) {
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        String name = mc.player.getName().getString();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                command.replace("@s", name)));
    }

    /** What's wrong while they're on; then switches them off and adds what didn't go back. */
    public static List<String> checkAndSwitchOff(Minecraft mc) {
        List<String> problems = new ArrayList<>();
        if (mc.level == null || mc.player == null) {
            problems.add("no world to check the modules in");
            return problems;
        }
        if (mc.level.getRainLevel(1f) < 0.99f || mc.level.getThunderLevel(1f) < 0.99f) {
            problems.add("the weather changer's thunder doesn't show (rain " + mc.level.getRainLevel(1f) + ")");
        }
        //? if >=26.3 {
        /*long clock = new ClientClockManager.ClientClockInstance().totalTicks();
        if (clock != 18000L) {
            problems.add("the time changer's midnight doesn't show on the world clocks (" + clock + ")");
        }
        *///?} elif >=26.1 {
        //?} else {
        if (mc.level.getDayTime() != 18000L) {
            problems.add("the time changer's midnight doesn't show (day time " + mc.level.getDayTime() + ")");
        }
        //?}
        if (mc.options.gamma().get() < 9.9) {
            problems.add("fullbright didn't raise the brightness (" + mc.options.gamma().get() + ")");
        }
        if (!mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            problems.add("fullbright's night vision isn't on");
        }
        if (!rotated) {
            problems.add("the hotbar rows key didn't swap (no inventory to swap in)");
        } else if (!mc.player.getInventory().getItem(0).is(net.minecraft.world.item.Items.DIAMOND)) {
            problems.add("the hotbar rows key didn't bring the top row's diamond to the hotbar (it holds "
                    + mc.player.getInventory().getItem(0) + ")");
        }
        // Three more turns put every row back; then the diamond goes.
        for (int i = 0; i < 3 && rotated; i++) {
            io.github.georgwav.cosmetics.client.modules.InventoryTweaks.rotateRows(mc);
        }
        command(mc, "item replace entity @s inventory.0 with minecraft:air");
        Modules.INVENTORY.setEnabled(inventoryWasOn);
        int[] boxes = io.github.georgwav.cosmetics.client.modules.WorldOverlays.structureBoxes();
        if (pyramidX == Integer.MIN_VALUE) {
            problems.add("the made-up desert pyramid wasn't planted for World Overlays' structures");
        } else if (io.github.georgwav.cosmetics.client.modules.WorldOverlays.structureScans == scansBefore) {
            problems.add("World Overlays didn't look for structures");
        } else if (boxes.length != 6 || boxes[0] != pyramidX || boxes[2] != pyramidZ) {
            problems.add("World Overlays didn't find the desert pyramid at " + pyramidX + ", " + pyramidZ + " (found "
                    + java.util.Arrays.toString(boxes) + ")");
        }
        plantPyramid(mc, false);
        ModulesSelfTest.<Boolean>setting(Modules.WORLD_OVERLAYS, "structures").set(structuresWereOn);
        Modules.WORLD_OVERLAYS.setEnabled(overlaysWereOn);
        Modules.FULLBRIGHT.setEnabled(false);
        setting(Modules.ATMOSPHERE, "lock_time").reset();
        setting(Modules.ATMOSPHERE, "lock_weather").reset();
        setting(Modules.ATMOSPHERE, "time").reset();
        setting(Modules.ATMOSPHERE, "weather").reset();
        if (Math.abs(mc.options.gamma().get() - gammaBefore) > 1e-6) {
            problems.add("fullbright didn't put the brightness back (" + mc.options.gamma().get() + ", was " + gammaBefore + ")");
        }
        if (mc.player.hasEffect(MobEffects.NIGHT_VISION)) {
            problems.add("fullbright's night vision stayed on after switching it off");
        }
        return problems;
    }

    @SuppressWarnings("unchecked")
    private static <T> Setting<T> setting(Module m, String id) {
        for (Setting<?> s : m.settings) {
            if (s.id.equals(id)) {
                return (Setting<T>) s;
            }
        }
        throw new IllegalArgumentException(m.id + " has no setting " + id);
    }

    /** Every HUD module and the crosshair on (and a potion effect for the effect timers to show), for a screenshot. */
    private static float pitchBefore;

    /**
     * The HUD modules all on, the crosshair, a raised hotbar, and a red block outline: the player looks down at the
     * ground in front so there is a block to outline.
     */
    public static void switchOnHud(Minecraft mc) {
        ModulesSelfTest.<Boolean>setting(Modules.COORDINATES, "biome").set(true);
        ModulesSelfTest.<String>setting(Modules.STATS, "font").set("geist");
        ModulesSelfTest.<String>setting(Modules.STATS, "color").set("yellow");
        ModulesSelfTest.<Boolean>setting(Modules.STATS, "ping").set(true);
        ModulesSelfTest.<Boolean>setting(Modules.STATS, "clock").set(true);
        if (mc.player != null) {
            mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 95, 0));
            pitchBefore = mc.player.getXRot();
            mc.player.setXRot(30f);
        }
        ModulesSelfTest.<String>setting(Modules.OUTLINE, "color").set("red");
        ModulesSelfTest.<Double>setting(Modules.OUTLINE, "opacity").set(1.0);
        Modules.OUTLINE.setEnabled(true);
        for (Module m : Modules.hud()) {
            m.setEnabled(true);
        }
        Modules.CROSSHAIR.setEnabled(true);
        // The game's hotbar with its hearts, armor, hunger and air raised a little, as the HUD editor would.
        for (VanillaHud.Part part : new VanillaHud.Part[]{VanillaHud.Part.HOTBAR, VanillaHud.Part.HEALTH,
                VanillaHud.Part.ARMOR, VanillaHud.Part.FOOD, VanillaHud.Part.AIR}) {
            VanillaHud.move(part, 0f, -30f);
        }
    }

    public static void switchOffHud(Minecraft mc) {
        for (Module m : Modules.hud()) {
            m.setEnabled(false);
        }
        Modules.CROSSHAIR.setEnabled(false);
        VanillaHud.reset();
        ModulesSelfTest.<Boolean>setting(Modules.COORDINATES, "biome").reset();
        ModulesSelfTest.<String>setting(Modules.STATS, "font").reset();
        ModulesSelfTest.<String>setting(Modules.STATS, "color").reset();
        ModulesSelfTest.<Boolean>setting(Modules.STATS, "ping").reset();
        ModulesSelfTest.<Boolean>setting(Modules.STATS, "clock").reset();
        Modules.OUTLINE.setEnabled(false);
        ModulesSelfTest.<String>setting(Modules.OUTLINE, "color").reset();
        ModulesSelfTest.<Double>setting(Modules.OUTLINE, "opacity").reset();
        if (mc.player != null) {
            mc.player.setXRot(pitchBefore);
            mc.player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }

    /** Wears a plain test skin with slim arms the way Apply does, checks your player has it, and takes it off. */
    public static List<String> checkOwnSkin(Minecraft mc) {
        List<String> problems = new ArrayList<>();
        if (mc.player == null) {
            problems.add("no player to wear a skin");
            return problems;
        }
        int[] argb = new int[64 * 64];
        java.util.Arrays.fill(argb, 0xFF3A7BD5);
        OwnSkin.set(argb, true);
        if (!OwnSkin.isWorn(mc.player.getSkin())) {
            problems.add("an applied skin doesn't show on your own player");
        }
        OwnSkin.clear();
        if (OwnSkin.isWorn(mc.player.getSkin())) {
            problems.add("the applied skin stayed on after taking it off");
        }
        return problems;
    }
}
