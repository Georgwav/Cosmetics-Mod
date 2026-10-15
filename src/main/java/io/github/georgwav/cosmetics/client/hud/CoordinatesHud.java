package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Coordinates: where you are (block coordinates), which way you face and the biome, with a compass strip under it.
 * Sol UI shows steady mono numbers with the axes in the accent, the biome underneath, and the strip with an accent
 * marker (the brief's coordinates and compass); the plain look writes lines.
 */
public final class CoordinatesHud extends TextHud {
    private final Setting.Toggle direction = add(new Setting.Toggle("direction", true));
    private final Setting.Toggle biome = add(new Setting.Toggle("biome", false));
    private final Setting.Toggle compass = add(new Setting.Toggle("compass", true));
    private String biomeId = "", biomeName = "";

    public CoordinatesHud() {
        super("coordinates", "pin", 0f, 0f, 4f, 40f);
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("XYZ " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());
        if (direction.get()) {
            lines.add(facing(mc));
        }
        if (biome.get()) {
            lines.add(biome(mc));
        }
        return lines;
    }

    private static String facing(Minecraft mc) {
        Direction facing = mc.player.getDirection();
        String axis = switch (facing) {
            case NORTH -> "-Z";
            case SOUTH -> "+Z";
            case WEST -> "-X";
            case EAST -> "+X";
            default -> "";
        };
        return Component.translatable("cosmeticsmod.module.coordinates.facing." + facing.getSerializedName()).getString()
                + " (" + axis + ")";
    }

    private String biome(Minecraft mc) {
        String id = mc.level.getBiome(mc.player.blockPosition()).getRegisteredName();
        if (!id.equals(biomeId)) {
            biomeId = id;
            biomeName = biomeName(id);
        }
        return biomeName;
    }

    /** "minecraft:dark_forest" as "Dark Forest". */
    private static String biomeName(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }

    // ---------------------------------------------------------------- Sol UI

    private static final float PAD_X = 6f, PAD_Y = 4.5f, LINE = 9f, SMALL_LINE = 7.5f, STRIP_W = 112f, STRIP_H = 11f;

    private boolean has(Minecraft mc) {
        return (mc.player != null && mc.level != null) || preview();
    }

    private int[] xyz(Minecraft mc) {
        return mc.player == null ? new int[]{128, 64, -256} : new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()};
    }

    private String sub(Minecraft mc) {
        if (mc.player == null) {
            return "Plains";
        }
        String b = biome.get() ? biome(mc) : null;
        String d = direction.get() ? facing(mc) : null;
        return b != null && d != null ? b + "  ·  " + d : b != null ? b : d;
    }

    private float cardWidth(Minecraft mc) {
        int[] p = xyz(mc);
        float mono = HudKit.measure(SolFont.Family.MONO, HudKit.TEXT, "0", scale());
        float w = 0f;
        for (int v : p) {
            w += mono * (2 + Integer.toString(v).length()) + 5f;
        }
        String sub = sub(mc);
        if (sub != null) {
            w = Math.max(w, HudKit.measure(SolFont.Family.MEDIUM, HudKit.SMALL, sub, scale()));
        }
        return w - 5f + PAD_X * 2f;
    }

    private float cardHeight(Minecraft mc) {
        return PAD_Y * 2f + LINE - 2f + (sub(mc) != null ? SMALL_LINE : 0f);
    }

    @Override
    protected int solWidth(Minecraft mc) {
        return !has(mc) ? 0 : (int) Math.ceil(Math.max(cardWidth(mc), compass.get() ? STRIP_W : 0f));
    }

    @Override
    protected int solHeight(Minecraft mc) {
        return !has(mc) ? 0 : (int) Math.ceil(cardHeight(mc) + (compass.get() ? 3f + STRIP_H : 0f));
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        float cw = cardWidth(mc), ch = cardHeight(mc);
        HudKit.card(g, tk, 0f, 0f, cw, ch, HudKit.CARD_R);
        SolFont mono = HudKit.mono(g), small = HudKit.small(g);
        int[] p = xyz(mc);
        String[] axes = {"X", "Y", "Z"};
        float x = PAD_X, cy = PAD_Y + (LINE - 2f) / 2f;
        float digit = g.width(mono, "0");
        for (int i = 0; i < 3; i++) {
            g.textMid(mono, axes[i], x, cy, tk.accent);
            x += digit * 2f;
            String v = Integer.toString(p[i]);
            g.textMid(mono, v, x, cy, 0xFFFFFFFF);
            x += digit * v.length() + 5f;
        }
        String sub = sub(mc);
        if (sub != null) {
            g.textMid(small, sub, PAD_X, cy + LINE - 0.5f, 0xA8FFFFFF);
        }
        if (compass.get()) {
            strip(g, tk, mc, (solWidth(mc) - STRIP_W) / 2f, ch + 3f);
        }
    }

    /** The compass strip: ±60° around where you face, ticks every 15°, the accent marker in the middle. */
    private void strip(Gfx g, Tokens tk, Minecraft mc, float x, float y) {
        HudKit.card(g, tk, x, y, STRIP_W, STRIP_H, STRIP_H / 2f);
        float heading = mc.player == null ? 0f : Mth.wrapDegrees(mc.player.getYRot() + 180f);
        float k = STRIP_W / 120f, mid = x + STRIP_W / 2f, cy = y + STRIP_H / 2f;
        SolFont small = HudKit.small(g), letter = g.font(SolFont.Family.SEMIBOLD, HudKit.SMALL);
        g.clip(x + 3f, y, x + STRIP_W - 3f, y + STRIP_H);
        int first = (int) Math.floor((heading - 60f) / 15f) * 15;
        for (int d = first; d <= heading + 60f; d += 15) {
            float px = mid + (d - heading) * k;
            float fade = 1f - Math.min(1f, Math.abs(d - heading) / 60f);
            int deg = Math.floorMod(d, 360);
            if (deg % 90 == 0) {
                String n = deg == 0 ? "N" : deg == 90 ? "E" : deg == 180 ? "S" : "W";
                g.textCentre(letter, n, px, cy, Colors.fade(deg == 0 ? 0xFFFFFFFF : 0xD0FFFFFF, 0.35f + 0.65f * fade));
            } else if (deg % 45 == 0) {
                String n = deg == 45 ? "NE" : deg == 135 ? "SE" : deg == 225 ? "SW" : "NW";
                g.textCentre(small, n, px, cy, Colors.fade(0x99FFFFFF, 0.3f + 0.7f * fade));
            } else {
                g.rect(px - 0.25f, cy - 1.25f, 0.5f, 2.5f, Colors.fade(0x66FFFFFF, fade));
            }
        }
        g.endClip();
        g.glowSprite(mid, cy, 3f, 6f, Colors.withAlpha(tk.accent, 0.5f));
        g.rect(mid - 0.5f, y + 2f, 1f, STRIP_H - 4f, tk.accent);
    }
}
