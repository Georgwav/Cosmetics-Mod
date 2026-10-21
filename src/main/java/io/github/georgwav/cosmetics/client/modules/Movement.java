package io.github.georgwav.cosmetics.client.modules;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Movement: Toggle Sprint and Toggle Sneak (the game's own "Sprint: Toggle" and "Sneak: Toggle" in Controls,
 * switched from here; switching the module off puts back what you had), and a status line on screen saying whether
 * you're sprinting, sneaking or flying, and whether it's toggled.
 */
public final class Movement extends Module {
    private final Setting.Toggle sprint = add(new Setting.Toggle("sprint", true).group("toggles"));
    private final Setting.Toggle sneak = add(new Setting.Toggle("sneak", false).group("toggles"));
    /** A line on screen saying what you're doing: sprinting, sneaking, flying, and whether it's toggled. */
    private final Setting.Toggle status = add(new Setting.Toggle("status", false).group("status"));
    private final Setting.Choice place = add(new Setting.Choice("status_place", "top_left", List.of("top_left", "top_right",
            "above_hotbar")).group("status"));
    private final Setting.Color statusColor = add(new Setting.Color("status_color", Setting.Color.THEME).was(0xFFFFFFFF).themed().group("status"));
    private final Setting.Toggle statusShadow = add(new Setting.Toggle("status_shadow", true).group("status"));
    /** What Controls had before the module changed it; null while it hasn't. */
    private Boolean ownSprint;
    private Boolean ownSneak;

    public Movement() {
        super("movement", Category.VIEW, "sprint");
    }

    @Override
    protected void onEnable(Minecraft mc) {
        if (ownSprint == null) {
            ownSprint = mc.options.toggleSprint().get();
            ownSneak = mc.options.toggleCrouch().get();
        }
        apply(mc);
    }

    @Override
    protected void onSettingsChanged(Minecraft mc) {
        apply(mc);
    }

    private void apply(Minecraft mc) {
        mc.options.toggleSprint().set(sprint.get());
        mc.options.toggleCrouch().set(sneak.get());
    }

    @Override
    protected void onDisable(Minecraft mc) {
        if (ownSprint != null) {
            mc.options.toggleSprint().set(ownSprint);
            mc.options.toggleCrouch().set(ownSneak);
            ownSprint = null;
            ownSneak = null;
        }
    }

    /** The status line now, or null when there's nothing to say. */
    private String statusLine(Minecraft mc) {
        var p = mc.player;
        if (p == null) {
            return null;
        }
        String k = "cosmeticsmod.module.movement.state.";
        String line;
        if (p.getAbilities().flying) {
            line = k + "flying";
        } else if (p.isShiftKeyDown()) {
            line = sneak.get() && mc.options.keyShift.isDown() ? k + "sneaking_toggled" : k + "sneaking";
        } else if (p.isSprinting()) {
            line = sprint.get() && mc.options.keySprint.isDown() ? k + "sprinting_toggled" : k + "sprinting";
        } else if (sprint.get() && mc.options.keySprint.isDown()) {
            // Toggled on but standing still: it sprints as soon as you walk.
            line = k + "sprint_ready";
        } else {
            return null;
        }
        return Component.translatable(line).getString();
    }

    /** Drawn over the HUD (Hud): the status line in its corner. */
    public static void drawHud(Canvas g, Minecraft mc, int w, int h) {
        Movement m = Modules.MOVEMENT;
        if (!m.enabled() || !m.status.get()) {
            return;
        }
        String line = m.statusLine(mc);
        if (line == null) {
            return;
        }
        int tw = mc.font.width(line);
        int x, y;
        switch (m.place.get()) {
            case "top_right" -> {
                x = w - tw - 4;
                y = 4;
            }
            case "above_hotbar" -> {
                x = (w - tw) / 2;
                y = h - 59;
            }
            default -> {
                x = 4;
                y = 4;
            }
        }
        g.text(mc.font, line, x, y, m.statusColor.argb(), m.statusShadow.get());
    }

    @Override
    protected void saveState(JsonObject state) {
        if (ownSprint != null) {
            state.addProperty("ownSprint", ownSprint);
            state.addProperty("ownSneak", ownSneak);
        }
    }

    @Override
    protected void loadState(JsonObject state) {
        if (state.has("ownSprint")) {
            ownSprint = state.get("ownSprint").getAsBoolean();
            ownSneak = state.has("ownSneak") && state.get("ownSneak").getAsBoolean();
        }
    }
}
