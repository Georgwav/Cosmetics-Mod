package io.github.georgwav.cosmetics.sol.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.theme.ThemeRegistry;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.ScreenOpener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Search or run anything (Ctrl K, or just typing): a module to turn on or off, its settings, a preset, a section, a
 * profile, an action. Every word typed has to match; the best matches first; Enter runs the chosen one.
 */
final class CommandBar {
    /** One thing to run; {@code key} names it for the suggestions ("module:camera", "preset:ember_solstice", ...). */
    record Item(String key, String label, String kind, String icon, Runnable run) {
    }

    /** What shows before anything is typed: the prototype's five, then stand-ins while a module doesn't exist yet. */
    private static final String[] SUGGESTED = {"module:camera", "action:edit_hud", "preset:ember_solstice", "profile:",
            "settings:glint", "settings:atmosphere", "module:crosshair"};

    private final SolMenu menu;
    private String query;
    private int selected;
    private List<Item> results = List.of();
    private float caret;
    private float boxX, boxY, boxW, boxH;

    CommandBar(SolMenu menu, String seed) {
        this.menu = menu;
        this.query = seed == null ? "" : seed;
    }

    private List<Item> items() {
        List<Item> it = new ArrayList<>();
        for (Module m : Modules.all()) {
            if (!m.available()) {
                continue;
            }
            it.add(new Item("module:" + m.id, m.name(), SolMenu.tr(m.enabled() ? "sol.cmd.turn_off" : "sol.cmd.turn_on"), ModuleIcons.module(m.id),
                    () -> ModulesSection.toggle(menu, m)));
        }
        for (Module m : Modules.all()) {
            if (m.available() && !m.settings().isEmpty()) {
                it.add(new Item("settings:" + m.id, SolMenu.tr("sol.cmd.settings_of", m.name()), SolMenu.tr("sol.cmd.open"), "gear", () -> {
                    menu.go("modules");
                    menu.openDrawer(m);
                }));
            }
        }
        for (ThemeRegistry.Entry e : ThemeRegistry.all()) {
            it.add(new Item("preset:" + e.id(), SolMenu.tr("sol.cmd.preset", Widgets.themeName(e)), SolMenu.tr("sol.cmd.kind.preset"), "sun",
                    () -> WorldSection.apply(menu, e)));
        }
        for (String s : SolMenu.STOPS) {
            it.add(new Item("section:" + s, SolMenu.tr("sol.cmd.open_section", SolMenu.tr("sol.section." + s)), SolMenu.tr("sol.cmd.kind.section"),
                    ModuleIcons.SECTIONS.get(s), () -> menu.go(s)));
        }
        for (String p : SolProfiles.names()) {
            if (p.equals(SolProfiles.active())) {
                continue;
            }
            it.add(new Item("profile:" + p, SolMenu.tr("sol.cmd.switch_profile", p), SolMenu.tr("sol.cmd.kind.profile"), "people", () -> {
                SolProfiles.switchTo(p);
                menu.toast(SolMenu.tr("sol.notify.profile", p));
            }));
        }
        it.add(new Item("action:edit_hud", SolMenu.tr("sol.cmd.edit_hud"), SolMenu.tr("sol.cmd.kind.action"), "hud",
                () -> ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.HudEditor(menu))));
        return it;
    }

    private void refresh() {
        List<Item> all = items();
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            List<Item> picks = new ArrayList<>();
            for (String key : SUGGESTED) {
                for (Item i : all) {
                    if (picks.size() < 5 && (key.endsWith(":") ? i.key().startsWith(key) : i.key().equals(key))) {
                        picks.add(i);
                        break;
                    }
                }
            }
            results = picks;
        } else {
            String[] words = q.split("\\s+");
            List<Item> found = new ArrayList<>();
            for (Item i : all) {
                String hay = (i.label() + " " + i.kind()).toLowerCase(Locale.ROOT);
                boolean all1 = true;
                for (String w : words) {
                    all1 &= hay.contains(w);
                }
                if (all1) {
                    found.add(i);
                }
            }
            // Starts-with matches first.
            found.sort((a, b) -> Boolean.compare(!a.label().toLowerCase(Locale.ROOT).startsWith(words[0]),
                    !b.label().toLowerCase(Locale.ROOT).startsWith(words[0])));
            results = found.size() > 8 ? found.subList(0, 8) : found;
        }
        selected = Math.max(0, Math.min(selected, results.size() - 1));
    }

    void draw(Gfx g, Ui ui, Tokens tk) {
        refresh();
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0x6B000000);
        float w = 580f, x = 640f - w / 2f, y = 206f;
        float listH = Math.max(1, results.size()) * 38f;
        float h = 52f + listH + 30f;
        boxX = x;
        boxY = y;
        boxW = w;
        boxH = h;
        g.glow(x, y, w, h, 18f, 40f, Colors.withAlpha(tk.glow, 0.25f));
        g.round(x, y, w, h, 18f, 0xF50A0A0E);
        g.stroke(x, y, w, h, 18f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(x, y, w, h, 18f, 0x38FFFFFF);
        // Input.
        Icons.draw(g, "search", x + 16f, y + 17f, 18f, 0x99FFFFFF, Icons.FILL, 0);
        SolFont in = g.font(SolFont.Family.MEDIUM, 16f);
        float tx = x + 44f;
        if (query.isEmpty()) {
            g.textMid(in, SolMenu.tr("sol.cmd.placeholder"), tx, y + 26f, 0x66FFFFFF);
        } else {
            g.textMid(in, query, tx, y + 26f, 0xFFFFFFFF);
        }
        caret += menu.dt();
        if (((int) (caret * 2f)) % 2 == 0) {
            g.rect(tx + g.width(in, query) + 1f, y + 16f, 1.5f, 20f, 0xFFFFFFFF);
        }
        g.rect(x + 1f, y + 52f, w - 2f, Math.max(1f / g.d.u, 1f), 0x1AFFFFFF);
        // Results.
        SolFont f = g.font(SolFont.Family.MEDIUM, 13.5f), k = g.font(SolFont.Family.MONO, 11f);
        float ry = y + 52f;
        if (results.isEmpty()) {
            g.textMid(f, SolMenu.tr("sol.cmd.nothing", query), x + 44f, ry + 19f, 0xB3FFFFFF);
        }
        for (int i = 0; i < results.size(); i++) {
            Item it = results.get(i);
            boolean sel = i == selected;
            final int idx = i;
            ui.click(x, ry, w, 38f, () -> run(idx));
            if (ui.over(x, ry, w, 38f)) {
                selected = i;
                sel = true;
            }
            if (sel) {
                g.rect(x + 1f, ry, w - 2f, 38f, 0x14FFFFFF);
            }
            Icons.draw(g, it.icon(), x + 18f, ry + 11f, 16f, sel ? tk.accent : 0x99FFFFFF, sel ? Icons.FILL_ON : Icons.FILL, 0);
            g.textMid(f, g.fit(f, it.label(), w - 44f - 120f), x + 46f, ry + 19f, 0xFFFFFFFF);
            g.textRight(k, it.kind(), x + w - 16f, ry + 19f, 0x80FFFFFF);
            ry += 38f;
        }
        g.rect(x + 1f, y + h - 30f, w - 2f, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        g.textMid(k, SolMenu.tr("sol.cmd.foot"), x + 16f, y + h - 15f, 0x73FFFFFF);
        ui.area(x, y, w, h, () -> { });
    }

    private void run(int i) {
        if (i < 0 || i >= results.size()) {
            return;
        }
        Item it = results.get(i);
        menu.closePalette();
        it.run().run();
    }

    boolean press(float x, float y) {
        if (x >= boxX && x <= boxX + boxW && y >= boxY && y <= boxY + boxH) {
            menu.ui.press(x, y);
        } else {
            menu.closePalette();
        }
        return true;
    }

    boolean scroll(float amount) {
        selected = Math.max(0, Math.min(results.size() - 1, selected - (int) Math.signum(amount)));
        return true;
    }

    boolean typed(String text) {
        if (text.isEmpty() || Character.isISOControl(text.charAt(0))) {
            return false;
        }
        query += text;
        selected = 0;
        caret = 0f;
        return true;
    }

    boolean key(int key, boolean ctrl) {
        switch (key) {
            case InputConstants.KEY_ESCAPE -> menu.closePalette();
            case InputConstants.KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = ctrl ? "" : query.substring(0, query.offsetByCodePoints(query.length(), -1));
                }
                caret = 0f;
            }
            case InputConstants.KEY_DOWN -> selected = Math.min(results.size() - 1, selected + 1);
            case InputConstants.KEY_UP -> selected = Math.max(0, selected - 1);
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> run(selected);
            default -> {
                return false;
            }
        }
        return true;
    }
}
