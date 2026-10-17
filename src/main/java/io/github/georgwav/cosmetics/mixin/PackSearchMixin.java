package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.PackPreview;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Resource pack search (PackPreview) before 1.21.9, where the game's pack screen has none: a search box at the top
 * left keeps only the packs whose name or description has what you type. From 1.21.9 the game's own search is
 * there and this does nothing.
 */
@Mixin(PackSelectionScreen.class)
abstract class PackSearchMixin extends Screen {
    protected PackSearchMixin(Component title) {
        super(title);
    }

    //? if <1.21.9 {
    /*@org.spongepowered.asm.mixin.Shadow
    private void populateLists() {
        throw new AssertionError();
    }

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$search(CallbackInfo ci) {
        PackPreview.clearSearch();
        if (!PackPreview.on()) {
            return;
        }
        int w = Math.min(160, width / 2 - font.width(title) / 2 - 16);
        if (w < 60) {
            return;
        }
        var box = new net.minecraft.client.gui.components.EditBox(font, 6, 6, w, 16, Component.translatable("sol.packs.search"));
        box.setHint(Component.translatable("sol.packs.search").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        box.setResponder(text -> {
            PackPreview.search(text);
            populateLists();
        });
        addRenderableWidget(box);
        PackPreview.searchBoxes++;
    }

    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "populateLists", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/packs/PackSelectionScreen;updateList(Lnet/minecraft/client/gui/screens/packs/TransferableSelectionList;Ljava/util/stream/Stream;)V"),
            index = 1, require = 0)
    private java.util.stream.Stream<net.minecraft.client.gui.screens.packs.PackSelectionModel.Entry> cosmeticsmod$filter(
            java.util.stream.Stream<net.minecraft.client.gui.screens.packs.PackSelectionModel.Entry> packs) {
        return packs.filter(PackPreview::matches);
    }
    *///?}
}
