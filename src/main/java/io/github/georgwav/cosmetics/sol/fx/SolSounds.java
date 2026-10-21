package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Sol's sounds for the effects and the UI (C.3): six packs of Sol's own, synthesised by tools/sol-sounds (Tick, Glass,
 * Thock, Chime, Pop, Zap), each with a hit, crit, kill, pop, click and toggle; and your own packs, folders of Ogg files
 * with the same names in {@code client/sounds/<pack>/}, mounted as a small resource pack of their own. Played through
 * the game's sound engine in the Players category, heard only by you.
 */
public final class SolSounds {
    public static final List<String> PACKS = List.of("tick", "glass", "thock", "chime", "pop", "zap");
    public static final List<String> EVENTS = List.of("hit", "crit", "kill", "pop", "click", "toggle");
    private static final String PACK_ID = "cosmeticsmod:sol_sounds_yours";
    private static final RandomSource RANDOM = RandomSource.create();
    /** When a kill sound last played (ns): one kill makes one kill sound, whichever module plays it first. */
    private static long lastKill;
    private static List<String> yours;

    private SolSounds() {
    }

    /** Your packs' folder: {@code client/sounds}. */
    public static Path folder() {
        return MainFolder.clientFolder().resolve("sounds");
    }

    /** Your packs, by folder name (read once; a new folder shows after a restart). */
    public static synchronized List<String> yours() {
        if (yours == null) {
            List<String> found = new ArrayList<>();
            Path dir = folder();
            if (Files.isDirectory(dir)) {
                try (Stream<Path> s = Files.list(dir)) {
                    s.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                            .filter(n -> n.matches("[a-z0-9_.-]{1,32}")).sorted().limit(16).forEach(found::add);
                } catch (IOException e) {
                    CosmeticsMod.LOGGER.debug("[Sol] Sounds: couldn't list {}", dir, e);
                }
            }
            yours = List.copyOf(found);
        }
        return yours;
    }

    /** A sound setting's choices: Sol's packs, yours ("yours_<name>"), and off. */
    public static List<String> choices() {
        List<String> c = new ArrayList<>(PACKS);
        for (String y : yours()) {
            c.add("yours_" + y);
        }
        c.add("off");
        return List.copyOf(c);
    }

    /** Plays {@code event} from the pack a sound setting picked, at {@code volume} (1 = 100%) and {@code pitch}. */
    public static void play(String choice, String event, float volume, float pitch) {
        if (choice == null || choice.equals("off") || volume <= 0f) {
            return;
        }
        if (event.equals("kill")) {
            long now = System.nanoTime();
            if (now - lastKill < 150_000_000L) {
                return;
            }
            lastKill = now;
        }
        String path = choice.startsWith("yours_") ? "user." + choice.substring(6) + "." + event : "sol." + choice + "." + event;
        try {
            //? if >=1.21.11 {
            Identifier id = Identifier.fromNamespaceAndPath("cosmeticsmod", path);
            //?} else {
            /*ResourceLocation id = ResourceLocation.fromNamespaceAndPath("cosmeticsmod", path);
            *///?}
            SoundInstance sound = new SimpleSoundInstance(id, SoundSource.PLAYERS, Math.min(2f, volume),
                    Math.max(0.5f, Math.min(2f, pitch)), RANDOM, false, 0, SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            Minecraft.getInstance().getSoundManager().play(sound);
        } catch (RuntimeException e) {
            CosmeticsMod.LOGGER.debug("[Sol] Sound {}: {}", path, e.toString());
        }
    }

    /** Mounts your packs (with the resource packs, on each reload). */
    public static void addPack(Consumer<Pack> out) {
        List<String> packs = yours();
        if (packs.isEmpty()) {
            return;
        }
        try {
            Path root = MainFolder.clientFolder().resolve("cache").resolve("sounds_pack");
            Path sounds = root.resolve("assets/cosmeticsmod/sounds/user");
            Files.createDirectories(sounds);
            Files.writeString(root.resolve("pack.mcmeta"), """
                    {
                      "pack": {
                        "description": "Your Sol sounds",
                        "pack_format": 34,
                        "supported_formats": [34, 999],
                        "min_format": 34,
                        "max_format": 999
                      }
                    }
                    """);
            StringBuilder json = new StringBuilder("{\n");
            boolean first = true;
            for (String pack : packs) {
                Path dir = sounds.resolve(pack);
                Files.createDirectories(dir);
                for (String event : EVENTS) {
                    Path from = folder().resolve(pack).resolve(event + ".ogg");
                    if (!Files.isRegularFile(from)) {
                        continue;
                    }
                    Files.copy(from, dir.resolve(event + ".ogg"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    json.append(first ? "" : ",\n").append(String.format(Locale.ROOT,
                            "  \"user.%s.%s\": {\"sounds\": [\"cosmeticsmod:user/%s/%s\"]}", pack, event, pack, event));
                    first = false;
                }
            }
            json.append("\n}\n");
            Files.writeString(root.resolve("assets/cosmeticsmod/sounds.json"), json.toString());
            Pack pack = Pack.readMetaAndCreate(new PackLocationInfo(PACK_ID, Component.literal("Your Sol sounds"), PackSource.BUILT_IN,
                    Optional.empty()), new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(root),
                    PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
            if (pack != null) {
                out.accept(pack);
            }
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Sounds: couldn't mount your sound packs: {}", e.toString());
        }
    }
}
