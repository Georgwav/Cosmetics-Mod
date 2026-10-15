package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.SolChat;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
//? if >=26.1 {
/*import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
*///?} else {
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
//?}

/**
 * Sol Chat's messages: each one on its way into the chat goes through SolChat (timestamp, stacking, mentions) and is
 * added again as it becomes; a stacked one takes the place of the one it repeats.
 */
@Mixin(ChatComponent.class)
abstract class SolChatMixin {
    @Shadow
    @Final
    private List<GuiMessage> allMessages;
    @Unique
    private boolean cosmeticsmod$adding;

    @Shadow
    private void refreshTrimmedMessages() {
        throw new AssertionError();
    }

    /** What to add instead (null: the message as it is), with the stacked one it repeats taken away. */
    @Unique
    private SolChat.Added cosmeticsmod$process(Component message) {
        if (cosmeticsmod$adding || !Guard.on(Feature.CHAT)) {
            return null;
        }
        try {
            SolChat.Added added = SolChat.process(message);
            if (added != null && added.replacesLast() && !allMessages.isEmpty()) {
                allMessages.remove(0);
                refreshTrimmedMessages();
            }
            return added;
        } catch (RuntimeException | LinkageError e) {
            Guard.fail(Feature.CHAT, e);
            return null;
        }
    }

    @Unique
    private static GuiMessageTag cosmeticsmod$tag(SolChat.Added added, GuiMessageTag tag) {
        return added.mention() ? new GuiMessageTag(SolChat.mentionColor(), null,
                Component.translatable("sol.chat.mention"), "Mention") : tag;
    }

    //? if >=26.1 {
    /*@Shadow
    private void addMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag) {
        throw new AssertionError();
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$add(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag,
                                  CallbackInfo ci) {
        SolChat.Added added = cosmeticsmod$process(message);
        if (added == null) {
            return;
        }
        cosmeticsmod$adding = true;
        try {
            addMessage(added.component(), signature, source, cosmeticsmod$tag(added, tag));
        } finally {
            cosmeticsmod$adding = false;
        }
        ci.cancel();
    }
    *///?} else {
    @Shadow
    public abstract void addMessage(Component message, MessageSignature signature, GuiMessageTag tag);

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$add(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        SolChat.Added added = cosmeticsmod$process(message);
        if (added == null) {
            return;
        }
        cosmeticsmod$adding = true;
        try {
            addMessage(added.component(), signature, cosmeticsmod$tag(added, tag));
        } finally {
            cosmeticsmod$adding = false;
        }
        ci.cancel();
    }
    //?}
}
