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
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
*///?}

/**
 * Every registration that goes through a Minecraft or Fabric API that changed between versions.
 * Only Fabric API hooks are used here; the mod has no mixins.
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
