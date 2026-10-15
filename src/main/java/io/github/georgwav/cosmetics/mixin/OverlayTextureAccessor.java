package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The game's overlay texture (the red flash entities show when hit), for PvP Tweaks' hit colour. */
@Mixin(OverlayTexture.class)
public interface OverlayTextureAccessor {
    @Accessor("texture")
    DynamicTexture cosmeticsmod$texture();
}
