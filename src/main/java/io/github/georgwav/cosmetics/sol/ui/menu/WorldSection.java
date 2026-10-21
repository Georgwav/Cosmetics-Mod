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
import io.github.georgwav.cosmetics.sol.ui.kit.LogoRenderer;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.ui.quick.PresetThumbs;

/**
 * World: the world's settings for the preset in use (Atmosphere, particles, fog, clouds, ambience, the time lock) in
 * a recess beside the preview pane with its sky; Customise opens the theme's page, Night sky the Night page
 * ({@link NightPage}). The presets themselves have their own tab.
 */
final class WorldSection implements Section {
    private static final float PANE_W = PresetsSection.PANE_W, ROW_H = 52f;
    private static final int SKY_W = 197, SKY_H = 240;
    private final PresetThumbs skies = new PresetThumbs(SKY_W, SKY_H, false);
    private final LogoRenderer logo = new LogoRenderer(false);
    private final NightPage nightPage = new NightPage();
    private boolean night;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        PixelTokens p = PixelTokens.get();
        ThemeRegistry.Entry cur = ThemeState.baseEntry();
        String name = Widgets.themeName(cur);
        // The head: Customise and the Night sky.
        String cust = SolMenu.tr("sol.menu.world.customise", name), nl = SolMenu.tr(night ? "sol.menu.world.themes" : "sol.menu.world.night");
        float right = x + w - ui.bigButtonWidth("brush", cust);
        ui.bigButton(right, y, "brush", cust, false, false, () -> menu.openTheme(night));
        right -= 8f + ui.bigButtonWidth(night ? "sun" : "moon", nl);
        ui.bigButton(right, y, night ? "sun" : "moon", nl, night, true, () -> night = !night);
        SolMenu.head(g, SolMenu.tr("sol.menu.world.head"), SolMenu.tr("sol.menu.world.count", name), x, y + 21f);

        float gy = y + 42f + 16f, gh = h - 42f - 16f;
        if (night) {
            nightPage.draw(menu, g, ui, tk, x, gy, w, gh);
            return;
        }
        float gw = w - PANE_W - 18f;
        Pix.recess(g, p, x, gy, gw, gh);
        float rx = x + Pix.E + 18f, rr = x + gw - Pix.E - 18f, ry = gy + Pix.E + 10f;
        SolFont lf = g.font(SolFont.Family.SEMIBOLD, 14f), sf = g.font(SolFont.Family.MEDIUM, 12f);
        Atmosphere at = Atmosphere.INSTANCE;
        Customisation c = ThemeState.customisation();
        // Atmosphere itself.
        if (Modules.ATMOSPHERE != null) {
            boolean on = Modules.ATMOSPHERE.enabled();
            row(g, lf, sf, rx, ry, Modules.ATMOSPHERE.name(), SolMenu.tr("sol.menu.world.atmosphere_line"));
            ui.smallSwitch(tk, rr - 30f, ry + ROW_H / 2f - 9f, on, () -> {
                Modules.ATMOSPHERE.setEnabled(!Modules.ATMOSPHERE.enabled());
                Modules.save();
            });
            ry = divider(g, p, rx, rr, ry);
        }
        row(g, lf, sf, rx, ry, SolMenu.tr("sol.menu.world.particles"), SolMenu.tr("sol.menu.world.particles_line"));
        ui.slider(tk, rr, ry + ROW_H / 2f, c.density() / 3f, Math.round(c.density() * 100f) + "%", k -> customise(c.withDensity(k * 3f)));
        ry = divider(g, p, rx, rr, ry);
        float fog = c.fog() == null ? cur.base().fog().density() : c.fog();
        row(g, lf, sf, rx, ry, SolMenu.tr("sol.menu.world.fog"), SolMenu.tr("sol.menu.world.fog_line"));
        ui.slider(tk, rr, ry + ROW_H / 2f, fog, Math.round(fog * 100f) + "%", k -> customise(ThemeState.customisation().withFog(k)));
        ry = divider(g, p, rx, rr, ry);
        if (at != null) {
            row(g, lf, sf, rx, ry, SolMenu.tr("sol.menu.world.clouds"), SolMenu.tr("sol.menu.world.clouds_line"));
            ui.smallSwitch(tk, rr - 30f, ry + ROW_H / 2f - 9f, at.clouds.get(), () -> at.clouds.set(!at.clouds.get()));
            ry = divider(g, p, rx, rr, ry);
            row(g, lf, sf, rx, ry, SolMenu.tr("sol.menu.world.ambience"), SolMenu.tr("sol.menu.world.ambience_line"));
            ui.smallSwitch(tk, rr - 30f, ry + ROW_H / 2f - 9f, at.ambience.get(), () -> at.ambience.set(!at.ambience.get()));
            ry = divider(g, p, rx, rr, ry);
            row(g, lf, sf, rx, ry, SolMenu.tr("sol.menu.world.lock_time"), SolMenu.tr("sol.menu.world.lock_time_line"));
            ui.smallSwitch(tk, rr - 30f, ry + ROW_H / 2f - 9f, at.lockTime.get(), () -> at.lockTime.set(!at.lockTime.get()));
        }
        // The pane: the preset's sky with Sol's logo.
        float vx = x + w - PANE_W;
        Pix.pane(g, p, vx, gy, PANE_W, gh);
        SolTexture sky = skies.get(cur);
        if (sky != null) {
            Pix.pixels(g, sky, SKY_W, SKY_H, vx + Pix.E, gy + Pix.E, PANE_W - 2f * Pix.E, gh - 2f * Pix.E);
        }
        SolTexture t = logo.texture(cur, menu.time(), menu.dt());
        int k = Math.max(1, Math.round(3f * g.d.u));
        float lw = LogoRenderer.W * k, lh = LogoRenderer.H * k;
        float lx = Math.round(g.d.sx(vx + PANE_W / 2f) - lw / 2f), ly = Math.round(g.d.sy(gy + 50f));
        g.imageScreen(t, lx, ly, lx + lw, ly + lh, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        Pix.paneEdge(g, p, vx, gy, PANE_W, gh);
        Pix.caption(g, p, vx, gy, PANE_W, gh, name, Widgets.themeLine(cur));
    }

    /** A settings row's name and its line, at the row's top {@code y}. */
    private static void row(Gfx g, SolFont lf, SolFont sf, float x, float y, String name, String line) {
        g.textMid(lf, name, x, y + ROW_H / 2f - 8f, PixelTokens.INK);
        g.textMid(sf, line, x, y + ROW_H / 2f + 10f, PixelTokens.INK3);
    }

    /** The 3 px line under a row; returns the next row's top. */
    private static float divider(Gfx g, PixelTokens p, float x0, float x1, float y) {
        Pix.fill(g, x0, y + ROW_H + 2f, x1 - x0, Pix.E, 0xFF2A2A2E);
        return y + ROW_H + 8f;
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
    }

    @Override
    public boolean scroll(float amount) {
        return night && nightPage.scroll(amount);
    }

    @Override
    public void closed() {
        skies.release();
        logo.release();
    }
}
