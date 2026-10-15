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
 *   from its colours, or one it ships) and a subtle vignette.</li>
 * </ul>
 *
 * <p>Presets follow Atmosphere's quality: Low has no pass, Medium the bloom, grade and scattering (at most
 * 0.8 ms at 1080p), High adds sunlight on the blocks and the light shafts (at most 1.5 ms); Auto picks from the frame
 * time. It stands aside while an Iris shader pack is running. The passes read their per-frame values from a small
 * texture (the sun, the camera, the theme) so nothing in any era has to set shader uniforms from code.
 */
public final class LightPass {
    public static final int OFF = 0, MEDIUM = 1, HIGH = 2;
    private static final int PARAMS = 64;
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

    /** After the hand: bloom, grade, vignette. */
    public static void grade() {
        if (preset == OFF || lastWorldFrame != FrameInfo.frame) {
            //? if >=1.21.2 {
            pool.endFrame();
            //?}
            return;
        }
        long t0 = System.nanoTime();
        Guard.run(Feature.LIGHT_PASS, () -> run("sol_grade"));
        //? if >=1.21.2 {
        pool.endFrame();
        //?}
        nanos += System.nanoTime() - t0;
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
        float vis = KeyLight.visible * (1f - FrameInfo.rain * 0.85f);
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
        v[13] = Math.min(1f, tk.lightStrength * 1.2f * tune("sun")) * in;
        v[14] = FogControl.start(100000f);
        v[15] = FogControl.end(100000f);
        float rain = FrameInfo.rain;
        v[16] = tk.scatter * (1f - 0.7f * rain) * tune("scatter") * in;
        v[17] = tk.haze * tune("haze") * (0.4f + 0.6f * in);
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
                * (1f - 0.85f * Ease.smoothstep(0.3f, 0.65f, sceneKey)) * tune("shafts") * in;
        v[22] = FrameInfo.time;
        v[23] = 0.3f * KeyLight.rim * tune("rim") * in;
        v[24] = view.m10();
        v[25] = view.m11();
        v[26] = view.m12();
        v[27] = rain;
        v[28] = FrameInfo.width / Math.max(1f, FrameInfo.height);
        v[29] = tk.night;
        v[30] = WorldDraw.pixel ? 1f : 0f;
        rgb(v, 32, KeyLight.color);
        rgb(v, 35, tk.lightShadow);
        rgb(v, 38, FogControl.color());
        rgb(v, 41, KeyLight.glow);
        rgb(v, 44, tk.bloomTint);
        // The rim on faces lit at a grazing angle, and a lightning flash lighting the world.
        v[47] = KeyLight.faceRim * tune("rim") * in;
        v[48] = KeyLight.flash * (0.5f + 0.5f * in) * tune("flash");
        // Exposure on the faces the light reaches: a night theme's light has to light a dark world (by day a held
        // night is also drawn darker, see SkyTime). A theme that dims its own day (Eclipse, Monsoon's overcast)
        // stays dim: its light shows in the rims and the colour, not in a lift.
        float dark = Math.max(tk.night, 1f - SkyTime.worldLight);
        v[49] = 1.3f * KeyLight.strength * dark * in * tune("exposure");
        v[50] = KeyLight.rimAll * in;
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
        for (int i = 0; i < PARAMS; i++) {
            int bits = Float.floatToRawIntBits(v[i]);
            int b0 = bits & 0xFF, b1 = (bits >>> 8) & 0xFF, b2 = (bits >>> 16) & 0xFF, b3 = (bits >>> 24) & 0xFF;
            params[i] = (b3 << 24) | (b0 << 16) | (b1 << 8) | b2;
        }
        paramsTexture.upload(params);

        String key = tk.lut + "|" + tk.gradeHighlights + "|" + tk.gradeShadows + "|" + tk.saturation + "|" + tk.contrast
                + "|" + tune("grade") * Atmosphere.intensity();
        if (!key.equals(lutKey)) {
            lutKey = key;
            lut = buildLut(tk, lut);
            lutTexture.upload(lut);
        }
    }

    private static void rgb(float[] v, int at, int c) {
        v[at] = Colors.r(c) / 255f;
        v[at + 1] = Colors.g(c) / 255f;
        v[at + 2] = Colors.b(c) / 255f;
    }

    private static boolean zeroToOne() {
        //? if >=26.3 {
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
