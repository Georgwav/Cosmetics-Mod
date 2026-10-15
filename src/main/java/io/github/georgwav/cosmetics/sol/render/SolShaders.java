package io.github.georgwav.cosmetics.sol.render;

//? if >=1.21.2 && <1.21.5 {
/*import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderDefines;
import net.minecraft.client.renderer.ShaderProgram;
import net.minecraft.resources.ResourceLocation;
*///?} elif <1.21.2 {
/*import io.github.georgwav.cosmetics.CosmeticsMod;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Sol's own core shaders, for the two ways its light is drawn (shaders/core in each render era's resources,
 * written by tools/sol-assets/make_light_pass.py):
 * <ul>
 *   <li><b>Crisp</b> ({@code sol_crisp}, the Pixel light style): a light drops every pixel under 10% alpha, so a
 *   glow ends in a clean edge instead of fading to nothing. 1.21.1's own textured shader already does this.</li>
 *   <li><b>Smooth</b>: the game's textured shader, which from 1.21.2 keeps faint light; on 1.21.1 Sol's copy of
 *   it without the cut ({@code sol_tex_color}).</li>
 * </ul>
 * From 1.21.5 the render pipelines name these shaders directly (see {@link WorldDraw}).
 */
public final class SolShaders {
    private SolShaders() {
    }

    //? if >=1.21.2 && <1.21.5 {
    /*private static final ShaderProgram CRISP = new ShaderProgram(
            ResourceLocation.fromNamespaceAndPath("cosmeticsmod", "core/sol_crisp"), DefaultVertexFormat.POSITION_TEX_COLOR,
            ShaderDefines.EMPTY);

    public static void register() {
    }

    public static ShaderProgram crisp() {
        return CRISP;
    }
    *///?} elif <1.21.2 {
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
