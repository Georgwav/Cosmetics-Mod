package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.hud.RollingText;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Stats: the small numbers, each one a line you switch on (FPS on by default): frame rate, clicks per second,
 * ping, the clock, memory, the world's day, how long you've played, the server, the resource pack in use, your
 * speed, your last hit's reach, your combo and a stopwatch (its own key). Sol UI shows each as a glass chip with an accent dot and a number that
 * rolls instead of jumping, amber or red when it's poor (FPS under 60, ping over 150 ms, memory over 80%); the plain
 * look writes them as lines. Replaces the separate FPS, Ping and Clock modules (their settings move here).
 */
public final class StatsHud extends TextHud {
    public enum Stat {
        FPS(true), CPS(false), PING(false), CLOCK(false), MEMORY(false), DAY(false), PLAYTIME(false), SERVER(false),
        PACK(false), SPEED(false), REACH(false), COMBO(false), STOPWATCH(false);

        final boolean on;

        Stat(boolean on) {
            this.on = on;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter H12 = DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT);
    private final Map<Stat, Setting.Toggle> shown = new EnumMap<>(Stat.class);
    private final Setting.Choice format = add(new Setting.Choice("format", "24h", List.of("24h", "12h")).group("look"));
    private final Setting.Choice layout = add(new Setting.Choice("layout", "row", List.of("row", "column")).group("look"));
    /** Sol UI's look: the coloured dot before each value, and the label after it ("FPS", "ms"). */
    private final Setting.Toggle dots = add(new Setting.Toggle("dots", true).group("look"));
    private final Setting.Toggle labels = add(new Setting.Toggle("labels", true).group("look"));
    private final Map<Stat, RollingText> rolling = new EnumMap<>(Stat.class);
    private final Map<Stat, String> label = new EnumMap<>(Stat.class);

    // What the stats count between frames.
    private long joinedAt = -1L;
    private double lastX, lastZ;
    private boolean moved;
    private float speed;
    private float reach = -1f;
    private int combo;
    private long lastHit;
    private int hurtBefore;
    private long stopwatchStart = -1L, stopwatchStop = -1L;
    /** The resource packs in use, as the line shows them (made again once a second). */
    private String packs;
    private int packAge;

