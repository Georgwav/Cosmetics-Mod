package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import net.minecraft.client.model.geom.ModelPart;

/**
 * How the game's elytra is drawn on your player, read by the elytra mixins (WingsLayerMixin, PlayerRendererMixin for
 * the tuck from 1.21.2, and ElytraModelMixin):
 * <ul>
 *   <li>during an elytra emote it isn't drawn and your cape shows instead: you're drawn upright there, and a
 *   spread elytra would stick out from your back;</li>
 *   <li>during any other emote it is drawn from your posed body, as armour is (the game draws it from where the
 *   body would be unposed, so it was left behind when an emote moved you);</li>
 *   <li>"Elytra close to your back" (on by default) lays it on your back like a cape. The game's elytra is a 4 px
 *   deep box (a 2 px one grown by 1 px all round) starting 1 px inside the back, and the inner half of its edge is
 *   see-through, so it looked like a plate floating a pixel behind you; and it is held 15 degrees out, 5 px off at
 *   the bottom. Close to the back it is half as deep and half a pixel further in (1 to 3 px behind the body's
 *   middle, the back is at 2: its solid half lies on the back where a cape does), and tucked in to 5 degrees.
 *   Less as the wings spread to glide, none once they are spread, so it eases in and out with the game's own
 *   easing of the wings (crouching, gliding, landing) instead of jumping.</li>
 * </ul>
 *
 * <p>Render thread only.
 */
public final class ElytraLook {
    /** How far the tuck turns the elytra's lower edge in towards the back, radians. */
    static final float TUCK = (float) Math.toRadians(10);
    /** Wings spread sideways ({@code zRot}) up to this far, radians (the game's 45 degrees crouching), are fully tucked. */
    static final float TUCKED_SPREAD = (float) (Math.PI / 4);
    /** Wings spread this far (the game's 90 degrees gliding) aren't tucked. */
    static final float GLIDING_SPREAD = (float) (Math.PI / 2);
    /** How deep the elytra is drawn close to the back, of the game's 4 px. */
    static final float DEPTH = 0.5f;
    /** How far in it is drawn then, px (the wings' pivots, which the depth shrinks towards, move in too). */
    static final float IN = 0.5f;

    /** Frames the elytra was hidden, drawn from the posed body, tucked, laid on the back (read by the in-game test). */
    public static long hiddenFrames;
    public static long followedFrames;
    public static long tuckedFrames;
    public static long fittedFrames;

    private ElytraLook() {
    }

    /** Whether the elytra of the player {@code entityId} isn't drawn: during your elytra emote. */
    public static boolean hidden(int entityId) {
        return EmotePlayer.upright(entityId) > 0f;
    }

    /** Whether the elytra of the player {@code entityId} is drawn from the posed body: during your emote. */
    public static boolean followsBody(int entityId) {
        return EmotePlayer.posing(entityId);
    }

    /**
     * The elytra's turn out from the back ({@code xRot}, radians) with the tuck, if it's on; {@code zRot} is how far
     * its wings spread sideways.
     */
    public static float tucked(float xRot, float zRot) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (!config.enabled || !config.elytraCloseToBack) {
            return xRot;
        }
        float out = tuck(xRot, zRot);
        if (out != xRot) {
            tuckedFrames++;
        }
        return out;
    }

    /**
     * {@code xRot} turned in by {@link #TUCK} (less as the wings spread, see the class), no further than against the
     * back. Crouching the game holds it 40 degrees out, so tucked it still clears the body leaning 29 degrees.
     */
    static float tuck(float xRot, float zRot) {
        float amount = TUCK * closeness(zRot);
        return xRot <= 0f ? xRot : Math.max(0f, xRot - amount);
    }

    /**
     * Lays the elytra's wings on the back (see the class), once the game (and the tuck) posed them: half as deep and
     * half a pixel in, eased by how far they spread. Sets the game's own depth and place when it's off, as the game
     * doesn't reset them before 1.21.2.
     */
    public static void fit(ModelPart left, ModelPart right) {
        CosmeticsConfig config = CosmeticsConfig.get();
        float close = config.enabled && config.elytraCloseToBack ? closeness(left.zRot) : 0f;
        left.zScale = right.zScale = depth(close);
        left.z = right.z = -IN * close;
        if (close > 0f) {
            fittedFrames++;
        }
    }

    /** The wings' depth (1 = the game's) at {@code close} (0..1, see {@link #closeness}). */
    static float depth(float close) {
        return 1f - (1f - DEPTH) * close;
    }

    /** How close to the back the wings are drawn: 1 up to crouching spread ({@code zRot}), 0 spread to glide. */
    static float closeness(float zRot) {
        float spread = Math.abs(zRot);
        return Math.max(0f, Math.min(1f, (GLIDING_SPREAD - spread) / (GLIDING_SPREAD - TUCKED_SPREAD)));
    }
}
