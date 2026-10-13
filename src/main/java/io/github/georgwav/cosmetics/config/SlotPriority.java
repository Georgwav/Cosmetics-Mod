package io.github.georgwav.cosmetics.config;

/**
 * Per-slot rule for what happens when another mod already puts something in the same slot for a
 * player (a cape from another source, a Figura avatar, ...).
 */
public enum SlotPriority {
    /** Default: if anything else claims the slot for that player, our cosmetic is not drawn. */
    DEFER,
    /** Draw our cosmetic anyway. Only takes effect when the user explicitly turns it on. */
    OVERRIDE
}
