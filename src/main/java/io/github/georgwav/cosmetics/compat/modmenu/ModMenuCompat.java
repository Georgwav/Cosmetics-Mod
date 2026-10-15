package io.github.georgwav.cosmetics.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.georgwav.cosmetics.sol.ui.menu.SolMenu;

/**
 * Mod Menu's "Configure" button opens the Sol menu. Loaded only through the "modmenu"
 * entrypoint, which only Mod Menu reads, so without Mod Menu this class is never touched.
 */
public final class ModMenuCompat implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> SolMenu.create(parent, "modules", false);
    }
}
