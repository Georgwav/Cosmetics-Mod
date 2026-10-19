package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * The Punchy module's dark arms: the entity render types the first-person arms are made with (the game's arm, or
 * Punchy's whole first-person model) take the dark texture instead of your skin while the hands are drawn. Items use
 * other textures and are left alone. Names that a version doesn't have are skipped.
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.rendertype.RenderTypes.class)
//?} else {
/*@Mixin(net.minecraft.client.renderer.RenderType.class)
*///?}
abstract class DarkArmsMixin {
    //? if >=1.21.11 {
    @ModifyVariable(method = {"entitySolid", "entityCutout", "entityCutoutNoCull", "entityCutoutCull", "entityTranslucent"},
            at = @At("HEAD"), argsOnly = true, require = 0)
    private static net.minecraft.resources.Identifier cosmeticsmod$darkArms(net.minecraft.resources.Identifier texture) {
        return PunchyModule.skinOrDark(texture);
    }
    //?} else {
    /*@ModifyVariable(method = {"entitySolid", "entityCutout", "entityCutoutNoCull", "entityTranslucent"},
            at = @At("HEAD"), argsOnly = true, require = 0)
    private static net.minecraft.resources.ResourceLocation cosmeticsmod$darkArms(net.minecraft.resources.ResourceLocation texture) {
        return PunchyModule.skinOrDark(texture);
    }
    *///?}
}
