package io.github.georgwav.cosmetics;

import io.github.georgwav.cosmetics.aura.AuraParticles;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.hud.Hud;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.devtools.DevScenario;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
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
        VersionHooks.registerCommand();
        KeyMapping openKey = VersionHooks.registerKeyMapping();
        KeyMapping quickKey = VersionHooks.registerQuickMenuKeyMapping();
        KeyMapping emoteKey = VersionHooks.registerEmoteKeyMapping();
        KeyMapping wheelKey = VersionHooks.registerUnboundKey("key.cosmeticsmod.quick_wheel");
        KeyMapping lookKey = VersionHooks.registerUnboundKey("key.cosmeticsmod.look_around");
        KeyMapping stylesKey = VersionHooks.registerUnboundKey("key.cosmeticsmod.styles");
        io.github.georgwav.cosmetics.sol.ui.screens.QuickWheel.key = wheelKey;
        io.github.georgwav.cosmetics.sol.ui.screens.EmoteWheel.key = emoteKey;
        KeyBinds.open = openKey;
        KeyBinds.quick = quickKey;
        KeyBinds.emotes = emoteKey;
        Modules.init(VersionHooks::registerModuleKey);
        Hud.register();
        io.github.georgwav.cosmetics.sol.Sol.init();
        try {
            OutlineHook.register();
        } catch (LinkageError e) {
            // Some Fabric API builds (early 1.21.9 ones) have no block outline event: there Block Outline changes the
            // game's own outline (OutlineFallbackMixin). Expected there, so info rather than a warning.
            LOGGER.info("No block outline event in this Fabric API, Block Outline changes the game's own outline: {}", e.toString());
        }
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> io.github.georgwav.cosmetics.client.Modules.flush());
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            io.github.georgwav.cosmetics.client.Modules.flushIfDue();
            while (openKey.consumeClick()) {
                ScreenOpener.request(() -> new io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen(null));
            }
            while (quickKey.consumeClick()) {
                ScreenOpener.request(() -> new io.github.georgwav.cosmetics.sol.ui.quick.QuickMenu(null));
            }
            while (stylesKey.consumeClick()) {
                ScreenOpener.request(() -> io.github.georgwav.cosmetics.sol.ui.menu.SolMenu.create(null, "styles", false));
            }
            while (lookKey.consumeClick()) {
                io.github.georgwav.cosmetics.sol.atmosphere.LookAround.toggle();
            }
            while (wheelKey.consumeClick()) {
                io.github.georgwav.cosmetics.sol.ui.screens.QuickWheel.openIfAllowed(client);
            }
            while (emoteKey.consumeClick()) {
                io.github.georgwav.cosmetics.sol.ui.screens.EmoteWheel.openIfAllowed(client);
            }
            ScreenOpener.tick();
            Modules.tick(client);
            AuraParticles.tick(client);
            EmotePlayer.tick(client);
        });
        DevScenario.init();
    }
}
