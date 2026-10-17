package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.ServerListUpgrade;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Servers' folders (ServerListUpgrade): moving a server up or down while a folder is shown swaps it with its
 * neighbour in the folder. The screen asks by place in the list as shown; that's turned into the server file's,
 * and a move past the folder's ends does nothing.
 */
@Mixin(ServerList.class)
abstract class ServerListSwapMixin {
    @Unique
    private static boolean cosmeticsmod$swapping;

    @Inject(method = "swap", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$inFolder(int first, int second, CallbackInfo ci) {
        if (cosmeticsmod$swapping || !ServerListUpgrade.filtering()) {
            return;
        }
        ci.cancel();
        int[] real = ServerListUpgrade.realPair(first, second);
        if (real == null) {
            return;
        }
        cosmeticsmod$swapping = true;
        try {
            ((ServerList) (Object) this).swap(real[0], real[1]);
        } finally {
            cosmeticsmod$swapping = false;
        }
    }
}
