package io.github.georgwav.cosmetics.client;

// Up to 1.21.8 Fabric's block outline event lets the client draw the outline of the block you look at itself (in
// BlockOutline's colour). 1.21.9 moved world rendering to render states; that comes later.
//? if <1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class OutlineHook {
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
            return false;
        });
    }

    // Each edge of the shape as a line, as the game draws its own outline.
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
*///?} else {
public final class OutlineHook {
    private OutlineHook() {
    }

    public static void register() {
    }
}
//?}
