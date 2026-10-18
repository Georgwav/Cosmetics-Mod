package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.Streamer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.9 {
import net.minecraft.world.entity.player.PlayerSkin;
//?} else {
/*import net.minecraft.client.resources.PlayerSkin;
*///?}

/** Streamer mode (Social): your own entry in the player list wears the game's default skin, so its head in the tab list does. */
@Mixin(PlayerInfo.class)
abstract class PlayerInfoSkinMixin {
    @Inject(method = "getSkin", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$streamer(CallbackInfoReturnable<PlayerSkin> cir) {
        //? if >=1.21.9 {
        java.util.UUID id = ((PlayerInfo) (Object) this).getProfile().id();
        //?} else {
        /*java.util.UUID id = ((PlayerInfo) (Object) this).getProfile().getId();
        *///?}
        if (Streamer.hides(id)) {
            cir.setReturnValue(DefaultPlayerSkin.get(id));
        }
    }
}
