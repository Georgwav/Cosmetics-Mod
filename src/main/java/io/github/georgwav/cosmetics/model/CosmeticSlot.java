package io.github.georgwav.cosmetics.model;

import java.util.Locale;

/**
 * Where a cosmetic attaches. Each slot has its own priority setting in the config, so a user can
 * decide per slot whether another mod's cosmetic (Figura avatar, a cape from another mod, ...) wins.
 */
public enum CosmeticSlot {
    /** Hats, halos. Attached to the head model part. */
    HEAD(Anchor.HEAD),
    /** Glasses, masks. Attached to the head model part. */
    FACE(Anchor.HEAD),
    /** Scarves, belts. Attached to the body model part. */
    BODY(Anchor.BODY),
    /** Backpacks, wings. Attached to the body model part. */
    BACK(Anchor.BODY),
    /** Capes. Attached to the body and swayed like the vanilla cape. */
    CAPE(Anchor.BODY),
    /**
     * Shields. Drawn in place of a held shield, in the vanilla shield model's space: the plate spans
     * x -6..6, y -11..11, z -2..-1, its front facing -Z.
     */
    SHIELD(Anchor.HELD);

    /** HELD: drawn in place of a held item rather than on the player model. */
    public enum Anchor { HEAD, BODY, HELD }

    private final Anchor anchor;

    CosmeticSlot(Anchor anchor) {
        this.anchor = anchor;
    }

    public Anchor anchor() {
        return anchor;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static CosmeticSlot byId(String id) {
        for (CosmeticSlot slot : values()) {
            if (slot.id().equals(id)) {
                return slot;
            }
        }
        throw new IllegalArgumentException("Unknown cosmetic slot '" + id + "'");
    }
}
