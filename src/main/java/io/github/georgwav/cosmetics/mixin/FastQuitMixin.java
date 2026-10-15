package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.FastQuit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Fast quit: leaving a single-player world doesn't wait for it to finish saving (see {@link FastQuit}). */
@Mixin(Minecraft.class)
public abstract class FastQuitMixin {
    @Redirect(method = "disconnect", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isShutdown()Z"),
            require = 0)
    private boolean cosmeticsmod$fastQuit(IntegratedServer server) {
        return FastQuit.shutdownOrLeave(server);
    }
}