    public StatsHud() {
        super("stats", "debug", 0f, 0f, 4f, 4f);
        for (Stat s : Stat.values()) {
            shown.put(s, add(new Setting.Toggle(s.id(), s.on).group("lines")));
            rolling.put(s, new RollingText());
        }
        addKey("stopwatch");
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (level.isClientSide() && player == mc.player) {
                HitResult aim = mc.hitResult;
                if (aim instanceof EntityHitResult e && e.getEntity() == entity) {
                    reach = (float) player.getEyePosition().distanceTo(e.getLocation());
                }
                long now = System.currentTimeMillis();
                combo = now - lastHit > 3000L ? 1 : combo + 1;
                lastHit = now;
            }
            return InteractionResult.PASS;
        });
    }

    /** Whether a stat is shown (for the migration test and the self-test). */
    public boolean shows(Stat s) {
        return shown.get(s).get();
    }

    public void show(Stat s, boolean on) {
        shown.get(s).set(on);
    }

    @Override
    protected void tick(Minecraft mc) {
        if (shown.get(Stat.PACK).get() && (packs == null || ++packAge >= 20)) {
            packAge = 0;
            packs = packs(mc);
        }
        if (mc.player == null) {
            joinedAt = -1L;
            moved = false;
            return;
        }
        if (joinedAt < 0L) {
            joinedAt = System.currentTimeMillis();
        }
        double x = mc.player.getX(), z = mc.player.getZ();
        if (moved) {
            float now = (float) Math.sqrt((x - lastX) * (x - lastX) + (z - lastZ) * (z - lastZ)) * 20f;
            speed += (now - speed) * 0.35f;
        }
        lastX = x;
        lastZ = z;
        moved = true;
        // Taking a hit ends the combo.
        int hurt = mc.player.hurtTime;
        if (hurt > hurtBefore) {
            combo = 0;
        }
        hurtBefore = hurt;
        if (System.currentTimeMillis() - lastHit > 3000L) {
            combo = 0;
        }
        var key = key("stopwatch");
        while (key != null && key.consumeClick()) {
            long now = System.currentTimeMillis();
            if (stopwatchStart >= 0L && stopwatchStop < 0L) {
                stopwatchStop = now;
            } else {
                stopwatchStart = now;
                stopwatchStop = -1L;
            }
        }
    }

    /**
     * The resource packs you put on (not the game's own, Fabric's or mods'): the top one's name, "+n" for the others;
     * null with none.
     */
    private static String packs(Minecraft mc) {
        String top = null;
        int more = 0;
        for (var pack : mc.getResourcePackRepository().getSelectedPacks()) {
            if (!pack.getId().startsWith("file/")) {
                continue;
            }
            // The list runs bottom to top: the last one is on top.
            if (top != null) {
                more++;
            }
            top = pack.getTitle().getString();
        }
        return top == null ? null : more > 0 ? top + " +" + more : top;
    }

    /** A stat's number (or text) and its label, or null when there's nothing to show. */
    private String value(Minecraft mc, Stat s) {
        return switch (s) {
            case FPS -> Integer.toString(mc.getFps());
            case CPS -> Hud.rightCps() > 0 ? Hud.leftCps() + "|" + Hud.rightCps() : Integer.toString(Hud.leftCps());
            case PING -> {
                ClientPacketListener connection = mc.getConnection();
                PlayerInfo info = connection == null || mc.player == null ? null : connection.getPlayerInfo(mc.player.getUUID());
                yield info == null ? (preview() ? "24" : null) : Integer.toString(info.getLatency());
            }
            case CLOCK -> LocalTime.now().format(format.get().equals("12h") ? H12 : H24);
            case MEMORY -> {
                Runtime rt = Runtime.getRuntime();
                yield Long.toString(Math.round(100.0 * (rt.totalMemory() - rt.freeMemory()) / rt.maxMemory()));
            }
            case DAY -> mc.level == null ? null : Long.toString(io.github.georgwav.cosmetics.sol.render.Mc.dayTime(mc.level) / 24000L);
            case PLAYTIME -> joinedAt < 0L ? null : duration(System.currentTimeMillis() - joinedAt, false);
            case SERVER -> {
                if (io.github.georgwav.cosmetics.client.modules.Social.streaming()) {
                    yield null;
                }
                if (mc.hasSingleplayerServer()) {
                    yield Component.translatable("cosmeticsmod.module.stats.singleplayer").getString();
                }
                var server = mc.getCurrentServer();
                yield server == null ? null : server.ip;
            }
            case PACK -> packs != null ? packs : preview() ? "Faithful" : null;
            case SPEED -> String.format(Locale.ROOT, "%.1f", speed);
            case REACH -> reach < 0f ? (preview() ? "3.00" : null) : String.format(Locale.ROOT, "%.2f", reach);
            case COMBO -> combo <= 0 && !preview() ? null : Integer.toString(combo);
            case STOPWATCH -> stopwatchStart < 0L ? (preview() ? "0:00.0" : null)
                    : duration((stopwatchStop >= 0L ? stopwatchStop : System.currentTimeMillis()) - stopwatchStart, true);
        };
    }

    private String label(Stat s) {
        return label.computeIfAbsent(s, k -> Component.translatable("cosmeticsmod.module.stats.label." + k.id()).getString());
    }

    /** How good a stat is (1 good, 0 bad), for its colour; NaN for the ones without a judgement. */
    private static float quality(Stat s, String v) {
        try {
            return switch (s) {
                case FPS -> Integer.parseInt(v) / 60f;
                case PING -> 1f - Integer.parseInt(v) / 300f;
                case MEMORY -> 1f - Integer.parseInt(v) / 100f;
                default -> Float.NaN;
            };
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static int color(Tokens tk, Stat s, String v) {
        float q = quality(s, v);
        if (Float.isNaN(q)) {
            return tk.accent;
        }
        return switch (s) {
            case FPS -> q < 0.5f ? Tokens.RED : q < 1f ? Tokens.AMBER : tk.accent;
            case PING -> q < 0f ? Tokens.RED : q < 0.5f ? Tokens.AMBER : tk.accent;
            default -> q < 0.08f ? Tokens.RED : q < 0.2f ? Tokens.AMBER : tk.accent;
        };
    }

    static String duration(long ms, boolean tenths) {
        long s = ms / 1000L;
        String hm = s >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
                : String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        return tenths ? hm + "." + (ms / 100L % 10L) : hm;
    }

    // ---------------------------------------------------------------- the plain look: lines

    @Override
    protected List<String> lines(Minecraft mc) {
        List<String> out = new ArrayList<>();
        for (Stat s : Stat.values()) {
            if (!shows(s)) {
                continue;
            }
            String v = value(mc, s);
            if (v != null) {
                out.add(s == Stat.SERVER || s == Stat.PACK || s == Stat.CLOCK || !labels.get() ? v : v + " " + label(s));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- Sol UI: chips

    private static final float CHIP_H = 12f, GAP = 3f, PAD = 5f;

    /** The chips shown now: stat and value. */
    private List<Map.Entry<Stat, String>> chips(Minecraft mc) {
        List<Map.Entry<Stat, String>> out = new ArrayList<>();
        for (Stat s : Stat.values()) {
            if (shows(s)) {
                String v = value(mc, s);
                if (v != null) {
                    out.add(Map.entry(s, v));
                }
            }
        }
        return out;
    }

    private float chipWidth(Stat s, String v) {
        boolean text = s == Stat.SERVER || s == Stat.PACK;
        float w = PAD + (dots.get() ? 3.5f + 3f : 0f) + (text ? HudKit.measure(SolFont.Family.MEDIUM, HudKit.TEXT, v, scale())
                : HudKit.measure(SolFont.Family.PIXEL_BOLD, HudKit.TEXT, "0", scale()) * v.length());
        if (labels.get() && s != Stat.SERVER && s != Stat.PACK && s != Stat.CLOCK) {
            w += 2.5f + HudKit.measure(HudKit.LABEL, HudKit.SMALL, label(s), scale());
        }
        return w + PAD;
    }

    @Override
    protected int solWidth(Minecraft mc) {
        float w = 0f;
        boolean row = layout.get().equals("row");
        for (var c : chips(mc)) {
            float cw = chipWidth(c.getKey(), c.getValue());
            w = row ? w + cw + (w > 0f ? GAP : 0f) : Math.max(w, cw);
        }
        return (int) Math.ceil(w);
    }

    @Override
    protected int solHeight(Minecraft mc) {
        int n = chips(mc).size();
        if (n == 0) {
            return 0;
        }
        return layout.get().equals("row") ? (int) CHIP_H : (int) Math.ceil(n * CHIP_H + (n - 1) * GAP);
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        boolean row = layout.get().equals("row");
        float x = 0f, y = 0f;
        SolFont mono = HudKit.mono(g), small = HudKit.small(g), text = HudKit.text(g);
        float full = solWidth(mc);
        for (var c : chips(mc)) {
            Stat s = c.getKey();
            String v = c.getValue();
            float cw = chipWidth(s, v);
            float cx = row ? x : anchorX >= 0.5f ? full - cw : 0f;
            HudKit.card(g, tk, cx, y, cw, CHIP_H, CHIP_H / 2f);
            int col = color(tk, s, v);
            float cy = y + CHIP_H / 2f;
            float tx = cx + PAD;
            if (dots.get()) {
                HudKit.dot(g, col, tx, cy);
                tx += 3.5f + 3f;
            }
            if (s == Stat.SERVER || s == Stat.PACK) {
                g.textMid(text, v, tx, cy, 0xFFFFFFFF);
            } else {
                tx += rolling.get(s).draw(g, mono, v, tx, cy, col == tk.accent ? 0xFFFFFFFF : col);
                if (s != Stat.CLOCK && labels.get()) {
                    g.textMid(small, label(s), tx + 2.5f, cy, 0xA8FFFFFF);
                }
            }
            if (row) {
                x += cw + GAP;
            } else {
                y += CHIP_H + GAP;
            }
        }
    }
}
