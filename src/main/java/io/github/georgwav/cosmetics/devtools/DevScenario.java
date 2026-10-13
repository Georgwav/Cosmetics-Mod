package io.github.georgwav.cosmetics.devtools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.aura.AuraParticles;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.compat.iris.IrisCompat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.render.RenderStats;
import io.github.georgwav.cosmetics.ui.CosmeticsScreen;
import io.github.georgwav.cosmetics.ui.EmoteWheelScreen;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Automated in-game scenario used by CI on every Minecraft version, and by the benchmark.
 * Off unless started with {@code -Dcosmeticsmod.smokeTest=true} or {@code -Dcosmeticsmod.benchmark=true}.
 *
 * <p>Once a world is loaded it switches to third person and runs fixed phases: front view, back view,
 * the emote wheel and an emote, every page of the settings screen (and, with Mod Menu, the screen its
 * "Configure" button opens),
 * then (with Iris and a shaderpack) shaders on, and shaders on with "render in shadow pass" turned off.
 * Each phase records what the render layer did. The result goes to {@code cosmeticsmod-results.json}
 * in the game directory, a PASSED/FAILED line goes to the log, and the game quits.
 *
 * <p>Marker files ({@code cosmeticsmod-phase-<name>}) are written as each phase starts so the CI
 * script can grab a screenshot of the window at the right moment.
 */
public final class DevScenario {
    private static final String PREFIX = CosmeticsMod.MOD_ID + ".";

    private enum Phase { WAIT_FOR_WORLD, WARMUP, FRONT, BACK, EMOTE_WHEEL, EMOTE, SETTINGS_UI, MOD_MENU, SHADERS_WARMUP, SHADERS_ON, SHADOW_SKIP, DONE }

    /** Ticks each settings tab stays open during the UI check. */
    private static final int UI_TAB_TICKS = 60;
    /** Ticks an emote plays during the emote check. */
    private static final int EMOTE_TICKS = 100;
    /** Bundled emotes; fewer loaded means some failed to load. */
    private static final int BUNDLED_EMOTES = 14;

    private final boolean smoke;
    private final boolean benchmark;
    private final int phaseTicks;
    private final boolean wantShaders;
    private final boolean expectShaders;
    private final Path gameDir = FabricLoader.getInstance().getGameDir();
    private final JsonObject results = new JsonObject();
    private final JsonObject phases = new JsonObject();
    private final List<String> failures = new ArrayList<>();

    private Phase phase = Phase.WAIT_FOR_WORLD;
    private int ticks;
    private int uiTab;
    private int uiFramesAtTabStart;
    /** The settings page the UI check has open. */
    private CosmeticsScreen uiScreen;
    private long phaseStart;
    private long particlesAtPhaseStart;
    private long emoteFramesAtStart;
    private int wheelFramesAtStart;
    private boolean finished;

    private DevScenario(boolean smoke, boolean benchmark) {
        this.smoke = smoke;
        this.benchmark = benchmark;
        this.phaseTicks = Integer.getInteger(PREFIX + "phaseTicks", benchmark ? 600 : 200);
        this.expectShaders = Boolean.getBoolean(PREFIX + "expectShaders");
        this.wantShaders = Compat.IRIS && (expectShaders || benchmark);
    }

    public static void init() {
        boolean smoke = Boolean.getBoolean(PREFIX + "smokeTest");
        boolean benchmark = RenderStats.TIMING;
        if (!smoke && !benchmark) {
            return;
        }
        if (!Boolean.getBoolean(PREFIX + "equipAll")) {
            CosmeticsMod.LOGGER.warn("Dev scenario without -D{}equipAll=true: only cosmetics equipped in the config are drawn", PREFIX);
        }
        if (smoke) {
            // Exercise the per-cosmetic options in the world too: a tinted, larger hat and a rainbow halo.
            CosmeticsConfig.CosmeticOptions hat = CosmeticsConfig.get().editOptions("cosmeticsmod:top_hat");
            hat.color = 0xFF5555;
            hat.scale = 1.1f;
            CosmeticsConfig.get().editOptions("cosmeticsmod:halo").rainbow = true;
        }
        DevScenario scenario = new DevScenario(smoke, benchmark);
        CosmeticsMod.LOGGER.info("Dev scenario armed (smokeTest={}, benchmark={}, phaseTicks={}, shaders={})",
                smoke, benchmark, scenario.phaseTicks, scenario.wantShaders);
        ClientTickEvents.END_CLIENT_TICK.register(scenario::tick);
    }

    private void tick(Minecraft client) {
        if (finished) {
            return;
        }
        ticks++;
        try {
            step(client);
        } catch (RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.error("Dev scenario crashed", e);
            failures.add("scenario crashed: " + e);
            finish(client);
        }
    }

