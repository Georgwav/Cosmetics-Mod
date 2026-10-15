package io.github.georgwav.cosmetics.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.modules.PvpTweaks;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** PvP Tweaks' low fire: the burning overlay moved down while it's drawn (renderFire, submitFire from 26.2). */
@Mixin(ScreenEffectRenderer.class)
abstract class LowFireMixin {
    @ModifyVariable(method = {"renderFire", "submitFire"}, at = @At("HEAD"), argsOnly = true, require = 0)
    private static PoseStack cosmeticsmod$lower(PoseStack pose) {
        io.github.georgwav.cosmetics.sol.Hooks.FIRE.hit();
        float drop = PvpTweaks.fireDrop();
        if (drop > 0f) {
            pose.translate(0f, -drop, 0f);
        }
        return pose;
    }

    @ModifyVariable(method = {"renderFire", "submitFire"}, at = @At("RETURN"), argsOnly = true, require = 0)
    private static PoseStack cosmeticsmod$back(PoseStack pose) {
        float drop = PvpTweaks.fireDrop();
        if (drop > 0f) {
            pose.translate(0f, drop, 0f);
        }
        return pose;
    }
}
