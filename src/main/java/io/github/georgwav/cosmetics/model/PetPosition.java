package io.github.georgwav.cosmetics.model;

import java.util.Locale;

/** Where a pet sits: on a shoulder, on the head, or floating beside the player. */
public enum PetPosition {
    LEFT_SHOULDER, RIGHT_SHOULDER, HEAD, FLOATING;

    /** The style (animation) a pet plays here unless the player picks one: "air" floating, "shoulder" elsewhere. */
    public String preferredStyle() {
        return this == FLOATING ? "air" : "shoulder";
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
