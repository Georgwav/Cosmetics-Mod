package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.ServerListUpgrade;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Servers' folders (ServerListUpgrade): the server list fills with the shown folder's servers only. */
@Mixin(ServerSelectionList.class)
abstract class ServerFoldersMixin {
    @Inject(method = "updateOnlineServers", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$prepare(ServerList list, CallbackInfo ci) {
        ServerListUpgrade.prepare(list);
    }

    @Redirect(method = "updateOnlineServers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;size()I"), require = 0)
    private int cosmeticsmod$size(ServerList list) {
        return ServerListUpgrade.size(list);
    }

    @Redirect(method = "updateOnlineServers", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;get(I)Lnet/minecraft/client/multiplayer/ServerData;"), require = 0)
    private ServerData cosmeticsmod$get(ServerList list, int index) {
        return ServerListUpgrade.get(list, index);
    }
}
