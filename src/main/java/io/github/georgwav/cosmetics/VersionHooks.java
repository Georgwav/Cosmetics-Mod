package io.github.georgwav.cosmetics;

import io.github.georgwav.cosmetics.model.CosmeticLoader;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.CosmeticsLayer;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
//? if <1.21.9 {
/*import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
*///?} else {
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
//?}
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} elif >=1.21.9 {
/*import net.minecraft.resources.ResourceLocation;
*///?}
//? if <26.1 {
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
*///?}
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.KeyMapping;

/**
 * Every registration that goes through a Minecraft or Fabric API that changed between versions.
 * Only Fabric API hooks are used here; the few mixins are in the {@code mixin} package.
 */
final class VersionHooks {
    private VersionHooks() {
    }

    /** Adds {@link CosmeticsLayer} to the vanilla player renderers. */
    static void registerRenderLayer() {
        //? if <26.1 {
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
        //?} else {
        /*LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, renderer, helper, context) -> {
        *///?}
            //? if <1.21.2 {
            /*if (renderer instanceof PlayerRenderer playerRenderer) {
                helper.register(new CosmeticsLayer(playerRenderer));
            }
            *///?} elif <1.21.9 {
            /*if (renderer instanceof PlayerRenderer playerRenderer) {
                helper.register(new CosmeticsLayer<>(playerRenderer));
            }
            *///?} else {
            if (renderer instanceof AvatarRenderer<?> avatarRenderer) {
                helper.register(new CosmeticsLayer<>(avatarRenderer));
            }
            //?}
        });
    }

    /**
     * Hides the vanilla cape only for players who wear one of our capes AND whose cape slot the user set
     * to OVERRIDE. With the default (DEFER) this never hides anything.
     */
    static void registerCapeOverride() {
        //? if <1.21.2 {
        /*LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(player ->
                !CosmeticRenderer.shouldHideVanillaCape(player.getUUID(), player == Minecraft.getInstance().player));
        *///?} else {
        LivingEntityFeatureRenderEvents.ALLOW_CAPE_RENDER.register(state -> {
            var level = Minecraft.getInstance().level;
            Entity entity = level == null ? null : level.getEntity(state.id);
            return entity == null
                    || !CosmeticRenderer.shouldHideVanillaCape(entity.getUUID(), entity == Minecraft.getInstance().player);
        });
        //?}
    }

    /** "Open the hub" key: Right Shift, like other clients' menus. /cosmetics and Mod Menu open the settings. */
    static KeyMapping registerKeyMapping() {
        return registerKeyMapping("key.cosmeticsmod.open", InputConstants.KEY_RSHIFT);
    }

    /** "Emote wheel" key, B by default like Lunar's (vanilla uses B only with Ctrl, for the narrator). */
    static KeyMapping registerEmoteKeyMapping() {
        return registerKeyMapping("key.cosmeticsmod.emotes", InputConstants.KEY_B);
    }

    private static KeyMapping registerKeyMapping(String name, int defaultKey) {
        //? if <1.21.9 {
        /*KeyMapping key = new KeyMapping(name, InputConstants.Type.KEYSYM, defaultKey, "key.categories.cosmeticsmod");
        *///?} else {
        KeyMapping key = new KeyMapping(name, defaultKey, KeyMapping.Category.MISC);
        //?}
        //? if <26.1 {
        return KeyBindingHelper.registerKeyBinding(key);
        //?} else {
        /*return KeyMappingHelper.registerKeyMapping(key);
        *///?}
    }

    /**
     * {@code /cosmetics} opens the settings screen. Built with plain Brigadier rather than Fabric's
     * command helper class, which 26.1 renamed.
     */
    static void registerCommand() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommand(dispatcher));
    }

    private static <S> void registerCommand(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("cosmetics").executes(context -> {
            ScreenOpener.requestOpen();
            return 1;
        }));
    }

    /** Loads cosmetics from resource packs on startup and on every F3+T. */
    static void registerReloadListener() {
        //? if <1.21.9 {
        /*ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "cosmetics");
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return id;
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                CosmeticLoader.reload(manager);
            }
        });
        *///?} elif <1.21.11 {
        /*ResourceLocation id = ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "cosmetics");
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(id, reloadListener());
        *///?} elif <26.1 {
        Identifier id = Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "cosmetics");
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloader(id, reloadListener());
        //?} else {
        /*Identifier id = Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "cosmetics");
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(id, reloadListener());
        *///?}
    }

    //? if >=1.21.9 {
    private static ResourceManagerReloadListener reloadListener() {
        return new ResourceManagerReloadListener() {
            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                CosmeticLoader.reload(manager);
            }
        };
    }
    //?}
}
