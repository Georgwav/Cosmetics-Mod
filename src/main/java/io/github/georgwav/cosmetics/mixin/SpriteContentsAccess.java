package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A sprite's pixels at each mip level, for Sol Styles' live colour changes (written in place, then uploaded). */
@Mixin(SpriteContents.class)
public interface SpriteContentsAccess {
    @Accessor("byMipLevel")
    NativeImage[] cosmeticsmod$byMipLevel();
}
