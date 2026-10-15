package io.github.georgwav.cosmetics.sol;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.devtools.SolScenario;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.atmosphere.SkyModGuard;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.util.Spring;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * Sol Client's start: its config, the first launch, the theme, its tick, and the reports of features that switched
 * themselves off.
 */
public final class Sol {
    private static boolean firstLaunch;
    private static boolean started;

    private Sol() {
    }

    public static void init() {
        SolConfig cfg = SolConfig.get();
        firstLaunch = !cfg.firstLaunchDone;
        Spring.reducedMotion = cfg.calmMotion();
        String theme = System.getProperty("cosmeticsmod.theme");
        ThemeState.setTheme(theme != null ? theme : io.github.georgwav.cosmetics.sol.config.SolProfiles.activeTheme(), false);
        Guard.onFailure = feature -> Notifications.post(
                Component.translatable("sol.notify.feature_off", Component.translatable("sol.feature." + feature.key)).getString());
        ClientTickEvents.END_CLIENT_TICK.register(Sol::tick);
        SolScenario.init();
        CosmeticsMod.LOGGER.info("[Sol] Sol Client ready{}", firstLaunch ? " (first launch)" : "");
    }

    /** Resource reload (startup and F3+T): the themes. */
    public static void onResourceReload(ResourceManager manager) {
        try {
            ThemeRegistry.reload(manager);
        } catch (RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.error("[Sol] Couldn't load themes", e);
        }
        io.github.georgwav.cosmetics.sol.render.LightPass.reset();
    }

    private static void tick(Minecraft mc) {
        if (!started && mc.options != null) {
            started = true;
            SolConfig cfg = SolConfig.get();
            Atmosphere at = Atmosphere.INSTANCE;
            // A shader pack paused by Atmosphere and never put back (a crash): put it back unless Atmosphere is on.
            if (cfg.irisPausedByAtmosphere && (at == null || !at.enabled())) {
                SkyModGuard.restore();
            }
            if (firstLaunch) {
                io.github.georgwav.cosmetics.sol.config.FirstLaunch.run(mc);
                cfg.firstLaunchDone = true;
                SolConfig.save();
            }
        }
        if (mc.level == null) {
            FrameInfo.inWorld = false;
        }
        io.github.georgwav.cosmetics.sol.config.SolProfiles.tick();
    }
}
