package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.EmotePlayer;

/**
 * How the game's elytra is drawn on your player, read by the elytra mixins (WingsLayerMixin, and
 * PlayerRendererMixin / ElytraModelMixin for the tuck):
 * <ul>
 *   <li>during an elytra emote it isn't drawn and your cape shows instead: you're drawn upright there, and a
 *   spread elytra would stick out from your back;</li>
 *   <li>during any other emote it is drawn from your posed body, as armour is (the game draws it from where the
 *   body would be unposed, so it was left behind when an emote moved you);</li>
 *   <li>"Elytra close to your back" (on by default) tucks its lower edge in: the game holds it 15 degrees out from
 *   your back, 5 px off at the bottom. Less as the wings spread to glide, none once they are spread, so it eases
 *   in and out with the game's own easing of the wings (crouching, gliding, landing) instead of jumping.</li>
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

    /** Frames the elytra was hidden, drawn from the posed body, and tucked (read by the in-game test). */
    public static long hiddenFrames;
    public static long followedFrames;
    public static long tuckedFrames;

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
        float spread = Math.abs(zRot);
        float amount = TUCK * Math.max(0f, Math.min(1f, (GLIDING_SPREAD - spread) / (GLIDING_SPREAD - TUCKED_SPREAD)));
        return xRot <= 0f ? xRot : Math.max(0f, xRot - amount);
    }
}
