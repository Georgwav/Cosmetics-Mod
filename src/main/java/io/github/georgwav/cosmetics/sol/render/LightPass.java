package io.github.georgwav.cosmetics.sol.render;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.atmosphere.FogControl;
import io.github.georgwav.cosmetics.sol.atmosphere.KeyLight;
import io.github.georgwav.cosmetics.sol.atmosphere.SkyTime;
import io.github.georgwav.cosmetics.sol.atmosphere.QualityGovernor;
import io.github.georgwav.cosmetics.sol.atmosphere.SkyPass;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.Ease;
import io.github.georgwav.cosmetics.sol.util.LogOnce;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
//? if >=1.21.2 {
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
//?} else {
/*import net.minecraft.client.renderer.PostChain;
*///?}
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.HashMap;
import java.util.Map;

/**
 * The Sol light pass: post-processing that makes the world look lit rather than tinted. Two chains of the game's
 * own post effects, one set of shaders per render era (tools/sol-assets/make_light_pass.py):
 *
 * <ul>
 *   <li>after the level, before the hand ({@link #world()}): sunlight on the blocks (normals rebuilt from depth,
 *   faces toward the sun in the theme's light, faces away in its shadow colour, strongest at golden hour, a warm
 *   rim on silhouettes against a low sun), the fog scattering brighter and warmer toward the sun and thicker near
 *   the ground, and light shafts (a quarter-resolution radial blur of the bright sky toward the sun);</li>
 *   <li>after the hand ({@link #grade()}): bloom (the bright parts at half resolution, blurred through a five-level
 *   dual-filter mip chain and added back in the theme's bloom tint), the theme's colour grade (a 32x32x32 LUT built
 *   from its colours, or one it ships), a subtle vignette, then the Punchy module's rim round the hands.</li>
 * </ul>
 *
 * <p>Presets follow Atmosphere's quality: Low has no pass, Medium the bloom, grade and scattering (at most
 * 0.8 ms at 1080p), High adds sunlight on the blocks and the light shafts (at most 1.5 ms); Auto picks from the frame
 * time. It stands aside while an Iris shader pack is running. The passes read their per-frame values from a small
 * texture (the sun, the camera, the theme) so nothing in any era has to set shader uniforms from code.
 */
public final class LightPass {
    public static final int OFF = 0, MEDIUM = 1, HIGH = 2;
    private static final int PARAMS = 128;
    private static final float SEA_LEVEL = 63f;
    /** Distant Horizons is installed: its land never reaches the depth buffer (see the skyline rim). */
    private static final boolean DISTANT_HORIZONS = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("distanthorizons");
    private static final int LUT_W = 1024, LUT_H = 32;

    private static final float[] values = new float[PARAMS];
    private static final int[] params = new int[PARAMS];
    private static SolTexture paramsTexture;
    private static SolTexture lutTexture;
    private static SolTexture blackTexture;
    private static int[] lut;
    private static String lutKey = "";
    private static int preset;
    private static long lastWorldFrame = -1;
    /** The frame the parameter texture was last filled for. */
    private static long lastUpdate = -1;
    /** For the benchmark: nanoseconds spent in the passes this frame. */
    public static long nanos;
    /**
     * Strength multipliers for tuning the look against the reference frames (the scenario's tune shots set them;
     * all 1 in play): bloom, scatter, shafts, grade, sun, rim, haze.
     */
    public static final Map<String, Float> tune = new HashMap<>();

    private static float tune(String key) {
        Float f = tune.get(key);
        return f == null ? 1f : f;
    }

    //? if >=1.21.2 {
    private static final CrossFrameResourcePool pool = new CrossFrameResourcePool(3);
    //?} else {
    /*private static final Map<String, PostChain> chains = new HashMap<>();
    private static int chainW, chainH;
    *///?}

    private LightPass() {
    }

