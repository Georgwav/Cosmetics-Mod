package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.devtools.SolScenario;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * What Sol draws over everything (see SolOverlayMixin): above the HUD and any open screen, even with the HUD hidden.
 * Sol's notifications are here: sunrise toasts that spring up from the bottom (the prototype's toast: a dark pill
 * with the theme's rim and glow and a small square sun), newest at the bottom, older ones moved up.
 */
public final class SolOverlay {
    private static final Design D = new Design();
    private static final Gfx G = new Gfx(D);
    private static long lastNanos;
    /** The action buttons drawn last frame, in GUI pixels: {x0, y0, x1, y1} per note with an action. */
    private static final java.util.Map<Notifications.Note, float[]> ACTIONS = new java.util.IdentityHashMap<>();

    private SolOverlay() {
    }

    public static void render(Canvas g) {
        if (!Guard.on(Feature.SOL_UI)) {
            return;
        }
        Guard.run(Feature.SOL_UI, () -> draw(g));
    }

    private static void draw(Canvas g) {
        Minecraft mc = Minecraft.getInstance();
        if (SolScenario.crosshairOnly && io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null && mc.level != null) {
            // The reference frames show the world and the crosshair, nothing else.
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            int cx = w / 2, cy = h / 2;
            g.fill(cx - 4, cy, cx + 5, cy + 1, 0xE6FFFFFF);
            g.fill(cx, cy - 4, cx + 1, cy, 0xE6FFFFFF);
            g.fill(cx, cy + 1, cx + 1, cy + 5, 0xE6FFFFFF);
        }
        if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() instanceof net.minecraft.client.gui.screens.packs.PackSelectionScreen packs) {
            double scale = mc.getWindow().getGuiScale();
            int mx = (int) (mc.mouseHandler.xpos() / scale), my = (int) (mc.mouseHandler.ypos() / scale);
            io.github.georgwav.cosmetics.sol.client.PackPreview.draw(g, packs, mx, my);
        }
        if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen servers) {
            io.github.georgwav.cosmetics.sol.client.ServerListUpgrade.drawTotal(g, servers.getServers(), servers.width);
        }
        // Like the game's own toasts, Sol's hide with the HUD (F1) unless a screen is open.
        if (!io.github.georgwav.cosmetics.sol.render.Mc.hudHidden() || io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null) {
            toasts(mc, g);
        }
    }

    private static void toasts(Minecraft mc, Canvas c) {
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        List<Notifications.Note> notes = Notifications.showing();
        ACTIONS.keySet().retainAll(notes);
        if (notes.isEmpty()) {
            return;
        }
        double gs = mc.getWindow().getGuiScale();
        D.update(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), gs);
        Tokens tk = ThemeState.tokens();
        c.push();
        c.scale((float) (1.0 / gs), (float) (1.0 / gs));
        G.begin(c);
        SolFont f = G.font(SolFont.Family.MEDIUM, 13f);
        float h = 36f, gap = 8f, lift = 0f;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Notifications.Note n = notes.get(i);
            // Spring up when posted, sink away when its time is up (the prototype's .45 s overshooting rise).
            float target = n.age() < n.life() ? 1f : 0f;
            if (SolScreen.calm()) {
                n.shown = target;
            } else {
                n.velocity += (target - n.shown) * 170f * dt;
                n.velocity *= (float) Math.exp(-14f * dt);
                n.shown += n.velocity * dt;
            }
            float k = Math.max(0f, Math.min(1f, n.shown));
            if (k <= 0.01f && target == 0f) {
                continue;
            }
            float tw = G.width(f, n.text);
            SolFont af = G.font(SolFont.Family.SEMIBOLD, 13f);
            float aw = n.action != null ? G.width(af, n.action) + 12f : 0f;
            float w = 11f + 12f + 10f + tw + 16f + aw;
            float x = 640f - w / 2f, y = D.bottom() - 34f - h - lift + (1f - n.shown) * 46f;
            G.alpha = Math.min(1f, k * 1.4f);
            G.glow(x, y, w, h, h / 2f, 30f, Colors.withAlpha(tk.glow, 0.32f));
            G.round(x, y, w, h, h / 2f, 0xE60A0A0E);
            G.stroke(x, y, w, h, h / 2f, Math.max(1f, D.u), tk.rim);
            G.glowSprite(x + 17f, y + h / 2f, 11f, 11f, Colors.withAlpha(tk.glow, 0.8f));
            G.rect(x + 11f, y + h / 2f - 6f, 12f, 12f, 0xFFFFF6D8);
            G.textMid(f, n.text, x + 33f, y + h / 2f, 0xFFFFFFFF);
            if (n.action != null) {
                float ax = x + 33f + tw + 12f;
                G.textMid(af, n.action, ax, y + h / 2f, tk.accent);
                ACTIONS.put(n, new float[]{D.sx(ax - 6f) / (float) gs, D.sy(y) / (float) gs, D.sx(ax + aw) / (float) gs,
                        D.sy(y + h) / (float) gs});
            }
            lift += (h + gap) * k;
        }
        G.alpha = 1f;
        G.end();
        c.pop();
    }

    /** A click (GUI pixels) on a toast's button runs its action; true if it took the click. */
    public static boolean click(double x, double y) {
        for (var e : ACTIONS.entrySet()) {
            float[] r = e.getValue();
            if (x >= r[0] && x <= r[2] && y >= r[1] && y <= r[3] && !e.getKey().dismissed) {
                e.getKey().dismissed = true;
                e.getKey().onAction.run();
                return true;
            }
        }
        return false;
    }
}
