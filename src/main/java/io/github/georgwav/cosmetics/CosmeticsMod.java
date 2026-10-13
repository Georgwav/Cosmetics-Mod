package io.github.georgwav.cosmetics;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.devtools.DevScenario;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client entrypoint. The only hard dependency is Fabric API; every other mod is optional and handled
 * in {@link Compat}.
 */
public final class CosmeticsMod implements ClientModInitializer {
    public static final String MOD_ID = "cosmeticsmod";
    public static final Logger LOGGER = LoggerFactory.getLogger("CosmeticsMod");

    @Override
    public void onInitializeClient() {
        CosmeticsConfig.load();
        Compat.logEnvironment();
        VersionHooks.registerReloadListener();
        VersionHooks.registerRenderLayer();
        VersionHooks.registerCapeOverride();
        DevScenario.init();
    }
}
