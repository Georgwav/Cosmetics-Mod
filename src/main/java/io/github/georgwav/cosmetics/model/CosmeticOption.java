package io.github.georgwav.cosmetics.model;

import java.util.Locale;

/**
 * A setting players can change on a cosmetic's page. A cosmetic file can turn options off with
 * {@code "options"}; an option that is off is hidden on the page and ignored when drawing, so the
 * cosmetic always looks the way its creator set it there.
 */
public enum CosmeticOption {
    /** Pick a tint colour. */
    TINT,
    /** Cycle through the colour wheel. */
    RAINBOW,
    /** How strong the tint or rainbow colour is. */
    SATURATION,
    /** Make it bigger or smaller. */
    SIZE,
    /** Move it. */
    POSITION,
    /** Turn its glowing parts off. */
    GLOW,
    /** Turn its animation off or change its speed. */
    ANIMATION,
    /** Pick one of its styles (its skeletal meshes' named animations). */
    STYLE;

    /** Every option allowed. */
    public static final int ALL = (1 << values().length) - 1;

    public int bit() {
        return 1 << ordinal();
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
