package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.SolChat;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sol Chat's shortcodes in what you send: the chat screen's tidied message, before it goes. */
@Mixin(ChatScreen.class)
abstract class ChatSendMixin {
    @Inject(method = "normalizeChatMessage(Ljava/lang/String;)Ljava/lang/String;", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$emoji(String message, CallbackInfoReturnable<String> cir) {
        String out = cir.getReturnValue();
        if (out != null && !out.isEmpty()) {
            cir.setReturnValue(SolChat.outgoing(out));
        }
    }
}
