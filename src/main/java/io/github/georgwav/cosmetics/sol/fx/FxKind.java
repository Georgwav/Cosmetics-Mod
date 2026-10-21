package io.github.georgwav.cosmetics.sol.fx;

/**
 * A kind of effect (a kill effect's style, a jump ring, a word hit...): how many may play at once and how one is
 * drawn at a moment of its life. Kinds are stateless; everything an effect needs is in its {@link Fx}.
 */
public abstract class FxKind {
    public final String id;
    /** How many of this kind may play at once (before the quality scale). */
    public final int cap;

    protected FxKind(String id, int cap) {
        this.id = id;
        this.cap = cap;
    }

    /**
     * Draws the effect at {@code age} seconds into its life ({@code now} is the pool's clock), into the pass
     * {@code out} is in.
     */
    public abstract void draw(Fx fx, float age, float now, FxOut out);
}
