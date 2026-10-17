package io.github.georgwav.cosmetics.mixin;

import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Picks this Minecraft version's mixins: the game draws held items differently across 1.21 and 26.x, so each
 * version gets the hooks that fit it (see {@link io.github.georgwav.cosmetics.render.HeldItems}); every version
 * gets the player model and player renderer hooks for emotes ({@link io.github.georgwav.cosmetics.emote.EmotePlayer},
 * {@link io.github.georgwav.cosmetics.render.ElytraUpright}) and the elytra layer and model hooks
 * ({@link io.github.georgwav.cosmetics.render.ElytraLook}). The mixin config lists none itself.
 */
public final class CosmeticsMixinPlugin implements IMixinConfigPlugin {
    @Override
    public List<String> getMixins() {
        List<String> all = new java.util.ArrayList<>(base());
        all.add("SolOverlayMixin");
        all.add("SolCloudsMixin");
        all.add("SolGameRendererMixin");
        all.add("TitleSwapMixin");
        all.add("LoadingLookMixin");
        all.add("ButtonLookMixin");
        all.add("TooltipLookMixin");
        all.add("PauseLookMixin");
        all.add("DeathLookMixin");
        all.add("ListLookMixin");
        all.add("PackFoldersMixin");
        all.add("TotemSizeMixin");
        all.add("WaterOverlayMixin");
        all.add("InventoryDimMixin");
        all.add("NametagLookMixin");
        all.add("NametagHealthMixin");
        all.add("NametagShadowMixin");
        all.add("BeaconLevelsAccess");
        all.add("HitboxColorMixin");
        all.add("HitboxWidthMixin");
        all.add("HitboxFilterMixin");
        all.add("FoodDataMixin");
        all.add("SolBossBarMixin");
        all.add("SolTabListMixin");
        all.add("TabListTweaksMixin");
        all.add("HudTweaksMixin");
        all.add("BossBarTweaksMixin");
        all.add("PlayerInfoSkinMixin");
        all.add("SolHudLookMixin");
        all.add("TotemPopMixin");
        all.add("SolChatMixin");
        all.add("ChatSendMixin");
        all.add("CameraTweaksMixin");
        all.add("FreelookTurnMixin");
        all.add("ViewBobMixin");
        all.add("LowFireMixin");
        all.add("OverlayTextureAccessor");
        all.add("HeldItemsMixin");
        all.add("ScreenOverlayMixin");
        all.add("NametagMixin");
        all.add("ParticleTweaksMixin");
        all.add("ScreenshotMixin");
        all.add("ItemDecorationsMixin");
        all.add("DisconnectedScreenMixin");
        //? if >=1.21.11 {
        all.add("SolChatDrawMixin");
        //?} else {
        /*all.add("SolChatLookMixin");
        *///?}
        //? if <1.21.11 {
        /*all.add("SolLightMixin");
        *///?}
        //? if <26.1 {
        all.add("ClientLevelDataMixin");
        //?}
        return all;
    }

    private static List<String> base() {
        //? if >=26.3 {
        /*return List.of("SolSkyMixin", "SolLevelRendererMixin", "SolFogMixin", "SolTintMixin", "ItemModelResolverMixin", "ShieldSpecialRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin", "AbstractClientPlayerMixin",
                "ClockInstanceMixin", "LevelRendererOutlineMixin");
        *///?} elif >=26.1 {
        /*return List.of("SolSkyMixin", "SolLevelRendererMixin", "SolFogMixin", "SolTintMixin", "ItemModelResolverMixin", "ShieldSpecialRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin", "AbstractClientPlayerMixin",
                "ClockManagerMixin", "LevelRendererOutlineMixin");
        *///?} elif >=1.21.6 {
        return List.of("SolSkyMixin", "SolLevelRendererMixin", "SolFogMixin", "SolTintMixin", "ItemModelResolverMixin", "ShieldSpecialRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin", "AbstractClientPlayerMixin");
        //?} elif >=1.21.4 {
        /*return List.of("SolSkyMixin", "SolLevelRendererMixin", "SolFogMixin", "SolTintMixin", "ItemModelResolverMixin", "ShieldSpecialRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin", "AbstractClientPlayerMixin",
                "GuiMixin", "BossOverlayMixin");
        *///?} elif >=1.21.2 {
        /*return List.of("SolSkyMixin", "SolLevelRendererMixin", "SolFogMixin", "SolTintMixin", "ItemInHandRendererMixin", "ItemInHandLayerMixin", "ShieldItemRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin", "AbstractClientPlayerMixin",
                "GuiMixin", "BossOverlayMixin");
        *///?} else {
        /*return List.of("SolSkyMixin", "SolLevelRendererMixin", "SolFogMixin", "SolTintMixin", "ItemInHandRendererMixin", "ShieldItemRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin", "AbstractClientPlayerMixin",
                "GuiMixin", "GuiAirMixin", "BossOverlayMixin");
        *///?}
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
