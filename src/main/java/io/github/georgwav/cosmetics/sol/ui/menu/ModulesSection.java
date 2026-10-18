package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Modules: every module as a card with its switch on the card; the gear opens its settings in the drawer, the star
 * pins it (Starred, the quick menu's tray). Filters, not folders: All, On, Starred and the categories.
 */
final class ModulesSection implements Section {
    static final String[] FILTERS = {"all", "on", "starred", "pvp", "visual", "world", "hud", "utility", "social", "kept"};
    private String filter = "all";
    private final Scroll scroll = new Scroll();
    private final Map<String, Float> knobs = new HashMap<>();
    private final Map<String, Float> lift = new HashMap<>();

    /** The prototype's category of a module. */
    static String category(Module m) {
        String id = m.id;
        return switch (id) {
            case "pvp_tweaks", "pvp_effects", "hitboxes", "held_items", "punchy", "crosshair", "nametags" -> "pvp";
            case "camera", "glint", "screen", "particles", "fullbright", "outline" -> "visual";
            case "atmosphere", "world_overlays", "time", "weather" -> "world";
            case "sol_chat", "music", "lyrics", "social", "servers" -> "social";
            default -> switch (m.category) {
                case HUD -> "hud";
                case WORLD -> "world";
                case VIEW -> "visual";
                case CHAT -> "social";
                default -> "utility";
            };
        };
    }

    static String description(Module m) {
        String key = "sol.module." + m.id + ".desc";
        return Widgets.has(key) ? I18n.get(key) : m.note();
    }

    private List<Module> list() {
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.all()) {
            if (!m.available()) {
                continue;
            }
            boolean keep = switch (filter) {
                case "all" -> true;
                case "on" -> m.enabled();
                case "starred" -> SolProfiles.pinned().contains(m.id);
                default -> category(m).equals(filter);
            };
            if (keep) {
                out.add(m);
            }
        }
        return out;
    }

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        int on = 0, all = 0;
        for (Module m : Modules.all()) {
            if (m.available()) {
                all++;
                on += m.enabled() ? 1 : 0;
            }
        }
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f), mono = g.font(SolFont.Family.MONO, 12f);
        String title = SolMenu.tr("sol.section.modules");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        float tx = x + g.width(h2, title) + 14f;
        g.textMid(mono, SolMenu.tr("sol.menu.modules.count", on, all), tx, headY, 0x8CFFFFFF);
        // Filters on the right, laid out from the right edge.
        float fx = x + w;
        float[] widths = new float[FILTERS.length];
        SolFont cf = g.font(SolFont.Family.MEDIUM, 12f);
        float total = 0f;
        for (int i = 0; i < FILTERS.length; i++) {
            widths[i] = g.width(cf, SolMenu.tr("sol.menu.filter." + FILTERS[i])) + 22f;
            total += widths[i] + 6f;
        }
        fx = x + w - total + 6f;
        for (int i = 0; i < FILTERS.length; i++) {
            String f = FILTERS[i];
            ui.chip(tk, fx, headY, SolMenu.tr("sol.menu.filter." + f), f.equals(filter), () -> {
                filter = f;
                scroll.reset();
            });
            fx += widths[i] + 6f;
        }

        // The grid.
        float gy = y + 42f, gh = h - 42f;
        int cols = menu.drawer() != null ? 3 : 4;
        float gap = 10f, cw = (w - gap * (cols - 1) - 8f) / cols, ch = 64f;
        List<Module> list = list();
        int rows = (list.size() + cols - 1) / cols;
        scroll.setMax(rows * (ch + gap) - gap - gh + 12f);
        scroll.step(menu.dt());
        g.clip(x - 4f, gy - 4f, x + w + 4f, gy + gh);
        if (list.isEmpty()) {
            g.text(g.font(SolFont.Family.MEDIUM, 13f), SolMenu.tr("sol.menu.modules.empty"), x, gy + 18f, 0x99FFFFFF);
        }
        for (int i = 0; i < list.size(); i++) {
            Module m = list.get(i);
            float cx = x + (i % cols) * (cw + gap), cy = gy + 2f + (i / cols) * (ch + gap) - scroll.value();
            if (cy + ch < gy - 8f || cy > gy + gh + 8f) {
                continue;
            }
            card(menu, g, ui, tk, m, cx, cy, cw, ch);
        }
        g.endClip();
        scroll.bar(g, x + w + 4f, gy, gh);
    }

    private void card(SolMenu menu, Gfx g, Ui ui, Tokens tk, Module m, float x, float y, float w, float h) {
        boolean on = m.enabled();
        boolean starred = SolProfiles.pinned().contains(m.id);
        boolean selected = menu.drawer() != null && menu.drawer().module() == m;
        boolean hover = ui.over(x, y, w, h);
        float lt = lift.getOrDefault(m.id, 0f);
        lt += ((hover ? 1f : 0f) - lt) * Math.min(1f, menu.dt() * 12f);
        lift.put(m.id, lt);
        float ky = knobs.getOrDefault(m.id, on ? 1f : 0f);
        ky += ((on ? 1f : 0f) - ky) * Math.min(1f, menu.dt() * 16f);
        knobs.put(m.id, ky);
        y -= 2f * lt;
        int fi = ui.click(x, y, w, h, () -> toggle(menu, m));
        if (on) {
            g.glow(x, y, w, h, 14f, 22f, Colors.withAlpha(tk.glow, 0.16f));
        }
        if (selected) {
            g.glow(x, y, w, h, 14f, 26f, Colors.withAlpha(tk.glow, 0.32f));
        }
        g.round(x, y, w, h, 14f, tk.panel);
        int rim = selected ? tk.accent : on ? tk.rim : hover ? 0x38FFFFFF : 0x17FFFFFF;
        g.stroke(x, y, w, h, 14f, Math.max(1f, g.d.u), rim);
        g.highlight(x, y, w, h, 14f, on ? 0x38FFFFFF : 0x24FFFFFF);
        // Icon tile.
        float ix = x + 12f, iy = y + (h - 40f) / 2f;
        g.round(ix, iy, 40f, 40f, 11f, on ? 0x1AFFFFFF : 0x0FFFFFFF);
        Icons.draw(g, ModuleIcons.module(m.id), ix + 10f, iy + 10f, 20f, on ? tk.accent : 0x80FFFFFF,
                on ? Icons.FILL_ON : Icons.FILL, on ? Colors.withAlpha(tk.glow, 0.5f) : 0);
        // Name, star, description.
        SolFont nf = g.font(SolFont.Family.SEMIBOLD, 14f), df = g.font(SolFont.Family.MEDIUM, 11.5f);
        float textX = ix + 52f, room = w - 52f - 12f - 36f - 12f - 12f;
        String name = g.fit(nf, m.name(), room - (starred ? 13f : 0f));
        g.text(nf, name, textX, y + 27f, 0xFFFFFFFF);
        if (starred) {
            Icons.draw(g, "star", textX + g.width(nf, name) + 5f, y + 17f, 9f, tk.accent, Icons.FILL_ON, 0);
        }
        g.text(df, g.fit(df, ModulesSection.description(m), room), textX, y + 44f, 0x8FFFFFFF);
        // Switch.
        ui.bigSwitch(tk, x + w - 12f - 36f, y + (h - 22f) / 2f, on, ky, () -> toggle(menu, m));
        // Star and gear, on hover or focus.
        if (hover || ui.focused(fi) || selected) {
            float bx = x + w - 58f - 58f, by = y + (h - 30f) / 2f;
            g.round(bx, by, 58f, 30f, 9f, 0xE60A0A0E);
            ui.tool(tk, bx + 2f, by + 2f, "star", starred, () -> {
                boolean now = !SolProfiles.pinned().contains(m.id);
                SolProfiles.setPinned(m.id, now);
                menu.toast(SolMenu.tr(now ? "sol.notify.starred" : "sol.notify.unstarred"));
            });
            ui.tool(tk, bx + 30f, by + 2f, "gear", false, () -> menu.openDrawer(m));
        }
        if (ui.focused(fi)) {
            g.stroke(x - 2f, y - 2f, w + 4f, h + 4f, 16f, 2f * g.d.u, tk.accent);
        }
    }

    static void toggle(SolMenu menu, Module m) {
        m.setEnabled(!m.enabled());
        Modules.save();
        menu.toast(SolMenu.tr(m.enabled() ? "sol.notify.module_on" : "sol.notify.module_off", m.name()));
    }

    @Override
    public boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    @Override
    public void opened() {
        scroll.reset();
    }
}
