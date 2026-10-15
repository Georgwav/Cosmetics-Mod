package io.github.georgwav.cosmetics.mixin;

//? if <26.1 {
import io.github.georgwav.cosmetics.client.modules.TimeChanger;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The client's own day time, which the sky, the sun, the moon's phase and the light read (through the level data,
 * not {@code Level.getDayTime}): the time changer and Atmosphere's time lock set the time of day here, keeping the
 * day count.
 */
@Mixin(ClientLevel.ClientLevelData.class)
abstract class ClientLevelDataMixin {
    @Inject(method = "getDayTime", at = @At("RETURN"), cancellable = true)
    private void cosmeticsmod$time(CallbackInfoReturnable<Long> cir) {
        long time = TimeChanger.dayTime();
        if (time >= 0L) {
            cir.setReturnValue(Math.floorDiv(cir.getReturnValueJ(), 24000L) * 24000L + time);
        }
    }
}
//?}
