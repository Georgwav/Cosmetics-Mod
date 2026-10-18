package io.github.georgwav.cosmetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block Outline's fill: the outside faces of the looked-at block's shape, see-through, a hair outside it so they
 * don't flicker against the block, into a position-and-colour quads buffer (the game's debug quads).
 */
public final class OutlineFill {
    /** How far outside the block the faces sit, and how far past a face another box is looked for. */
    private static final double OUT = 0.002, NUDGE = 1e-4;
    /** Fills drawn (for the in-game test). */
    public static int drawn;

    private OutlineFill() {
    }

    /**
     * The outside faces of {@code shape} at (x, y, z) from the camera, in {@code argb}: a face against another of the
     * shape's boxes (inside stairs, a fence or a wall) is left out, so the see-through fill shows no seams.
     */
    public static void faces(PoseStack.Pose pose, VertexConsumer c, VoxelShape shape, double x, double y, double z, int argb) {
        drawn++;
        java.util.List<AABB> boxes = shape.toAabbs();
        for (int i = 0; i < boxes.size(); i++) {
            AABB b = boxes.get(i);
            double cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2, cz = (b.minZ + b.maxZ) / 2;
            float x0 = (float) (b.minX + x - OUT), y0 = (float) (b.minY + y - OUT), z0 = (float) (b.minZ + z - OUT);
            float x1 = (float) (b.maxX + x + OUT), y1 = (float) (b.maxY + y + OUT), z1 = (float) (b.maxZ + z + OUT);
            if (!covered(boxes, i, cx, b.minY - NUDGE, cz)) {
                quad(pose, c, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            }
            if (!covered(boxes, i, cx, b.maxY + NUDGE, cz)) {
                quad(pose, c, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            }
            if (!covered(boxes, i, cx, cy, b.minZ - NUDGE)) {
                quad(pose, c, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            }
            if (!covered(boxes, i, cx, cy, b.maxZ + NUDGE)) {
                quad(pose, c, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
            }
            if (!covered(boxes, i, b.minX - NUDGE, cy, cz)) {
                quad(pose, c, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            }
            if (!covered(boxes, i, b.maxX + NUDGE, cy, cz)) {
                quad(pose, c, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
            }
        }
    }

    /** Whether the point just past a face of box {@code self} is inside another of the boxes. */
    private static boolean covered(java.util.List<AABB> boxes, int self, double px, double py, double pz) {
        for (int j = 0; j < boxes.size(); j++) {
            AABB o = boxes.get(j);
            if (j != self && px > o.minX && px < o.maxX && py > o.minY && py < o.maxY && pz > o.minZ && pz < o.maxZ) {
                return true;
            }
        }
        return false;
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer c, int argb, float ax, float ay, float az, float bx, float by,
                             float bz, float cx, float cy, float cz, float dx, float dy, float dz) {
        c.addVertex(pose, ax, ay, az).setColor(argb);
        c.addVertex(pose, bx, by, bz).setColor(argb);
        c.addVertex(pose, cx, cy, cz).setColor(argb);
        c.addVertex(pose, dx, dy, dz).setColor(argb);
    }
}