    /** The preset in use now: off when Atmosphere is off, a shader pack runs, or the setting says Low. */
    public static int preset() {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null || !Atmosphere.active() || !FrameInfo.inWorld || !Guard.on(Feature.LIGHT_PASS)
                || Compat.isShaderPackActive() || !FogControl.activeSky()) {
            return OFF;
        }
        return switch (at.quality.get()) {
            case "low" -> OFF;
            case "medium" -> MEDIUM;
            case "high" -> HIGH;
            default -> switch (QualityGovernor.level()) {
                case 0 -> OFF;
                case 1 -> MEDIUM;
                default -> HIGH;
            };
        };
    }

    /** After the level, before the hand: the world in the theme's light. */
    public static void world() {
        nanos = 0L;
        preset = preset();
        if (preset == OFF) {
            return;
        }
        long t0 = System.nanoTime();
        Guard.run(Feature.LIGHT_PASS, () -> {
            update();
            lastWorldFrame = FrameInfo.frame;
            run(preset == HIGH ? "sol_world_high" : "sol_world_medium");
        });
        nanos = System.nanoTime() - t0;
    }

    /**
     * After the hand: bloom, grade, vignette, then the glow round the hands (after them, so the bloom doesn't spread
     * the rim into a haze and the grade doesn't shift its colour); then motion blur over the finished frame.
     */
    public static void grade() {
        if (preset != OFF && lastWorldFrame == FrameInfo.frame) {
            long t0 = System.nanoTime();
            //? if <1.21.5 {
            /*boolean keep = glowWanted();
            if (keep) {
                keepDepth(true);
            }
            *///?}
            Guard.run(Feature.LIGHT_PASS, () -> run("sol_grade"));
            //? if <1.21.5 {
            /*if (keep) {
                keepDepth(false);
            }
            *///?}
            nanos += System.nanoTime() - t0;
        }
        handGlow();
        motionBlur();
        pixelWorld();
        //? if >=1.21.2 {
        pool.endFrame();
        //?}
    }

    // ------------------------------------------------------------------ the world in pixels

    private static SolTexture pixelTexture;
    private static int pixelUploaded = -1;
    private static int pixelCell, pixelOx, pixelOy;
    private static long pixelAsked = -10;
    /** For the in-game test: frames the world was drawn in pixels. */
    public static int pixelFrames;

    /**
     * The quick menu asks for the world under it in its own pixels (each frame it's open): cells {@code cell} screen
     * pixels wide, lined up with its wheel ({@code left}, {@code top}: a corner of the grid in screen pixels).
     */
    public static void pixelate(int cell, int left, int top) {
        int h = Mc.mainTarget().height;
        pixelCell = Math.max(1, Math.min(255, cell));
        pixelOx = Math.floorMod(left, pixelCell);
        pixelOy = Math.floorMod(h - top, pixelCell);
        pixelAsked = FrameInfo.frame;
    }

    /** The world in the pixel UI's pixels while the quick menu is open (it asked on the frame before). */
    private static void pixelWorld() {
        if (FrameInfo.frame - pixelAsked > 1 || pixelCell < 2 || !FrameInfo.inWorld || Compat.isShaderPackActive()
                || !Guard.on(Feature.QUICK_MENU)) {
            return;
        }
        long t0 = System.nanoTime();
        ensureTextures();
        if (pixelTexture == null || pixelTexture.released()) {
            //? if >=1.21.2 {
            String ns = CosmeticsMod.MOD_ID;
            //?} else {
            /*String ns = "minecraft";
            *///?}
            pixelTexture = new SolTexture(ns, "textures/effect/sol_pixel.png", 1, 1, false);
            pixelUploaded = -1;
        }
        int v = 0xFF000000 | pixelCell << 16 | pixelOx << 8 | pixelOy;
        if (v != pixelUploaded) {
            pixelTexture.upload(new int[]{v});
            pixelUploaded = v;
        }
        if (Guard.run(Feature.QUICK_MENU, () -> run("sol_pixel"))) {
            pixelFrames++;
        }
        nanos += System.nanoTime() - t0;
    }

    // ------------------------------------------------------------------ hand glow

    /** For the in-game test: frames the hand glow ran on. */
    public static int handGlowFrames;
    /** The last frame the hand glow ran on (its motion trail carries on only from the frame before). */
    private static long lastGlowFrame = -10;

    /**
     * The Punchy module's glow round the first-person hands and held item: everything drawn after the game cleared
     * the depth for the hands is the mask, and a thin rim is laid round it from each pixel's distance to its edge.
     * Runs whether Atmosphere is on or not; it stands aside for a shader pack (which draws the hands its own way).
     */
    private static void handGlow() {
        if (!glowWanted()) {
            return;
        }
        long t0 = System.nanoTime();
        if (Guard.run(Feature.HAND_GLOW, () -> {
            if (lastUpdate != FrameInfo.frame) {
                update();
            }
            // How the hands moved, now that they're drawn (the trail fills their path).
            HandMotion.params(values, 92);
            pack();
            run("sol_hand_glow");
        })) {
            handGlowFrames++;
        }
        lastGlowFrame = FrameInfo.frame;
        nanos += System.nanoTime() - t0;
    }

    private static boolean glowWanted() {
        return FrameInfo.inWorld && !Compat.isShaderPackActive() && Guard.on(Feature.HAND_GLOW)
                && io.github.georgwav.cosmetics.client.modules.PunchyModule.glowNow() > 0f;
    }

    //? if <1.21.5 {
    /*/^* The main target's depth while the grade runs (null until the glow first needs it). ^/
    private static com.mojang.blaze3d.pipeline.RenderTarget depthKept;

    /^*
     * Keeps the main target's depth over the grade ({@code save}) and puts it back after: 1.21.1 to 1.21.4's post passes
     * clear the depth of the target they draw to, and the glow after the grade finds the hands by it.
     ^/
    private static void keepDepth(boolean save) {
        var main = Mc.mainTarget();
        if (save) {
            if (depthKept != null && (depthKept.width != main.width || depthKept.height != main.height)) {
                depthKept.destroyBuffers();
                depthKept = null;
            }
            if (depthKept == null) {
                //? if <1.21.2 {
                depthKept = new com.mojang.blaze3d.pipeline.TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
                //?} else {
                /^depthKept = new com.mojang.blaze3d.pipeline.TextureTarget(main.width, main.height, true);
                ^///?}
            }
            depthKept.copyDepthFrom(main);
        } else {
            main.copyDepthFrom(depthKept);
        }
        main.bindWrite(false);
    }
    *///?}

    // ------------------------------------------------------------------ motion blur

    private static SolTexture blurTexture;
    private static int blurUploaded = -1;
    private static boolean blurWarm;
    /** For the in-game test: frames motion blur ran on. */
    public static int blurFrames;

    /** Whether this version can blur: its post chains keep a target from one frame to the next (not 1.21.2 to 1.21.5). */
    public static boolean blurSupported() {
        //? if >=1.21.2 && <1.21.6 {
        /*return false;
        *///?} else {
        return true;
        //?}
    }

    /**
     * Motion blur (the Screen module): the frame mixed with the last one, which holds the ones before it. The amount
     * is what's left of a frame after 1/60 s, so the trail lasts as long at any frame rate. It stands aside for a
     * shader pack (which has its own).
     */
    private static void motionBlur() {
        float amount = io.github.georgwav.cosmetics.client.modules.ScreenTweaks.motionBlur();
        if (amount > 0f && !blurSupported()) {
            LogOnce.info("motion-blur", "Motion blur isn't available on this version of Minecraft");
            amount = 0f;
        }
        if (amount <= 0f || !FrameInfo.inWorld || Compat.isShaderPackActive() || !Guard.on(Feature.MOTION_BLUR)) {
            blurWarm = false;
            return;
        }
        // The first frame only fills the last-frame target.
        float k = blurWarm ? (float) Math.pow(amount, Math.min(4.0, FrameInfo.frameSeconds * 60.0)) : 0f;
        blurWarm = true;
        ensureTextures();
        if (blurTexture == null || blurTexture.released()) {
            //? if >=1.21.2 {
            String ns = CosmeticsMod.MOD_ID;
            //?} else {
            /*String ns = "minecraft";
            *///?}
            blurTexture = new SolTexture(ns, "textures/effect/sol_blur.png", 1, 1, false);
            blurUploaded = -1;
        }
        int r = Math.round(k * 255f);
        if (r != blurUploaded) {
            blurTexture.upload(new int[]{0xFF000000 | r << 16});
            blurUploaded = r;
        }
        if (Guard.run(Feature.MOTION_BLUR, () -> run("sol_motion_blur"))) {
            blurFrames++;
        }
    }

    /** Resources reloaded: the game rebuilds its chains (1.21.1: Sol's own, closed here). */
    public static void reset() {
        //? if <1.21.2 {
        /*chains.values().forEach(PostChain::close);
        chains.clear();
        *///?}
    }

    // ------------------------------------------------------------------ running a chain

    //? if >=1.21.11 {
    private static final Map<String, Identifier> ids = new HashMap<>();
    //?} elif >=1.21.2 {
    /*private static final Map<String, ResourceLocation> ids = new HashMap<>();
    *///?}

    //? if >=1.21.11 {
    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, name);
    }
    //?} elif >=1.21.2 {
    /*private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, name);
    }
    *///?}

    private static void run(String name) {
        Minecraft mc = Minecraft.getInstance();
        ensureTextures();
        //? if >=1.21.2 {
        var id = ids.computeIfAbsent(name, LightPass::id);
        PostChain chain = mc.getShaderManager().getPostChain(id, LevelTargetBundle.MAIN_TARGETS);
        if (chain == null) {
            throw new IllegalStateException("the post effect " + id + " didn't load");
        }
        //? if >=1.21.5 && <1.21.6 {
        /*chain.process(Mc.mainTarget(), pool, null);
        *///?} else {
        chain.process(Mc.mainTarget(), pool);
        //?}
        //? if <1.21.5 {
        /*Mc.mainTarget().bindWrite(false);
        *///?}
        //?} else {
        /*var target = Mc.mainTarget();
        PostChain chain = chains.get(name);
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), target,
                        ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "shaders/post/" + name + ".json"));
            } catch (java.io.IOException e) {
                throw new IllegalStateException("the post effect " + name + " didn't load", e);
            }
            chain.resize(target.width, target.height);
            chains.put(name, chain);
        }
        if (chainW != target.width || chainH != target.height) {
            chainW = target.width;
            chainH = target.height;
            chains.values().forEach(c -> c.resize(chainW, chainH));
        }
        chain.process(FrameInfo.partialTick);
        target.bindWrite(false);
        *///?}
    }

    private static void ensureTextures() {
        //? if >=1.21.2 {
        String ns = CosmeticsMod.MOD_ID;
        //?} else {
        /*String ns = "minecraft";
        *///?}
        if (paramsTexture == null || paramsTexture.released()) {
            paramsTexture = new SolTexture(ns, "textures/effect/sol_params.png", PARAMS, 1, false);
        }
        if (lutTexture == null || lutTexture.released()) {
            lutTexture = new SolTexture(ns, "textures/effect/sol_lut.png", LUT_W, LUT_H, true);
            lutKey = "";
        }
        if (blackTexture == null || blackTexture.released()) {
            blackTexture = new SolTexture(ns, "textures/effect/sol_black.png", 1, 1, false);
            blackTexture.upload(new int[]{0xFF000000});
        }
    }

    // ------------------------------------------------------------------ the frame's values

    private static final Matrix4f scratch = new Matrix4f();

    /** Fills and uploads the parameter texture, and rebuilds the grade when the theme changes it. */
    private static void update() {
        lastUpdate = FrameInfo.frame;
        ensureTextures();
        Tokens tk = ThemeState.tokens();
        Matrix4f proj = FrameInfo.projection;
        float[] v = values;
        java.util.Arrays.fill(v, 0f);

        // The theme's light (the sun, the moon, the rune, the ring, the ribbons...: see KeyLight): on screen (uv),
        // visible, and its direction in view space.
        float in = Atmosphere.intensity();
        float sx = KeyLight.x, sy = KeyLight.y, sz = KeyLight.z;
        var view = FrameInfo.view;
        float vx = view.m00() * sx + view.m10() * sy + view.m20() * sz;
        float vy = view.m01() * sx + view.m11() * sy + view.m21() * sz;
        float vz = view.m02() * sx + view.m12() * sy + view.m22() * sz;
        // The sun's light on the screen only as far as the sky reaches where you are (none in a cave).
        float open = io.github.georgwav.cosmetics.sol.atmosphere.SkyOpen.get();
        float vis = KeyLight.visible * (1f - FrameInfo.rain * 0.85f) * open;
        if (vz < -0.02f) {
            float u = proj.m00() * vx / -vz * 0.5f + 0.5f;
            float w = proj.m11() * vy / -vz * 0.5f + 0.5f;
            v[0] = u;
            v[1] = w;
            float off = Math.max(Math.max(-u, u - 1f), Math.max(-w, w - 1f));
            vis *= 1f - Ease.smoothstep(0f, 0.35f, off);
        } else {
            vis = 0f;
        }
        v[2] = vis;
        v[3] = vx;
        v[4] = vy;
        v[5] = vz;
        // Field of view and depth: 1 / distance = depth · A + B, from the projection itself (so reversed depth and
        // either depth range come out right).
        v[6] = 1f / Math.max(1e-4f, proj.m00());
        v[7] = 1f / Math.max(1e-4f, proj.m11());
        boolean zeroToOne = zeroToOne();
        float c0 = zeroToOne ? -proj.m22() : 0.5f - 0.5f * proj.m22();
        float c1 = zeroToOne ? proj.m32() : 0.5f * proj.m32();
        if (Math.abs(c1) < 1e-9f) {
            c1 = -1e-9f;
        }
        v[8] = 1f / c1;
        v[9] = -c0 / c1;
        // The cleared depth (where nothing was drawn: the sky): 1, or 0 with reversed depth.
        v[10] = c1 > 0f ? 0f : 1f;
        v[11] = preset;
        v[12] = KeyLight.strength;
        v[13] = Math.min(1f, tk.lightStrength * 1.2f * tune("sun")) * in * open;
        v[14] = FogControl.start(100000f);
        v[15] = FogControl.end(100000f);
        // Where the game's own land ends (the skyline rim is drawn only there).
        v[30] = FrameInfo.renderDistance;
        // The eye's height above sea level (the low haze is measured from there).
        v[31] = (float) FrameInfo.camY - SEA_LEVEL;
        // Where the depth buffer's land ends while far land goes on (the world's light fades out before it): the
        // last chunks with Distant Horizons (its land never reaches the depth buffer), else the game's far plane,
        // along the view axis (Voxy's land past it reads as sky; the game's own land ends well short of it).
        v[59] = DISTANT_HORIZONS ? FrameInfo.renderDistance : 1f / Math.max(1e-6f, v[10] * v[8] + v[9]);
        v[60] = DISTANT_HORIZONS ? 0f : 1f;
        float rain = FrameInfo.rain;
        v[16] = tk.scatter * (1f - 0.7f * rain) * tune("scatter") * in * open;
        v[17] = tk.haze * tune("haze") * (0.4f + 0.6f * in) * open;
        v[18] = tk.bloom * 0.9f * tune("bloom") * in;
        // The theme's threshold, as a brightness the bloom starts at (luminance; only real light blooms), raised
        // in a bright scene (a day sky or white mist would otherwise bloom all over).
        float sceneKey = Math.max(Colors.luma(tk.skyMiddle), Colors.luma(FogControl.color()));
        float thr = Math.max(0.55f + 0.35f * tk.bloomThreshold, 0.6f + 0.33f * Ease.smoothstep(0.25f, 0.65f, sceneKey));
        v[19] = Math.min(0.97f, thr * tune("threshold"));
        v[18] *= 1f - 0.45f * Ease.smoothstep(0.35f, 0.75f, sceneKey);
        v[20] = tk.vignette * in;
        // Light shafts only show against a darker sky: in a bright day scene they would wash the sky round the
        // sun white (Frost Morning), so they fade with the scene's brightness.
        v[21] = (Atmosphere.INSTANCE != null && Atmosphere.INSTANCE.rays.get() ? KeyLight.shafts : 0f)
                * (1f - 0.85f * Ease.smoothstep(0.3f, 0.65f, sceneKey)) * tune("shafts") * in * open;
        v[22] = FrameInfo.time;
        // The skyline rim at the end of the world; none with Distant Horizons, whose land the depth buffer never
        // holds (the game's last chunks would get a line drawn across its land).
        v[23] = DISTANT_HORIZONS ? 0f : 0.3f * KeyLight.rim * tune("rim") * in * open;
        v[24] = view.m10();
        v[25] = view.m11();
        v[26] = view.m12();
        v[27] = rain;
        v[28] = FrameInfo.width / Math.max(1f, FrameInfo.height);
        v[29] = tk.night;
        rgb(v, 32, KeyLight.color);
        rgb(v, 35, tk.lightShadow);
        rgb(v, 38, FogControl.color());
        rgb(v, 41, KeyLight.glow);
        rgb(v, 44, tk.bloomTint);
        // The rim on faces lit at a grazing angle, and a lightning flash lighting the world.
        v[47] = KeyLight.faceRim * tune("rim") * in * open;
        v[48] = KeyLight.flash * (0.5f + 0.5f * in) * tune("flash") * open;
        // Exposure on the faces the light reaches: a night theme's light has to light a dark world (by day a held
        // night is also drawn darker, see SkyTime). A theme that dims its own day (Eclipse, Monsoon's overcast)
        // stays dim: its light shows in the rims and the colour, not in a lift.
        float dark = Math.max(tk.night, 1f - SkyTime.worldLight);
        v[49] = 1.3f * KeyLight.strength * dark * in * tune("exposure") * open;
        v[50] = DISTANT_HORIZONS ? 0f : KeyLight.rimAll * in * open;
        // The second light colour sweeping over the ground in bands, and the world's east in view space (the
        // bands' reference).
        rgb(v, 51, KeyLight.color2);
        v[54] = KeyLight.bands;
        v[55] = view.m00();
        v[56] = view.m01();
        v[57] = view.m02();
        // A theme that dims its own day (Eclipse's darkened world, Monsoon's storm) dims the world here too: the
        // game's brightness curve lifts a dimmed lightmap most of the way back.
        float dim = 0.35f + 0.65f * Math.min(1f, tk.lightBoost);
        v[58] = Ease.lerp(1f, dim, in);
        // A second light (the moon where the theme's own light is something else), in view space.
        float ex = KeyLight.extraX, ey = KeyLight.extraY, ez = KeyLight.extraZ;
        v[64] = view.m00() * ex + view.m10() * ey + view.m20() * ez;
        v[65] = view.m01() * ex + view.m11() * ey + view.m21() * ez;
        v[66] = view.m02() * ex + view.m12() * ey + view.m22() * ez;
        rgb(v, 67, KeyLight.extraColor);
        v[70] = KeyLight.extraStrength * in * open;
        // Day for night: how far the world turns to the night's blue with its shadows lifted, and the floor's colour;
        // and how much warm lights bloom against the cold.
        float night = io.github.georgwav.cosmetics.sol.atmosphere.NightSky.dark * (1f - 0.3f * rain);
        v[71] = night * in * open;
        rgb(v, 72, io.github.georgwav.cosmetics.sol.atmosphere.NightSky.floorColor);
        v[75] = night * in;
        // The glow round the hands (the Punchy module).
        io.github.georgwav.cosmetics.client.modules.PunchyModule.glowParams(v, 76, tk, lastGlowFrame == FrameInfo.frame - 1);
        pack();

        String key = tk.lut + "|" + tk.gradeHighlights + "|" + tk.gradeShadows + "|" + tk.saturation + "|" + tk.contrast
                + "|" + tune("grade") * Atmosphere.intensity();
        if (!key.equals(lutKey)) {
            lutKey = key;
            lut = buildLut(tk, lut);
            lutTexture.upload(lut);
        }
    }

    /** The values into the parameter texture (each float's bytes in a texel) and up to the GPU. */
    private static void pack() {
        for (int i = 0; i < PARAMS; i++) {
            int bits = Float.floatToRawIntBits(values[i]);
            int b0 = bits & 0xFF, b1 = (bits >>> 8) & 0xFF, b2 = (bits >>> 16) & 0xFF, b3 = (bits >>> 24) & 0xFF;
            params[i] = (b3 << 24) | (b0 << 16) | (b1 << 8) | b2;
        }
        paramsTexture.upload(params);
    }

    private static void rgb(float[] v, int at, int c) {
        v[at] = Colors.r(c) / 255f;
        v[at + 1] = Colors.g(c) / 255f;
        v[at + 2] = Colors.b(c) / 255f;
    }

    /**
     * Whether the depth buffer runs 0 to 1 (clip control): from 26.2 the game turns it on wherever the GPU can, which
     * read as -1 to 1 put every block a million blocks away (a hard haze line at the horizon, no sunlight on blocks).
     */
    private static boolean zeroToOne() {
        //? if >=26.2 {
        /*return com.mojang.blaze3d.systems.RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        *///?} else {
        return false;
        //?}
    }

    // ------------------------------------------------------------------ the grade

    /** The theme's grade as the pass's LUT, or the LUT image it ships ({@code grade.lut}). */
    public static int[] buildLut(Tokens tk, int[] out) {
        if (out == null || out.length != LUT_W * LUT_H) {
            out = new int[LUT_W * LUT_H];
        }
        if (tk.lut != null && loadLut(tk.lut, out)) {
            return out;
        }
        return Grade.lut(tk.gradeHighlights, tk.gradeShadows, tk.saturation, tk.contrast, tune("grade") * Atmosphere.intensity(), out);
    }

    /** A LUT image a theme ships (namespace:path to a 1024x32 PNG), read from the resources. */
    private static boolean loadLut(String location, int[] out) {
        try {
            var mc = Minecraft.getInstance();
            //? if >=1.21.11 {
            var id = Identifier.parse(location);
            //?} else {
            /*var id = ResourceLocation.parse(location);
            *///?}
            var res = mc.getResourceManager().getResource(id);
            if (res.isEmpty()) {
                LogOnce.warn("lut-missing-" + location, "The theme's LUT {} isn't in any resource pack", location);
                return false;
            }
            try (var in = res.get().open()) {
                java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
                if (img == null || img.getWidth() != LUT_W || img.getHeight() != LUT_H) {
                    LogOnce.warn("lut-size-" + location, "The theme's LUT {} isn't a 1024x32 image", location);
                    return false;
                }
                img.getRGB(0, 0, LUT_W, LUT_H, out, 0, LUT_W);
                return true;
            }
        } catch (Exception e) {
            LogOnce.warn("lut-read-" + location, "Couldn't read the theme's LUT {}: {}", location, e.toString());
            return false;
        }
    }
}
