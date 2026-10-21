package io.github.georgwav.cosmetics.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Explosions the server tells us of, for Crystal PvP's explosion rings (FxEvents.blast). */
@Mixin(ClientPacketListener.class)
abstract class ExplosionMixin {
    @Inject(method = "handleExplosion", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$blast(ClientboundExplodePacket packet, CallbackInfo ci) {
        //? if >=1.21.2 {
        var at = packet.center();
        io.github.georgwav.cosmetics.sol.fx.FxEvents.blast(at.x, at.y, at.z, false);
        //?} else {
        /*io.github.georgwav.cosmetics.sol.fx.FxEvents.blast(packet.getX(), packet.getY(), packet.getZ(), false);
        *///?}
    }
}
