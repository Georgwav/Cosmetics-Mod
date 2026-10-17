package io.github.georgwav.cosmetics.sol.render;

//? if <1.21.2 {
/*import io.github.georgwav.cosmetics.CosmeticsMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Sol's own core shader for its light on 1.21.1 ({@code sol_tex_color}, in that era's resources, written by
 * tools/sol-assets/make_light_pass.py): the game's textured, coloured shader without its cut of light under 10%
 * alpha, so every glow fades out smoothly. From 1.21.2 the game's own shader keeps faint light, and Sol uses it.
 */
public final class SolShaders {
    private SolShaders() {
    }

    //? if <1.21.2 {
    /*private static ShaderInstance texColor;

    public static void register() {
        CoreShaderRegistrationCallback.EVENT.register(context -> context.register(
                ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "sol_tex_color"), DefaultVertexFormat.POSITION_TEX_COLOR,
                shader -> texColor = shader));
    }

    // The textured, coloured shader without the alpha cut (the game's while Sol's hasn't loaded).
    public static ShaderInstance texColor() {
        return texColor != null ? texColor : GameRenderer.getPositionTexColorShader();
    }
    *///?} else {
    public static void register() {
    }
    //?}
}
