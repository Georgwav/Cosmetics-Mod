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
 * more of the screen. And a glow round the hands and the held item, in the theme's colours by default. The arms and
 * the glow work without Punchy too.
 */
public final class PunchyModule extends Module {
    /** How far in front of the eye the hands are (blocks), roughly: what the arms are moved back from. */
    private static final float HAND_DEPTH = 0.72f, HAND_DROP = 0.52f;

    private final Setting.Toggle solArms = add(new Setting.Toggle("sol_arms", true).group("arms"));
    private final Setting.Slider armSize = add(new Setting.Slider("arm_size", 82, 60, 100, 1, v -> Math.round(v) + "%").group("arms"));
    private final Setting.Toggle pvp = add(new Setting.Toggle("pvp", false).group("pvp"));
    private final Setting.Toggle glow = add(new Setting.Toggle("glow", true).group("glow"));
    private final Setting.Choice glowColours = add(new Setting.Choice("glow_colours", "theme",
            List.of("theme", "item", "rainbow", "custom")).group("glow"));
    private final Setting.Color glowColour = add(new Setting.Color("glow_colour", 0xFFFF60D0).group("glow"));
    private final Setting.Slider glowStrength = add(new Setting.Slider("glow_strength", 100, 10, 200, 5, v -> Math.round(v) + "%").group("glow"));
    private final Setting.Slider glowSize = add(new Setting.Slider("glow_size", 65, 0, 100, 5, v -> Math.round(v) + "%").group("glow"));
    private final Setting.Toggle glowPulse = add(new Setting.Toggle("glow_pulse", true).group("glow"));
    private final Setting.Toggle glowLines = add(new Setting.Toggle("glow_lines", true).group("glow"));
    /** Punchy's own values of what PvP mode changes, put back when it goes off. */
    private final Map<String, Object> own = new LinkedHashMap<>();

    public PunchyModule() {
        super("punchy", Category.VIEW, "flame");
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
     * 1 the item's own, 2 rainbow), pulse, the sheen on the hand itself, how wide it spreads, and
     * the lines of light moving through it.
     */
    public static void glowParams(float[] v, int at, Tokens tk) {
        PunchyModule m = Modules.PUNCHY;
        int a, b;
        float mode = 0f;
        switch (m.glowColours.get()) {
            case "custom" -> {
                a = m.glowColour.argb();
                b = Colors.mix(a, 0xFFFFFFFF, 0.35f);
            }
            case "item" -> {
                a = tk.accent;
                b = tk.glow;
                mode = 1f;
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
        v[at + 9] = 0.06f;
        v[at + 10] = (float) (m.glowSize.get() / 100.0);
        v[at + 11] = m.glowLines.get() ? 1f : 0f;
    }

    private static void rgb(float[] v, int at, int c) {
        v[at] = Colors.r(c) / 255f;
        v[at + 1] = Colors.g(c) / 255f;
        v[at + 2] = Colors.b(c) / 255f;
    }
}
