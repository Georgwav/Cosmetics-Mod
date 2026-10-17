package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.sol.client.ServerListUpgrade;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.multiplayer.ServerData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Servers' server list upgrade on each server's row (ServerListUpgrade): the ping written in milliseconds in place
 * of the bars (the first sprite the row draws), the player count moved left to make room for it.
 */
@Mixin(net.minecraft.client.gui.screens.multiplayer.ServerSelectionList.OnlineServerEntry.class)
abstract class ServerEntryMixin {
    @Shadow
    @Final
    private ServerData serverData;

    //? if >=26.3 {
    /*@Redirect(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$ping(net.minecraft.client.gui.GuiGraphicsExtractor g, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline,
                                   net.minecraft.resources.Identifier sprite, int x, int y, int w, int h) {
        if (!ServerListUpgrade.ping(new Canvas(g), serverData, x, y, w)) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
        }
    }

    @ModifyArg(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 2, require = 0)
    private int cosmeticsmod$count(int x) {
        return x - ServerListUpgrade.countShift(serverData);
    }
    *///?} elif >=26.1 {
    /*@Redirect(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$ping(net.minecraft.client.gui.GuiGraphicsExtractor g, com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                   net.minecraft.resources.Identifier sprite, int x, int y, int w, int h) {
        if (!ServerListUpgrade.ping(new Canvas(g), serverData, x, y, w)) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
        }
    }

    @ModifyArg(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 2, require = 0)
    private int cosmeticsmod$count(int x) {
        return x - ServerListUpgrade.countShift(serverData);
    }
    *///?} elif >=1.21.11 {
    @Redirect(method = "renderContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$ping(net.minecraft.client.gui.GuiGraphics g, com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                   net.minecraft.resources.Identifier sprite, int x, int y, int w, int h) {
        if (!ServerListUpgrade.ping(new Canvas(g), serverData, x, y, w)) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
        }
    }

    @ModifyArg(method = "renderContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 2, require = 0)
    private int cosmeticsmod$count(int x) {
        return x - ServerListUpgrade.countShift(serverData);
    }
    //?} elif >=1.21.6 {
    /*@Redirect(method = {"render", "renderContent"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$ping(net.minecraft.client.gui.GuiGraphics g, com.mojang.blaze3d.pipeline.RenderPipeline pipeline,
                                   net.minecraft.resources.ResourceLocation sprite, int x, int y, int w, int h) {
        if (!ServerListUpgrade.ping(new Canvas(g), serverData, x, y, w)) {
            g.blitSprite(pipeline, sprite, x, y, w, h);
        }
    }

    @ModifyArg(method = {"render", "renderContent"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), index = 2, require = 0)
    private int cosmeticsmod$count(int x) {
        return x - ServerListUpgrade.countShift(serverData);
    }
    *///?} elif >=1.21.2 {
    /*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Ljava/util/function/Function;Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$ping(net.minecraft.client.gui.GuiGraphics g, java.util.function.Function<net.minecraft.resources.ResourceLocation, net.minecraft.client.renderer.RenderType> type,
                                   net.minecraft.resources.ResourceLocation sprite, int x, int y, int w, int h) {
        if (!ServerListUpgrade.ping(new Canvas(g), serverData, x, y, w)) {
            g.blitSprite(type, sprite, x, y, w, h);
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)I"), index = 2, require = 0)
    private int cosmeticsmod$count(int x) {
        return x - ServerListUpgrade.countShift(serverData);
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), index = 2, require = 0)
    private int cosmeticsmod$countShadowed(int x) {
        return x - ServerListUpgrade.countShift(serverData);
    }
    *///?} else {
    /*@Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 0), require = 0)
    private void cosmeticsmod$ping(net.minecraft.client.gui.GuiGraphics g, net.minecraft.resources.ResourceLocation sprite, int x, int y, int w, int h) {
        if (!ServerListUpgrade.ping(new Canvas(g), serverData, x, y, w)) {
            g.blitSprite(sprite, x, y, w, h);
        }
    }

    @ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"), index = 2, require = 0)
    private int cosmeticsmod$count(int x) {
        return x - ServerListUpgrade.countShift(serverData);
    }
    *///?}
}
