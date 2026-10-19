package io.github.georgwav.cosmetics.melee;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The knives the player dropped into {@code client/melee} in the main folder, as a resource pack the game loads with
 * the others: mounted by {@link GunpackMount} each time the game looks for packs (at start and on F3+T; an unchanged
 * folder isn't mounted again), always on and above the other packs. Nothing when there are no knives.
 */
public final class KnifePacks {
    /** The pack's id in the game's pack list. */
    public static final String ID = "cosmeticsmod:knives";
    /** Dev runs: write the stand-in knife into the folder, so CI has a knife without any real pack. */
    private static final boolean STAND_IN = Boolean.getBoolean("cosmeticsmod.standInKnife");

    private KnifePacks() {
    }

    /** Dev runs: another knives folder (local checks with real packs, which never go into CI). */
    private static final String FOLDER = System.getProperty("cosmeticsmod.knivesFolder", "");

    /** Where players drop their gunpacks. */
    public static Path folder() {
        return FOLDER.isEmpty() ? MainFolder.clientFolder().resolve("melee") : Path.of(FOLDER);
    }

    /** Mounts the knives and hands their pack to the game, when there are any. Never throws. */
    public static void load(Consumer<Pack> out) {
        Path mounted;
        try {
            Path folder = folder();
            if (STAND_IN && !Files.exists(folder.resolve("StandIn"))) {
                StandInKnife.write(folder);
            }
            GunpackMount.Result[] result = new GunpackMount.Result[1];
            mounted = GunpackMount.mount(folder, result);
            GunpackMount.Result r = result[0];
            if (r != null && (!r.cosmetics().isEmpty() || !r.problems().isEmpty())) {
                CosmeticsMod.LOGGER.info("Knives: mounted {} from {}", r.cosmetics().size(), folder);
                for (String problem : r.problems()) {
                    CosmeticsMod.LOGGER.warn("Knives: {}", problem);
                }
            }
        } catch (Exception e) {
            CosmeticsMod.LOGGER.warn("Knives: couldn't mount the packs in {}: {}", folder(), e.toString());
            return;
        }
        if (mounted == null) {
            return;
        }
        Pack pack = Pack.readMetaAndCreate(
                new PackLocationInfo(ID, Component.translatable("sol.pack.knives"), PackSource.BUILT_IN, Optional.empty()),
                new PathPackResources.PathResourcesSupplier(mounted), PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));
        if (pack != null) {
            out.accept(pack);
        }
    }
}
