package com.terraformersmc.modmenu.api;

/**
 * Compile-time stand-in for Mod Menu's API interface, so the optional Mod Menu integration builds for
 * every Minecraft version without a per-version Mod Menu dependency. Excluded from the mod jar by
 * build.gradle: at runtime the real interface comes from Mod Menu, and without Mod Menu the
 * "modmenu" entrypoint is never loaded.
 */
public interface ModMenuApi {
    default ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> null;
    }
}
