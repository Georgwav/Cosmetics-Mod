package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CrashReportsTest {
    private static final String REPORT = """
            ---- Minecraft Crash Report ----
            // Who set us up the TNT?

            Time: 2026-10-14 21:03:11
            Description: Rendering screen

            java.lang.NullPointerException: Cannot invoke "Object.toString()" because "value" is null
            \tat com.example.fancymenu.Widgets.label(Widgets.java:42)
            \tat net.minecraft.client.gui.screens.TitleScreen.handler$zza000$fancymenu$onRender(TitleScreen.java:9001)
            \tat net.minecraft.client.gui.screens.TitleScreen.render(TitleScreen.java:300)
            \tat net.minecraft.client.renderer.GameRenderer.render(GameRenderer.java:1000)
            Caused by: java.lang.IllegalStateException: boom
            \tat java.base/java.util.Objects.requireNonNull(Objects.java:233)
            \t... 4 more

            A detailed walkthrough of the error, its code path and all known details is as follows:
            ---------------------------------------------------------------------------------------
            \tat net.minecraft.client.Minecraft.run(Minecraft.java:1)
            """;

    @Test
    void readsWhatTheReportSays() {
        CrashReports.Report r = CrashReports.parse(REPORT);
        assertEquals("2026-10-14 21:03:11", r.time());
        assertEquals("Rendering screen", r.description());
        assertTrue(r.exception().startsWith("java.lang.NullPointerException"));
        assertEquals("com.example.fancymenu.Widgets", r.frames().get(0));
        assertTrue(r.frames().contains("java.util.Objects"), "module prefixes are dropped");
        assertFalse(r.frames().contains("net.minecraft.client.Minecraft"), "the walkthrough isn't the stack");
        assertEquals(java.util.List.of("fancymenu"), r.handlerMods());
    }

    @Test
    void guessesTheModByItsClassesThenByMixins() {
        CrashReports.Report r = CrashReports.parse(REPORT);
        Map<String, String> jars = Map.of("com.example.fancymenu.Widgets", "fancymenu");
        assertEquals("fancymenu", CrashReports.guess(r, jars::get));
        // The class isn't found in any jar: the mixin handler's mod.
        assertEquals("fancymenu", CrashReports.guess(r, c -> null));
        // All the game's own.
        CrashReports.Report game = CrashReports.parse("Description: Ticking\n\njava.lang.Error\n\tat net.minecraft.world.Level.tick(Level.java:1)\n");
        assertNull(CrashReports.guess(game, c -> "minecraft"));
    }

    @Test
    void handlerNames() {
        assertEquals("sodium", CrashReports.handlerMod("handler$abc000$sodium$render"));
        assertEquals("cosmeticsmod", CrashReports.handlerMod("redirect$zzk000$cosmeticsmod$glass"));
        assertNull(CrashReports.handlerMod("render"));
        assertNull(CrashReports.handlerMod("lambda$render$0"));
        assertTrue(CrashReports.platform("net.minecraft.client.Minecraft"));
        assertFalse(CrashReports.platform("com.example.Mod"));
    }
}
