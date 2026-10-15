package io.github.georgwav.cosmetics.mixin;

//? if >=1.21.4 {
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An item's render layers and how many are in use (Glint by place clears their foil). */
@Mixin(net.minecraft.client.renderer.item.ItemStackRenderState.class)
interface ItemLayersAccess {
    @Accessor("layers")
    net.minecraft.client.renderer.item.ItemStackRenderState.LayerRenderState[] cosmeticsmod$layers();

    @Accessor("activeLayerCount")
    int cosmeticsmod$count();
}
//?}
