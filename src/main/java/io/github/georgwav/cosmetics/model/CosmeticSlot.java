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
    SHIELD(Anchor.HELD),
    /** Auras: particles around the player, usually without a model. */
    AURA(Anchor.BODY),
    /** Pets: sit on the wearer's left shoulder; modelled standing on the origin, which is the top of that shoulder. */
    PET(Anchor.BODY),
    /** Armbands, bracelets: on an arm (the left one unless the cosmetic says), the origin halfway down it. */
    ARM(Anchor.ARMS),
    /** Boots, anklets: on both legs (unless the cosmetic says), modelled standing on the origin, the sole. */
    FEET(Anchor.LEGS);

    /** HELD: drawn in place of a held item rather than on the player model. ARMS, LEGS: on one or both limbs. */
    public enum Anchor { HEAD, BODY, HELD, ARMS, LEGS }

    /** Which limbs a cosmetic in this slot goes on unless it says: bit 1 the left one, bit 2 the right one. */
    public int defaultSides() {
        return this == ARM ? CosmeticDefinition.LEFT : CosmeticDefinition.LEFT | CosmeticDefinition.RIGHT;
    }

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
