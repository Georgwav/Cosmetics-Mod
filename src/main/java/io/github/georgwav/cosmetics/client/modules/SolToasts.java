package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.UiSounds;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Sol toasts: small slabs with an icon and one line of pixel type that slide in from a corner in whole steps when
 * something happens (a module switched with its key or a wheel, a totem popping, a kill, the song changing, your
 * health getting low), each kind on its own switch. Up to three at once, the newest nearest the corner, stacked past
 * any HUD module already in that corner; each leaves after 2.5 s. Several modules switched at once (a profile, a
 * preset) make none. Over the HUD and any screen.
 */
public final class SolToasts extends Module {
    private final Setting.Choice corner = add(new Setting.Choice("corner", "bottom_right",
            List.of("top_right", "top_left", "bottom_right", "bottom_left")).group("look"));
    private final Setting.Toggle toggles = add(new Setting.Toggle("toast_toggles", true).group("events"));
    private final Setting.Toggle totems = add(new Setting.Toggle("toast_totems", true).group("events"));
    private final Setting.Toggle kills = add(new Setting.Toggle("toast_kills", true).group("events"));
    private final Setting.Toggle songs = add(new Setting.Toggle("toast_songs", true).group("events"));
    private final Setting.Toggle lowHealth = add(new Setting.Toggle("toast_low_health", true).group("events"));

    /** How long one stays, and how long it takes to slide. */
    public static final float SECONDS = 2.5f, SLIDE = 0.2f;
    private static final int MAX = 3;

    /** One toast: its icon, line and icon colour, and when it came (seconds on the frame clock). */
    private static final class Toast {
        String icon = "", text = "";
        int colour;
        float at = -100f;
    }

    /** The toasts, newest first; a new one takes the oldest's place. */
    private final Toast[] toasts = new Toast[MAX];
    /** A module switched this tick (and how many), shown at the tick's end if it was the only one. */
    private Module switched;
    private boolean switchedOn;
    private int switches;
    /** For the in-game test: toasts posted and frames drawn with one showing; an age every toast is held at (NaN: live). */
    public static int posted, framesDrawn;
    public static float hold = Float.NaN;

