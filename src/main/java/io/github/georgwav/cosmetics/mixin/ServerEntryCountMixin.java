package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.ServerListUpgrade;
import net.minecraft.client.multiplayer.ServerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Servers' folders (ServerListUpgrade): a server's row counts the servers shown, not the whole file, so the
 * folder's last server offers no move down (which would swap the row with the LAN header below it).
 */
@Mixin(net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry.class)
abstract class ServerEntryCountMixin {
    @Redirect(method = "*", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ServerList;size()I"), require = 0)
    private int cosmeticsmod$shown(ServerList list) {
        return ServerListUpgrade.size(list);
    }
}
