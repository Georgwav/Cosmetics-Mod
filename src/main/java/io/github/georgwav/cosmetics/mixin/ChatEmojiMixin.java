package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.chat.EmojiPicker;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sol Chat's emoji picker on the chat screen (EmojiPicker): the chat box made a little shorter for its button, the
 * button and grid drawn over the screen, its clicks taken before the screen's, Esc shutting the grid first.
 */
@Mixin(ChatScreen.class)
abstract class ChatEmojiMixin {
    @Shadow
    protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$room(CallbackInfo ci) {
        EmojiPicker.reset();
        if (EmojiPicker.shown() && input != null) {
            input.setWidth(input.getWidth() - EmojiPicker.BUTTON);
        }
    }

    //? if >=26.1 {
    /*@Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$draw(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        EmojiPicker.draw(new Canvas(graphics), self.width, self.height, mouseX, mouseY);
    }
    *///?} else {
    @Inject(method = "render", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$draw(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        EmojiPicker.draw(new Canvas(graphics), self.width, self.height, mouseX, mouseY);
    }
    //?}

    //? if >=1.21.9 {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$click(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && EmojiPicker.click(self.width, self.height, event.x(), event.y(), input::insertText)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$escape(net.minecraft.client.input.KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && EmojiPicker.escape()) {
            cir.setReturnValue(true);
        }
    }
    //?} else {
    /*@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$click(double x, double y, int button, CallbackInfoReturnable<Boolean> cir) {
        Screen self = (Screen) (Object) this;
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && EmojiPicker.click(self.width, self.height, x, y, input::insertText)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$escape(int key, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && EmojiPicker.escape()) {
            cir.setReturnValue(true);
        }
    }
    *///?}
}
