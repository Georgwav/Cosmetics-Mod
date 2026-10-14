package io.github.georgwav.cosmetics.devtools;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.aura.AuraParticles;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.compat.iris.IrisCompat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.render.ElytraLook;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import io.github.georgwav.cosmetics.render.NameTags;
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
 * the emote wheel and an emote, an elytra seen from the side (during the emote, and standing with the mod on and
 * off), an elytra emote while gliding, every page of the settings screen (and, with Mod Menu, the screen its
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

    private enum Phase { WAIT_FOR_WORLD, WARMUP, FRONT, BACK, SHIELD_ON, SHIELD_OFF, SHIELD_COSMETIC, EMOTE_WHEEL, EMOTE, EMOTE_ELYTRA, ELYTRA_STAND_ON, ELYTRA_STAND_OFF, ELYTRA_LAUNCH, ELYTRA_EMOTE, SETTINGS_UI, MOD_MENU, SHADERS_WARMUP, SHADERS_ON, SHADOW_SKIP, DONE }

    /** Ticks each settings tab stays open during the UI check. */
    private static final int UI_TAB_TICKS = 60;
    /** Ticks the player blocks with a shield, with the mod on and then off (CI screenshots 4 s in). */
    private static final int SHIELD_TICKS = 200;
    /** Ticks an emote plays during the emote check. */
    private static final int EMOTE_TICKS = 100;
    /** Ticks each look at an elytra on the ground lasts (CI screenshots 4 s in). */
    private static final int ELYTRA_LOOK_TICKS = 120;
    /** How far the body is turned from where the camera looks for those, degrees: nearly from the side. */
    private static final float SIDE_VIEW = 70f;
    /** Ticks the elytra emote plays while gliding. */
    private static final int ELYTRA_TICKS = 140;
    /** How high above where the test stands the player starts gliding, blocks. */
    private static final double ELYTRA_LAUNCH_HEIGHT = 100;
    /** Bundled emotes; fewer loaded means some failed to load. */
    private static final int BUNDLED_EMOTES = 16;

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
    private int wheelPicturesAtStart;
    private long uprightFramesAtStart;
    private long elytraFramesAtStart;
    private double[] groundPosition;
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
                    // Every cosmetic is worn, the top hat among them, so the name tag must go up.
                    float lift = NameTags.lift(client.player);
                    phases.getAsJsonObject("vanilla_front").addProperty("nameTagLift", lift);
                    if (smoke && Boolean.getBoolean(PREFIX + "equipAll") && lift <= 0f) {
                        failures.add("head cosmetics didn't raise the name tag");
                    }
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    enter(Phase.BACK);
                }
            }
            case BACK -> {
                if (ticks >= phaseTicks) {
                    record("vanilla_back");
                    if (smoke) {
                        // A plain held shield, blocking, in first person, with the mod on but nothing of it showing
                        // (only a cape), then with the mod off: the two screenshots must show the same shield.
                        client.options.setCameraType(CameraType.FIRST_PERSON);
                        CosmeticsConfig config = CosmeticsConfig.get();
                        for (CosmeticSlot slot : CosmeticSlot.values()) {
                            config.slotVisible.put(slot, slot == CosmeticSlot.CAPE);
                        }
                        client.options.keyUse.setDown(true);
                        enter(Phase.SHIELD_ON);
                    } else {
                        startShadersOrFinish();
                    }
                }
            }
            case SHIELD_ON -> {
                client.options.keyUse.setDown(true);
                if (ticks >= SHIELD_TICKS) {
                    JsonObject shield = new JsonObject();
                    shield.addProperty("blocking", client.player.isUsingItem());
                    phases.add("shield", shield);
                    CosmeticsConfig.get().enabled = false;
                    enter(Phase.SHIELD_OFF);
                }
            }
            case SHIELD_OFF -> {
                client.options.keyUse.setDown(true);
                if (ticks >= SHIELD_TICKS) {
                    // Then blocking with the shield cosmetic in first person (the screenshot shows how it sits
                    // next to the vanilla one above).
                    CosmeticsConfig config = CosmeticsConfig.get();
                    config.enabled = true;
                    for (CosmeticSlot slot : CosmeticSlot.values()) {
                        config.slotVisible.put(slot, true);
                    }
                    enter(Phase.SHIELD_COSMETIC);
                }
            }
            case SHIELD_COSMETIC -> {
                client.options.keyUse.setDown(true);
                if (ticks >= SHIELD_TICKS) {
                    client.options.keyUse.setDown(false);
                    client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    // The emote wheel (a few emotes in its slots, the rest empty), then an emote on the player.
                    for (String name : new String[]{"wave", "dance", "dab", "cheer", "salute"}) {
                        Emotes.setOnWheel(CosmeticsMod.MOD_ID + ":" + name, false, true);
                    }
                    wheelFramesAtStart = EmoteWheelScreen.framesRendered;
                    wheelPicturesAtStart = EmoteWheelScreen.picturesMade();
                    ScreenOpener.open(new EmoteWheelScreen(false));
                    enter(Phase.EMOTE_WHEEL);
                    writeMarker("ui_emote_wheel");
                }
            }
            case EMOTE_WHEEL -> {
                if (ticks >= UI_TAB_TICKS) {
                    int frames = EmoteWheelScreen.framesRendered - wheelFramesAtStart;
                    if (frames <= 0) {
                        failures.add("the emote wheel never rendered");
                    }
                    int pictures = EmoteWheelScreen.picturesMade() - wheelPicturesAtStart;
                    if (pictures <= 0) {
                        failures.add("the emote wheel drew no emote pictures");
                    }
                    JsonObject emote = new JsonObject();
                    emote.addProperty("loaded", Emotes.all().size());
                    emote.addProperty("wheelFrames", frames);
                    emote.addProperty("wheelPictures", pictures);
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
                    // The emote goes on with an elytra on, seen from the side: the elytra must stay on the posed
                    // body, as armour does.
                    groundPosition = new double[]{client.player.getX(), client.player.getY(), client.player.getZ()};
                    wearElytra(client, true);
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    elytraFramesAtStart = ElytraLook.followedFrames;
                    enter(Phase.EMOTE_ELYTRA);
                }
            }
            case EMOTE_ELYTRA -> {
                sideView(client);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    long followed = ElytraLook.followedFrames - elytraFramesAtStart;
                    JsonObject look = new JsonObject();
                    look.addProperty("followedFrames", followed);
                    phases.add("elytra_look", look);
                    if (client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && followed <= 0) {
                        failures.add("during an emote the elytra wasn't drawn from the posed body");
                    }
                    // Standing with the elytra, the mod on (close to the back) and off (the game's own).
                    EmotePlayer.stop();
                    elytraFramesAtStart = ElytraLook.tuckedFrames;
                    enter(Phase.ELYTRA_STAND_ON);
                }
            }
            case ELYTRA_STAND_ON -> {
                sideView(client);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    long tucked = ElytraLook.tuckedFrames - elytraFramesAtStart;
                    phases.getAsJsonObject("elytra_look").addProperty("tuckedFrames", tucked);
                    if (client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && tucked <= 0) {
                        failures.add("standing, the elytra wasn't drawn close to the back");
                    }
                    CosmeticsConfig.get().enabled = false;
                    enter(Phase.ELYTRA_STAND_OFF);
                }
            }
            case ELYTRA_STAND_OFF -> {
                sideView(client);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    CosmeticsConfig.get().enabled = true;
                    CosmeticsMod.LOGGER.info("Dev scenario elytra look: {}", phases.getAsJsonObject("elytra_look"));
                    client.player.setXRot(0f);
                    client.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
                    // Next an elytra emote: glide high above the ground.
                    launchGlider(client, true);
                    enter(Phase.ELYTRA_LAUNCH);
                }
            }
            case ELYTRA_LAUNCH -> {
                // Falling by now (the server only lets a player in the air glide); try every half second.
                if (!client.player.isFallFlying() && ticks % 10 == 5) {
                    startGliding(client);
                }
                if (client.player.isFallFlying() || ticks >= 100) {
                    JsonObject elytra = new JsonObject();
                    elytra.addProperty("gliding", client.player.isFallFlying());
                    phases.add("elytra", elytra);
                    if (!client.player.isFallFlying()) {
                        failures.add("the elytra test never got the player gliding");
                    }
                    // Pressing the emote key while gliding opens the elytra wheel.
                    EmoteWheelScreen.openIfAllowed(client);
                    elytra.addProperty("elytraWheel", ScreenOpener.current() instanceof EmoteWheelScreen wheel
                            && wheel.isElytraWheel());
                    ScreenOpener.open(null);
                    EmotePlayer.play(Emotes.get(CosmeticsMod.MOD_ID + ":cloud_rider"), true);
                    emoteFramesAtStart = EmotePlayer.posedFrames;
                    uprightFramesAtStart = ElytraUpright.frames;
                    elytraFramesAtStart = ElytraLook.hiddenFrames;
                    enter(Phase.ELYTRA_EMOTE);
                }
            }
            case ELYTRA_EMOTE -> {
                if (ticks >= ELYTRA_TICKS || (ticks > 20 && !client.player.isFallFlying())) {
                    JsonObject elytra = phases.getAsJsonObject("elytra");
                    elytra.addProperty("ticks", ticks);
                    elytra.addProperty("posedFrames", EmotePlayer.posedFrames - emoteFramesAtStart);
                    elytra.addProperty("uprightFrames", ElytraUpright.frames - uprightFramesAtStart);
                    elytra.addProperty("elytraHiddenFrames", ElytraLook.hiddenFrames - elytraFramesAtStart);
                    elytra.addProperty("stillPlaying", EmotePlayer.playing() != null);
                    CosmeticsMod.LOGGER.info("Dev scenario elytra: {}", elytra);
                    if (elytra.get("gliding").getAsBoolean()) {
                        if (!elytra.get("elytraWheel").getAsBoolean()) {
                            failures.add("the emote key while gliding didn't open the elytra wheel");
                        }
                        if (ElytraUpright.frames - uprightFramesAtStart <= 0) {
                            failures.add("the elytra emote never drew the player upright");
                        }
                        if (ElytraLook.hiddenFrames - elytraFramesAtStart <= 0) {
                            failures.add("the elytra was still drawn during the elytra emote");
                        }
                        if (EmotePlayer.posedFrames - emoteFramesAtStart <= 0) {
                            failures.add("the elytra emote never posed the player model");
                        }
                    }
                    EmotePlayer.stop();
                    launchGlider(client, false);
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
                    if (uiTab == 0) {
                        // The Wardrobe's tiles draw each cosmetic's picture.
                        ui.addProperty("tile_pictures", CosmeticsScreen.tilePicturesMade());
                        if (CosmeticsScreen.tilePicturesMade() <= 0) {
                            failures.add("the Wardrobe drew no tile pictures");
                        }
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

    /**
     * With {@code up}: an elytra on and high in the air (done by the integrated server; {@link #startGliding}
     * then glides). Without: back on the ground where the test stood, elytra off.
     */
    private void launchGlider(Minecraft client, boolean up) {
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null || groundPosition == null) {
            return;
        }
        java.util.UUID id = client.player.getUUID();
        double[] ground = groundPosition;
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                return;
            }
            if (up) {
                player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA));
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
                player.teleportTo(ground[0], ground[1] + ELYTRA_LAUNCH_HEIGHT, ground[2]);
            } else {
                player.teleportTo(ground[0], ground[1], ground[2]);
                player.resetFallDistance();
                player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            }
        });
    }

    /** Puts an elytra on (or takes it off) where the player stands (done by the integrated server). */
    private static void wearElytra(Minecraft client, boolean on) {
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        java.util.UUID id = client.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                player.setItemSlot(EquipmentSlot.CHEST, on ? new ItemStack(Items.ELYTRA) : ItemStack.EMPTY);
            }
        });
    }

    /**
     * Turns the body {@link #SIDE_VIEW} degrees from where the camera looks, a little from above, so the third-person
     * camera behind the player sees the back from the side (set after the game's tick, so it holds for the frames).
     */
    private static void sideView(Minecraft client) {
        var player = client.player;
        player.setXRot(20f);
        player.yBodyRot = player.getYRot() - SIDE_VIEW;
        player.yBodyRotO = player.yBodyRot;
    }

    /** Starts the player gliding, as pressing jump in the air with an elytra on does. */
    private static void startGliding(Minecraft client) {
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null) {
            return;
        }
        java.util.UUID id = client.player.getUUID();
        server.execute(() -> {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null && !player.onGround()) {
                player.startFallFlying();
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
