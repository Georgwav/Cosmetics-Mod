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
 * with the theme's rim and glow and a small square sun), newest at the bottom, older ones moved up; and Sol toasts
 * (SolToasts), an opening screen's dither (ScreenSlide) and the cursor's particles (CursorParticles).
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
        if (SolScenario.crosshairOnly && io.github.georgwav.cosmetics.sol.render.Mc.hudHidden()
                && io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null && mc.level != null) {
            // The reference frames show the world and the crosshair, nothing else (with the HUD shown, the HUD's own
            // crosshair is there).
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
        if (io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null) {
            // Over an opening inventory or chat, its dither; over any screen, the cursor's particles.
            io.github.georgwav.cosmetics.sol.hud.SolHud.custom(g, ScreenSlide::dither);
            io.github.georgwav.cosmetics.sol.hud.SolHud.custom(g, CursorParticles::draw);
        }
        // Like the game's own toasts, Sol's hide with the HUD (F1) unless a screen is open.
        if (!io.github.georgwav.cosmetics.sol.render.Mc.hudHidden() || io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null) {
            toasts(mc, g);
            io.github.georgwav.cosmetics.sol.hud.SolHud.custom(g, io.github.georgwav.cosmetics.client.modules.SolToasts::draw);
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
        // The pixel kit's toasts: a slab with an accent outline and accent Silkscreen 12, bottom centre, 136 px up in
        // game and 24 px up over a window; they slide in and out in whole 3 px steps.
        PixelTokens p = PixelTokens.get();
        SolFont f = G.font(SolFont.Family.PIXEL_BOLD, 12f);
        float h = 38f, gap = 8f, lift = 0f;
        float base = io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null ? 24f : 136f;
        for (int i = notes.size() - 1; i >= 0; i--) {
            Notifications.Note n = notes.get(i);
            float target = n.age() < n.life() ? 1f : 0f;
            if (SolScreen.calm()) {
                n.shown = target;
            } else {
                n.shown += Math.signum(target - n.shown) * Math.min(Math.abs(target - n.shown), dt / 0.18f);
            }
            float k = Math.max(0f, Math.min(1f, n.shown));
            if (k <= 0.01f && target == 0f) {
                continue;
            }
            float tw = G.width(f, n.text);
            float aw = n.action != null ? G.width(f, n.action) + 16f : 0f;
            float w = 12f + 24f + 10f + tw + 16f + aw;
            // Whole 3 px steps as it slides.
            float slide = Math.round((1f - k) * 16f) * 3f;
            float x = 640f - w / 2f, y = D.bottom() - base - h - lift + slide;
            Pix.slab(G, p, x, y, w, h, Pix.NORMAL);
            G.stroke(x, y, w, h, 0f, G.px(Pix.E), p.acc);
            Icons.draw(G, "sun", x + 12f, y + h / 2f - 12f, 24f, p.acc, 0f, 0);
            G.textMid(f, n.text, x + 46f, y + h / 2f, p.acc);
            if (n.action != null) {
                float ax = x + 46f + tw + 16f;
                G.textMid(f, n.action, ax, y + h / 2f, PixelTokens.INK);
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
