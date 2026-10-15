package io.github.georgwav.cosmetics.mixin;

// 26.x has no Fabric block outline event: the game's own outline takes BlockOutline's colour where it's drawn,
// renderHitOutline on 26.1 and 26.2 and submitHitOutline on 26.3 (the colour is their first int).
//? if >=26.1 {
/*import io.github.georgwav.cosmetics.client.OutlineHook;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LevelRenderer.class)
abstract class LevelRendererOutlineMixin {
    @ModifyVariable(method = {"renderHitOutline", "submitHitOutline"}, at = @At("HEAD"), argsOnly = true, ordinal = 0,
            require = 0)
    private int cosmeticsmod$outlineColor(int color) {
        return OutlineHook.color(color);
    }
}
*///?}
