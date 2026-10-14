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
 *   <li>while you aren't gliding, "Elytra close to your back" (on by default) tucks its lower edge in: the game
 *   holds it 15 degrees out from your back, 5 px off at the bottom.</li>
 * </ul>
 *
 * <p>Render thread only.
 */
public final class ElytraLook {
    /** How far the tuck turns the elytra's lower edge in towards the back, radians. */
    static final float TUCK = (float) Math.toRadians(10);
    /** How far crouching leans the body forward (the game's), radians: the elytra stays at least that far out. */
    static final float CROUCH_LEAN = 0.5f;

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

    /** The elytra's turn out from the back ({@code xRot}, radians) with the tuck, if it's on. */
    public static float tucked(float xRot, boolean crouching, boolean gliding) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (gliding || !config.enabled || !config.elytraCloseToBack) {
            return xRot;
        }
        float out = tuck(xRot, crouching);
        if (out != xRot) {
            tuckedFrames++;
        }
        return out;
    }

    /** {@code xRot} turned {@link #TUCK} in, no closer than the body (upright, or leaning when crouching). */
    static float tuck(float xRot, boolean crouching) {
        float least = crouching ? CROUCH_LEAN : 0f;
        return xRot <= least ? xRot : Math.max(least, xRot - TUCK);
    }
}
