package io.github.georgwav.cosmetics.sol;

/**
 * A census of the game hooks the modules ride on: each counts when the game calls it. The test scenario checks the
 * ones every frame passes through drew at least once, so a hook a version doesn't have (they're optional, require = 0)
 * fails the run on that version instead of quietly doing nothing. Render thread only.
 */
public enum Hooks {
    CAMERA_SETUP(true), HURT_BOB(true), CHAT_ADD(false), FIRE(false), FREELOOK_TURN(false), HELD_ITEM(false), CRYSTAL_SIZE(false),
    CRYSTAL_SPIN(false),
    /** Knives: the pack folder's packs looked for, the main hand drawn, an attack click, a held item left out in F5. */
    KNIFE_PACKS(false), KNIFE_HAND(false), KNIFE_INPUT(false), KNIFE_THIRD(false);

    /** Whether every frame in a world passes through it (so the scenario can expect it). */
    public final boolean everyFrame;
    private int count;

    Hooks(boolean everyFrame) {
        this.everyFrame = everyFrame;
    }

    public void hit() {
        count++;
    }

    public int count() {
        return count;
    }
}
