package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import net.minecraft.network.chat.Component;

/**
 * Keeps one thing drawing the sky. A shader pack draws its own sky and fog over Sol's, so turning Atmosphere on
 * pauses the pack (and says so, with Undo); turning it off puts the pack back exactly as it was. The pause is
 * remembered across restarts, so a crash never leaves shaders off for good.
 */
public final class SkyModGuard {
    private SkyModGuard() {
    }

    static void atmosphereOn() {
        if (!Compat.IRIS || !Compat.isShaderPackActive()) {
            return;
        }
        if (Compat.setShadersEnabled(false)) {
            SolConfig.get().irisPausedByAtmosphere = true;
            SolConfig.save();
            Notifications.post(Component.translatable("sol.notify.shaders_paused").getString(),
                    Component.translatable("sol.notify.undo").getString(), SkyModGuard::undo);
        } else {
            Notifications.post(Component.translatable("sol.notify.shaders_not_paused").getString());
        }
    }

    static void atmosphereOff() {
        restore();
    }

    /** Undo from the notification: shaders back on, Atmosphere off. */
    private static void undo() {
        Atmosphere a = Atmosphere.INSTANCE;
        if (a != null) {
            a.setEnabled(false);
        }
        restore();
    }

    /** Puts the shader pack back on if Sol paused it (also at startup after a crash, see Sol.init). */
    public static void restore() {
        SolConfig cfg = SolConfig.get();
        if (cfg.irisPausedByAtmosphere) {
            cfg.irisPausedByAtmosphere = false;
            SolConfig.save();
            if (Compat.IRIS) {
                Compat.setShadersEnabled(true);
            }
        }
    }
}