    private void step(Minecraft client) {
        switch (phase) {
            case WAIT_FOR_WORLD -> {
                if (client.level != null && client.player != null) {
                    client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    if (smoke) {
                        giveShield(client);
                    }
                    enter(Phase.WARMUP);
                } else if (ticks > 20 * 60 * 5) {
                    failures.add("never entered a world");
                    finish(client);
                }
            }
            case WARMUP -> {
                if (ticks >= 100) {
                    enter(Phase.FRONT);
                }
            }
            case FRONT -> {
                if (ticks >= phaseTicks) {
                    record("vanilla_front");
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    enter(Phase.BACK);
                }
            }
            case BACK -> {
                if (ticks >= phaseTicks) {
                    record("vanilla_back");
                    if (smoke) {
                        // The emote wheel, then an emote on the player.
                        client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                        wheelFramesAtStart = EmoteWheelScreen.framesRendered;
                        ScreenOpener.open(new EmoteWheelScreen());
                        enter(Phase.EMOTE_WHEEL);
                        writeMarker("ui_emote_wheel");
                    } else {
                        startShadersOrFinish();
                    }
                }
            }
            case EMOTE_WHEEL -> {
                if (ticks >= UI_TAB_TICKS) {
                    int frames = EmoteWheelScreen.framesRendered - wheelFramesAtStart;
                    if (frames <= 0) {
                        failures.add("the emote wheel never rendered");
                    }
                    JsonObject emote = new JsonObject();
                    emote.addProperty("loaded", Emotes.all().size());
                    emote.addProperty("wheelFrames", frames);
                    phases.add("emote", emote);
                    if (Emotes.all().size() < BUNDLED_EMOTES) {
                        failures.add("only " + Emotes.all().size() + " of the " + BUNDLED_EMOTES + " bundled emotes loaded");
                    }
                    ScreenOpener.open(null);
                    // A looping emote, so it still plays when the phase ends.
                    EmotePlayer.play(Emotes.get(CosmeticsMod.MOD_ID + ":dance"));
                    emoteFramesAtStart = EmotePlayer.posedFrames;
                    enter(Phase.EMOTE);
                }
            }
            case EMOTE -> {
                if (ticks >= EMOTE_TICKS) {
                    long posed = EmotePlayer.posedFrames - emoteFramesAtStart;
                    JsonObject emote = phases.getAsJsonObject("emote");
                    emote.addProperty("posedFrames", posed);
                    emote.addProperty("stillPlaying", EmotePlayer.playing() != null);
                    CosmeticsMod.LOGGER.info("Dev scenario emote: {}", emote);
                    if (posed <= 0) {
                        failures.add("playing an emote never posed the player model");
                    }
                    EmotePlayer.stop();
                    client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    // Open every tab of the settings screen, and a cosmetic's own page, to prove the
                    // UI works on this version.
                    uiTab = 0;
                    openSettingsTab(client);
                    enter(Phase.SETTINGS_UI);
                    writeMarker("ui_" + CosmeticsScreen.testPageName(0));
                }
            }
            case SETTINGS_UI -> {
                if (ticks >= UI_TAB_TICKS * (uiTab + 1)) {
                    int frames = CosmeticsScreen.framesRendered - uiFramesAtTabStart;
                    JsonObject ui = phases.has("settings_ui") ? phases.getAsJsonObject("settings_ui") : new JsonObject();
                    ui.addProperty(CosmeticsScreen.testPageName(uiTab), frames);
                    phases.add("settings_ui", ui);
                    if (frames <= 0) {
                        failures.add("settings screen page '" + CosmeticsScreen.testPageName(uiTab) + "' never rendered");
                    }
                    // Switching tabs and resizing the window rebuild the page; its list must stay at the top.
                    if (uiScreen != null && !uiScreen.rebuildKeepsListAtTop()) {
                        failures.add("settings screen page '" + CosmeticsScreen.testPageName(uiTab)
                                + "' started its list lower after being rebuilt");
                    }
                    uiTab++;
                    if (uiTab < CosmeticsScreen.TEST_PAGE_COUNT) {
                        openSettingsTab(client);
                        writeMarker("ui_" + CosmeticsScreen.testPageName(uiTab));
                    } else if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                        openThroughModMenu();
                        enter(Phase.MOD_MENU);
                        writeMarker("ui_modmenu");
                    } else {
                        ScreenOpener.open(null);
                        startShadersOrFinish();
                    }
                }
            }
            case MOD_MENU -> {
                if (ticks >= UI_TAB_TICKS) {
                    int frames = CosmeticsScreen.framesRendered - uiFramesAtTabStart;
                    JsonObject ui = phases.getAsJsonObject("settings_ui");
                    ui.addProperty("modmenu", frames);
                    if (uiScreen != null && frames <= 0) {
                        failures.add("the settings screen opened from Mod Menu never rendered");
                    }
                    ScreenOpener.open(null);
                    startShadersOrFinish();
                }
            }
            case SHADERS_WARMUP -> {
                // Shaderpacks compile slowly on software rendering; give them time.
                if (ticks >= 200) {
                    if (!IrisCompat.isShaderPackInUse()) {
                        failures.add("Iris reports no shaderpack in use after enabling shaders");
                        IrisCompat.setShadersEnabled(false);
                        enter(Phase.DONE);
                    } else {
                        enter(Phase.SHADERS_ON);
                    }
                }
            }
            case SHADERS_ON -> {
                if (ticks >= phaseTicks) {
                    record("shaders_on");
                    if (smoke) {
                        CosmeticsConfig.get().renderInShadowPass = false;
                        enter(Phase.SHADOW_SKIP);
                    } else {
                        IrisCompat.setShadersEnabled(false);
                        enter(Phase.DONE);
                    }
                }
            }
            case SHADOW_SKIP -> {
                if (ticks >= phaseTicks) {
                    record("shaders_on_shadow_pass_skipped");
                    CosmeticsConfig.get().renderInShadowPass = true;
                    IrisCompat.setShadersEnabled(false);
                    enter(Phase.DONE);
                }
            }
            case DONE -> finish(client);
        }
    }

    /** A shield in the off hand, so the shield cosmetic (equipped with everything else) gets drawn. */
    private static void giveShield(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        java.util.UUID id = client.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
            }
        });
    }

    private static boolean shieldCosmeticLoaded() {
        return CosmeticRegistry.all().stream().anyMatch(c -> c.slot() == io.github.georgwav.cosmetics.model.CosmeticSlot.SHIELD);
    }

    private void openSettingsTab(Minecraft client) {
        uiFramesAtTabStart = CosmeticsScreen.framesRendered;
        uiScreen = CosmeticsScreen.testPage(uiTab);
        ScreenOpener.open(uiScreen);
    }

    /**
     * Opens the settings screen the way Mod Menu's "Configure" button does: Mod Menu's list, then the screen
     * Mod Menu gets from this mod's "modmenu" entrypoint.
     */
    private void openThroughModMenu() {
        uiFramesAtTabStart = CosmeticsScreen.framesRendered;
        uiScreen = null;
        try {
            Screen modList = (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                    .getConstructor(Screen.class).newInstance((Screen) null);
            Object screen = Class.forName("com.terraformersmc.modmenu.ModMenu")
                    .getMethod("getConfigScreen", String.class, Screen.class)
                    .invoke(null, CosmeticsMod.MOD_ID, modList);
            if (screen instanceof CosmeticsScreen ours) {
                uiScreen = ours;
                ScreenOpener.open(ours);
            } else {
                failures.add("Mod Menu has no settings screen for this mod (got " + screen + ")");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : e;
            CosmeticsMod.LOGGER.error("Opening the settings screen through Mod Menu failed", cause);
            failures.add("opening the settings screen through Mod Menu failed: " + cause);
        }
    }

    private void startShadersOrFinish() {
        if (wantShaders && IrisCompat.setShadersEnabled(true)) {
            enter(Phase.SHADERS_WARMUP);
        } else {
            if (expectShaders) {
                failures.add("shaders were expected but Iris could not enable them");
            }
            enter(Phase.DONE);
        }
    }

    private void enter(Phase next) {
        phase = next;
        ticks = 0;
        RenderStats.reset();
        phaseStart = System.nanoTime();
        particlesAtPhaseStart = AuraParticles.spawned;
        if (next != Phase.DONE) {
            CosmeticsMod.LOGGER.info("Dev scenario phase: {}", next);
            if (next != Phase.SETTINGS_UI && next != Phase.EMOTE_WHEEL) {
                writeMarker(next.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    private void record(String name) {
        double seconds = (System.nanoTime() - phaseStart) / 1.0e9;
        JsonObject p = new JsonObject();
        p.addProperty("seconds", seconds);
        p.addProperty("frames", RenderStats.localPlayerFrames);
        p.addProperty("fps", RenderStats.localPlayerFrames / Math.max(seconds, 1.0e-3));
        p.addProperty("meshesDrawn", RenderStats.meshesDrawn);
        p.addProperty("shadowPassMeshes", RenderStats.shadowPassMeshes);
        p.addProperty("skippedShadowPass", RenderStats.skippedShadowPass);
        p.addProperty("deferredToOtherMods", RenderStats.deferredToOtherMods);
        p.addProperty("heldDrawn", RenderStats.heldDrawn);
        p.addProperty("particles", AuraParticles.spawned - particlesAtPhaseStart);
        if (RenderStats.TIMING && RenderStats.localPlayerFrames > 0) {
            p.addProperty("cosmeticsMsPerFrame", RenderStats.nanos / 1.0e6 / RenderStats.localPlayerFrames);
        }
        p.addProperty("shaderPackInUse", Compat.IRIS && IrisCompat.isShaderPackInUse());
        phases.add(name, p);
        CosmeticsMod.LOGGER.info("Dev scenario {}: {}", name, p);
    }

    private void finish(Minecraft client) {
        finished = true;
        evaluate();
        results.addProperty("passed", failures.isEmpty());
        results.addProperty("cosmeticsLoaded", CosmeticRegistry.all().size());
        results.addProperty("renderFailures", RenderGuard.failureCount());
        results.addProperty("layerDisabled", RenderGuard.isLayerDisabled());
        results.addProperty("copies", RenderStats.COPIES);
        results.add("phases", phases);
        JsonArray failureList = new JsonArray();
        failures.forEach(failureList::add);
        results.add("failures", failureList);
        String json = new GsonBuilder().setPrettyPrinting().create().toJson(results);
        try {
            Files.writeString(gameDir.resolve("cosmeticsmod-results.json"), json);
        } catch (IOException e) {
            CosmeticsMod.LOGGER.error("Could not write results", e);
        }
        if (failures.isEmpty()) {
            CosmeticsMod.LOGGER.info("COSMETICS SMOKE TEST PASSED\n{}", json);
        } else {
            CosmeticsMod.LOGGER.error("COSMETICS SMOKE TEST FAILED: {}\n{}", failures, json);
        }
        if (Boolean.parseBoolean(System.getProperty(PREFIX + "exitWhenDone", "true"))) {
            client.stop();
        }
    }

    private void evaluate() {
        if (!smoke) {
            return;
        }
        if (CosmeticRegistry.all().isEmpty()) {
            failures.add("no cosmetics were loaded from resources");
        }
        if (RenderGuard.failureCount() > 0) {
            failures.add(RenderGuard.failureCount() + " render failure(s) were caught; see the log");
        }
        long frontMeshes = meshes("vanilla_front");
        if (phases.has("vanilla_front") && frontMeshes <= 0) {
            failures.add("the render layer drew nothing in third person (front)");
        }
        if (phases.has("vanilla_back") && meshes("vanilla_back") <= 0) {
            failures.add("the render layer drew nothing in third person (back)");
        }
        if (phases.has("vanilla_front") && Boolean.getBoolean(PREFIX + "equipAll") && shieldCosmeticLoaded()
                && held("vanilla_front") + held("vanilla_back") <= 0) {
            failures.add("the shield cosmetic was never drawn in place of the held shield");
        }
        boolean particleCosmetic = CosmeticRegistry.all().stream().anyMatch(c -> !c.definition().particles().isEmpty());
        if (phases.has("vanilla_front") && Boolean.getBoolean(PREFIX + "equipAll") && particleCosmetic
                && count("vanilla_front", "particles") + count("vanilla_back", "particles") <= 0) {
            failures.add("particle cosmetics (auras) gave off no particles");
        }
        if (phases.has("shaders_on") && phases.getAsJsonObject("shaders_on").get("shadowPassMeshes").getAsLong() <= 0) {
            failures.add("cosmetics were not drawn in the Iris shadow pass (they should cast shadows by default)");
        }
        if (phases.has("shaders_on_shadow_pass_skipped")) {
            JsonObject skip = phases.getAsJsonObject("shaders_on_shadow_pass_skipped");
            if (skip.get("shadowPassMeshes").getAsLong() != 0) {
                failures.add("cosmetics were still drawn in the shadow pass with renderInShadowPass=false");
            }
            if (skip.get("meshesDrawn").getAsLong() <= 0) {
                failures.add("cosmetics disappeared from the main pass with renderInShadowPass=false");
            }
        }
    }

    private long count(String phaseName, String key) {
        return phases.has(phaseName) ? phases.getAsJsonObject(phaseName).get(key).getAsLong() : 0;
    }

    private long held(String phaseName) {
        return phases.has(phaseName) ? phases.getAsJsonObject(phaseName).get("heldDrawn").getAsLong() : 0;
    }

    private long meshes(String phaseName) {
        return phases.has(phaseName) ? phases.getAsJsonObject(phaseName).get("meshesDrawn").getAsLong() : 0;
    }

    private void writeMarker(String name) {
        try {
            Files.writeString(gameDir.resolve("cosmeticsmod-phase-" + name), Long.toString(System.currentTimeMillis()));
        } catch (IOException e) {
            CosmeticsMod.LOGGER.warn("Could not write phase marker", e);
        }
    }
}
