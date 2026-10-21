package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.hud.ArmourItemsHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Totem pops (the game's entity event 35): yours for Armour & Items, everyone's for Crystal PvP and the effects. The
 * game handles this event in its packet listener and never passes it on to the entity, so it's read there, once the
 * packet is on the game's thread (the method's returns; the first call, on the network thread, throws before them).
 */
@Mixin(ClientPacketListener.class)
abstract class TotemPopMixin {
    @Inject(method = "handleEntityEvent", at = @At("RETURN"), require = 0)
    private void cosmeticsmod$totem(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (packet.getEventId() != 35 || mc.level == null || !(packet.getEntity(mc.level) instanceof LivingEntity living)) {
            return;
        }
        if (living == mc.player) {
            ArmourItemsHud.totemPops++;
        }
        io.github.georgwav.cosmetics.client.modules.CrystalPvp.popped(living);
        io.github.georgwav.cosmetics.sol.fx.FxEvents.totem(living);
    }
}
