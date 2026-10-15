package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import net.minecraft.client.Minecraft;

/**
 * Hitboxes: the game's own hitbox view (F3+B) kept on while the module is, and put back as it was when it's
 * switched off. Up to 1.21.8 the entity renderer's switch; from 1.21.9 the debug screen's entity hitboxes entry.
 */
public final class Hitboxes extends Module {
    private Boolean before;

    public Hitboxes() {
        super("hitboxes", Category.VIEW, "hitbox");
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
