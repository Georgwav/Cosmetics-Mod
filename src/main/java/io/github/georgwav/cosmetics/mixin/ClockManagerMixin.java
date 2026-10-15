package io.github.georgwav.cosmetics.mixin;

// 26.1 and 26.2 keep the client's world clocks inside ClientClockManager (26.3 has ClockInstanceMixin): the time
// changer sets the time of day it answers with, keeping the day count (the moon's phase).
//? if >=26.3 {
//?} elif >=26.1 {
/*import io.github.georgwav.cosmetics.client.modules.TimeChanger;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientClockManager.class)
abstract class ClockManagerMixin {
    @Inject(method = "getTotalTicks", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$time(Holder<WorldClock> clock, CallbackInfoReturnable<Long> cir) {
        long time = TimeChanger.dayTime();
        if (time >= 0L) {
            cir.setReturnValue(Math.floorDiv(cir.getReturnValueJ(), 24000L) * 24000L + time);
        }
    }
}
*///?}
