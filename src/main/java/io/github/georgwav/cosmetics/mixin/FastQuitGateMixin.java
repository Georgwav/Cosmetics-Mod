package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.FastQuit;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No world is opened, edited or deleted while one is still saving in the background (fast quit). */
@Mixin(LevelStorageSource.class)
public abstract class FastQuitGateMixin {
    @Inject(method = {"validateAndCreateAccess", "createAccess"}, at = @At("HEAD"), require = 0)
    private void cosmeticsmod$waitForSaves(String levelId, CallbackInfoReturnable<?> cir) {
        if (FastQuit.saving()) {
            FastQuit.waitForSaves();
        }
    }
}
