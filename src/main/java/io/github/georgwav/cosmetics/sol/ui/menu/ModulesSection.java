package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modules, as Dawn's mod menu in Sol's pixels (A.7.2): filter tabs and a search over a recess of cards, four to a row.
 * Each card has the module's name and its pin star, its big icon, a line on what it does, an ENABLED (glowing, with
 * the sparkle) or DISABLED button, and the gear for its settings. Filters, not folders: All, On, Pinned and the
 * categories.
 */
final class ModulesSection implements Section {
    static final String[] FILTERS = {"all", "on", "starred", "pvp", "visual", "world", "hud", "utility", "social", "kept"};
    /** A card's size and the grid's gaps (design px). */
    static final float CARD_H = 196f, GAP = 14f, PAD = 14f;
    private String filter = "all";
    private final Scroll scroll = new Scroll();
    private final TextField search = new TextField(40, s -> scroll.reset());
    private final List<Module> shown = new ArrayList<>();

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

    private void list() {
        shown.clear();
        String q = search.text().trim().toLowerCase(Locale.ROOT);
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
            if (keep && (q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q) || m.id.contains(q))) {
                shown.add(m);
            }
        }
    }

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        PixelTokens p = PixelTokens.get();
        int on = 0;
        for (Module m : Modules.all()) {
            on += m.available() && m.enabled() ? 1 : 0;
        }
        list();
        // The head: filter tabs on the left; the count and the search on the right.
        float tabH = 40f, ty = y;
        SolFont cf = g.font(SolFont.Family.PIXEL, 11f);
        String count = SolMenu.tr("sol.menu.modules.count_shown", on, shown.size());
        // The tabs in Silkscreen 12, or 10 when that leaves no room for the search (pixel type has whole steps only).
        SolFont tf = g.font(SolFont.Family.PIXEL_BOLD, 12f);
        float room = w - 200f - 12f - g.width(cf, count) - 12f, need = 0f;
        for (String f : FILTERS) {
            need += g.width(tf, SolMenu.tr("sol.menu.filter." + f)) + 26f + 6f;
        }
        if (need > room) {
            tf = g.font(SolFont.Family.PIXEL_BOLD, 10f);
        }
        float tx = x;
        for (String f : FILTERS) {
            String label = SolMenu.tr("sol.menu.filter." + f);
            float bw = g.width(tf, label) + 26f;
            boolean pressed = f.equals(filter), over = ui.over(tx, ty, bw, tabH);
            int st = pressed ? Pix.ON : ui.pressed(tx, ty, bw, tabH) ? Pix.PRESSED : over ? Pix.HOVER : Pix.NORMAL;
            Pix.slab(g, p, tx, ty, bw, tabH, st);
            g.textMid(tf, label, tx + 13f, ty + tabH / 2f + (st == Pix.PRESSED ? Pix.E : 0f), Pix.text(p, st));
            int fi = ui.click(tx, ty, bw, tabH, () -> {
                filter = f;
                scroll.reset();
            });
            if (ui.focused(fi)) {
                Pix.focus(g, tx, ty, bw, tabH);
            }
            tx += bw + 6f;
        }
        float sw = Math.min(280f, x + w - (tx + 12f + g.width(cf, count) + 12f));
        float sx = x + w - sw;
        if (sw >= 140f) {
            search.draw(g, ui, tk, sx, ty - 1f, sw, 42f, "search", SolMenu.tr("sol.menu.modules.search"), menu.dt());
            g.textRight(cf, count, sx - 12f, ty + tabH / 2f, PixelTokens.INK3);
        } else {
            g.textRight(cf, count, x + w, ty + tabH / 2f, PixelTokens.INK3);
        }

        // The cards in a recess, four to a row (three with a module's settings open beside it).
        float gy = y + tabH + 16f, gh = h - tabH - 16f;
        Pix.recess(g, p, x, gy, w, gh);
        int cols = 4;
        float inX = x + Pix.E + PAD, inY = gy + Pix.E + PAD, inW = w - 2f * (Pix.E + PAD) - 10f, inH = gh - 2f * Pix.E - PAD;
        float cw = (inW - GAP * (cols - 1)) / cols;
        int rows = (shown.size() + cols - 1) / cols;
        float content = rows * (CARD_H + GAP) - GAP + PAD;
        scroll.setMax(content - inH);
        scroll.step(menu.dt());
        g.clip(x + Pix.E, gy + Pix.E, x + w - Pix.E, gy + gh - Pix.E);
        if (shown.isEmpty()) {
            g.text(g.font(SolFont.Family.MEDIUM, 13f), SolMenu.tr("sol.menu.modules.empty"), inX, inY + 18f, PixelTokens.INK2);
        }
        for (int i = 0; i < shown.size(); i++) {
            Module m = shown.get(i);
            float cx = inX + (i % cols) * (cw + GAP), cy = inY + (i / cols) * (CARD_H + GAP) - scroll.value();
            if (cy + CARD_H < gy || cy > gy + gh) {
                continue;
            }
            card(menu, g, ui, p, m, cx, cy, cw, CARD_H);
        }
        g.endClip();
        if (content > inH) {
            float from = scroll.value() / content, to = (scroll.value() + inH) / content;
            Pix.scrollbar(g, p, x + w - Pix.E - 10f, gy + Pix.E + 4f, gh - 2f * Pix.E - 8f, from, Math.min(1f, to));
        }
    }

    private void card(SolMenu menu, Gfx g, Ui ui, PixelTokens p, Module m, float x, float y, float w, float h) {
        boolean on = m.enabled();
        boolean pinned = SolProfiles.pinned().contains(m.id);
        if (on) {
            Pix.cardOn(g, p, x, y, w, h);
        } else {
            Pix.card(g, p, x, y, w, h);
        }
        // The name and the pin.
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 14f);
        float pinX = x + w - 12f - 30f, top = y + 12f;
        g.textMid(nf, g.fitLabel(nf, m.name(), pinX - x - 20f), x + 12f, top + 15f, on ? 0xFFF4F0EA : 0xFF8D8892);
        boolean overPin = ui.over(pinX, top, 30f, 30f);
        Icons.draw(g, "star", pinX + 3f, top + 3f, 24f, pinned ? p.acc : overPin ? 0xFFBDB7C2 : 0xFF57535C, 0f, 0);
        int fp = ui.click(pinX, top, 30f, 30f, () -> {
            boolean now = !SolProfiles.pinned().contains(m.id);
            SolProfiles.setPinned(m.id, now);
            menu.toast(SolMenu.tr(now ? "sol.notify.starred" : "sol.notify.unstarred"));
        });
        if (ui.focused(fp)) {
            Pix.focus(g, pinX, top, 30f, 30f);
        }
        // The big icon and what it does.
        Icons.draw(g, ModuleIcons.module(m.id), x + w / 2f - 24f, y + 54f, 48f, on ? 0xFFEFEAE4 : 0xFF9A95A0, 0f, 0);
        SolFont df = g.font(SolFont.Family.MEDIUM, 12f);
        g.textCentre(df, g.fit(df, description(m), w - 24f), x + w / 2f, y + 122f, PixelTokens.INK3);
        // ENABLED / DISABLED, and the gear.
        float by = y + h - 14f - 42f, gearX = x + w - 12f - 42f, bw = gearX - 8f - (x + 12f);
        stateButton(menu, g, ui, p, m, x + 12f, by, bw, 42f);
        boolean overGear = ui.over(gearX, by, 42f, 42f);
        Pix.slab(g, p, gearX, by, 42f, 42f, ui.pressed(gearX, by, 42f, 42f) ? Pix.PRESSED : overGear ? Pix.HOVER : Pix.NORMAL);
        Icons.draw(g, "gear", gearX + 9f, by + 9f, 24f, Pix.text(p, overGear ? Pix.HOVER : Pix.NORMAL), 0f, 0);
        int fg = ui.click(gearX, by, 42f, 42f, () -> menu.openDrawer(m));
        if (ui.focused(fg)) {
            Pix.focus(g, gearX, by, 42f, 42f);
        }
    }

    /** Dawn's state button: ENABLED in the on glow with the sparkle, or DISABLED in the off look. */
    private static void stateButton(SolMenu menu, Gfx g, Ui ui, PixelTokens p, Module m, float x, float y, float w, float h) {
        boolean on = m.enabled(), over = ui.over(x, y, w, h);
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 12f);
        String label = SolMenu.tr(on ? "sol.menu.modules.enabled" : "sol.menu.modules.disabled");
        float dy = ui.pressed(x, y, w, h) ? Pix.E : 0f;
        if (on) {
            Pix.onGlow(g, p, x, y + dy, w, h, over);
            Pix.onText(g, p, f, label, x + w / 2f - g.width(f, label) / 2f, y + dy + h / 2f);
            Pix.sparkle(g, p, x + w - 7f, y + dy + 7f, menu.time(), SolMenu.calm());
        } else {
            Pix.slab(g, p, x, y + dy, w, h, over ? Pix.HOVER : Pix.NORMAL);
            g.textCentre(f, label, x + w / 2f, y + dy + h / 2f, Pix.offText(over));
        }
        int fi = ui.click(x, y, w, h, () -> toggle(menu, m));
        if (ui.focused(fi)) {
            Pix.focus(g, x, y, w, h);
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
    public boolean typed(String text) {
        return search.typed(text);
    }

    @Override
    public boolean key(int key, boolean ctrl) {
        return search.key(key, ctrl);
    }

    @Override
    public void opened() {
        scroll.reset();
    }
}
