package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.fx.effects.WorldFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World FX › Cosmos break: while it's on, the block you mine shows Sol's own cracks (glowing, over its stars) instead of
 * the game's: your own mining's crack stage is taken off as it's set. Others' mining keeps the game's cracks.
 */
@Mixin(ClientLevel.class)
abstract class CosmosCracksMixin {
    @Inject(method = "destroyBlockProgress", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$cracks(int breaker, BlockPos pos, int progress, CallbackInfo ci) {
        if (progress >= 0 && progress < 10 && WorldFx.ownCracks(breaker)) {
            ((ClientLevel) (Object) this).destroyBlockProgress(breaker, pos, -1);
            ci.cancel();
        }
    }
}
