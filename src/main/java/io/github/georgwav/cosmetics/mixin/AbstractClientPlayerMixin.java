package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.skins.OwnSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.9 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

/**
 * Your own player wears the skin you just applied (OwnSkin) until the game fetches it from Mojang on rejoining, and
 * the game's default skin while streaming (Social).
 */
@Mixin(AbstractClientPlayer.class)
abstract class AbstractClientPlayerMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$ownSkin(CallbackInfoReturnable<PlayerSkin> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        if (io.github.georgwav.cosmetics.sol.client.Streamer.on()) {
            cir.setReturnValue(net.minecraft.client.resources.DefaultPlayerSkin.get(((AbstractClientPlayer) (Object) this).getUUID()));
        } else if (OwnSkin.active()) {
            cir.setReturnValue(OwnSkin.apply(cir.getReturnValue()));
        }
    }
}
