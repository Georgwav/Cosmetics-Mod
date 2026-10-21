package io.github.georgwav.cosmetics.model;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * The cosmetics' part of a resource reload (startup and F3+T): every cosmetic and emote is read and baked with the
 * game's other loading work off its thread, and only put in place, with the themes, on the game's thread.
 */
public class CosmeticReload extends SimplePreparableReloadListener<CosmeticLoader.Prepared> {
    @Override
    protected CosmeticLoader.Prepared prepare(ResourceManager manager, ProfilerFiller profiler) {
        return CosmeticLoader.prepare(manager);
    }

    @Override
    protected void apply(CosmeticLoader.Prepared prepared, ResourceManager manager, ProfilerFiller profiler) {
        CosmeticLoader.apply(prepared);
        io.github.georgwav.cosmetics.sol.Sol.onResourceReload(manager);
    }
}
