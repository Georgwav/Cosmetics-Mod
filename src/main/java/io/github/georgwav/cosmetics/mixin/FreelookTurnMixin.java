package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.CameraTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Freelook: the mouse turns the camera instead of your player. */
@Mixin(Entity.class)
abstract class FreelookTurnMixin {
    @Inject(method = "turn(DD)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$freelook(double yaw, double pitch, CallbackInfo ci) {
        io.github.georgwav.cosmetics.sol.Hooks.FREELOOK_TURN.hit();
        if (CameraTweaks.freelooking() && (Object) this == Minecraft.getInstance().player) {
            CameraTweaks.turn(yaw, pitch);
            ci.cancel();
        }
    }
}
