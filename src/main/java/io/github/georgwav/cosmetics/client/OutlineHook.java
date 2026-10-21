package io.github.georgwav.cosmetics.client;

// Fabric's block outline event lets the client draw the outline of the block you look at itself, in BlockOutline's
// colour and width (a wider or thinner copy of the game's lines before 1.21.11), with its see-through fill, and skip
// the game's own. Up to 1.21.8 it hands over the block and a line buffer; from 1.21.9 the outline's render state, and
// the game may ask once for each of its passes, so each frame's outline (a new render state) is drawn once, as wide as
// the game draws its own (2.5 pixels on a 1080p window, wider on bigger ones) times the module's width. framesDrawn
// counts the frames the client drew it in, for the in-game test; available says it can be drawn: by the event, or on
// 1.21.9 (whose Fabric API has no such event) by OutlineFallbackMixin changing the game's own. 26.x has no such
// event: LevelRendererOutlineMixin gives the game's own outline the colour and width there (color(); the
// high-contrast black border under it stays black) and adds the fill.
//? if >=26.1 {
/*public final class OutlineHook {
    public static int framesDrawn;
    public static boolean available = true;

    private OutlineHook() {
    }

    public static void register() {
    }

    public static int color(int vanilla) {
        if (!Modules.OUTLINE.enabled() || vanilla == 0xFF000000) {
            return vanilla;
        }
        framesDrawn++;
        // Sol's glow outline is drawn with the effects; the game's lines go clear.
        return Modules.OUTLINE.glowLook() ? 0x00FFFFFF : Modules.OUTLINE.argb();
    }
}
*///?} elif >=1.21.11 {
import io.github.georgwav.cosmetics.client.modules.BlockOutline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class OutlineHook {
    public static int framesDrawn;
    public static boolean available;
    private static BlockOutlineRenderState drawn;

    private OutlineHook() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            if (!Modules.OUTLINE.enabled() || context.consumers() == null) {
                return true;
            }
            if (outline != drawn) {
                drawn = outline;
                Vec3 camera = context.gameRenderer().getMainCamera().position();
                BlockPos pos = outline.pos();
                double x = pos.getX() - camera.x, y = pos.getY() - camera.y, z = pos.getZ() - camera.z;
                if (!Modules.OUTLINE.glowLook()) {
                    ShapeRenderer.renderShape(context.matrices(), context.consumers().getBuffer(RenderTypes.lines()),
                            outline.shape(), x, y, z, Modules.OUTLINE.argb(), lineWidth() * BlockOutline.widthScale());
                }
                int fill = Modules.OUTLINE.fillArgb();
                if (fill != 0) {
                    OutlineFill.faces(context.matrices().last(), context.consumers().getBuffer(RenderTypes.debugQuads()), outline.shape(),
                            x, y, z, fill);
                }
                framesDrawn++;
            }
            return false;
        });
        available = true;
    }

    private static float lineWidth() {
        return Math.max(2.5f, Minecraft.getInstance().getWindow().getWidth() / 1920f * 2.5f);
    }
}
//?} elif >=1.21.9 {
/*import io.github.georgwav.cosmetics.client.modules.BlockOutline;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class OutlineHook {
    public static int framesDrawn;
    public static boolean available;
    /^* Whether Fabric's event draws it; without it (1.21.9's Fabric API) OutlineFallbackMixin changes the game's own. ^/
    private static boolean event;
    private static BlockOutlineRenderState drawn;

    private OutlineHook() {
    }

    public static void register() {
        WorldRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, outline) -> {
            if (!Modules.OUTLINE.enabled() || context.consumers() == null) {
                return true;
            }
            if (outline != drawn) {
                drawn = outline;
                Vec3 camera = context.gameRenderer().getMainCamera().position();
                BlockPos pos = outline.pos();
                double x = pos.getX() - camera.x, y = pos.getY() - camera.y, z = pos.getZ() - camera.z;
                if (!Modules.OUTLINE.glowLook()) {
                    ShapeRenderer.renderShape(context.matrices(), context.consumers().getBuffer(
                            io.github.georgwav.cosmetics.render.HitboxLines.lines(BlockOutline.widthScale())), outline.shape(), x, y, z,
                            Modules.OUTLINE.argb());
                }
                int fill = Modules.OUTLINE.fillArgb();
                if (fill != 0) {
                    OutlineFill.faces(context.matrices().last(), context.consumers().getBuffer(RenderType.debugQuads()), outline.shape(),
                            x, y, z, fill);
                }
                framesDrawn++;
            }
            return false;
        });
        event = true;
        available = true;
    }

    /^* OutlineFallbackMixin is in place: without the event, the game's own outline takes the module's look. ^/
    public static void fallbackReady() {
        available = true;
    }

    /^* Whether the fallback changes the game's outline now (no event, the module on). ^/
    public static boolean fallback() {
        return !event && Modules.OUTLINE.enabled();
    }

    /^* The fallback's colour for the game's outline (its high-contrast black border stays black). ^/
    public static int color(int vanilla) {
        if (!fallback() || vanilla == 0xFF000000) {
            return vanilla;
        }
        framesDrawn++;
        return Modules.OUTLINE.glowLook() ? 0x00FFFFFF : Modules.OUTLINE.argb();
    }
}
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.client.modules.BlockOutline;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OutlineHook {
    public static int framesDrawn;
    public static boolean available;

    private OutlineHook() {
    }

    public static void register() {
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
            if (!Modules.OUTLINE.enabled() || context.world() == null) {
                return true;
            }
            BlockPos pos = outline.blockPos();
            VoxelShape shape = outline.blockState().getShape(context.world(), pos, CollisionContext.of(outline.entity()));
            double x = pos.getX() - outline.cameraX(), y = pos.getY() - outline.cameraY(), z = pos.getZ() - outline.cameraZ();
            float scale = BlockOutline.widthScale();
            // At the game's width into its own lines; wider or thinner into a copy of them with that width.
            VertexConsumer lines = scale == 1f || context.consumers() == null ? outline.vertexConsumer()
                    : context.consumers().getBuffer(io.github.georgwav.cosmetics.render.HitboxLines.lines(scale));
            if (!Modules.OUTLINE.glowLook()) {
                draw(context.matrixStack(), lines, shape, x, y, z, Modules.OUTLINE.argb());
            }
            int fill = Modules.OUTLINE.fillArgb();
            if (fill != 0 && context.consumers() != null) {
                OutlineFill.faces(context.matrixStack().last(), context.consumers().getBuffer(net.minecraft.client.renderer.RenderType.debugQuads()),
                        shape, x, y, z, fill);
            }
            framesDrawn++;
            return false;
        });
        available = true;
    }

    private static void draw(PoseStack poseStack, VertexConsumer lines, VoxelShape shape, double x, double y, double z,
                             int argb) {
        PoseStack.Pose pose = poseStack.last();
        shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float dx = (float) (x2 - x1);
            float dy = (float) (y2 - y1);
            float dz = (float) (z2 - z1);
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0f) {
                dx /= length;
                dy /= length;
                dz /= length;
            }
            lines.addVertex(pose, (float) (x1 + x), (float) (y1 + y), (float) (z1 + z)).setColor(argb).setNormal(pose, dx, dy, dz);
            lines.addVertex(pose, (float) (x2 + x), (float) (y2 + y), (float) (z2 + z)).setColor(argb).setNormal(pose, dx, dy, dz);
        });
    }
}
*///?}
