package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.client.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

/**
 * World FX › Item physics: a dropped item on the ground lies flat (a block sits still) at its own fixed turn, with no
 * bob or spin; one in the air tumbles over as it falls. ItemPhysicsMixin hands the game's own lift and spin through
 * here. Render thread only; nothing allocated per item.
 */
public final class ItemPhysics {
    private static final Quaternionf Q = new Quaternionf();
    private static final BlockPos.MutableBlockPos BELOW = new BlockPos.MutableBlockPos();
    /** For the in-game test: items drawn lying flat. */
    public static int laidFlat;

    private ItemPhysics() {
    }

    public static boolean on() {
        return Modules.WORLD_FX.enabled() && Modules.WORLD_FX.itemPhysics.get();
    }

    /** Whether something at (x, y, z) rests on a block (for the versions whose render state doesn't say). */
    public static boolean resting(double x, double y, double z) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return false;
        }
        BELOW.set(Math.floor(x), Math.floor(y - 0.06), Math.floor(z));
        BlockState state = level.getBlockState(BELOW);
        return !state.getCollisionShape(level, BELOW).isEmpty();
    }

    /**
     * The height the game lifts an item by ({@code vanilla}, its bob included): just over the ground for a flat item
     * lying there, a block's own height without the bob, the game's own in the air.
     */
    public static float lift(float vanilla, boolean onGround, boolean flat, float age, float bobOffset) {
        if (!on() || !onGround) {
            return vanilla;
        }
        if (flat) {
            return 0.03f;
        }
        float bob = (float) Math.sin(age / 10f + bobOffset) * 0.1f + 0.1f;
        return vanilla - bob;
    }

    /**
     * The turn the game gives an item ({@code spin} round the vertical, radians): on the ground its own fixed turn, laid
     * down when it's flat; in the air tumbling over.
     */
    public static Quaternionf turn(float spin, boolean onGround, boolean flat, float age, float bobOffset) {
        if (!on()) {
            return Q.rotationY(spin);
        }
        if (onGround) {
            Q.rotationY(bobOffset);
            if (flat) {
                Q.rotateX((float) (Math.PI / 2));
                laidFlat++;
            }
            return Q;
        }
        return Q.rotationXYZ(age * 0.31f + bobOffset, bobOffset, age * 0.23f);
    }
}
