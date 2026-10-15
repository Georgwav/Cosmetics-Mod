package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Screenshots;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** Screenshots: told when the game has saved one (its message callback, after the file is written). */
@Mixin(Screenshot.class)
abstract class ScreenshotMixin {
    @ModifyVariable(method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
            at = @At("HEAD"), argsOnly = true, require = 0)
    private static Consumer<Component> cosmeticsmod$saved(Consumer<Component> message) {
        return c -> {
            message.accept(c);
            Screenshots.saved();
        };
    }
}
