package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A pack screen row's pack: resource pack previews (PackPreview). */
@Mixin(net.minecraft.client.gui.screens.packs.TransferableSelectionList.PackEntry.class)
public interface PackEntryAccess {
    @Accessor("pack")
    PackSelectionModel.Entry cosmeticsmod$pack();
}
