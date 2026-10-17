package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;

/**
 * Performance: the frame rate while the game's window isn't in front (the game keeps drawing at full speed behind
 * other windows), so it stays cool and quiet in the background.
 */
public final class Performance extends Module {
    private final Setting.Slider background = add(new Setting.Slider("background_fps", 30, 5, 120, 5,
            v -> Math.round(v) + " FPS"));
    /** A small graph of the last frames' times in the bottom-right corner. */
    private final Setting.Toggle graph = add(new Setting.Toggle("frame_graph", false));
    /** One choice for the game's heaviest video settings (render, simulation and entity distance, biome blend). */
    private final Setting.Choice preset = add(new Setting.Choice("video_preset", "custom",
            java.util.List.of("custom", "low", "balanced", "high")));
    /** The preset the video settings were last set from (or loaded with): only a new pick changes them. */
    private String applied;

    private static final float[] FRAMES = new float[120];
    private static int head;
    private static long lastNanos;
    /** The label's value in tenths of a millisecond, and the label made for it. */
    private static int labelTenths = -1;
    private static String label = "";

    public Performance() {
        super("performance", Category.TOOLS, "bolt");
    }

    @Override
    protected void loadState(com.google.gson.JsonObject state) {
        // What's saved stays as the video settings are now: a preset is applied only when it's picked.
        applied = preset.get();
    }

    @Override
    protected void onEnable(Minecraft mc) {
        // A preset picked while the module was off.
        onSettingsChanged(mc);
    }

    @Override
    protected void onSettingsChanged(Minecraft mc) {
        String p = preset.get();
        if (p.equals(applied)) {
            return;
        }
        applied = p;
        if (p.equals("custom")) {
            return;
        }
        var o = mc.options;
        int distance = switch (p) {
            case "low" -> 6;
            case "balanced" -> 12;
            default -> 20;
        };
        o.renderDistance().set(distance);
        o.simulationDistance().set(Math.min(distance, p.equals("low") ? 6 : 10));
        o.entityDistanceScaling().set(p.equals("low") ? 0.75 : p.equals("balanced") ? 1.0 : 1.5);
        o.biomeBlendRadius().set(p.equals("low") ? 0 : p.equals("balanced") ? 2 : 5);
        o.save();
    }

    /** Each frame: its length, for the graph (timed here, so a long hitch shows at its real length). */
    public static void frame() {
        long now = System.nanoTime();
        if (lastNanos != 0L) {
            FRAMES[head] = Math.min(10f, (now - lastNanos) / 1e9f);
            head = (head + 1) % FRAMES.length;
        }
        lastNanos = now;
    }

    /** The frame graph, over the HUD: green within 60 FPS, amber within 30, red beyond. */
    public static void drawGraph(io.github.georgwav.cosmetics.ui.Canvas g, int w, int h) {
        Performance p = Modules.PERFORMANCE;
        if (!p.enabled() || !p.graph.get()) {
            return;
        }
        int gw = FRAMES.length, gh = 30, x = w - gw - 6, y = h - gh - 52;
        g.fill(x - 3, y - 13, x + gw + 3, y + gh + 3, 0x80000000);
        float worst = 0f;
        for (int i = 0; i < gw; i++) {
            float s = FRAMES[(head + i) % gw];
            worst = Math.max(worst, s);
            int bar = Math.max(1, Math.min(gh, Math.round(s * 1000f / 66f * gh)));
            int colour = s <= 1f / 58f ? 0xFF7FD49A : s <= 1f / 29f ? 0xFFFFC35C : 0xFFFF6A5A;
            g.fill(x + i, y + gh - bar, x + i + 1, y + gh, colour);
        }
        g.fill(x, y + gh - Math.round(16.7f / 66f * gh), x + gw, y + gh - Math.round(16.7f / 66f * gh) + 1, 0x40FFFFFF);
        int tenths = Math.round(worst * 10000f);
        if (tenths != labelTenths) {
            labelTenths = tenths;
            label = tenths / 10 + "." + tenths % 10 + " ms max";
        }
        g.text(Minecraft.getInstance().font, label, x, y - 11, 0xFFFFFFFF, true);
    }

    /** The frame rate cap for the game's {@code limit} now. */
    public static int limit(int limit) {
        Performance p = Modules.PERFORMANCE;
        if (!p.enabled() || Minecraft.getInstance().isWindowActive()) {
            return limit;
        }
        return Math.min(limit, (int) Math.round(p.background.get()));
    }
}
