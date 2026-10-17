package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.hud.HudTweaks;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

/**
 * Sol UI's tab list tweaks (HudTweaks): laid out for the screen at its size and drawn scaled; a wider ping column
 * with the ping written in milliseconds; everyone's head shown (the game shows heads only in online mode); how many
 * are online under the header.
 */
@Mixin(PlayerTabOverlay.class)
abstract class TabListTweaksMixin {
    //? if >=26.1 {
    /*private static final String RENDER = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V";
    *///?} else {
    private static final String RENDER = "render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/world/scores/Scoreboard;Lnet/minecraft/world/scores/Objective;)V";
    //?}

    @Shadow
    private Component header;
    @Unique
    private boolean cosmeticsmod$scaled;

    @ModifyVariable(method = RENDER, at = @At("HEAD"), argsOnly = true, require = 0)
    private int cosmeticsmod$width(int screenWidth) {
        return HudTweaks.tabWidth(screenWidth);
    }

    @ModifyConstant(method = RENDER, constant = @Constant(intValue = 13), require = 0)
    private int cosmeticsmod$pingRoom(int game) {
        return HudTweaks.pingRoom(game);
    }

    @Redirect(method = RENDER, at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;",
            opcode = Opcodes.GETFIELD), require = 0)
    private Component cosmeticsmod$header(PlayerTabOverlay self) {
        return HudTweaks.header(header);
    }

    //? if >=26.2 {
    /*@Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientPacketListener;onlineMode()Z"), require = 0)
    private boolean cosmeticsmod$heads(net.minecraft.client.multiplayer.ClientPacketListener connection) {
        return HudTweaks.tabHeads(connection.onlineMode());
    }
    *///?} else {
    @Redirect(method = RENDER, at = @At(value = "INVOKE", target = "Lnet/minecraft/network/Connection;isEncrypted()Z"), require = 0)
    private boolean cosmeticsmod$heads(net.minecraft.network.Connection connection) {
        return HudTweaks.tabHeads(connection.isEncrypted());
    }
    //?}

    //? if >=26.1 {
    /*@Inject(method = RENDER, at = @At("HEAD"), require = 0)
    private void cosmeticsmod$in(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        cosmeticsmod$scaled = HudTweaks.tabIn(new Canvas(graphics));
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 0)
    private void cosmeticsmod$out(GuiGraphicsExtractor graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (cosmeticsmod$scaled) {
            cosmeticsmod$scaled = false;
            new Canvas(graphics).pop();
        }
    }

    @Inject(method = "extractPingIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$ping(GuiGraphicsExtractor graphics, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
        if (HudTweaks.pingText()) {
            HudTweaks.ping(new Canvas(graphics), width, x, y, info);
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = RENDER, at = @At("HEAD"), require = 0)
    private void cosmeticsmod$in(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        cosmeticsmod$scaled = HudTweaks.tabIn(new Canvas(graphics));
    }

    @Inject(method = RENDER, at = @At("RETURN"), require = 0)
    private void cosmeticsmod$out(GuiGraphics graphics, int width, Scoreboard scoreboard, Objective objective, CallbackInfo ci) {
        if (cosmeticsmod$scaled) {
            cosmeticsmod$scaled = false;
            new Canvas(graphics).pop();
        }
    }

    @Inject(method = "renderPingIcon", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$ping(GuiGraphics graphics, int width, int x, int y, PlayerInfo info, CallbackInfo ci) {
        if (HudTweaks.pingText()) {
            HudTweaks.ping(new Canvas(graphics), width, x, y, info);
            ci.cancel();
        }
    }
    //?}
}
