package io.github.georgwav.cosmetics.model;

import java.util.Locale;

/** Where a pet sits: on a shoulder, on the head, or floating beside the player. */
public enum PetPosition {
    LEFT_SHOULDER, RIGHT_SHOULDER, HEAD, FLOATING,
    /** On the ground beside you: it runs after you, steps up blocks and sits when you stop. */
    FOLLOW,
    /** In the air beside you: it flies after you, and hovers low when you stop. */
    FOLLOW_FLYING;

    /**
     * The style (animation) a pet plays here unless the player picks one: "air" in the air, "follow" on the ground
     * after you, "shoulder" elsewhere.
     */
    public String preferredStyle() {
        return switch (this) {
            case FLOATING, FOLLOW_FLYING -> "air";
            case FOLLOW -> "follow";
            default -> "shoulder";
        };
    }

    /** Whether it moves on its own after you (drawn in the world by itself, not on you). */
    public boolean follows() {
        return this == FOLLOW || this == FOLLOW_FLYING;
    }

    /** The position named in a cosmetic file ({@code left_shoulder}, {@code head}, ...), or null for no such name. */
    public static PetPosition byName(String name) {
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
