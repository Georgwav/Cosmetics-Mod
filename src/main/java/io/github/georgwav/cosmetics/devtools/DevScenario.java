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
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.render.CapeElytra;
import io.github.georgwav.cosmetics.render.ElytraLook;
import io.github.georgwav.cosmetics.render.ElytraUpright;
import io.github.georgwav.cosmetics.render.NameTags;
import io.github.georgwav.cosmetics.render.RecoloredTextures;
import io.github.georgwav.cosmetics.render.Recolor;
import io.github.georgwav.cosmetics.model.ColorStyle;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.render.RenderStats;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.sol.ui.menu.SolMenu;
import io.github.georgwav.cosmetics.sol.ui.quick.QuickMenu;
import io.github.georgwav.cosmetics.sol.ui.screens.EmoteWheel;
import io.github.georgwav.cosmetics.sol.ui.screens.HudEditor;
import io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen;
import io.github.georgwav.cosmetics.client.hud.Hud;
import io.github.georgwav.cosmetics.client.ModulesSelfTest;
import io.github.georgwav.cosmetics.client.OutlineHook;
import io.github.georgwav.cosmetics.client.modules.BlockOutline;
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
 * off, also three-quarters from behind), a cape on the elytra from behind, an elytra emote while gliding, every page
 * of the settings screen (and, with Mod Menu, the screen its "Configure" button opens; a cape's page with the preview
 * wearing an elytra),
 * then (with Iris and a shaderpack) shaders on, and shaders on with "render in shadow pass" turned off.
 * The benchmark instead runs the back view and the shaders-on view three times each: at its stress load (every
 * cosmetic drawn {@code benchmark.copies} times), at a normal load (once), and with the mod switched off, so the
 * frame rates show what cosmetics cost. The stress-load phases come first and at the same time as in older builds
 * (front, back, shaders on), so an A/B run compares them like for like; the back view's normal and off phases run
 * last, after shaders are off again.
 * Each phase records what the render layer did. The result goes to {@code cosmeticsmod-results.json}
 * in the game directory, a PASSED/FAILED line goes to the log, and the game quits.
 *
 * <p>Marker files ({@code cosmeticsmod-phase-<name>}) are written as each phase starts so the CI
 * script can grab a screenshot of the window at the right moment.
 */
public final class DevScenario {
    private static final String PREFIX = CosmeticsMod.MOD_ID + ".";

    private enum Phase { WAIT_FOR_WORLD, WARMUP, FRONT, BACK, BACK_WARMUP, BACK_NORMAL, BACK_OFF, SHIELD_ON, SHIELD_OFF, SHIELD_COSMETIC, EMOTE_WHEEL, EMOTE, EMOTE_ELYTRA, ELYTRA_STAND_ON, ELYTRA_ANGLE_ON, ELYTRA_STAND_OFF, ELYTRA_ANGLE_OFF, ELYTRA_CAPE, ELYTRA_LAUNCH, ELYTRA_EMOTE, SETTINGS_UI, QUICK_MENU, MENU_MODULES, MENU_DRAWER, WARDROBE_SKINS, MENU_WARDROBE, MENU_SETTINGS, WORLD_MODULES, FREECAM, HUD_MODULES, HUD_EDITOR, MOD_MENU, SHADERS_WARMUP, SHADERS_ON, SHADERS_NORMAL, SHADERS_OFF, SHADOW_SKIP, SOL_WARMUP, SOL_ATMOS_OFF, SOL_ATMOS_ON, SOL_ALL, SOL_NIGHT, DONE }

