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
        //? if >=1.21.4 {
        return List.of("ItemModelResolverMixin", "ShieldSpecialRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin");
        //?} elif >=1.21.2 {
        /*return List.of("ItemInHandRendererMixin", "ItemInHandLayerMixin", "ShieldItemRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin");
        *///?} else {
        /*return List.of("ItemInHandRendererMixin", "ShieldItemRendererMixin", "PlayerModelMixin", "PlayerRendererMixin",
                "ElytraModelMixin", "WingsLayerMixin", "OptionInstanceAccessor", "OptionsMixin", "LevelMixin");
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
