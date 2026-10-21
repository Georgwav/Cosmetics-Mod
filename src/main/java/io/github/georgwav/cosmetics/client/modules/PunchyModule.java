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
 * (off while the module is off), and gives it Sol's tuning once (smaller hands nearer the middle). Sol's arms make the
 * arms and what they hold smaller and closer to the middle of the screen, the same look without Punchy; with it, the
 * size sliders move Punchy's hands on from its tuning. PvP mode tones all of Punchy's animations down. And a glow round
 * the hands and the held item, in the theme's colours by default: a crisp bright outline on their edge fading out (or a
 * soft haze), with the arms drawn as dark silhouettes if you like (as in Georg's picture). The arms and the glow work
 * without Punchy too.
 */
public final class PunchyModule extends Module {
    /**
     * For the test scenario's swing shot on a slow software renderer: fade the trail as if each frame took this long
     * (-Dcosmeticsmod.trailDt=0.016), so its picture shows the trail as it builds up at a playable frame rate.
     */
    private static final float TRAIL_DT = Float.parseFloat(System.getProperty("cosmeticsmod.trailDt", "-1"));

    /** How far in front of the eye the hands are (blocks), roughly: what the arms are moved back from. */
    private static final float HAND_DEPTH = 0.72f, HAND_DROP = 0.52f;

    /**
     * Sol's arms: automatic (the default) is Punchy's hands when Punchy is installed and Sol's smaller hands without it
     * (the fallback); or always on, or off. A new id, so the new default reaches saved settings.
     */
    private final Setting.Choice solArms = add(new Setting.Choice("sol_arms_when", "auto", List.of("auto", "on", "off")).group("arms"));
    /** How big the hands look (new ids: the preset changed to smaller hands nearer the middle). */
    private final Setting.Slider handSize = add(new Setting.Slider("hand_size", 60, 40, 100, 1, v -> Math.round(v) + "%").group("arms"));
    /** How far toward the middle of the screen they sit. */
    private final Setting.Slider handsCentre = add(new Setting.Slider("hands_centre", 50, 0, 100, 5, v -> Math.round(v) + "%").group("arms"));
    private final Setting.Toggle pvp = add(new Setting.Toggle("pvp", false).group("pvp"));
    /** The arms as dark silhouettes (your skin swapped for a dark texture while the hands are drawn). */
    private final Setting.Toggle darkArms = add(new Setting.Toggle("arms_dark", false).group("arms"));
    private final Setting.Toggle glow = add(new Setting.Toggle("glow", true).group("glow"));
    /**
     * Golden light round the hands that their movement leaves behind (no outline unless asked for), a thin bright
     * outline on its own, a soft glow all round, the hands as a window onto a drifting starfield (with the outline), or
     * the ramp's four tones in stepped rings round them. A new id: "glow_style" meant something else before.
     */
    private final Setting.Choice glowStyle = add(new Setting.Choice("glow_look", "golden",
            List.of("golden", "outline", "soft", "stars", "bands")).group("glow"));
    /**
     * The theme's colour by default, as every colour in the client (it was your own, gold, as in Georg's picture of
     * Clarity's glow: a glow still at that default follows the theme now).
     */
    private final Setting.Choice glowColours = add(new Setting.Choice("glow_colours", "theme",
            List.of("theme", "item", "rainbow", "custom")).was("custom").group("glow"));
    /** The custom colour: a rich gold until you pick another (the glow runs from deep gold to pale gold round it). */
    private final Setting.Color glowColour = add(new Setting.Color("glow_colour", 0xFFFFD84F).themed().group("glow"));
    private final Setting.Slider glowStrength = add(new Setting.Slider("glow_strength", 100, 10, 200, 5, v -> Math.round(v) + "%").group("glow"));
    private final Setting.Slider glowSize = add(new Setting.Slider("glow_size", 100, 100, 300, 10, v -> Math.round(v) + "%").group("glow"));
    private final Setting.Toggle glowPulse = add(new Setting.Toggle("glow_pulse", false).group("glow"));
    /**
     * Thin lines of light rising through the soft glow; off by default (the "movement lines" Georg asked for turned
     * out to be the motion trail).
     */
    private final Setting.Toggle glowLines = add(new Setting.Toggle("rising_lines", false).group("glow"));
    /** While the hands move (a swing, or just walking), their glow stays a moment behind them, fading. */
    private final Setting.Toggle motionTrail = add(new Setting.Toggle("motion_trail", true).group("glow"));
    private final Setting.Slider trailLength = add(new Setting.Slider("trail_length", 60, 10, 100, 5, v -> Math.round(v) + "%").group("glow"));
    // The golden glow's own look.
    /** How much further up than round the sides the golden light reaches (warm light rises); 0 for none. */
    private final Setting.Slider goldenRise = add(new Setting.Slider("golden_rise", 100, 0, 200, 10, v -> Math.round(v) + "%").group("golden"));
    /** How pale and bright the light is right at the hands. */
    private final Setting.Slider goldenCore = add(new Setting.Slider("golden_core", 50, 0, 100, 5, v -> Math.round(v) + "%").group("golden"));
    /** How deep (towards amber) the gold gets where the light fades out. */
    private final Setting.Slider goldenEdge = add(new Setting.Slider("golden_edge", 100, 0, 100, 5, v -> Math.round(v) + "%").group("golden"));
    /** The thin outline round the hands as well; off, as the outline would sit in the light. */
    private final Setting.Toggle goldenLine = add(new Setting.Toggle("golden_line", false).group("golden"));
    // Knives (a melee cosmetic held in place of a sword).
    /**
     * Play the knives' animations at their own pace (the default: fitted to the sword they ran up to 3x as fast), or
     * fitted to Minecraft's sword. A new id, so the new default reaches saved settings.
     */
    private final Setting.Choice knifeTiming = add(new Setting.Choice("knife_pace", "original", List.of("original", "fit")).group("knives"));
    /** Critical hits play the heavy attack (each game's right-click attack). */
    private final Setting.Toggle knifeHeavy = add(new Setting.Toggle("knife_heavy", true).group("knives"));
    private final Setting.Slider knifeShake = add(new Setting.Slider("knife_shake", 100, 0, 200, 5, v -> Math.round(v) + "%").group("knives"));
    private final Setting.Slider knifeSway = add(new Setting.Slider("knife_sway", 100, 0, 200, 5, v -> Math.round(v) + "%").group("knives"));
    private final Setting.Slider knifeVolume = add(new Setting.Slider("knife_volume", 100, 0, 100, 5, v -> Math.round(v) + "%").group("knives"));
    /** Show the hands some packs hide (VCT Lock In's), at the size their other animations have them. */
    private final Setting.Toggle knifeHands = add(new Setting.Toggle("knife_hands", true).group("knives"));
    /** The knife in third person too; off, so third person shows the sword (a new id, so it's off for everyone). */
    private final Setting.Toggle knifeThirdPerson = add(new Setting.Toggle("knife_third", false).group("knives"));
    private final Setting.Choice knifeUseOn = add(new Setting.Choice("knife_use_on", "swords", List.of("swords", "swords_axes")).group("knives"));
    // The view model: each hand moved, turned and sized (written for a hand on the right; the left one mirrors them).
    /** No sway as you turn and no bob as you walk (your view still bobs, as set in the game). */
    private final Setting.Toggle keepStill = add(new Setting.Toggle("keep_still", false).group("view_model"));
    private final Setting.Slider[] mainHand = hand("main");
    private final Setting.Slider[] offHand = hand("off");
    /**
     * How the arm swings what it holds: the game's swing, 1.7's (no dip after each hit), or a style of its own, each
     * played in the sword's swing length. Off while Punchy's own animations draw the hands.
     */
    private final Setting.Choice swingStyle = add(new Setting.Choice("swing_style", "vanilla",
            io.github.georgwav.cosmetics.render.FirstPersonHands.SWING_STYLES).group("swing"));
    // What you hold.
    /** Little pixel flames lick up from what you hold, in three frames. */
    private final Setting.Toggle flaming = add(new Setting.Toggle("flaming_item", false).group("held"));
    private final Setting.Color flameColour = add(new Setting.Color("flame_colour", Setting.Color.THEME).group("held"));
    private final Setting.Slider flameSize = add(new Setting.Slider("flame_size", 100, 50, 200, 10, v -> Math.round(v) + "%").group("held"));
    /** Your shield tinted by its cooldown: as it is when ready, amber while it cools, red right after it's disabled. */
    private final Setting.Toggle shieldStatus = add(new Setting.Toggle("shield_status", true).group("held"));
    /** Your arm stays on screen holding what you carry (the game shows it only with an empty hand). */
    private final Setting.Toggle armWithItems = add(new Setting.Toggle("arm_with_items", false).group("held"));
    /** Punchy's own values of what PvP mode changes, put back when it goes off. */
    private final Map<String, Object> own = new LinkedHashMap<>();

    /** Whether the saved settings already had this module on by default (see {@link #turnOnOnce}). */
    private boolean onByDefaultSeen = true;
    /** Whether Sol's hand preset was given to Punchy (once: after that the player's own Punchy tuning stays). */
    private boolean tuningGiven;

    public PunchyModule() {
        super("punchy", Category.VIEW, "flame");
        flameColour.shownWhen(flaming::get);
        flameSize.shownWhen(flaming::get);
        // The knives' inspect key; with Punchy installed its own inspect key inspects the knife (see KnifeInput), so
        // this one starts unbound (one key can only reach one key bind).
        if (Compat.PUNCHY) {
            addKey("inspect");
        } else {
            addKey("inspect", com.mojang.blaze3d.platform.InputConstants.KEY_V);
        }
    }

    /** One hand's view model: across, up, forward (pixels), turned round x, y and z (degrees), and its size. */
    private Setting.Slider[] hand(String which) {
        String g = which + "_hand";
        java.util.function.DoubleFunction<String> px = v -> (v > 0 ? "+" : "") + (v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v)) + " px";
        java.util.function.DoubleFunction<String> deg = v -> (v > 0 ? "+" : "") + Math.round(v) + "\u00b0";
        return new Setting.Slider[]{
                add(new Setting.Slider(which + "_x", 0, -8, 8, 0.5, px).group(g)),
                add(new Setting.Slider(which + "_y", 0, -8, 8, 0.5, px).group(g)),
                add(new Setting.Slider(which + "_z", 0, -8, 8, 0.5, px).group(g)),
                add(new Setting.Slider(which + "_turn_x", 0, -180, 180, 5, deg).group(g)),
                add(new Setting.Slider(which + "_turn_y", 0, -180, 180, 5, deg).group(g)),
                add(new Setting.Slider(which + "_turn_z", 0, -180, 180, 5, deg).group(g)),
                add(new Setting.Slider(which + "_size", 100, 25, 200, 5, v -> Math.round(v) + "%").group(g))};
    }

    /** On in a fresh install: the arms, the glow and the knives live here (the Punchy mod itself stays optional). */
    @Override
    public boolean onByDefault() {
        return true;
    }

    /** Settings saved while this module was off by default turn it on once; turning it off after that sticks. */
    @Override
    public boolean turnOnOnce() {
        boolean once = !onByDefaultSeen;
        onByDefaultSeen = true;
        return once;
    }

    /** While Punchy's own animations draw the hands, the settings they leave out say so. */
    @Override
    public String settingHint(Setting<?> setting) {
        String hint = super.settingHint(setting);
        if (punchyAnimates() && setting.group != null && (setting.group.equals("swing") || setting.group.endsWith("_hand")
                || setting.id.equals("flaming_item") || setting.id.equals("arm_with_items"))) {
            String off = net.minecraft.network.chat.Component.translatable("cosmeticsmod.module.punchy.off_with_punchy").getString();
            return hint.isEmpty() ? off : off + ". " + hint;
        }
        return hint;
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

    /**
     * In the first world after Punchy is installed (its tuning loaded with the resources by then): Punchy's hands get
     * Sol's preset, once.
     */
    @Override
    protected void tick(Minecraft mc) {
        io.github.georgwav.cosmetics.render.FirstPersonHands.tick(mc);
        if (!tuningGiven && Compat.PUNCHY && mc.player != null) {
            tuningGiven = true;
            PunchyCompat.applyTuning(io.github.georgwav.cosmetics.client.MainFolder.clientFolder());
            Modules.save();
        }
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
        state.addProperty("on_by_default", true);
        state.addProperty("punchy_tuning_given", tuningGiven);
    }

    @Override
    protected void loadState(JsonObject state) {
        onByDefaultSeen = state.has("on_by_default");
        tuningGiven = state.has("punchy_tuning_given") && state.get("punchy_tuning_given").getAsBoolean();
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
        if (!m.enabled() || !m.solArmsOn()) {
            return pose;
        }
        float s = (float) (m.handSize.get() / 100.0), c = (float) (m.handsCentre.get() / 100.0);
        if (Compat.PUNCHY) {
            // Punchy's tuning (Sol's preset, given to it once) already has the hands smaller and nearer the middle:
            // the sliders move them on from there, so at their defaults Punchy's hands are as tuned (not shrunk twice).
            s /= PRESET_SIZE;
            c -= PRESET_CENTRE;
        }
        return place(pose, s, c);
    }

    /** Whether Sol's arms are on now: as set, or (automatic) only when Punchy isn't installed. */
    private boolean solArmsOn() {
        String when = solArms.get();
        return when.equals("on") || when.equals("auto") && !Compat.PUNCHY;
    }

    /**
     * While Punchy draws the hands, a knife's hands (which the knife draws itself, Punchy's tuning doesn't reach them)
     * are drawn at the size Punchy's tuning has its own, in their place: scaled round where the game holds an item
     * (the screen's lower corner on that side), so the hand stays there and its arm still comes in from the edge.
     * Pushed onto the pose here; the caller pops it when this says so. Sol's arms, when on with Punchy, move both on
     * from there (through the hands' pose).
     */
    public static boolean knifePreset(PoseStack pose, boolean leftHanded) {
        PunchyModule m = Modules.PUNCHY;
        if (!Compat.PUNCHY || !m.enabled()) {
            return false;
        }
        float[] t = knifeWithPunchyTry;
        float size = t != null ? t[0] : KNIFE_WITH_PUNCHY_SIZE, lift = t != null ? t[1] : KNIFE_WITH_PUNCHY_LIFT;
        float x = leftHanded ? -ITEM_X : ITEM_X;
        pose.pushPose();
        pose.translate(x, ITEM_Y + lift, ITEM_Z);
        pose.scale(size, size, size);
        pose.translate(-x, -ITEM_Y, -ITEM_Z);
        return true;
    }

    /** Where the game holds an item in first person (the right hand's; the left's x is mirrored). */
    private static final float ITEM_X = 0.56f, ITEM_Y = -0.52f, ITEM_Z = -0.72f;
    /** How big a knife's hands are drawn next to Punchy's tuned hands, and how far they're lifted (blocks). */
    static final float KNIFE_WITH_PUNCHY_SIZE = 0.5f, KNIFE_WITH_PUNCHY_LIFT = 0.06f;
    /** For the test scenario's local tries: size and lift instead of the two above (null: those). */
    public static float[] knifeWithPunchyTry;

    /** The hand size and centring Sol's preset has (the sliders' defaults), which Punchy's tuning matches. */
    static final float PRESET_SIZE = 0.60f, PRESET_CENTRE = 0.50f;

    /**
     * The hands at {@code size} (1 as the game has them) and {@code centre} (0 as the game has them, 1 nearest the
     * middle; below 0 farther out): moved back and down, and both drawn in toward the middle.
     */
    public static PoseStack place(PoseStack pose, float size, float centre) {
        if (Math.abs(size - 1f) < 0.001f && Math.abs(centre) < 0.001f) {
            return pose;
        }
        float back = HAND_DEPTH * (1f / size - 1f);
        // Moving back lifts the arms toward the middle of the screen too; how much of that stays is the centring.
        float down = HAND_DROP * (1f / size - 1f) * (1f - 0.6f * centre);
        pose.translate(0f, -down, -back);
        // And both arms drawn in toward the middle: the right one to the left, the left one to the right.
        pose.scale(1f - (1f - size) * 0.6f - 0.18f * centre, 1f, 1f);
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

    // ------------------------------------------------------------------ view model, swing, what you hold

    /** Whether Punchy's own animations draw the hands (its own way: the game's hand methods are left out). */
    public static boolean punchyAnimates() {
        return Compat.PUNCHY && Modules.PUNCHY.enabled();
    }

    /** No turn sway and no walking bob on the hands. */
    public static boolean keepStill() {
        PunchyModule m = Modules.PUNCHY;
        return m.enabled() && m.keepStill.get();
    }

    /**
     * A hand's view model into {@code out}: across, up, forward (blocks), turns round x, y and z (degrees) and its size;
     * false when it's as the game has it (or the module is off).
     */
    public static boolean viewModel(boolean main, float[] out) {
        PunchyModule m = Modules.PUNCHY;
        if (!m.enabled()) {
            return false;
        }
        Setting.Slider[] h = main ? m.mainHand : m.offHand;
        boolean moved = false;
        for (int i = 0; i < 7; i++) {
            double v = h[i].get();
            out[i] = i < 3 ? (float) (v / 16.0) : i < 6 ? (float) v : (float) (v / 100.0);
            moved |= i < 6 ? v != 0.0 : v != 100.0;
        }
        return moved;
    }

    /** The swing style (an index into {@code FirstPersonHands.SWING_STYLES}; 0 the game's). */
    public static int swingStyle() {
        PunchyModule m = Modules.PUNCHY;
        return m.enabled() ? Math.max(0, io.github.georgwav.cosmetics.render.FirstPersonHands.SWING_STYLES.indexOf(m.swingStyle.get())) : 0;
    }

    public static boolean flamingItem() {
        PunchyModule m = Modules.PUNCHY;
        return m.enabled() && m.flaming.get();
    }

    /** The flames' colour (the theme's unless you picked one). */
    public static int flameColour() {
        return Modules.PUNCHY.flameColour.argb();
    }

    public static float flameSize() {
        return (float) (Modules.PUNCHY.flameSize.get() / 100.0);
    }

    public static boolean shieldStatus() {
        PunchyModule m = Modules.PUNCHY;
        return m.enabled() && m.shieldStatus.get();
    }

    public static boolean armWithItems() {
        PunchyModule m = Modules.PUNCHY;
        return m.enabled() && m.armWithItems.get();
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
     * 1 the item's own, 2 rainbow), pulse, the sheen on the hand itself, how wide it spreads, the lines of light
     * moving through it, the style (4 bands, 3 stars, 2 golden, 1 outline, 0 soft), how much of the trail is left after this frame, and
     * whether the trail shows; then, 38 on, the golden glow's own look.
     */
    public static void glowParams(float[] v, int at, Tokens tk, boolean continuous) {
        PunchyModule m = Modules.PUNCHY;
        int a, b;
        float mode = 0f;
        switch (m.glowColours.get()) {
            case "custom" -> {
                // Your colour alone (no paler second colour flowing through it), as Clarity's gold is.
                a = m.glowColour.argb();
                b = a;
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
        // No sheen on the hands (they stay flat); how far the glow reaches, 1x to 3x.
        v[at + 9] = 0f;
        v[at + 10] = (float) (m.glowSize.get() / 100.0);
        v[at + 11] = m.glowLines.get() ? 1f : 0f;
        v[at + 12] = switch (m.glowStyle.get()) {
            case "bands" -> 4f;
            case "stars" -> 3f;
            case "golden" -> 2f;
            case "outline" -> 1f;
            default -> 0f;
        };
        // The golden glow's look (from 114): how much it rises, its bright core, its deep edge, whether it has the
        // outline too, and how pale the outline is (cream in the golden light, the colour itself on its own).
        boolean golden = m.glowStyle.get().equals("golden");
        v[at + 38] = (float) (m.goldenRise.get() / 100.0);
        v[at + 39] = (float) (m.goldenCore.get() / 100.0);
        v[at + 40] = (float) (m.goldenEdge.get() / 100.0);
        v[at + 41] = !golden || m.goldenLine.get() ? 1f : 0f;
        v[at + 42] = golden ? 0.55f : 0.15f;
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
