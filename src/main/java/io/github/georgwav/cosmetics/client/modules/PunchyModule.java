package io.github.georgwav.cosmetics.client.modules;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.compat.punchy.PunchyCompat;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Punchy: your hands in first person. With the Punchy mod installed this module switches its animations on and off
 * (off while the module is off). Sol's arms make the arms and what they hold a little smaller, farther away and
 * closer to the middle of the screen; PvP mode calms Punchy's animations down and makes the arms smaller still, for
 * more of the screen. And a glow round the hands and the held item, in the theme's colours by default: a crisp
 * bright outline on their edge fading out (or a soft haze), with the arms drawn as dark silhouettes if you like (as in
 * Georg's picture). The arms and the glow work without Punchy too.
 */
public final class PunchyModule extends Module {
    /**
     * For the test scenario's swing shot on a slow software renderer: fade the trail as if each frame took this long
     * (-Dcosmeticsmod.trailDt=0.016), so its picture shows the trail as it builds up at a playable frame rate.
     */
    private static final float TRAIL_DT = Float.parseFloat(System.getProperty("cosmeticsmod.trailDt", "-1"));

    /** How far in front of the eye the hands are (blocks), roughly: what the arms are moved back from. */
    private static final float HAND_DEPTH = 0.72f, HAND_DROP = 0.52f;

    private final Setting.Toggle solArms = add(new Setting.Toggle("sol_arms", true).group("arms"));
    private final Setting.Slider armSize = add(new Setting.Slider("arm_size", 82, 60, 100, 1, v -> Math.round(v) + "%").group("arms"));
    private final Setting.Toggle pvp = add(new Setting.Toggle("pvp", false).group("pvp"));
    /** The arms as dark silhouettes (your skin swapped for a dark texture while the hands are drawn). */
    private final Setting.Toggle darkArms = add(new Setting.Toggle("dark_arms", true).group("arms"));
    private final Setting.Toggle glow = add(new Setting.Toggle("glow", true).group("glow"));
    /** A thin bright rim round the hands, fading out within a few pixels ("outline", its old id), or a soft glow. */
    private final Setting.Choice glowStyle = add(new Setting.Choice("glow_style", "outline", List.of("outline", "soft")).group("glow"));
    private final Setting.Choice glowColours = add(new Setting.Choice("glow_colours", "theme",
            List.of("theme", "item", "rainbow", "custom")).group("glow"));
    /** The custom colour: the gold of Georg's pictures until you pick another. */
    private final Setting.Color glowColour = add(new Setting.Color("glow_colour", 0xFFFFD21F).group("glow"));
    private final Setting.Slider glowStrength = add(new Setting.Slider("glow_strength", 100, 10, 200, 5, v -> Math.round(v) + "%").group("glow"));
    private final Setting.Slider glowSize = add(new Setting.Slider("glow_size", 100, 100, 300, 10, v -> Math.round(v) + "%").group("glow"));
    private final Setting.Toggle glowPulse = add(new Setting.Toggle("glow_pulse", false).group("glow"));
    /**
     * Thin lines of light rising through the soft glow; off by default (the "movement lines" Georg asked for turned
     * out to be the motion trail).
     */
    private final Setting.Toggle glowLines = add(new Setting.Toggle("rising_lines", false).group("glow"));
    /** While the hands move (a swing, or just walking), their rim stays a moment behind them, fading. */
    private final Setting.Toggle motionTrail = add(new Setting.Toggle("motion_trail", true).group("glow"));
    private final Setting.Slider trailLength = add(new Setting.Slider("trail_length", 60, 10, 100, 5, v -> Math.round(v) + "%").group("glow"));
    // Knives (a melee cosmetic held in place of a sword).
    /** Fit the knives' animations to Minecraft's sword, or play them at their own pace. */
    private final Setting.Choice knifeTiming = add(new Setting.Choice("knife_timing", "fit", List.of("fit", "original")).group("knives"));
    /** Critical hits play the heavy attack (each game's right-click attack). */
    private final Setting.Toggle knifeHeavy = add(new Setting.Toggle("knife_heavy", true).group("knives"));
    private final Setting.Slider knifeShake = add(new Setting.Slider("knife_shake", 100, 0, 200, 5, v -> Math.round(v) + "%").group("knives"));
    private final Setting.Slider knifeSway = add(new Setting.Slider("knife_sway", 100, 0, 200, 5, v -> Math.round(v) + "%").group("knives"));
    private final Setting.Slider knifeVolume = add(new Setting.Slider("knife_volume", 100, 0, 100, 5, v -> Math.round(v) + "%").group("knives"));
    /** Show the hands some packs hide (VCT Lock In's), at the size their other animations have them. */
    private final Setting.Toggle knifeHands = add(new Setting.Toggle("knife_hands", true).group("knives"));
    private final Setting.Toggle knifeThirdPerson = add(new Setting.Toggle("knife_third_person", true).group("knives"));
    private final Setting.Choice knifeUseOn = add(new Setting.Choice("knife_use_on", "swords", List.of("swords", "swords_axes")).group("knives"));
    /** Punchy's own values of what PvP mode changes, put back when it goes off. */
    private final Map<String, Object> own = new LinkedHashMap<>();

    public PunchyModule() {
        super("punchy", Category.VIEW, "flame");
        addKey("inspect", com.mojang.blaze3d.platform.InputConstants.KEY_V);
    }

    @Override
    public String note() {
        return Compat.PUNCHY ? super.note() : net.minecraft.network.chat.Component.translatable("cosmeticsmod.module.punchy.note_missing").getString();
    }

    /** Punchy follows this module: off while it's off, from the start. */
    @Override
    protected void onStartup(Minecraft mc) {
        sync();
    }

    @Override
    protected void onEnable(Minecraft mc) {
        sync();
    }

    @Override
    protected void onDisable(Minecraft mc) {
        sync();
    }

    @Override
    protected void onSettingsChanged(Minecraft mc) {
        sync();
    }

    private void sync() {
        Compat.setPunchy(enabled(), enabled() && pvp.get(), own);
        Modules.save();
    }

    @Override
    protected void saveState(JsonObject state) {
        JsonObject o = new JsonObject();
        own.forEach((k, v) -> o.add(k, v instanceof Boolean b ? new JsonPrimitive(b) : new JsonPrimitive((Number) v)));
        state.add("punchy_own", o);
    }

    @Override
    protected void loadState(JsonObject state) {
        own.clear();
        if (state.has("punchy_own") && state.get("punchy_own").isJsonObject()) {
            for (var e : state.getAsJsonObject("punchy_own").entrySet()) {
                if (!PunchyCompat.calms(e.getKey()) || !e.getValue().isJsonPrimitive()) {
                    continue;
                }
                var p = e.getValue().getAsJsonPrimitive();
                Object typed = PunchyCompat.typed(e.getKey(), p.isBoolean() ? (Object) p.getAsBoolean() : p.isNumber() ? p.getAsNumber() : null);
                if (typed != null) {
                    own.put(e.getKey(), typed);
                }
            }
        }
    }

    // ------------------------------------------------------------------ the arms

    /**
     * Before the game draws the hands: moves them back and a little down, so they look smaller and sit closer to the
     * middle of the screen (farther away, both arms lean toward the centre). Punchy draws with the same pose, so it
     * follows.
     */
    public static PoseStack arms(PoseStack pose) {
        PunchyModule m = Modules.PUNCHY;
        drawingHands = m.enabled() && m.darkArms.get();
        if (!m.enabled() || (!m.solArms.get() && !m.pvp.get())) {
            return pose;
        }
        float s = m.solArms.get() ? (float) (m.armSize.get() / 100.0) : 1f;
        if (m.pvp.get()) {
            s *= 0.82f;
        }
        if (s >= 0.999f) {
            return pose;
        }
        float back = HAND_DEPTH * (1f / s - 1f);
        // Moving back lifts the arms toward the middle of the screen too; most of that is taken back.
        float down = HAND_DROP * (1f / s - 1f) * 0.7f;
        pose.translate(0f, -down, -back);
        // And both arms drawn in toward the middle: the right one to the left, the left one to the right.
        pose.scale(1f - (1f - s) * 0.6f, 1f, 1f);
        return pose;
    }

    // ------------------------------------------------------------------ knives

    /** Knives: their animations fitted to Minecraft's sword (else at their own pace). */
    public static boolean knifeFit() {
        return Modules.PUNCHY.knifeTiming.get().equals("fit");
    }

    public static boolean knifeHeavyOnCrits() {
        return Modules.PUNCHY.knifeHeavy.get();
    }

    /** How strong the knives' camera shake is (0 to 2); none in PvP mode. */
    public static float knifeShake() {
        PunchyModule m = Modules.PUNCHY;
        return m.enabled() && m.pvp.get() ? 0f : (float) (m.knifeShake.get() / 100.0);
    }

    /** How much the knives sway with your view (0 to 2); 30% of it in PvP mode. */
    public static float knifeSway() {
        PunchyModule m = Modules.PUNCHY;
        float s = (float) (m.knifeSway.get() / 100.0);
        return m.enabled() && m.pvp.get() ? s * 0.3f : s;
    }

    public static float knifeVolume() {
        return (float) (Modules.PUNCHY.knifeVolume.get() / 100.0);
    }

    public static boolean knifeShowsHands() {
        return Modules.PUNCHY.knifeHands.get();
    }

    public static boolean knifeInThirdPerson() {
        return Modules.PUNCHY.knifeThirdPerson.get();
    }

    /** Knives on axes as well as swords. */
    public static boolean knifeOnAxes() {
        return Modules.PUNCHY.knifeUseOn.get().equals("swords_axes");
    }

    // ------------------------------------------------------------------ dark arms

    /** Whether the game is drawing the first-person hands now with the arms dark. */
    private static boolean drawingHands;
    private static io.github.georgwav.cosmetics.sol.render.SolTexture dark;
    private static int darkColour;

    /** The game finished drawing the hands. */
    public static void handsDrawn() {
        drawingHands = false;
    }

    /** Your skin's texture as the hands draw it now: the dark arms' while they're on (knives' arms; no skin lookup). */
    //? if >=1.21.11 {
    public static net.minecraft.resources.Identifier skinNow(net.minecraft.resources.Identifier skin) {
    //?} else {
    /*public static net.minecraft.resources.ResourceLocation skinNow(net.minecraft.resources.ResourceLocation skin) {
    *///?}
        return drawingHands ? darkTexture().id : skin;
    }

    /**
     * A texture a render type is made with: while the hands are drawn with dark arms, your skin becomes a dark one
     * (so the game's arms and Punchy's both turn into silhouettes); anything else (the items) stays.
     */
    //? if >=1.21.11 {
    public static net.minecraft.resources.Identifier skinOrDark(net.minecraft.resources.Identifier texture) {
    //?} else {
    /*public static net.minecraft.resources.ResourceLocation skinOrDark(net.minecraft.resources.ResourceLocation texture) {
    *///?}
        if (!drawingHands || texture == null) {
            return texture;
        }
        var player = Minecraft.getInstance().player;
        //? if >=1.21.9 {
        var skin = player == null ? null : player.getSkin().body().texturePath();
        //?} else {
        /*var skin = player == null ? null : player.getSkin().texture();
        *///?}
        return texture.equals(skin) ? darkTexture().id : texture;
    }

    /** A skin of solid black: the arms as flat silhouettes, as in Georg's pictures. */
    private static io.github.georgwav.cosmetics.sol.render.SolTexture darkTexture() {
        int colour = 0xFF000000;
        if (dark == null || dark.released()) {
            dark = new io.github.georgwav.cosmetics.sol.render.SolTexture("punchy_dark_arms", 64, 64, false);
            darkColour = 0;
        }
        if (colour != darkColour) {
            int[] px = new int[64 * 64];
            java.util.Arrays.fill(px, colour);
            dark.upload(px);
            darkColour = colour;
        }
        return dark;
    }

    // ------------------------------------------------------------------ the glow

    /** The glow's strength this frame (1 = normal), or 0 when it's off or no hands are drawn. */
    public static float glowNow() {
        PunchyModule m = Modules.PUNCHY;
        if (!m.enabled() || !m.glow.get()) {
            return 0f;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || io.github.georgwav.cosmetics.sol.render.Mc.hudHidden() || !mc.options.getCameraType().isFirstPerson()
                || mc.player.isSpectator()) {
            return 0f;
        }
        return (float) (m.glowStrength.get() / 100.0);
    }

    /**
     * The glow's values for the light pass, from {@code at}: two colours (rgb), strength, colour mode (0 two colours,
     * 1 the item's own, 2 rainbow), pulse, the sheen on the hand itself, how wide it spreads, the
     * lines of light moving through it, the style (1 rim, 0 soft), how much of the trail is left after this frame,
     * and whether the trail shows.
     */
    public static void glowParams(float[] v, int at, Tokens tk, boolean continuous) {
        PunchyModule m = Modules.PUNCHY;
        int a, b;
        float mode = 0f;
        switch (m.glowColours.get()) {
            case "custom" -> {
                a = m.glowColour.argb();
                b = Colors.mix(a, 0xFFFFFFFF, 0.35f);
            }
            case "item" -> {
                int knife = io.github.georgwav.cosmetics.melee.KnifeRenderer.brightColour();
                if (knife != 0) {
                    // A knife in hand: its texture's bright colour (worked out with its texture).
                    a = knife;
                    b = Colors.mix(knife, 0xFFFFFFFF, 0.35f);
                } else {
                    a = tk.accent;
                    b = tk.glow;
                    mode = 1f;
                }
            }
            case "rainbow" -> {
                a = tk.accent;
                b = tk.glow;
                mode = 2f;
            }
            default -> {
                a = tk.accent;
                b = tk.glow;
            }
        }
        rgb(v, at, a);
        rgb(v, at + 3, b);
        v[at + 6] = glowNow();
        v[at + 7] = mode;
        v[at + 8] = m.glowPulse.get() ? 1f : 0f;
        // No sheen on the hands (they stay flat); how far the rim's fade reaches, 1x to 3x.
        v[at + 9] = 0f;
        v[at + 10] = (float) (m.glowSize.get() / 100.0);
        v[at + 11] = m.glowLines.get() ? 1f : 0f;
        v[at + 12] = m.glowStyle.get().equals("outline") ? 1f : 0f;
        // How much of the trail is left after this frame: half of it goes in a time set by its length, so it lasts
        // as long at any frame rate; none when the glow wasn't drawn last frame (no stale trail when it comes back).
        boolean trail = m.motionTrail.get() && continuous;
        float halfLife = 0.04f + 0.26f * (float) (m.trailLength.get() / 100.0);
        float dt = TRAIL_DT > 0f ? TRAIL_DT : Math.min(0.25f, io.github.georgwav.cosmetics.sol.render.FrameInfo.frameSeconds);
        v[at + 13] = trail ? (float) Math.pow(0.5, dt / halfLife) : 0f;
        // The trail needs a picture kept from one frame to the next, which 1.21.2 to 1.21.5's post chains don't keep.
        v[at + 14] = m.motionTrail.get() && io.github.georgwav.cosmetics.sol.render.LightPass.blurSupported() ? 1f : 0f;
    }

    private static void rgb(float[] v, int at, int c) {
        v[at] = Colors.r(c) / 255f;
        v[at + 1] = Colors.g(c) / 255f;
        v[at + 2] = Colors.b(c) / 255f;
    }
}
