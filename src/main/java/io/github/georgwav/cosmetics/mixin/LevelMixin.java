package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.TimeChanger;
import io.github.georgwav.cosmetics.client.modules.WeatherChanger;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The weather and time of day the client shows, when the weather or time changer is on: only for the client's own
 * world (the server's, also in singleplayer, keeps its own). 26.1 replaced the day time with world clocks, so the
 * time changer hooks the day time up to 1.21.11 only.
 */
@Mixin(Level.class)
abstract class LevelMixin {
    @Shadow
    public abstract boolean isClientSide();

    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$rain(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (isClientSide()) {
            float rain = WeatherChanger.rain();
            if (rain >= 0f) {
                cir.setReturnValue(rain);
            }
        }
    }

    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$thunder(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (isClientSide()) {
            float thunder = WeatherChanger.thunder();
            if (thunder >= 0f) {
                cir.setReturnValue(thunder);
            }
        }
    }

    //? if <26.1 {
    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$time(CallbackInfoReturnable<Long> cir) {
        if (isClientSide()) {
            long time = TimeChanger.dayTime();
            if (time >= 0L) {
                cir.setReturnValue(time);
            }
        }
    }
    //?}
}
