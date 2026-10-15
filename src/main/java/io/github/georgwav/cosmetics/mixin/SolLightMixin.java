package io.github.georgwav.cosmetics.mixin;

//? if <1.21.11 {
/*import io.github.georgwav.cosmetics.sol.atmosphere.WorldTint;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/^*
 * The theme's light tint up to 1.21.10: the lightmap's sky light colour, as it is computed (the first Vector3f the
 * lightmap update makes). From 1.21.11 it is an environment attribute, see SolTintMixin.
 ^/
@Mixin(LightTexture.class)
abstract class SolLightMixin {
    @ModifyVariable(method = "updateLightTexture", at = @At("STORE"), ordinal = 0)
    private Vector3f cosmeticsmod$tint(Vector3f sky) {
        WorldTint.light(sky);
        return sky;
    }
}
*///?}
