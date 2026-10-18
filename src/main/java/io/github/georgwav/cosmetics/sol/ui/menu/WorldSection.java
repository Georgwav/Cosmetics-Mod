package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.Customisation;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.quick.PresetThumbs;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.List;

/**
 * World: the presets (each theme's showpiece as a card) and the world's quick settings under them, or the Night
 * sky page ({@link NightPage}).
 */
final class WorldSection implements Section {
    private final Scroll scroll = new Scroll();
    private final PresetThumbs thumbs = new PresetThumbs();
    private final NightPage nightPage = new NightPage();
    private boolean night;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        ThemeRegistry.Entry cur = ThemeState.baseEntry();
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f), mono = g.font(SolFont.Family.MONO, 12f);
        String title = SolMenu.tr("sol.section.world");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        g.textMid(mono, SolMenu.tr("sol.menu.world.count", Widgets.themeName(cur)), x + g.width(h2, title) + 14f, headY, 0x8CFFFFFF);
        float bw = ui.button(tk, x + w, headY, SolMenu.tr("sol.menu.world.customise", Widgets.themeName(cur)), false,
                () -> {
                    if (Modules.ATMOSPHERE != null) {
                        menu.openDrawer(Modules.ATMOSPHERE);
                    }
                });
        bw += 8f + ui.button(tk, x + w - bw - 8f, headY, SolMenu.tr(night ? "sol.menu.world.themes" : "sol.menu.world.night"), night,
                () -> night = !night);
        if (!night) {
            ui.button(tk, x + w - bw - 8f, headY, SolMenu.tr("sol.menu.world.packs"), false,
                    () -> menu.toast(SolMenu.tr("sol.notify.packs")));
        }

        float gy = y + 42f, gh = h - 42f;
        if (night) {
            nightPage.draw(menu, g, ui, tk, x, gy, w, gh);
            return;
        }
        g.clip(x - 4f, gy - 4f, x + w + 4f, gy + gh);
        float top = gy + 2f - scroll.value();
        List<ThemeRegistry.Entry> themes = ThemeRegistry.all();
        int cols = menu.drawer() != null ? 4 : 6;
        float gap = 10f, cw = (w - gap * (cols - 1) - 8f) / cols, thumbH = 70f, ch = thumbH + 30f;
        SolFont nf = g.font(SolFont.Family.SEMIBOLD, 12f);
        for (int i = 0; i < themes.size(); i++) {
            ThemeRegistry.Entry e = themes.get(i);
            float cx = x + (i % cols) * (cw + gap), cy = top + (i / cols) * (ch + gap);
            if (cy + ch < gy - 8f || cy > gy + gh + 8f) {
                continue;
            }
            boolean on = e.id().equals(cur.id());
            boolean hover = ui.over(cx, cy, cw, ch);
            float lift = hover ? 2f : 0f;
            int fi = ui.click(cx, cy - lift, cw, ch, () -> apply(menu, e));
            if (on) {
                g.glow(cx, cy - lift, cw, ch, 12f, 22f, Colors.withAlpha(tk.glow, 0.35f));
            }
            g.round(cx, cy - lift, cw, ch, 12f, tk.panel);
            SolTexture t = thumbs.get(e);
            if (t != null) {
                g.clip(cx, cy - lift, cx + cw, cy - lift + thumbH);
                float aspect = (float) PresetThumbs.W / PresetThumbs.H;
                float tw = Math.max(cw, thumbH * aspect);
                g.image(t, cx + (cw - tw) / 2f, cy - lift, tw, thumbH, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
                g.endClip();
            }
            g.text(nf, g.fit(nf, Widgets.themeName(e), cw - 18f), cx + 9f, cy - lift + thumbH + 19f, 0xFFFFFFFF);
            g.stroke(cx, cy - lift, cw, ch, 12f, Math.max(1f, g.d.u), on ? tk.rim : 0x1AFFFFFF);
            if (ui.focused(fi)) {
                g.stroke(cx - 2f, cy - lift - 2f, cw + 4f, ch + 4f, 14f, 2f * g.d.u, tk.accent);
            }
        }
        int rows = (themes.size() + cols - 1) / cols;
        float qy = top + rows * (ch + gap) + 14f;
        quickRow(menu, g, ui, tk, x, qy, w);
        g.endClip();
        float contentH = rows * (ch + gap) + 14f + 40f;
        scroll.setMax(contentH - gh);
        scroll.step(menu.dt());
        scroll.bar(g, x + w + 4f, gy, gh);
    }

    private void quickRow(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w) {
        Atmosphere at = Atmosphere.INSTANCE;
        Customisation c = ThemeState.customisation();
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        float cy = y + 13f;
        float lx = x;
        g.textMid(f, SolMenu.tr("sol.menu.world.particles"), lx, cy, 0xFFFFFFFF);
        lx += g.width(f, SolMenu.tr("sol.menu.world.particles")) + 8f;
        float pv = c.density() / 3f;
        ui.slider(tk, lx + 140f + 44f, cy, pv, Math.round(c.density() * 100f) + "%", k -> customise(c.withDensity(k * 3f)));
        lx += 140f + 44f + 22f;
        g.textMid(f, SolMenu.tr("sol.menu.world.fog"), lx, cy, 0xFFFFFFFF);
        lx += g.width(f, SolMenu.tr("sol.menu.world.fog")) + 8f;
        float fog = c.fog() == null ? ThemeState.baseEntry().base().fog().density() : c.fog();
        ui.slider(tk, lx + 140f + 44f, cy, fog, Math.round(fog * 100f) + "%", k -> customise(ThemeState.customisation().withFog(k)));
        lx += 140f + 44f + 22f;
        if (at == null) {
            return;
        }
        lx = toggle(g, ui, tk, f, lx, cy, SolMenu.tr("sol.menu.world.clouds"), at.clouds.get(), () -> at.clouds.set(!at.clouds.get()));
        lx = toggle(g, ui, tk, f, lx, cy, SolMenu.tr("sol.menu.world.ambience"), at.ambience.get(), () -> at.ambience.set(!at.ambience.get()));
        toggle(g, ui, tk, f, lx, cy, SolMenu.tr("sol.menu.world.lock_time"), at.lockTime.get(), () -> at.lockTime.set(!at.lockTime.get()));
    }

    private static float toggle(Gfx g, Ui ui, Tokens tk, SolFont f, float x, float cy, String label, boolean on, Runnable toggle) {
        g.textMid(f, label, x, cy, 0xFFFFFFFF);
        float sx = x + g.width(f, label) + 8f;
        ui.smallSwitch(tk, sx, cy - 9f, on, toggle);
        return sx + 30f + 22f;
    }

    /** Opens on the Night sky page (the scenario's shot). */
    void showNight() {
        night = true;
    }

    static void customise(Customisation c) {
        ThemeState.setCustomisation(c);
        SolProfiles.setCustomisation(c);
    }

    static void apply(SolMenu menu, ThemeRegistry.Entry e) {
        boolean empty = e.id().equals("empty");
        if (Modules.ATMOSPHERE != null) {
            Modules.ATMOSPHERE.setEnabled(!empty);
        }
        ThemeState.setAccent(null);
        SolProfiles.setAccent(null);
        ThemeState.setTheme(e.id(), true);
        SolProfiles.setTheme(e.id());
        menu.toast(empty ? SolMenu.tr("sol.notify.preset_empty")
                : SolMenu.tr("sol.notify.preset", Widgets.themeName(e), SolProfiles.active()));
    }

    @Override
    public boolean scroll(float amount) {
        return night ? nightPage.scroll(amount) : scroll.wheel(amount);
    }

    @Override
    public void closed() {
        thumbs.release();
    }
}
