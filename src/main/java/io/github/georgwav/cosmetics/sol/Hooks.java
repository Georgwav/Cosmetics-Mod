package io.github.georgwav.cosmetics.sol;

/**
 * A census of the game hooks the modules ride on: each counts when the game calls it. The test scenario checks the
 * ones every frame passes through drew at least once, so a hook a version doesn't have (they're optional, require = 0)
 * fails the run on that version instead of quietly doing nothing. Render thread only.
 */
public enum Hooks {
    CAMERA_SETUP(true), WORLD_BOB(true), HURT_BOB(true), CHAT_ADD(false), FIRE(false), FREELOOK_TURN(false),
    MAX_ZOOM(false), HELD_ITEM(false);

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
