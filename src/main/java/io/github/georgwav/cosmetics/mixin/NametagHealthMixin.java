package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
//? if >=1.21.2 {
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//?} else {
/*import org.spongepowered.asm.mixin.injection.ModifyArg;
*///?}

/**
 * Nametags: a player's health after their name. The game picks a name tag's text in getNameTag from 1.21.2 (when it
 * fills the render state), and passes the display name to renderNameTag before that.
 */
@Mixin(EntityRenderer.class)
abstract class NametagHealthMixin {
    //? if >=1.21.2 {
    @Inject(method = "getNameTag", at = @At("RETURN"), cancellable = true, require = 0)
    private void cosmeticsmod$health(Entity entity, CallbackInfoReturnable<Component> cir) {
        Component name = cir.getReturnValue();
        Component shown = Nametags.withHealth(entity, name);
        if (shown != name) {
            cir.setReturnValue(shown);
        }
    }
    //?} else {
    /*@ModifyArg(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;renderNameTag(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/network/chat/Component;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V"), index = 1, require = 0)
    private Component cosmeticsmod$health(Entity entity, Component name, com.mojang.blaze3d.vertex.PoseStack pose,
                                          net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partialTick) {
        return Nametags.withHealth(entity, name);
    }
    *///?}
}
