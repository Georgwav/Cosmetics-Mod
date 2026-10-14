package io.github.georgwav.cosmetics.mixin;

// 26.1 replaced the day time with world clocks: the time changer sets the time of day the client's clocks show,
// keeping their day count (the moon's phase). Up to 1.21.11 LevelMixin changes the day time instead.
//? if >=26.1 {
/*import io.github.georgwav.cosmetics.client.modules.TimeChanger;
import net.minecraft.client.ClientClockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.ClientClockInstance.class)
abstract class ClockInstanceMixin {
    @Shadow
    private long totalTicks;

    @Inject(method = "totalTicks", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$time(CallbackInfoReturnable<Long> cir) {
        long time = TimeChanger.dayTime();
        if (time >= 0L) {
            cir.setReturnValue(Math.floorDiv(totalTicks, 24000L) * 24000L + time);
        }
    }

    // A time that stands still doesn't move between ticks either.
    @Inject(method = "partialTick", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$still(CallbackInfoReturnable<Float> cir) {
        if (TimeChanger.dayTime() >= 0L) {
            cir.setReturnValue(0f);
        }
    }
}
*///?}
