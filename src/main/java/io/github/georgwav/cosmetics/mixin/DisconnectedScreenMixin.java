package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Servers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Servers' auto reconnect: a button under the disconnect message that counts down, reconnects, or stops. */
@Mixin(DisconnectedScreen.class)
abstract class DisconnectedScreenMixin extends Screen {
    protected DisconnectedScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init()V", at = @At("TAIL"), require = 0)
    private void cosmeticsmod$button(CallbackInfo ci) {
        if (!Servers.canReconnect()) {
            return;
        }
        Servers.button = addRenderableWidget(Button.builder(Servers.label(), b -> {
            if (Servers.secondsLeft() > 0) {
                Servers.stop();
                b.setMessage(Servers.label());
            } else {
                Servers.reconnect(Minecraft.getInstance());
            }
        }).bounds(width / 2 - 100, height - 30, 200, 20).build());
    }

}
