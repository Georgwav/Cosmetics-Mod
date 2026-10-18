package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.CrystalPvp;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The server removing entities: Crystal PvP's optimizer learns the crystals it hid are really gone. */
@Mixin(ClientPacketListener.class)
abstract class CrystalRemovalMixin {
    @Inject(method = "handleRemoveEntities", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$removed(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        //? if >=26.3 {
        /*CrystalPvp.serverRemoved(packet.entityIds());
        *///?} else {
        CrystalPvp.serverRemoved(packet.getEntityIds());
        //?}
    }
}
