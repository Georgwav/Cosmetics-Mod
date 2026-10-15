package io.github.georgwav.cosmetics.sol.compat;

import io.github.georgwav.cosmetics.CosmeticsMod;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

/**
 * "Sol for kept mods": a built-in resource pack, on by default, giving mods Sol keeps their Sol look: Jade's tooltip
 * as Sol glass and Simple Voice Chat's microphone and speaker icons as Sol line icons (all drawn by
 * tools/sol-assets/KeptModIcons.java, nothing taken from the mods). Turned off like any pack.
 */
public final class KeptModsPack {
    private KeptModsPack() {
    }

    public static void register() {
        FabricLoader.getInstance().getModContainer(CosmeticsMod.MOD_ID).ifPresent(mod -> {
            //? if >=1.21.11 {
            var id = net.minecraft.resources.Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "sol_kept_mods");
            //?} else {
            /*var id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "sol_kept_mods");
            *///?}
            ResourceManagerHelper.registerBuiltinResourcePack(id, mod, Component.translatable("sol.pack.kept_mods"),
                    ResourcePackActivationType.DEFAULT_ENABLED);
        });
    }
}