    /** Ticks each settings tab stays open during the UI check. */
    private static final int UI_TAB_TICKS = 60;
    /** Ticks the player blocks with a shield, with the mod on and then off (CI screenshots 4 s in). */
    private static final int SHIELD_TICKS = 200;
    /** Ticks an emote plays during the emote check. */
    private static final int EMOTE_TICKS = 100;
    /** Ticks each look at an elytra on the ground lasts (CI screenshots 4 s in). */
    private static final int ELYTRA_LOOK_TICKS = 120;
    /**
     * How far the body is turned from where the camera looks for those, degrees: from the side (a gap between the
     * elytra and the back shows as grass behind the arm), and three-quarters from behind, as you see yourself.
     */
    private static final float SIDE_VIEW = 90f;
    private static final float ANGLE_VIEW = 40f;
    /** Nearly straight from behind, for a cape on the elytra. */
    private static final float BACK_VIEW = 15f;
    /** Field of view for the elytra close-ups (the whole player in view), and the game's default to go back to. */
    private static final int ZOOMED_FOV = 45;
    private static final int NORMAL_FOV = 70;
    /** Ticks the elytra emote plays while gliding. */
    private static final int ELYTRA_TICKS = 140;
    /** How high above where the test stands the player starts gliding, blocks. */
    private static final double ELYTRA_LAUNCH_HEIGHT = 100;
    /** Bundled emotes; fewer loaded means some failed to load. */
    private static final int BUNDLED_EMOTES = 17;

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
    private WardrobeScreen uiScreen;
    /** Whether Mod Menu's Configure button gave the Sol menu. */
    private boolean modMenuOpened;
    private int hubFramesAtStart;
    private int hudFramesAtStart;
    private int outlineFramesAtStart;
    private int outlineFillsAtStart;
    private long phaseStart;
    private long particlesAtPhaseStart;
    private long emoteFramesAtStart;
    private int wheelPicturesAtStart;
    private long uprightFramesAtStart;
    /** Whether the elytra emote still played after a right-click mid-flight; null when it wasn't tried. */
    private Boolean elytraEmoteKeptAfterUse;
    private long elytraFramesAtStart;
    private long fittedFramesAtStart;
    private double[] groundPosition;
    private int wheelFramesAtStart;
    private boolean finished;
    private boolean cameraWasOn;
    private net.minecraft.world.phys.Vec3 freecamFrom;

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
            // Exercise the per-cosmetic options in the world too: a tinted, larger hat whose texture is recoloured
            // (coloured as its texture says), a rainbow halo coloured in its coloured parts, and a crown coloured
            // all over.
            CosmeticsConfig.CosmeticOptions hat = CosmeticsConfig.get().editOptions("cosmeticsmod:top_hat");
            hat.color = 0xFF5555;
            hat.scale = 1.1f;
            hat.hue = 120f;
            hat.brightness = 1.2f;
            CosmeticsConfig.CosmeticOptions halo = CosmeticsConfig.get().editOptions("cosmeticsmod:halo");
            halo.rainbow = true;
            halo.colorStyle = ColorStyle.COLORED_PARTS;
            CosmeticsConfig.CosmeticOptions crown = CosmeticsConfig.get().editOptions("cosmeticsmod:crown");
            crown.color = 0x3366FF;
            crown.colorStyle = ColorStyle.WHOLE;
            // A saved outfit with its own settings, for the outfits page's tiles.
            CosmeticsConfig.Outfit outfit = new CosmeticsConfig.Outfit();
            outfit.name = "Purple";
            outfit.cosmetics = new ArrayList<>(List.of("cosmeticsmod:crown", "cosmeticsmod:feather_wings",
                    "cosmeticsmod:starlight_cape", "cosmeticsmod:halo", "cosmeticsmod:top_hat"));
            CosmeticsConfig.CosmeticOptions purpleCrown = new CosmeticsConfig.CosmeticOptions();
            purpleCrown.color = 0x8833FF;
            outfit.options.put("cosmeticsmod:crown", purpleCrown);
            CosmeticsConfig.get().savedOutfits.add(outfit);
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
                        MixinAudit.run(failures);
                    }
                    enter(Phase.WARMUP);
                } else if (ticks > 20 * 60 * 5) {
                    failures.add("never entered a world");
                    finish(client);
                }
            }
            case WARMUP -> {
                if (ticks >= 100) {
                    if (smoke) {
                        // The top hat's own red band swapped to green (its colours are known once textures load).
                        int[] hatColours = colours("cosmeticsmod:top_hat");
                        if (hatColours.length > 0) {
                            CosmeticsConfig.get().editOptions("cosmeticsmod:top_hat").swaps = new ArrayList<>(
                                    List.of(new CosmeticsConfig.ColorSwap(hatColours[0], 0x22CC44)));
                        }
                        // Nametags' health after your own name tag, shown in the front view.
                        nametagHealth(true);
                    }
                    enter(Phase.FRONT);
                }
            }
            case FRONT -> {
                if (ticks >= phaseTicks) {
                    record("vanilla_front");
                    // Every cosmetic is worn, the top hat among them, so the name tag must go up.
                    float lift = NameTags.lift(client.player);
                    phases.getAsJsonObject("vanilla_front").addProperty("nameTagLift", lift);
                    if (smoke) {
                        phases.getAsJsonObject("vanilla_front").addProperty("nameTagHealth",
                                io.github.georgwav.cosmetics.client.modules.Nametags.healthShown);
                        if (io.github.georgwav.cosmetics.client.modules.Nametags.healthShown <= 0) {
                            failures.add("Nametags never put the health after your name tag");
                        }
                        nametagHealth(false);
                    }
                    phases.getAsJsonObject("vanilla_front").addProperty("recoloredTextures", RecoloredTextures.made());
                    if (smoke && Boolean.getBoolean(PREFIX + "equipAll") && RecoloredTextures.made() <= 0) {
                        failures.add("the recoloured hat's texture was never made");
                    }
                    JsonObject painted = new JsonObject();
                    for (Recolor.Paint paint : Recolor.Paint.values()) {
                        painted.addProperty(paint.name().toLowerCase(java.util.Locale.ROOT), RecoloredTextures.painted(paint));
                    }
                    phases.getAsJsonObject("vanilla_front").add("paintedTextures", painted);
                    if (smoke && Boolean.getBoolean(PREFIX + "equipAll")) {
                        if (RecoloredTextures.painted(Recolor.Paint.GREY) <= 0) {
                            failures.add("the crown coloured all over was never painted");
                        }
                        if (RecoloredTextures.painted(Recolor.Paint.COLORED) <= 0) {
                            failures.add("the rainbow halo coloured in its coloured parts was never painted");
                        }
                    }
                    if (smoke) {
                        // A cosmetic's own colours, to change one by one: the knight shield is blue, gold and wood.
                        int shieldColours = colours("cosmeticsmod:knight_shield").length;
                        phases.getAsJsonObject("vanilla_front").addProperty("knightShieldColours", shieldColours);
                        phases.getAsJsonObject("vanilla_front").addProperty("swappedTextures", RecoloredTextures.swapped());
                        if (shieldColours < 2) {
                            failures.add("the knight shield's own colours (blue, gold) weren't found");
                        }
                        if (Boolean.getBoolean(PREFIX + "equipAll") && RecoloredTextures.swapped() <= 0) {
                            failures.add("the top hat with its red swapped to green was never painted");
                        }
                        // The settings screen's key binds are the game's own: changing one changes it in Controls.
                        for (String problem : KeyBinds.selfTest()) {
                            failures.add("key binds: " + problem);
                        }
                    }
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
            case BACK_WARMUP -> {
                // Shaders were just switched off: let the game settle before timing again.
                if (ticks >= 100) {
                    normalLoad();
                    enter(Phase.BACK_NORMAL);
                }
            }
            case BACK_NORMAL -> {
                if (ticks >= phaseTicks) {
                    record("vanilla_back_normal");
                    cosmeticsOff();
                    enter(Phase.BACK_OFF);
                }
            }
            case BACK_OFF -> {
                if (ticks >= phaseTicks) {
                    record("vanilla_back_off");
                    restoreLoad();
                    // Sol's cost, with every cosmetic drawn once as on a normal server.
                    normalLoad();
                    SolBench.setUp(client);
                    enter(Phase.SOL_WARMUP);
                }
            }
            case SOL_WARMUP -> {
                if (ticks >= 100) {
                    SolBench.setUp(client);
                    enter(Phase.SOL_ATMOS_OFF);
                }
            }
            case SOL_ATMOS_OFF -> {
                if (ticks >= phaseTicks) {
                    record("sol_atmos_off");
                    SolBench.atmosphereOn();
                    enter(Phase.SOL_ATMOS_ON);
                }
            }
            case SOL_ATMOS_ON -> {
                if (ticks >= phaseTicks) {
                    record("sol_atmos_on");
                    SolBench.everythingOn();
                    enter(Phase.SOL_ALL);
                }
            }
            case SOL_ALL -> {
                if (ticks >= phaseTicks) {
                    record("sol_all");
                    SolBench.check(phases.getAsJsonObject("sol_all"), failures);
                    SolBench.night(client);
                    enter(Phase.SOL_NIGHT);
                }
            }
            case SOL_NIGHT -> {
                if (ticks >= phaseTicks) {
                    record("sol_night");
                    SolBench.checkNight(phases.getAsJsonObject("sol_night"), failures);
                    SolBench.tearDown();
                    restoreLoad();
                    enter(Phase.DONE);
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
                    wheelFramesAtStart = EmoteWheel.framesRendered;
                    wheelPicturesAtStart = EmoteWheel.picturesMade();
                    ScreenOpener.open(new EmoteWheel(false));
                    enter(Phase.EMOTE_WHEEL);
                    writeMarker("ui_emote_wheel");
                }
            }
            case EMOTE_WHEEL -> {
                if (ticks >= UI_TAB_TICKS) {
                    int frames = EmoteWheel.framesRendered - wheelFramesAtStart;
                    if (frames <= 0) {
                        failures.add("the emote wheel never rendered");
                    }
                    int pictures = EmoteWheel.picturesMade() - wheelPicturesAtStart;
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
                    // body, as armour does. Cosmetics are hidden meanwhile, so the screenshots show the elytra.
                    groundPosition = new double[]{client.player.getX(), client.player.getY(), client.player.getZ()};
                    wearElytra(client, true);
                    for (CosmeticSlot slot : CosmeticSlot.values()) {
                        CosmeticsConfig.get().slotVisible.put(slot, false);
                    }
                    client.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                    // Zoomed in, so a gap of a pixel between the elytra and the back shows in the screenshots.
                    client.options.fov().set(ZOOMED_FOV);
                    elytraFramesAtStart = ElytraLook.followedFrames;
                    enter(Phase.EMOTE_ELYTRA);
                }
            }
            case EMOTE_ELYTRA -> {
                sideView(client, SIDE_VIEW, 10f);
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
                    fittedFramesAtStart = ElytraLook.fittedFrames;
                    enter(Phase.ELYTRA_STAND_ON);
                }
            }
            case ELYTRA_STAND_ON -> {
                sideView(client, SIDE_VIEW, 10f);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    long tucked = ElytraLook.tuckedFrames - elytraFramesAtStart;
                    long fitted = ElytraLook.fittedFrames - fittedFramesAtStart;
                    phases.getAsJsonObject("elytra_look").addProperty("tuckedFrames", tucked);
                    phases.getAsJsonObject("elytra_look").addProperty("fittedFrames", fitted);
                    if (client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && tucked <= 0) {
                        failures.add("standing, the elytra wasn't drawn close to the back");
                    }
                    if (client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && fitted <= 0) {
                        failures.add("standing, the elytra wasn't laid on the back (thin, against it)");
                    }
                    enter(Phase.ELYTRA_ANGLE_ON);
                }
            }
            case ELYTRA_ANGLE_ON -> {
                sideView(client, ANGLE_VIEW, 25f);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    CosmeticsConfig.get().enabled = false;
                    enter(Phase.ELYTRA_STAND_OFF);
                }
            }
            case ELYTRA_STAND_OFF -> {
                sideView(client, SIDE_VIEW, 10f);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    enter(Phase.ELYTRA_ANGLE_OFF);
                }
            }
            case ELYTRA_ANGLE_OFF -> {
                sideView(client, ANGLE_VIEW, 25f);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    // The mod on again with capes shown: the cape's design is on the elytra (and the cape isn't
                    // drawn, as with the game's capes).
                    CosmeticsConfig.get().enabled = true;
                    CosmeticsConfig.get().slotVisible.put(CosmeticSlot.CAPE, true);
                    elytraFramesAtStart = CapeElytra.frames;
                    enter(Phase.ELYTRA_CAPE);
                }
            }
            case ELYTRA_CAPE -> {
                sideView(client, BACK_VIEW, 15f);
                if (ticks >= ELYTRA_LOOK_TICKS) {
                    long caped = CapeElytra.frames - elytraFramesAtStart;
                    phases.getAsJsonObject("elytra_look").addProperty("capeFrames", caped);
                    phases.getAsJsonObject("elytra_look").addProperty("capeTextures", CapeElytra.made);
                    boolean wearsCape = java.util.Arrays.stream(EquippedCosmetics.forPlayer(client.player.getUUID(), true))
                            .anyMatch(c -> c.slot() == CosmeticSlot.CAPE);
                    if (wearsCape && client.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && caped <= 0) {
                        failures.add("the elytra wasn't drawn with the cape on it");
                    }
                    CosmeticsConfig config = CosmeticsConfig.get();
                    for (CosmeticSlot slot : CosmeticSlot.values()) {
                        config.slotVisible.put(slot, true);
                    }
                    CosmeticsMod.LOGGER.info("Dev scenario elytra look: {}", phases.getAsJsonObject("elytra_look"));
                    client.options.fov().set(NORMAL_FOV);
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
                    EmoteWheel.openIfAllowed(client);
                    elytra.addProperty("elytraWheel", ScreenOpener.current() instanceof EmoteWheel wheel
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
                // Right-click (a rocket) mid-flight: the elytra emote goes on.
                if (ticks == 40) {
                    client.options.keyUse.setDown(true);
                } else if (ticks == 46) {
                    client.options.keyUse.setDown(false);
                } else if (ticks == 50 && client.player.isFallFlying()) {
                    elytraEmoteKeptAfterUse = EmotePlayer.playing() != null;
                }
                if (ticks >= ELYTRA_TICKS || (ticks > 20 && !client.player.isFallFlying())) {
                    client.options.keyUse.setDown(false);
                    JsonObject elytra = phases.getAsJsonObject("elytra");
                    elytra.addProperty("ticks", ticks);
                    elytra.addProperty("posedFrames", EmotePlayer.posedFrames - emoteFramesAtStart);
                    elytra.addProperty("uprightFrames", ElytraUpright.frames - uprightFramesAtStart);
                    elytra.addProperty("elytraHiddenFrames", ElytraLook.hiddenFrames - elytraFramesAtStart);
                    elytra.addProperty("stillPlaying", EmotePlayer.playing() != null);
                    if (elytraEmoteKeptAfterUse != null) {
                        elytra.addProperty("keptAfterRightClick", elytraEmoteKeptAfterUse);
                        if (!elytraEmoteKeptAfterUse) {
                            failures.add("right-clicking (a rocket) stopped the elytra emote");
                        }
                    }
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
                    // Open every page of the Wardrobe (a cosmetic's own page, the outfits, the emotes, a cape's page
                    // with the elytra preview, and every settings page) to prove the UI works on this version.
                    uiTab = 0;
                    openSettingsTab(client);
                    enter(Phase.SETTINGS_UI);
                    writeMarker("ui_wardrobe_" + WardrobeScreen.TEST_PAGES[0]);
                }
            }
            case SETTINGS_UI -> {
                if (uiTab == 0 && ticks == UI_TAB_TICKS - 30 && uiScreen != null && !uiScreen.testClickTile()) {
                    failures.add("the Wardrobe's first tile took no click");
                }
                if (ticks >= UI_TAB_TICKS * (uiTab + 1)) {
                    String name = WardrobeScreen.TEST_PAGES[uiTab];
                    int frames = WardrobeScreen.framesRendered - uiFramesAtTabStart;
                    JsonObject ui = phases.has("settings_ui") ? phases.getAsJsonObject("settings_ui") : new JsonObject();
                    ui.addProperty(name, frames);
                    phases.add("settings_ui", ui);
                    if (frames <= 0) {
                        failures.add("Wardrobe page '" + name + "' never rendered");
                    }
                    if (name.equals("drawer_cape")) {
                        // The preview wore an elytra with the cape on it.
                        long previewed = CapeElytra.previewFrames - elytraFramesAtStart;
                        ui.addProperty("elytra_preview_frames", previewed);
                        if (uiScreen != null && uiScreen.showsPreview() && previewed <= 0) {
                            failures.add("the preview never wore an elytra on a cape's page with \"Preview with an elytra\" on");
                        }
                    }
                    if (uiTab == 0) {
                        // The Wardrobe's tiles draw each cosmetic's picture.
                        ui.addProperty("tile_pictures", WardrobeScreen.tilePicturesMade());
                        if (WardrobeScreen.tilePicturesMade() <= 0) {
                            failures.add("the Wardrobe drew no tile pictures");
                        }
                    }
                    // Every page opens at the top of its list.
                    if (uiScreen != null && !uiScreen.listAtTop()) {
                        failures.add("Wardrobe page '" + name + "' didn't start at the top of its list");
                    }
                    if (uiTab == 0 && uiScreen != null) {
                        // A click on a tile wears it (or takes it off) where it is; a click on its gear opens its page.
                        List<String> clicks = uiScreen.testCheckTileAndGear();
                        ui.addProperty("tile_clicks", clicks.isEmpty() ? "ok" : String.join("; ", clicks));
                        failures.addAll(clicks);
                    }
                    uiTab++;
                    if (uiTab < WardrobeScreen.TEST_PAGES.length) {
                        openSettingsTab(client);
                        writeMarker("ui_wardrobe_" + WardrobeScreen.TEST_PAGES[uiTab]);
                    } else {
                        // The quick menu (Right Shift), then the full menu's sections.
                        hubFramesAtStart = QuickMenu.framesRendered;
                        ScreenOpener.open(new QuickMenu(null));
                        enter(Phase.QUICK_MENU);
                        writeMarker("ui_quick");
                    }
                }
            }
            case QUICK_MENU, MENU_MODULES, MENU_DRAWER, WARDROBE_SKINS, MENU_WARDROBE, MENU_SETTINGS -> {
                // The Skins view reads the launcher's skins in the background: it stays open until they're read.
                boolean waiting = phase == Phase.WARDROBE_SKINS && !WardrobeScreen.skinsRead() && ticks < UI_TAB_TICKS * 10;
                if (ticks >= UI_TAB_TICKS && !waiting) {
                    int frames = (phase == Phase.QUICK_MENU ? QuickMenu.framesRendered
                            : phase == Phase.WARDROBE_SKINS || phase == Phase.MENU_WARDROBE ? WardrobeScreen.framesRendered
                            : SolMenu.framesRendered) - hubFramesAtStart;
                    String page = switch (phase) {
                        case QUICK_MENU -> "quick";
                        case MENU_MODULES -> "menu_modules";
                        case MENU_DRAWER -> "menu_drawer";
                        case WARDROBE_SKINS -> "wardrobe_skins";
                        case MENU_WARDROBE -> "menu_wardrobe";
                        default -> "menu_settings";
                    };
                    phases.getAsJsonObject("settings_ui").addProperty(page, frames);
                    if (frames <= 0) {
                        failures.add("the Sol menus (" + page + ") never rendered");
                    }
                    if (phase == Phase.QUICK_MENU) {
                        openMenu("modules", null);
                        enter(Phase.MENU_MODULES);
                        writeMarker("ui_menu_modules");
                    } else if (phase == Phase.MENU_MODULES) {
                        openMenu("modules", "drawer_atmosphere");
                        enter(Phase.MENU_DRAWER);
                        writeMarker("ui_menu_drawer");
                    } else if (phase == Phase.MENU_DRAWER) {
                        // The Skins view, on the launcher folder the CI run gives (ci/mcci.py): ten saved skins.
                        hubFramesAtStart = WardrobeScreen.framesRendered;
                        ScreenOpener.open(WardrobeScreen.on(null, "skins"));
                        enter(Phase.WARDROBE_SKINS);
                        writeMarker("ui_wardrobe_skins");
                    } else if (phase == Phase.WARDROBE_SKINS) {
                        phases.getAsJsonObject("settings_ui").addProperty("skins_shown", WardrobeScreen.skinsShown());
                        phases.getAsJsonObject("settings_ui").addProperty("skins_read_ticks", ticks);
                        if (WardrobeScreen.skinsShown() != 10) {
                            failures.add("the Skins view showed " + WardrobeScreen.skinsShown() + " of the launcher's 10 skins");
                        }
                        List<String> skinProblems = ModulesSelfTest.checkOwnSkin(client);
                        phases.getAsJsonObject("settings_ui").addProperty("own_skin", skinProblems.isEmpty() ? "ok" : String.join("; ", skinProblems));
                        failures.addAll(skinProblems);
                        openMenu("wardrobe", null);
                        enter(Phase.MENU_WARDROBE);
                        writeMarker("ui_menu_wardrobe");
                    } else if (phase == Phase.MENU_WARDROBE) {
                        openMenu("settings", null);
                        enter(Phase.MENU_SETTINGS);
                        writeMarker("ui_menu_settings");
                    } else {
                        // The client's world modules: thunder, midnight and fullbright, on your screen only.
                        ScreenOpener.open(null);
                        ModulesSelfTest.switchOn(client);
                        enter(Phase.WORLD_MODULES);
                        writeMarker("ui_world_modules");
                    }
                }
            }
            case WORLD_MODULES -> {
                if (ticks >= UI_TAB_TICKS) {
                    List<String> problems = ModulesSelfTest.checkAndSwitchOff(client);
                    phases.getAsJsonObject("settings_ui").addProperty("world_modules", problems.isEmpty() ? "ok" : String.join("; ", problems));
                    failures.addAll(problems);
                    // The freecam: forward held for three seconds flies the camera, not the player.
                    cameraWasOn = io.github.georgwav.cosmetics.client.Modules.CAMERA.enabled();
                    io.github.georgwav.cosmetics.client.Modules.CAMERA.setEnabled(true);
                    freecamFrom = client.player.position();
                    if (!io.github.georgwav.cosmetics.client.modules.CameraTweaks.setFreecam(client, true)) {
                        failures.add("the freecam didn't start in singleplayer");
                    }
                    client.options.keyUp.setDown(true);
                    enter(Phase.FREECAM);
                }
            }
            case FREECAM -> {
                if (ticks >= UI_TAB_TICKS) {
                    var camera = io.github.georgwav.cosmetics.client.modules.CameraTweaks.freecamPosition(1f);
                    double flown = camera.distanceTo(client.player.getEyePosition(1f));
                    double walked = client.player.position().distanceTo(freecamFrom);
                    phases.getAsJsonObject("settings_ui").addProperty("freecam", String.format(java.util.Locale.ROOT,
                            "flew %.1f, walked %.2f", flown, walked));
                    if (flown < 5.0) {
                        failures.add("the freecam didn't fly forward (" + String.format(java.util.Locale.ROOT, "%.1f", flown) + " blocks)");
                    }
                    if (walked > 0.3) {
                        failures.add("the player walked while the freecam flew (" + String.format(java.util.Locale.ROOT, "%.2f", walked) + " blocks)");
                    }
                    // Stopping gives the held keys back to the player: forward is let go after that.
                    io.github.georgwav.cosmetics.client.modules.CameraTweaks.setFreecam(client, false);
                    client.options.keyUp.setDown(false);
                    io.github.georgwav.cosmetics.client.Modules.CAMERA.setEnabled(cameraWasOn);
                    // The HUD modules, all on over the game, then in the HUD editor.
                    hudFramesAtStart = Hud.framesDrawn;
                    outlineFramesAtStart = OutlineHook.framesDrawn;
                    outlineFillsAtStart = io.github.georgwav.cosmetics.client.OutlineFill.drawn;
                    ModulesSelfTest.switchOnHud(client);
                    enter(Phase.HUD_MODULES);
                    writeMarker("ui_hud");
                }
            }
            case HUD_MODULES -> {
                if (ticks >= UI_TAB_TICKS) {
                    int frames = Hud.framesDrawn - hudFramesAtStart;
                    phases.getAsJsonObject("settings_ui").addProperty("hud", frames);
                    if (frames <= 0) {
                        failures.add("the HUD modules were never drawn");
                    }
                    int outlines = OutlineHook.framesDrawn - outlineFramesAtStart;
                    phases.getAsJsonObject("settings_ui").addProperty("outline", outlines);
                    if (BlockOutline.supported() && outlines <= 0) {
                        failures.add("the block outline was never drawn");
                    }
                    int fills = io.github.georgwav.cosmetics.client.OutlineFill.drawn - outlineFillsAtStart;
                    phases.getAsJsonObject("settings_ui").addProperty("outline_fill", fills);
                    if (BlockOutline.supported() && fills <= 0) {
                        failures.add("the block outline's fill was never drawn");
                    }
                    hudFramesAtStart = HudEditor.framesRendered;
                    ScreenOpener.open(new HudEditor(null));
                    enter(Phase.HUD_EDITOR);
                    writeMarker("ui_hud_editor");
                }
            }
            case HUD_EDITOR -> {
                if (ticks >= UI_TAB_TICKS) {
                    int frames = HudEditor.framesRendered - hudFramesAtStart;
                    phases.getAsJsonObject("settings_ui").addProperty("hud_editor", frames);
                    if (frames <= 0) {
                        failures.add("the HUD editor never rendered");
                    }
                    ScreenOpener.open(null);
                    ModulesSelfTest.switchOffHud(client);
                    if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                        openThroughModMenu();
                        enter(Phase.MOD_MENU);
                        writeMarker("ui_modmenu");
                    } else {
                        startShadersOrFinish();
                    }
                }
            }
            case MOD_MENU -> {
                if (ticks >= UI_TAB_TICKS) {
                    int frames = SolMenu.framesRendered - uiFramesAtTabStart;
                    JsonObject ui = phases.getAsJsonObject("settings_ui");
                    ui.addProperty("modmenu", frames);
                    if (modMenuOpened && frames <= 0) {
                        failures.add("the Sol menu opened from Mod Menu never rendered");
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
                        normalLoad();
                        enter(Phase.SHADERS_NORMAL);
                    }
                }
            }
            case SHADERS_NORMAL -> {
                if (ticks >= phaseTicks) {
                    record("shaders_on_normal");
                    cosmeticsOff();
                    enter(Phase.SHADERS_OFF);
                }
            }
            case SHADERS_OFF -> {
                if (ticks >= phaseTicks) {
                    record("shaders_on_off");
                    restoreLoad();
                    IrisCompat.setShadersEnabled(false);
                    // The back view at a normal load, then with the mod off (see normalLoad / cosmeticsOff).
                    enter(Phase.BACK_WARMUP);
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

    /** The own colours of a cosmetic's textures (see RecoloredTextures.palette); none if it isn't loaded. */
    private static int[] colours(String cosmeticId) {
        BakedCosmetic cosmetic = CosmeticRegistry.get(cosmeticId);
        if (cosmetic == null) {
            return new int[0];
        }
        return RecoloredTextures.palette(java.util.Arrays.stream(cosmetic.layers()).map(BakedCosmetic.Layer::texture)
                .distinct().toList());
    }

    /** Nametags on with players' health after their name (and back as it was). */
    private static boolean nametagsTurnedOn;

    private static void nametagHealth(boolean on) {
        var nametags = io.github.georgwav.cosmetics.client.Modules.NAMETAGS;
        if (on && !nametags.enabled()) {
            nametags.setEnabled(true);
            nametagsTurnedOn = true;
        } else if (!on && nametagsTurnedOn) {
            nametags.setEnabled(false);
            nametagsTurnedOn = false;
        }
        for (var setting : nametags.settings()) {
            if (setting.id.equals("health")) {
                if (on) {
                    ((io.github.georgwav.cosmetics.client.Setting.Toggle) setting).set(true);
                } else {
                    setting.reset();
                }
            }
        }
    }

    /** Benchmark: every cosmetic drawn once, as on a normal server, instead of the stress load. */
    private static void normalLoad() {
        RenderStats.copies = 1;
    }

    /** Benchmark: the mod's master switch off, so the same view shows what the game does without cosmetics. */
    private static void cosmeticsOff() {
        CosmeticsConfig.get().enabled = false;
    }

    /** Benchmark: back to the stress load with the mod on. */
    private static void restoreLoad() {
        CosmeticsConfig.get().enabled = true;
        RenderStats.copies = RenderStats.COPIES;
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
                if (on) {
                    // The shield from earlier would hide the elytra in the side views.
                    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                }
            }
        });
    }

    /**
     * Turns the body {@code turn} degrees from where the camera looks and the camera {@code down} degrees down, so the
     * third-person camera behind the player sees the back from that side (set after the game's tick, so it holds for
     * the frames).
     */
    private static void sideView(Minecraft client, float turn, float down) {
        var player = client.player;
        player.setXRot(down);
        player.yBodyRot = player.getYRot() - turn;
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
        uiFramesAtTabStart = WardrobeScreen.framesRendered;
        elytraFramesAtStart = CapeElytra.previewFrames;
        uiScreen = WardrobeScreen.testPage(uiTab);
        ScreenOpener.open(uiScreen);
    }

    /** The full menu on a section (and in a state, such as a module's drawer). */
    private void openMenu(String section, String state) {
        if (section.equals("wardrobe")) {
            // The menu's Wardrobe tab is the Wardrobe itself, in the same window.
            hubFramesAtStart = WardrobeScreen.framesRendered;
            ScreenOpener.open(WardrobeScreen.inMenu(null, "all", false));
            return;
        }
        hubFramesAtStart = SolMenu.framesRendered;
        SolMenu menu = SolMenu.create(null, section, false);
        ScreenOpener.open(state != null ? menu.stage(state) : menu);
    }

    /**
     * Opens the Sol menu the way Mod Menu's "Configure" button does: Mod Menu's list, then the screen Mod Menu gets
     * from this mod's "modmenu" entrypoint.
     */
    private void openThroughModMenu() {
        uiFramesAtTabStart = SolMenu.framesRendered;
        modMenuOpened = false;
        try {
            Screen modList = (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                    .getConstructor(Screen.class).newInstance((Screen) null);
            Object screen = Class.forName("com.terraformersmc.modmenu.ModMenu")
                    .getMethod("getConfigScreen", String.class, Screen.class)
                    .invoke(null, CosmeticsMod.MOD_ID, modList);
            if (screen instanceof SolMenu ours) {
                modMenuOpened = true;
                ScreenOpener.open(ours);
            } else {
                failures.add("Mod Menu has no settings screen for this mod (got " + screen + ")");
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            Throwable cause = e instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : e;
            CosmeticsMod.LOGGER.error("Opening the Sol menu through Mod Menu failed", cause);
            failures.add("opening the Sol menu through Mod Menu failed: " + cause);
        }
    }

    private void startShadersOrFinish() {
        if (wantShaders && IrisCompat.setShadersEnabled(true)) {
            enter(Phase.SHADERS_WARMUP);
        } else {
            if (expectShaders) {
                failures.add("shaders were expected but Iris could not enable them");
            }
            enter(benchmark ? Phase.BACK_WARMUP : Phase.DONE);
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
            p.addProperty("verticesPerFrame", (double) RenderStats.vertices / RenderStats.localPlayerFrames);
        }
        p.addProperty("cosmeticsOn", CosmeticsConfig.get().enabled);
        p.addProperty("copies", RenderStats.copies);
        p.addProperty("shaderPackInUse", Compat.IRIS && IrisCompat.isShaderPackInUse());
        if (name.startsWith("sol_")) {
            SolBench.record(p);
        }
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
