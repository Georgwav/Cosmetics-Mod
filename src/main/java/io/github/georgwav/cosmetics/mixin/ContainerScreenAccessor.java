package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Where a container screen's panel is (Sol UI's screen slide fades it in there). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos")
    int cosmeticsmod$left();

    @Accessor("topPos")
    int cosmeticsmod$top();

    @Accessor("imageWidth")
    int cosmeticsmod$width();

    @Accessor("imageHeight")
    int cosmeticsmod$height();
}
