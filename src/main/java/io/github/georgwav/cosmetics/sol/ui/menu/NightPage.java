package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.atmosphere.LookAround;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.theme.Customisation;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;

import java.util.List;

/**
 * World › Night sky: the night sky for every theme on the left (the game's own sky to compare, the constellations,
 * the moon's glow, the square moon, the sky's latitude and year, looking around), and on the right which of its
 * layers the theme in use shows, saved with the theme's edits in the profile, and the way to its colours, moon and
 * stars on the theme's page.
 */
final class NightPage {
    private static final List<String> MODES = List.of("off", "look", "always");
    private final Scroll scroll = new Scroll();

    void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        Atmosphere at = Atmosphere.INSTANCE;
        if (at == null) {
            return;
        }
        Customisation c = ThemeState.customisation();
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f), hf = g.font(SolFont.Family.SEMIBOLD, 13f), mono = g.font(SolFont.Family.MONO, 11f);
        g.clip(x - 4f, y - 4f, x + w + 4f, y + h);
        float colW = (w - 32f) / 2f, top = y + 6f - scroll.value();

        // Every theme.
        float lx = x, cy = top + 10f;
        g.textMid(hf, SolMenu.tr("sol.menu.night.every"), lx, cy, 0xFFFFFFFF);
        cy += 34f;
        cy = row(g, f, lx, cy, colW, "vanilla_sky", () -> ui.smallSwitch(tk, lx + colW - 30f, rowY - 9f, at.vanillaSky.get(),
                () -> at.vanillaSky.set(!at.vanillaSky.get())));
        cy = row(g, f, lx, cy, colW, "constellations", () -> {
            String[] names = MODES.stream().map(m -> at.optionName(at.constellations, m)).toArray(String[]::new);
            ui.segmented(tk, lx + colW, rowY, names, Math.max(0, MODES.indexOf(at.constellations.get())),
                    i -> at.constellations.set(MODES.get(i)));
        });
        cy = row(g, f, lx, cy, colW, "moon_glow", () -> {
            float v = at.moonGlow.get().floatValue() / 100f;
            ui.slider(tk, lx + colW, rowY, v, Math.round(v * 100f) + "%", k -> at.moonGlow.set(Math.round(k * 20f) * 5.0));
        });
        cy = row(g, f, lx, cy, colW, "square_moon", () -> ui.smallSwitch(tk, lx + colW - 30f, rowY - 9f, at.squareMoon.get(),
                () -> at.squareMoon.set(!at.squareMoon.get())));
        cy = row(g, f, lx, cy, colW, "latitude", () -> {
            double v = at.latitude.get();
            ui.slider(tk, lx + colW, rowY, (float) (v / 60.0), Math.round(v) + "°", k -> at.latitude.set((double) Math.round(k * 60f)));
        });
        cy = row(g, f, lx, cy, colW, "year", () -> {
            double v = at.year.get();
            ui.slider(tk, lx + colW, rowY, (float) ((v - 8.0) / 376.0), SolMenu.tr("sol.menu.night.days", Math.round(v)),
                    k -> at.year.set(8.0 + Math.round(k * 47f) * 8.0));
        });
        cy = row(g, f, lx, cy, colW, "look_around", () -> ui.button(tk, lx + colW, rowY,
                SolMenu.tr(LookAround.on() ? "sol.menu.night.look_stop" : "sol.menu.night.look_start"), LookAround.on(), () -> {
                    LookAround.toggle();
                    if (LookAround.on()) {
                        menu.onClose();
                    }
                }));
        float leftBottom = cy;

        // The theme in use.
        float rx = x + colW + 32f;
        cy = top + 10f;
        g.textMid(hf, SolMenu.tr("sol.menu.night.theme", Widgets.themeName(ThemeState.baseEntry())), rx, cy, 0xFFFFFFFF);
        float ew = ui.button(tk, rx + colW, cy, SolMenu.tr("sol.menu.night.everywhere"), false, () -> {
            SolProfiles.layersEverywhere();
            menu.toast(SolMenu.tr("sol.notify.night_everywhere"));
        });
        // Its colours, moon and stars: the theme's page at its night.
        ui.button(tk, rx + colW - ew - 8f, cy, SolMenu.tr("sol.menu.night.customise"), true, () -> menu.openTheme(true));
        cy += 34f;
        for (String id : Customisation.LAYERS) {
            boolean on = c.layerOn(id);
            final String layer = id;
            cy = row(g, f, rx, cy, colW, "layer." + id, () -> ui.smallSwitch(tk, rx + colW - 30f, rowY - 9f, on,
                    () -> WorldSection.customise(ThemeState.customisation().withLayer(layer, !on))));
        }
        g.textMid(mono, SolMenu.tr("sol.menu.night.saved"), rx, cy + 4f, 0x80FFFFFF);
        g.endClip();
        scroll.setMax(Math.max(leftBottom, cy + 24f) - top - h);
        scroll.step(menu.dt());
        scroll.bar(g, x + w + 4f, y, h);
    }

    /** The row's middle, for the control being drawn. */
    private float rowY;

    /** One labelled row from y with its control at the right; returns the next row's y. */
    private float row(Gfx g, SolFont f, float x, float y, float w, String key, Runnable control) {
        rowY = y;
        g.textMid(f, SolMenu.tr("sol.menu.night." + key), x, y, 0xFFFFFFFF);
        control.run();
        g.rect(x, y + 20f, w, Math.max(1f / g.d.u, 1f), 0x0DFFFFFF);
        return y + 40f;
    }

    boolean scroll(float amount) {
        return scroll.wheel(amount);
    }
}
