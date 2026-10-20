package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import net.minecraft.client.Minecraft;

/**
 * Hitboxes: the game's own hitbox view (F3+B) kept on while the module is, and put back as it was when it's
 * switched off; the boxes in a colour of your choice (another for the one you look at), their lines thinner or
 * thicker, and only on players, only on living things, or not on yourself if you like. Up to 1.21.8 the entity
 * renderer's switch; from 1.21.9 the debug screen's entity hitboxes entry.
 */
public final class Hitboxes extends Module {
    private Boolean before;
    /** The entity box's colour (the eyes and the look keep theirs). */
    private final io.github.georgwav.cosmetics.client.Setting.Color color = add(new io.github.georgwav.cosmetics.client.Setting.Color("color",
            io.github.georgwav.cosmetics.client.Setting.Color.THEME).was(0xFFFFFFFF).themed().names(java.util.Map.of("white", 0xFFFFFFFF, "accent", io.github.georgwav.cosmetics.client.Setting.Color.THEME,
            "red", 0xFFFF4D4D, "green", 0xFF4CFF6A, "cyan", 0xFF40E8FF, "yellow", 0xFFFFE040, "pink", 0xFFFF60D0)).group("look"));
    /** Another colour for the one you look at. */
    private final io.github.georgwav.cosmetics.client.Setting.Toggle targetColour = add(new io.github.georgwav.cosmetics.client.Setting.Toggle(
            "target_colour", false).group("look"));
    private final io.github.georgwav.cosmetics.client.Setting.Color target = add(new io.github.georgwav.cosmetics.client.Setting.Color(
            "target_color", 0xFFFF4D4D).themed().group("look"));
    /** The lines' width against the game's. */
    private final io.github.georgwav.cosmetics.client.Setting.Slider width = add(new io.github.georgwav.cosmetics.client.Setting.Slider("width",
            1.0, 0.5, 4.0, 0.25, v -> Math.round(v * 100) + "%").group("look"));
    private final io.github.georgwav.cosmetics.client.Setting.Toggle playersOnly = add(new io.github.georgwav.cosmetics.client.Setting.Toggle(
            "players_only", false).group("which"));
    /** Not on dropped items, arrows, XP orbs and the like: only on living things. */
    private final io.github.georgwav.cosmetics.client.Setting.Toggle livingOnly = add(new io.github.georgwav.cosmetics.client.Setting.Toggle(
            "living_only", false).group("which"));
    /** Not on yourself (in third person). */
    private final io.github.georgwav.cosmetics.client.Setting.Toggle self = add(new io.github.georgwav.cosmetics.client.Setting.Toggle(
            "self", true).group("which"));

    public Hitboxes() {
        super("hitboxes", Category.VIEW, "hitbox");
    }

    /** The entity whose box the game is making now (set where it starts on it, HitboxFilterMixin). */
    private static net.minecraft.world.entity.Entity current;

    /** The game starts on {@code entity}'s box. */
    public static void current(net.minecraft.world.entity.Entity entity) {
        current = entity;
    }

    /** The colour (RGB) of the box being made: the target's colour on the one you look at; 0 for the game's white. */
    public static int colour() {
        net.minecraft.world.entity.Entity entity = current;
        Hitboxes h = io.github.georgwav.cosmetics.client.Modules.HITBOXES;
        if (!h.enabled()) {
            return 0;
        }
        if (entity != null && h.targetColour.get() && entity == Minecraft.getInstance().crosshairPickEntity) {
            return h.target.argb() & 0xFFFFFF;
        }
        int rgb = h.color.argb() & 0xFFFFFF;
        return rgb == 0xFFFFFF ? 0 : rgb;
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
        current = entity;
        Hitboxes h = io.github.georgwav.cosmetics.client.Modules.HITBOXES;
        if (!h.enabled()) {
            return true;
        }
        if (h.playersOnly.get() && !(entity instanceof net.minecraft.world.entity.player.Player)) {
            return false;
        }
        if (h.livingOnly.get() && !(entity instanceof net.minecraft.world.entity.LivingEntity)) {
            return false;
        }
        return h.self.get() || entity != Minecraft.getInstance().player;
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
        // Only meant for the box being made: not kept past the frame's boxes (nor its world).
        current = null;
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