    public SolToasts() {
        super("sol_toasts", Category.HUD, "card");
        io.github.georgwav.cosmetics.sol.fx.SolPicks.module(this);
        for (int i = 0; i < MAX; i++) {
            toasts[i] = new Toast();
        }
        FxEvents.TOGGLE.add(new FxEvents.Toggle() {
            @Override
            public boolean listening() {
                return enabled() && toggles.get();
            }

            @Override
            public void toggled(Module m, boolean on) {
                // The full menu says it in its own toast.
                if (m != SolToasts.this && !(io.github.georgwav.cosmetics.ui.ScreenOpener.current()
                        instanceof io.github.georgwav.cosmetics.sol.ui.menu.SolMenu)) {
                    switched = m;
                    switchedOn = on;
                    switches++;
                }
            }
        });
        FxEvents.TOTEM.add(new FxEvents.Totem() {
            @Override
            public boolean listening() {
                return enabled() && totems.get();
            }

            @Override
            public void popped(LivingEntity who, int count) {
                if (!(who instanceof Player p)) {
                    return;
                }
                Player you = Minecraft.getInstance().player;
                String text = p == you ? tr("sol.toast.totem_you", count) : tr("sol.toast.totem", p.getName().getString(), count);
                post("totem", text, 0xFFFFC23C);
            }
        });
        FxEvents.KILL.add(new FxEvents.Kill() {
            @Override
            public boolean listening() {
                return enabled() && kills.get();
            }

            @Override
            public void killed(LivingEntity dead, PoseSnapshot copy) {
                post("skull", tr("sol.toast.kill", dead.getName().getString()), 0);
            }
        });
        FxEvents.SONG.add(new FxEvents.Song() {
            @Override
            public boolean listening() {
                return enabled() && songs.get();
            }

            @Override
            public void changed(String title, String artist) {
                post("note", artist == null || artist.isBlank() ? title : title + " · " + artist, 0);
            }
        });
        FxEvents.LOW.add(new FxEvents.LowHealth() {
            @Override
            public boolean listening() {
                return enabled() && lowHealth.get();
            }

            @Override
            public void low(float health) {
                post("heart", tr("sol.toast.low_health"), Tokens.RED);
            }
        });
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    protected void tick(Minecraft mc) {
        if (switches == 1 && switched != null) {
            post(switched.icon, tr(switchedOn ? "sol.notify.module_on" : "sol.notify.module_off", switched.name()), 0);
        }
        switches = 0;
        switched = null;
    }

    /** Shows a toast now: {@code icon} a pixel icon's name, {@code colour} its colour (0 for the theme's accent). */
    public void post(String icon, String text, int colour) {
        Toast t = toasts[MAX - 1];
        System.arraycopy(toasts, 0, toasts, 1, MAX - 1);
        toasts[0] = t;
        t.icon = icon;
        t.text = text;
        t.colour = colour;
        t.at = FxWorld.now();
        posted++;
        UiSounds.toast();
    }

    @Override
    protected void onDisable(Minecraft mc) {
        for (Toast t : toasts) {
            t.at = -100f;
        }
    }

    // ------------------------------------------------------------------ drawing (Sol's top layer)

    private static final float H = 14f, PAD = 3f, ICON = 8f, GAP = 2f, MARGIN = 4f;

    /** How wide the corner's column of toasts can be, in GUI pixels. */
    private static final float COLUMN = 180f;

    /**
     * Where the toasts start in their corner: the screen's edge, or past the HUD modules already in that corner (the
     * Armour & Items box, the kill feed, the active modules list), so they never cover them; over the chat, in the
     * bottom left.
     */
    private static float edge(Minecraft mc, float w, float h, boolean right, boolean top) {
        float edge = top ? MARGIN : h - MARGIN - (right ? 0f : 40f);
        int sw = Math.round(w), sh = Math.round(h);
        for (var hm : io.github.georgwav.cosmetics.client.Modules.hud()) {
            if (!hm.enabled() || hm.isEmpty(mc)) {
                continue;
            }
            float x0 = hm.x(sw, mc), y0 = hm.y(sh, mc);
            float x1 = x0 + hm.width(mc) * hm.scale(), y1 = y0 + hm.height(mc) * hm.scale();
            if (right ? x1 < w - COLUMN : x0 > COLUMN) {
                continue;
            }
            // Only what lies in the toasts' half (Keystrokes at the edge's middle stays out of it).
            if (top && y1 < h / 2f) {
                edge = Math.max(edge, y1 + GAP);
            } else if (!top && y0 > h / 2f) {
                edge = Math.min(edge, y0 - GAP);
            }
        }
        // Never off the screen, whatever is there.
        return top ? Math.min(edge, h - MARGIN - MAX * (H + GAP)) : Math.max(edge, MARGIN + MAX * (H + GAP));
    }

    /** Draws the toasts with the kit in GUI units (SolOverlay, through SolHud.custom). */
    public static void draw(Gfx g) {
        SolToasts m = io.github.georgwav.cosmetics.client.Modules.SOL_TOASTS;
        if (!m.enabled()) {
            return;
        }
        float now = FxWorld.now();
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        String c = m.corner.get();
        boolean right = c.endsWith("right"), top = c.startsWith("top");
        Tokens tk = ThemeState.tokens();
        SolFont f = HudKit.mono(g);
        float edge = edge(mc, w, h, right, top);
        float stack = 0f;
        boolean any = false;
        for (Toast t : m.toasts) {
            float age = Float.isNaN(hold) || t.at < -50f ? now - t.at : hold;
            if (age < 0f || age > SECONDS + SLIDE) {
                continue;
            }
            any = true;
            // In, then out, each in whole three-pixel steps.
            float k = FxWorld.calm() ? 1f : Math.min(1f, Math.min(age, SECONDS + SLIDE - age) / SLIDE);
            float tw = PAD + ICON + PAD + g.width(f, t.text) + PAD + 1f;
            float slide = Math.round((1f - k) * (tw + MARGIN) / 3f) * 3f;
            float x = right ? w - MARGIN - tw + slide : MARGIN - slide;
            float y = top ? edge + stack : edge - H - stack;
            int colour = t.colour == 0 ? 0xFF000000 | tk.accent : t.colour;
            HudKit.slab(g, tk, x, y, tw, H, 1f);
            HudKit.frame(g, x, y, tw, H, Colors.withAlpha(colour, 0.9f));
            Icons.draw(g, t.icon, x + PAD, y + (H - ICON) / 2f, ICON, colour, 0f, 0);
            g.textMid(f, t.text, x + PAD + ICON + PAD, y + H / 2f, 0xFFFFFFFF);
            stack += (H + GAP) * k;
        }
        if (any) {
            framesDrawn++;
        }
    }
}
