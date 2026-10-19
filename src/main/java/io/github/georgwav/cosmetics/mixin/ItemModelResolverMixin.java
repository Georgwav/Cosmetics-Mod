package io.github.georgwav.cosmetics.mixin;

// Every item model the game works out (hands, GUI, ground) passes through appendItemLayers, with whose item
// it is; HeldItems remembers the hand items of players, and a knife held in third person leaves the hand's own item
// out. 1.21.4 and later; before, see ItemInHandRendererMixin and ItemInHandLayerMixin.
//? if >=1.21.9 {
import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$enter(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
                                    ItemOwner owner, int seed, CallbackInfo ci) {
        if (io.github.georgwav.cosmetics.melee.KnifeThirdPerson.hides(owner, stack, context)) {
            // A knife is held instead (drawn from the cosmetics layer): the hand's item stays empty.
            ci.cancel();
            return;
        }
        HeldItems.enter(owner, context);
        io.github.georgwav.cosmetics.client.modules.GlintModule.drawing(context == ItemDisplayContext.GUI);
    }

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void cosmeticsmod$exit(CallbackInfo ci) {
        HeldItems.exit();
        io.github.georgwav.cosmetics.client.modules.GlintModule.drawing(false);
    }
}
//?} elif >=1.21.4 {
/*import io.github.georgwav.cosmetics.render.HeldItems;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("HEAD"), cancellable = true)
    private void cosmeticsmod$enter(ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, Level level,
                                    LivingEntity owner, int seed, CallbackInfo ci) {
        if (io.github.georgwav.cosmetics.melee.KnifeThirdPerson.hides(owner, stack, context)) {
            ci.cancel();
            return;
        }
        HeldItems.enter(owner, context);
        io.github.georgwav.cosmetics.client.modules.GlintModule.drawing(context == ItemDisplayContext.GUI);
    }

    @Inject(method = "appendItemLayers", at = @At("RETURN"))
    private void cosmeticsmod$exit(CallbackInfo ci) {
        HeldItems.exit();
        io.github.georgwav.cosmetics.client.modules.GlintModule.drawing(false);
    }
}
*///?}
