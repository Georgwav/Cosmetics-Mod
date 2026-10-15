package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Sol's title screen in place of the game's, wherever the game opens it (26.2 moved screens into Gui). */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Gui.class)
*///?} else {
@Mixin(net.minecraft.client.Minecraft.class)
//?}
public abstract class TitleSwapMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true, require = 0)
    private Screen cosmeticsmod$solTitle(Screen screen) {
        return io.github.georgwav.cosmetics.sol.ui.screens.SolTitleScreen.swap(screen);
    }
}
