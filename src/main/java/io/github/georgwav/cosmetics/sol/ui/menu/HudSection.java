package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.hud.HudModule;
import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.hud.SolHud;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * HUD: a small live picture of your HUD layout (each HUD module's box where it sits, over the theme's sky), the HUD
 * modules' switches, and "Edit layout" for the HUD editor, where everything snaps to edges, the middle and other
 * boxes.
 */
final class HudSection implements Section {
    private SolTexture sky;
    private String skyFor;
    private int skyGeneration = -1;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f), mono = g.font(SolFont.Family.MONO, 12f);
        String title = SolMenu.tr("sol.section.hud");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        g.textMid(mono, SolMenu.tr("sol.menu.hud.count", SolProfiles.active()), x + g.width(h2, title) + 14f, headY, 0x8CFFFFFF);
        ui.button(tk, x + w, headY, SolMenu.tr("sol.menu.hud.edit"), true,
                () -> ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.HudEditor(menu)));

        // The mini layout.
        float my = y + 42f, mw = 420f, mh = 236f;
        var e = ThemeState.baseEntry();
        if (sky == null || sky.released() || !e.id().equals(skyFor) || skyGeneration != ThemeState.tokens().generation) {
            if (sky != null) {
                sky.release();
            }
            skyFor = e.id();
            skyGeneration = ThemeState.tokens().generation;
            sky = SolTexture.of("hud_sky", 840, 472, true, MiniSky.render(e.base(), e.id(), 840, 472, 2f).toArgb(null));
        }
        g.clip(x, my, x + mw, my + mh);
        g.image(sky, x, my, mw, mh, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        Minecraft mc = menu.mc();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float k = Math.min(mw / Math.max(1, sw), mh / Math.max(1, sh));
        SolFont bf = g.font(SolFont.Family.MEDIUM, 9f);
        for (HudModule m : Modules.hud()) {
            if (!m.enabled() || !m.available()) {
                continue;
            }
            box(g, tk, bf, m.name(), x + m.x(sw, mc) * k, my + m.y(sh, mc) * k, m.width(mc) * m.scale() * k,
                    m.height(mc) * m.scale() * k);
        }
        // The game's own hotbar, where the editor has moved it.
        float[] hb = VanillaHud.Part.HOTBAR.box(sw, sh);
        box(g, tk, bf, SolMenu.tr("sol.menu.hud.hotbar"), x + (hb[0] + VanillaHud.dx(VanillaHud.Part.HOTBAR)) * k,
                my + (hb[1] + VanillaHud.dy(VanillaHud.Part.HOTBAR)) * k, hb[2] * k, hb[3] * k);
        // The middle guide the editor snaps to.
        g.rect(x + mw / 2f, my, 1f, mh, io.github.georgwav.cosmetics.sol.util.Colors.withAlpha(tk.accent, 0.55f));
        g.rect(x, my + mh / 2f, mw, 1f, io.github.georgwav.cosmetics.sol.util.Colors.withAlpha(tk.accent, 0.14f));
        g.endClip();
        g.stroke(x, my, mw, mh, 12f, Math.max(1f, g.d.u), 0x24FFFFFF);

        // Sol UI: the game's HUD in Sol's look, and which of its parts Sol draws (lit) or hands back to the game.
        SolConfig cfg = SolConfig.get();
        float sy = my + mh + 24f;
        SolFont sf = g.font(SolFont.Family.MEDIUM, 13f);
        g.textMid(sf, SolMenu.tr("sol.menu.hud.sol_ui"), x, sy, 0xFFFFFFFF);
        ui.smallSwitch(tk, x + mw - 30f, sy - 9f, cfg.solUi, () -> {
            cfg.solUi = !cfg.solUi;
            SolConfig.save();
        });
        // The parts' chips, wrapping across the section's width.
        float cxp = x, cyp = sy + 32f;
        SolFont chipFont = g.font(SolFont.Family.MEDIUM, 12f);
        for (SolHud.Part part : SOL_PARTS) {
            String label = SolMenu.tr("sol.menu.hud.part." + part.id);
            if (cxp > x && cxp + g.width(chipFont, label) + 22f > x + w) {
                cxp = x;
                cyp += 30f;
            }
            boolean on = cfg.solUi && !cfg.solUiOff.contains(part.id);
            cxp += ui.chip(tk, cxp, cyp, label, on, () -> toggle(cfg, part)) + 6f;
        }

        // The HUD modules' switches in two columns.
        float lx = x + mw + 22f, lw = w - mw - 22f, colW = (lw - 24f) / 2f;
        List<HudModule> list = Modules.hud();
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        int i = 0;
        for (HudModule m : list) {
            if (!m.available()) {
                continue;
            }
            float cx = lx + (i % 2) * (colW + 24f), cy = my + 13f + (i / 2) * 34f;
            g.textMid(f, g.fit(f, m.name(), colW - 40f), cx, cy, 0xFFFFFFFF);
            ui.smallSwitch(tk, cx + colW - 30f, cy - 9f, m.enabled(), () -> ModulesSection.toggle(menu, m));
            i++;
        }
        float ty = my + 13f + ((i + 1) / 2) * 34f + 10f;
        SolFont hint = g.font(SolFont.Family.MEDIUM, 13f);
        for (String line : wrap(g, hint, SolMenu.tr("sol.menu.hud.hint"), lw)) {
            g.text(hint, line, lx, ty, 0x99FFFFFF);
            ty += 18f;
        }
    }

    /** The parts with a chip; the level goes with the experience bar. */
    private static final SolHud.Part[] SOL_PARTS = {SolHud.Part.HOTBAR, SolHud.Part.HEALTH, SolHud.Part.ARMOR, SolHud.Part.FOOD,
            SolHud.Part.AIR, SolHud.Part.MOUNT, SolHud.Part.XP, SolHud.Part.BOSS, SolHud.Part.SIDEBAR, SolHud.Part.TAB,
            SolHud.Part.ACTION_BAR, SolHud.Part.MODULES};

    /** Hands a part back to the game, or gives it to Sol (turning Sol UI on if it was off). */
    private static void toggle(SolConfig cfg, SolHud.Part part) {
        boolean give = !cfg.solUi || cfg.solUiOff.contains(part.id);
        cfg.solUi = true;
        for (SolHud.Part p : part == SolHud.Part.XP ? new SolHud.Part[]{SolHud.Part.XP, SolHud.Part.LEVEL} : new SolHud.Part[]{part}) {
            cfg.solUiOff.remove(p.id);
            if (!give) {
                cfg.solUiOff.add(p.id);
            }
        }
        SolConfig.save();
    }

    /** One box of the layout: dashed, dark, its name in the middle. */
    private static void box(Gfx g, Tokens tk, SolFont f, String name, float bx, float by, float w, float h) {
        float bw = Math.max(18f, w), bh = Math.max(10f, h);
        g.round(bx, by, bw, bh, 5f, 0x59000000);
        io.github.georgwav.cosmetics.sol.ui.kit.Widgets.dashed(g, bx, by, bw, bh, 5f, tk.rim);
        g.textCentre(f, g.fit(f, name, bw - 4f), bx + bw / 2f, by + bh / 2f, 0xCCFFFFFF);
    }

    static List<String> wrap(Gfx g, SolFont f, String text, float width) {
        List<String> out = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (g.width(f, next) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    @Override
    public void closed() {
        if (sky != null) {
            sky.release();
        }
    }
}
