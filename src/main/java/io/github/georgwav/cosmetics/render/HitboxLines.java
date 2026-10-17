package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.client.modules.Hitboxes;

/**
 * The lines hitboxes are drawn with, at the width Hitboxes asks for: the game's lines render type when it's the
 * game's width, else a copy of it with that width (one per width, kept). From 1.21.11 the game draws hitboxes as
 * gizmos with their own stroke width (see HitboxWidthMixin), so this is for the versions before.
 */
public final class HitboxLines {
    /** Hitbox lines drawn at a width of the module's (for the self-test). */
    public static int drawn;
    //? if <1.21.11 {
    /*private static final java.util.Map<Integer, net.minecraft.client.renderer.RenderType> TYPES = new java.util.HashMap<>();
    *///?}

    private HitboxLines() {
    }

    //? if <1.21.11 {
    /*public static net.minecraft.client.renderer.RenderType lines() {
        float scale = Hitboxes.width();
        if (scale == 1f) {
            return net.minecraft.client.renderer.RenderType.lines();
        }
        drawn++;
        // The game's own width: 2.5 pixels on a 1920-wide window, more on wider ones.
        float game = Math.max(2.5f, net.minecraft.client.Minecraft.getInstance().getWindow().getWidth() / 1920f * 2.5f);
        // A width of exactly 1 would leave the last one set, so the smallest is a hair over.
        int key = Math.max(5, Math.round(game * scale * 4f));
        return TYPES.computeIfAbsent(key, k -> create("cosmeticsmod_hitbox_lines_" + k, k / 4.0));
    }

    private static net.minecraft.client.renderer.RenderType create(String name, double width) {
        //? if >=1.21.5 {
        return net.minecraft.client.renderer.RenderType.create(name, 1536, false, false, net.minecraft.client.renderer.RenderPipelines.LINES,
                net.minecraft.client.renderer.RenderType.CompositeState.builder()
                        .setLineState(new net.minecraft.client.renderer.RenderStateShard.LineStateShard(java.util.OptionalDouble.of(width)))
                        .setLayeringState(net.minecraft.client.renderer.RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setOutputState(net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET)
                        .createCompositeState(false));
        //?} else {
        /^return net.minecraft.client.renderer.RenderType.create(name, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_COLOR_NORMAL,
                com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES, 1536, false, false,
                net.minecraft.client.renderer.RenderType.CompositeState.builder()
                        .setShaderState(net.minecraft.client.renderer.RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(new net.minecraft.client.renderer.RenderStateShard.LineStateShard(java.util.OptionalDouble.of(width)))
                        .setLayeringState(net.minecraft.client.renderer.RenderStateShard.VIEW_OFFSET_Z_LAYERING)
                        .setTransparencyState(net.minecraft.client.renderer.RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setOutputState(net.minecraft.client.renderer.RenderStateShard.ITEM_ENTITY_TARGET)
                        .setWriteMaskState(net.minecraft.client.renderer.RenderStateShard.COLOR_DEPTH_WRITE)
                        .setCullState(net.minecraft.client.renderer.RenderStateShard.NO_CULL)
                        .createCompositeState(false));
        ^///?}
    }
    *///?} else {
    /** The gizmo style for a hitbox stroke the game makes in {@code argb}: the module's colour for the white box, its width for all. */
    public static net.minecraft.gizmos.GizmoStyle stroke(int argb) {
        float scale = Hitboxes.width();
        if (scale != 1f) {
            drawn++;
        }
        int c = Hitboxes.colour();
        int colour = c != 0 && argb == -1 ? 0xFF000000 | c : argb;
        return net.minecraft.gizmos.GizmoStyle.stroke(colour, 2.5f * scale);
    }
    //?}
}
