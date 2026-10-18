package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.modules.Hitboxes;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hitboxes: which entities get boxes, and which entity's box the game is making (for the target's colour). Where the
 * game decides an entity's box: the debug renderer's showHitboxes from 1.21.11, the entity renderer filling its
 * render state from 1.21.5, the entity render dispatcher's renderHitbox before.
 */
//? if >=1.21.11 {
@Mixin(net.minecraft.client.renderer.debug.EntityHitboxDebugRenderer.class)
//?} elif >=1.21.5 {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderer.class)
*///?} else {
/*@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
*///?}
abstract class HitboxFilterMixin {
    //? if >=1.21.11 {
    @Inject(method = "showHitboxes", at = @At("HEAD"), cancellable = true, require = 0)
    private void cosmeticsmod$players(Entity entity, float partialTick, boolean server, CallbackInfo ci) {
        if (!Hitboxes.boxes(entity)) {
            ci.cancel();
        }
    }
    //?} elif >=1.21.5 {
    /*@Inject(method = "extractHitboxes(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("HEAD"), require = 0)
    private void cosmeticsmod$current(Entity entity, net.minecraft.client.renderer.entity.state.EntityRenderState state, float partialTick,
                                      CallbackInfo ci) {
        Hitboxes.current(entity);
    }

    @Inject(method = "extractHitboxes(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V",
            at = @At("RETURN"), require = 0)
    private void cosmeticsmod$players(Entity entity, net.minecraft.client.renderer.entity.state.EntityRenderState state, float partialTick,
                                      CallbackInfo ci) {
        if (!Hitboxes.boxes(entity)) {
            state.hitboxesRenderState = null;
            state.serverHitboxesRenderState = null;
        }
    }
    *///?} else {
    /*@Inject(method = "renderHitbox", at = @At("HEAD"), cancellable = true, require = 0)
    private static void cosmeticsmod$players(com.mojang.blaze3d.vertex.PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer lines,
                                             Entity entity, float red, float green, float blue, float alpha, CallbackInfo ci) {
        if (!Hitboxes.boxes(entity)) {
            ci.cancel();
        }
    }
    *///?}
}
