package io.github.georgwav.cosmetics.mixin;

import io.github.georgwav.cosmetics.client.hud.ArmourItemsHud;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Totem pops (the game's entity event 35): yours for Armour & Items, everyone's for Crystal PvP and the effects. */
@Mixin(LivingEntity.class)
abstract class TotemPopMixin {
    @Inject(method = "handleEntityEvent(B)V", at = @At("HEAD"), require = 0)
    private void cosmeticsmod$totem(byte event, CallbackInfo ci) {
        if (event != 35) {
            return;
        }
        if ((Object) this == Minecraft.getInstance().player) {
            ArmourItemsHud.totemPops++;
        }
        io.github.georgwav.cosmetics.client.modules.CrystalPvp.popped((LivingEntity) (Object) this);
        io.github.georgwav.cosmetics.sol.fx.FxEvents.totem((LivingEntity) (Object) this);
    }
}
