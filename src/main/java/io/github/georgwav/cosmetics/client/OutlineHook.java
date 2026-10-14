package io.github.georgwav.cosmetics.client;

// Fabric's block outline event lets the client draw the outline of the block you look at itself, in BlockOutline's
// colour, and skip the game's own. Up to 1.21.8 it hands over the block and a line buffer; from 1.21.9 the outline's
// render state, and the game may ask once for each of its passes, so each frame's outline (a new render state) is
// drawn once, as wide as the game draws its own (2.5 pixels on a 1080p window, wider on bigger ones). framesDrawn
// counts the frames the client drew it in, for the in-game test. 26.x has no such event yet.
//? if >=26.1 {
/*public final class OutlineHook {
    public static int framesDrawn;

    private OutlineHook() {
    }

    public static void register() {
    }
}
*///?} elif >=1.21.11 {
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class OutlineHook {
    public static int framesDrawn;
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
                ShapeRenderer.renderShape(context.matrices(), context.consumers().getBuffer(RenderTypes.lines()),
                        outline.shape(), pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                        Modules.OUTLINE.argb(), lineWidth());
                framesDrawn++;
            }
            return false;
        });
    }

    private static float lineWidth() {
        return Math.max(2.5f, Minecraft.getInstance().getWindow().getWidth() / 1920f * 2.5f);
    }
}
//?} elif >=1.21.9 {
/*import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.state.BlockOutlineRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class OutlineHook {
    public static int framesDrawn;
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
                ShapeRenderer.renderShape(context.matrices(), context.consumers().getBuffer(RenderType.lines()),
                        outline.shape(), pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z,
                        Modules.OUTLINE.argb());
                framesDrawn++;
            }
            return false;
        });
    }
}
*///?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OutlineHook {
    public static int framesDrawn;

    private OutlineHook() {
    }

    public static void register() {
        WorldRenderEvents.BLOCK_OUTLINE.register((context, outline) -> {
            if (!Modules.OUTLINE.enabled() || context.world() == null) {
                return true;
            }
            BlockPos pos = outline.blockPos();
            VoxelShape shape = outline.blockState().getShape(context.world(), pos, CollisionContext.of(outline.entity()));
            draw(context.matrixStack(), outline.vertexConsumer(), shape, pos.getX() - outline.cameraX(),
                    pos.getY() - outline.cameraY(), pos.getZ() - outline.cameraZ(), Modules.OUTLINE.argb());
            framesDrawn++;
            return false;
        });
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
