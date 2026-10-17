package io.github.georgwav.cosmetics.client.modules;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;

/**
 * Movement: Toggle Sprint and Toggle Sneak (the game's own "Sprint: Toggle" and "Sneak: Toggle" in Controls,
 * switched from here; switching the module off puts back what you had).
 */
public final class Movement extends Module {
    private final Setting.Toggle sprint = add(new Setting.Toggle("sprint", true));
    private final Setting.Toggle sneak = add(new Setting.Toggle("sneak", false));
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
