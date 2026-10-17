package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import net.minecraft.client.Minecraft;

/**
 * Hitboxes: the game's own hitbox view (F3+B) kept on while the module is, and put back as it was when it's
 * switched off; the boxes in a colour of your choice, their lines thinner or thicker, and only on players if you
 * like. Up to 1.21.8 the entity renderer's switch; from 1.21.9 the debug screen's entity hitboxes entry.
 */
public final class Hitboxes extends Module {
    private Boolean before;
    /** The entity box's colour (the eyes and the look keep theirs). */
    private final io.github.georgwav.cosmetics.client.Setting.Choice color = add(new io.github.georgwav.cosmetics.client.Setting.Choice("color",
            "white", java.util.List.of("white", "accent", "red", "green", "cyan", "yellow", "pink")));
    /** The lines' width against the game's. */
    private final io.github.georgwav.cosmetics.client.Setting.Slider width = add(new io.github.georgwav.cosmetics.client.Setting.Slider("width",
            1.0, 0.5, 4.0, 0.25, v -> Math.round(v * 100) + "%"));
    private final io.github.georgwav.cosmetics.client.Setting.Toggle playersOnly = add(new io.github.georgwav.cosmetics.client.Setting.Toggle("players_only", false));

    public Hitboxes() {
        super("hitboxes", Category.VIEW, "hitbox");
    }

    /** The box colour (RGB), or 0 for the game's white. */
    public static int colour() {
        Hitboxes h = io.github.georgwav.cosmetics.client.Modules.HITBOXES;
        if (!h.enabled()) {
            return 0;
        }
        return switch (h.color.get()) {
            case "accent" -> io.github.georgwav.cosmetics.sol.theme.ThemeState.tokens().accent & 0xFFFFFF;
            case "red" -> 0xFF4D4D;
            case "green" -> 0x4CFF6A;
            case "cyan" -> 0x40E8FF;
            case "yellow" -> 0xFFE040;
            case "pink" -> 0xFF60D0;
            default -> 0;
        };
    }

    /** The lines' width against the game's (1 when the module is off). */
    public static float width() {
        Hitboxes h = io.github.georgwav.cosmetics.client.Modules.HITBOXES;
        return h.enabled() ? h.width.get().floatValue() : 1f;
    }

    /** Entities the game asked about (for the self-test). */
    public static int asked;

    /** Whether {@code entity} gets its hitbox drawn, as far as the module goes. */
    public static boolean boxes(net.minecraft.world.entity.Entity entity) {
        asked++;
        Hitboxes h = io.github.georgwav.cosmetics.client.Modules.HITBOXES;
        return !h.enabled() || !h.playersOnly.get() || entity instanceof net.minecraft.world.entity.player.Player;
    }

    private static boolean shown(Minecraft mc) {
        //? if >=1.21.9 {
        return mc.debugEntries.isCurrentlyEnabled(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES);
        //?} else {
        /*return mc.getEntityRenderDispatcher().shouldRenderHitBoxes();
        *///?}
    }

    private static void show(Minecraft mc, boolean on) {
        //? if >=1.21.9 {
        mc.debugEntries.setStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES,
                on ? net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.ALWAYS_ON
                        : net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.NEVER);
        //?} else {
        /*mc.getEntityRenderDispatcher().setRenderHitBoxes(on);
        *///?}
    }

    @Override
    protected void onEnable(Minecraft mc) {
        if (before == null) {
            before = shown(mc);
        }
        show(mc, true);
    }

    @Override
    protected void tick(Minecraft mc) {
        if (!shown(mc)) {
            show(mc, true);
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        show(mc, before != null && before);
        before = null;
    }
}
