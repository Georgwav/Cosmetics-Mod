package io.github.georgwav.cosmetics.sol.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.Customisation;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.ColorPicker;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A module's settings page, over the whole menu: its name, switch, a search over its settings and Reset all along the
 * top; on the left a live preview (the crosshair as it will look, the theme's sky) and the module's own keys; on the
 * right its settings in groups, each with its hint, the way back to its default when it's changed, colours with a
 * picker and long choices that open in place, and the advanced settings folded at the end with the key that switches
 * the whole module ("Toggle mod").
 */
public final class ModulePage {
    /** The page, in design pixels. */
    static final float X = 40f, Y = 40f, W = 1200f, H = 636f;
    private static final float LEFT_W = 360f, GAP = 28f, PAD = 20f;
    private static final int[] PRESETS = {0xFFFFFF, 0xFF4D4D, 0xFF9A3C, 0xFFD23F, 0x7CF27C, 0x3FD8C7, 0x4DA6FF, 0x8B6BFF,
            0xFF6EC7, 0xB0B0B8, 0x3A3A44, 0x000000};

    private final Module module;
    private final Scroll scroll = new Scroll();
    private final TextField search = new TextField(40, q -> { });
    private boolean advancedOpen;
    private float knob;
    /** The key waiting for a new binding, or null. */
    private KeyMapping binding;
    private final Map<Setting.Text, TextField> fields = new HashMap<>();
    /** The colour being picked (its picker open under its row), and the choice whose options are open. */
    private Setting.Color picking;
    private ColorPicker picker;
    private Setting.Choice choosing;
    private SolTexture sky;
    private String skyFor;
    private int skyGeneration = -1;
    /** The preview's box in screen pixels, for the game's own drawing (the crosshair). */
    private int pvX0, pvY0, pvX1, pvY1;

    ModulePage(Module module) {
        this.module = module;
        this.knob = module.enabled() ? 1f : 0f;
    }

    public Module module() {
        return module;
    }

    void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk) {
        g.glow(X, Y, W, H, 22f, 40f, Colors.withAlpha(tk.glow, 0.18f));
        g.round(X, Y, W, H, 22f, 0xFF0B0C11);
        g.stroke(X, Y, W, H, 22f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(X, Y, W, H, 22f, 0x40FFFFFF);
        ui.area(X, Y, W, H, () -> { });
        boolean on = module.enabled();
        knob += ((on ? 1f : 0f) - knob) * Math.min(1f, menu.dt() * 16f);
        header(menu, g, ui, tk, on);
        float top = Y + 92f, bottom = Y + H - PAD;
        left(menu, g, ui, tk, X + PAD, top, bottom);
        right(menu, g, ui, tk, X + PAD + LEFT_W + GAP, top, X + W - PAD, bottom);
    }

    // ------------------------------------------------------------------ header

    private void header(SolMenu menu, Gfx g, Ui ui, Tokens tk, boolean on) {
        float iy = Y + 16f;
        g.round(X + PAD, iy, 46f, 46f, 13f, 0x1AFFFFFF);
        Icons.draw(g, ModuleIcons.module(module.id), X + PAD + 11f, iy + 11f, 24f, tk.accent, Icons.FILL_ON,
                Colors.withAlpha(tk.glow, 0.5f));
        float right = X + W - PAD;
        ui.tool(tk, right - 26f, Y + 26f, "close", false, menu::closeDrawer);
        float sx = right - 26f - 16f - 36f;
        ui.bigSwitch(tk, sx, Y + 28f, on, knob, () -> ModulesSection.toggle(menu, module));
        float fw = 240f, fx = sx - 18f - fw;
        search.draw(g, ui, tk, fx, Y + 24f, fw, 30f, "search", SolMenu.tr("sol.menu.page.search"), menu.dt());
        float rw = ui.button(tk, fx - 10f, Y + 39f, SolMenu.tr("sol.menu.page.reset_all"), false, () -> {
            for (Setting<?> st : module.settings()) {
                st.reset();
            }
            Modules.save();
            menu.toast(SolMenu.tr("sol.notify.reset", module.name()));
        });
        float tx = X + PAD + 60f, room = fx - 10f - rw - 16f - tx;
        SolFont name = g.font(SolFont.Family.SEMIBOLD, 22f), desc = g.font(SolFont.Family.MEDIUM, 12.5f);
        g.textMid(name, g.fit(name, module.name(), room), tx, Y + 30f, 0xFFFFFFFF);
        g.textMid(desc, g.fit(desc, ModulesSection.description(module), room), tx, Y + 52f, 0x94FFFFFF);
        g.rect(X + 1f, Y + 76f, W - 2f, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
    }

    // ------------------------------------------------------------------ left: preview, keys, where it's saved

    private void left(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float top, float bottom) {
        float ph = 214f;
        preview(g, tk, x, top, LEFT_W, ph);
        float ky = top + ph + 16f;
        SolFont h = g.font(SolFont.Family.SEMIBOLD, 13f), f = g.font(SolFont.Family.MEDIUM, 13f), dim = g.font(SolFont.Family.MEDIUM, 11.5f);
        g.textMid(h, SolMenu.tr("sol.menu.page.keys"), x, ky + 8f, 0xFFFFFFFF);
        ky += 22f;
        int own = 0;
        for (String n : module.keyNames()) {
            KeyMapping k = module.key(n);
            if (k == null) {
                continue;
            }
            own++;
            String label = net.minecraft.network.chat.Component.translatable(k.getName()).getString();
            // Controls names them "Camera: freelook"; here the module is known.
            int colon = label.indexOf(": ");
            label = colon > 0 && colon < label.length() - 2 ? capital(label.substring(colon + 2)) : label;
            g.textMid(f, g.fit(f, label, LEFT_W * 0.55f), x, ky + 16f, 0xFFFFFFFF);
            keyButton(ui, tk, k, x + LEFT_W, ky + 16f);
            g.rect(x, ky + 32f, LEFT_W, Math.max(1f / g.d.u, 1f), 0x0DFFFFFF);
            ky += 36f;
        }
        if (own == 0) {
            for (String line : HudSection.wrap(g, dim, SolMenu.tr("sol.menu.page.no_keys"), LEFT_W)) {
                g.textMid(dim, line, x, ky + 10f, 0x8CFFFFFF);
                ky += 17f;
            }
        }
        float iy = bottom - 8f;
        if (!module.available()) {
            g.textMid(dim, g.fit(dim, SolMenu.tr("sol.menu.page.unavailable"), LEFT_W), x, iy - 18f, 0xFFFFB347);
        }
        g.textMid(dim, g.fit(dim, SolMenu.tr("sol.menu.drawer.saved", SolProfiles.active()), LEFT_W), x, iy, 0x8CFFFFFF);
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** A key's button ending at {@code right}: its key, or "Press a key" while waiting (Esc clears it). */
    private float keyButton(Ui ui, Tokens tk, KeyMapping key, float right, float cy) {
        boolean waiting = binding == key;
        String label = waiting ? SolMenu.tr("sol.menu.drawer.press_key") : KeyBinds.name(key);
        return ui.button(tk, right, cy, label, waiting, () -> binding = waiting ? null : key);
    }

    private void preview(Gfx g, Tokens tk, float x, float y, float w, float h) {
        g.round(x, y, w, h, 14f, 0x0AFFFFFF);
        g.stroke(x, y, w, h, 14f, Math.max(1f, g.d.u), 0x14FFFFFF);
        pvX0 = Math.round(g.d.sx(x));
        pvY0 = Math.round(g.d.sy(y));
        pvX1 = Math.round(g.d.sx(x + w));
        pvY1 = Math.round(g.d.sy(y + h));
        SolFont cap = g.font(SolFont.Family.MONO, 10.5f);
        if (module == Atmosphere.INSTANCE) {
            var e = ThemeState.baseEntry();
            if (sky == null || sky.released() || !e.id().equals(skyFor) || skyGeneration != ThemeState.tokens().generation) {
                if (sky != null) {
                    sky.release();
                }
                skyFor = e.id();
                skyGeneration = ThemeState.tokens().generation;
                int pw = Math.round(LEFT_W * 2), ph = Math.round(h * 2);
                sky = SolTexture.of("page_sky", pw, ph, true, MiniSky.render(e.base(), e.id(), pw, ph, 2f).toArgb(null));
            }
            g.clip(x, y, x + w, y + h);
            g.image(sky, x, y, w, h, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
            g.endClip();
            g.text(cap, Widgets.themeName(e), x + 12f, y + h - 10f, 0x8CFFFFFF);
        } else if (module.previewHeight() > 0f) {
            // Drawn by the game itself, as it will look (see game()).
            g.text(cap, SolMenu.tr("sol.menu.drawer.live"), x + 12f, y + h - 10f, 0x8CFFFFFF);
        } else {
            g.glowSprite(x + w / 2f, y + h / 2f, 54f, 54f, Colors.withAlpha(tk.glow, 0.35f));
            Icons.draw(g, ModuleIcons.module(module.id), x + w / 2f - 34f, y + h / 2f - 34f, 68f, tk.accent, Icons.FILL_ON,
                    Colors.withAlpha(tk.glow, 0.6f));
            g.text(cap, SolMenu.tr("sol.menu.drawer.live_game"), x + 12f, y + h - 10f, 0x8CFFFFFF);
        }
    }

    /** The game's own drawing over the page: a module's live preview (the crosshair, exactly as on screen). */
    void game(Canvas c) {
        if (module.previewHeight() <= 0f || pvX1 <= pvX0) {
            return;
        }
        float gs = (float) Minecraft.getInstance().getWindow().getGuiScale();
        c.push();
        c.scale(1f / gs, 1f / gs);
        module.drawPreview(c, pvX0, pvY0, pvX1, pvY1);
        c.pop();
    }

    // ------------------------------------------------------------------ right: the settings

    private void right(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float top, float x1, float bottom) {
        float w = x1 - x;
        String q = search.text().trim().toLowerCase(Locale.ROOT);
        g.clip(x - 6f, top - 4f, x1 + 6f, bottom);
        float y = top - scroll.value();
        // The groups in the order their first setting comes, the advanced ones last.
        Map<String, List<Setting<?>>> groups = new LinkedHashMap<>();
        List<Setting<?>> advanced = new ArrayList<>();
        for (Setting<?> st : module.settings()) {
            if (!q.isEmpty() && !matches(st, q)) {
                continue;
            }
            if (st.advanced) {
                advanced.add(st);
            } else {
                groups.computeIfAbsent(st.group, k -> new ArrayList<>()).add(st);
            }
        }
        boolean shownAny = false;
        for (Map.Entry<String, List<Setting<?>>> e : groups.entrySet()) {
            y = groupHeader(g, x, w, y, Module.groupName(e.getKey()), null);
            for (Setting<?> st : e.getValue()) {
                y = row(menu, g, ui, tk, st, x, w, y);
            }
            y += 10f;
            shownAny = true;
        }
        if (module == Atmosphere.INSTANCE && q.isEmpty()) {
            y = groupHeader(g, x, w, y, SolMenu.tr("sol.menu.page.theme_amounts"), null);
            y = themeAmounts(g, ui, tk, x, w, y) + 10f;
            shownAny = true;
        }
        KeyMapping toggle = module.toggleKey();
        boolean toggleShown = toggle != null && (q.isEmpty() || SolMenu.tr("sol.menu.page.toggle_mod").toLowerCase(Locale.ROOT).contains(q));
        int count = advanced.size() + (toggleShown ? 1 : 0);
        if (count > 0) {
            boolean open = advancedOpen || !q.isEmpty();
            float hy = y;
            y = groupHeader(g, x, w, y, SolMenu.tr("sol.menu.drawer.advanced"),
                    SolMenu.tr(open ? "sol.menu.page.hide" : "sol.menu.page.show", count));
            ui.click(x, hy, w, 30f, () -> advancedOpen = !advancedOpen);
            if (open) {
                if (toggleShown) {
                    y = keyRow(g, ui, tk, toggle, x, w, y);
                }
                for (Setting<?> st : advanced) {
                    y = row(menu, g, ui, tk, st, x, w, y);
                }
            }
            shownAny = true;
        }
        if (!shownAny) {
            SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
            g.textMid(f, q.isEmpty() ? SolMenu.tr("sol.menu.page.empty") : SolMenu.tr("sol.menu.page.no_match", search.text().trim()),
                    x, y + 20f, 0x8CFFFFFF);
            y += 40f;
        }
        g.endClip();
        scroll.setMax(y + scroll.value() - bottom + 12f);
        scroll.step(menu.dt());
        scroll.bar(g, x1 + 8f, top, bottom - top);
    }

    private boolean matches(Setting<?> st, String q) {
        if (module.settingName(st).toLowerCase(Locale.ROOT).contains(q) || module.settingHint(st).toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        if (st instanceof Setting.Choice c) {
            for (String o : c.options) {
                if (module.optionName(c, o).toLowerCase(Locale.ROOT).contains(q)) {
                    return true;
                }
            }
        }
        return Module.groupName(st.group).toLowerCase(Locale.ROOT).contains(q);
    }

    private static float groupHeader(Gfx g, float x, float w, float y, String name, String aside) {
        SolFont f = g.font(SolFont.Family.SEMIBOLD, 12f);
        g.textMid(f, name.toUpperCase(Locale.ROOT), x, y + 14f, 0x99FFFFFF);
        if (aside != null) {
            SolFont m = g.font(SolFont.Family.MEDIUM, 12f);
            g.textRight(m, aside, x + w, y + 14f, 0x99FFFFFF);
        }
        g.rect(x, y + 27f, w, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        return y + 34f;
    }

    /** One setting: its name and hint on the left, its control on the right, and the way back if it's changed. */
    @SuppressWarnings("unchecked")
    private float row(SolMenu menu, Gfx g, Ui ui, Tokens tk, Setting<?> st, float x, float w, float y) {
        String hint = module.settingHint(st);
        float rh = hint.isEmpty() ? 42f : 54f, cy = y + rh / 2f, right = x + w;
        float labelW = w * 0.5f;
        SolFont f = g.font(SolFont.Family.MEDIUM, 13.5f), hf = g.font(SolFont.Family.MEDIUM, 11.5f);
        if (hint.isEmpty()) {
            g.textMid(f, g.fit(f, module.settingName(st), labelW), x, cy, 0xFFFFFFFF);
        } else {
            g.textMid(f, g.fit(f, module.settingName(st), labelW), x, y + 18f, 0xFFFFFFFF);
            g.textMid(hf, g.fit(hf, hint, labelW), x, y + 37f, 0x80FFFFFF);
        }
        if (st.changed()) {
            // Back to its default: a small button between the name and the control.
            SolFont df = g.font(SolFont.Family.MEDIUM, 11f);
            String d = SolMenu.tr("sol.menu.page.default");
            float dw = g.width(df, d) + 16f, dx = x + labelW + 10f;
            ui.click(dx, cy - 10f, dw, 20f, () -> {
                st.reset();
                if (st == picking) {
                    picker = null;
                    picking = null;
                }
            });
            g.round(dx, cy - 10f, dw, 20f, 10f, ui.over(dx, cy - 10f, dw, 20f) ? 0x29FFFFFF : 0x12FFFFFF);
            g.textMid(df, d, dx + 8f, cy, 0xB3FFFFFF);
        }
        float extra = 0f;
        if (st instanceof Setting.Toggle t) {
            ui.smallSwitch(tk, right - 30f, cy - 9f, t.get(), () -> t.set(!t.get()));
        } else if (st instanceof Setting.Slider sl) {
            float v = (float) ((sl.get() - sl.min) / Math.max(1e-9, sl.max - sl.min));
            ui.slider(tk, right, cy, v, sl.text(), k -> sl.set(sl.min + k * (sl.max - sl.min)));
        } else if (st instanceof Setting.Text t) {
            TextField field = fields.computeIfAbsent(t, k -> new TextField(k.max, k::set));
            if (!field.focused()) {
                field.set(t.get());
            }
            float fw = w * 0.42f;
            field.draw(g, ui, tk, right - fw, cy - 14f, fw, 28f, null, SolMenu.tr("sol.menu.drawer.text_hint"), menu.dt());
        } else if (st instanceof Setting.Color c) {
            boolean open = picking == c;
            float sw = 46f;
            ui.click(right - sw, cy - 12f, sw, 24f, () -> {
                if (picking == c) {
                    picking = null;
                    picker = null;
                } else {
                    picking = c;
                    picker = new ColorPicker(() -> c.get() & 0xFFFFFF, rgb -> c.set((c.get() & 0xFF000000) | (rgb & 0xFFFFFF)));
                }
            });
            g.round(right - sw, cy - 12f, sw, 24f, 7f, c.get() | 0xFF000000);
            g.stroke(right - sw, cy - 12f, sw, 24f, 7f, Math.max(1f, g.d.u), open ? tk.accent : 0x40FFFFFF);
            SolFont m = g.font(SolFont.Family.MONO, 11.5f);
            g.textRight(m, String.format(Locale.ROOT, "#%06X", c.get() & 0xFFFFFF), right - sw - 10f, cy, 0xB3FFFFFF);
            if (open && picker != null) {
                List<Integer> presets = new ArrayList<>();
                presets.add(tk.accent & 0xFFFFFF);
                for (int p : PRESETS) {
                    presets.add(p);
                }
                picker.draw(g, ui, tk, x, y + rh, w, presets, SolMenu.tr("sol.menu.page.default"), c::reset, menu.dt());
                extra = ColorPicker.HEIGHT + 10f;
            }
        } else if (st instanceof Setting.Choice c) {
            String[] names = new String[c.options.size()];
            float total = 0f;
            SolFont of = g.font(SolFont.Family.MEDIUM, 12f);
            for (int i = 0; i < names.length; i++) {
                names[i] = module.optionName(c, c.options.get(i));
                total += g.width(of, names[i]) + 20f;
            }
            int sel = Math.max(0, c.options.indexOf(c.get()));
            if (total <= w * 0.46f) {
                ui.segmented(tk, right, cy, names, sel, i -> c.set(c.options.get(i)));
            } else {
                // Too many to sit side by side: they open under the row.
                boolean open = choosing == c;
                ui.button(tk, right, cy, names[sel] + "  …", open, () -> choosing = open ? null : c);
                if (open) {
                    float ox = x, oy = y + rh + 4f;
                    for (int i = 0; i < names.length; i++) {
                        float cw = g.width(of, names[i]) + 22f;
                        if (ox + cw > right) {
                            ox = x;
                            oy += 30f;
                        }
                        final int idx = i;
                        ui.chip(tk, ox, oy + 12f, names[i], i == sel, () -> {
                            c.set(c.options.get(idx));
                            choosing = null;
                        });
                        ox += cw + 6f;
                    }
                    extra = oy + 30f - (y + rh);
                }
            }
        }
        g.rect(x, y + rh + extra - 1f, w, Math.max(1f / g.d.u, 1f), 0x0AFFFFFF);
        return y + rh + extra;
    }

    /** The key that switches the whole module on and off: "Toggle mod". */
    private float keyRow(Gfx g, Ui ui, Tokens tk, KeyMapping key, float x, float w, float y) {
        SolFont f = g.font(SolFont.Family.MEDIUM, 13.5f), hf = g.font(SolFont.Family.MEDIUM, 11.5f);
        g.textMid(f, SolMenu.tr("sol.menu.page.toggle_mod"), x, y + 18f, 0xFFFFFFFF);
        g.textMid(hf, g.fit(hf, SolMenu.tr("sol.menu.page.toggle_mod_hint", module.name()), w * 0.5f), x, y + 37f, 0x80FFFFFF);
        keyButton(ui, tk, key, x + w, y + 27f);
        g.rect(x, y + 53f, w, Math.max(1f / g.d.u, 1f), 0x0AFFFFFF);
        return y + 54f;
    }

    /** Atmosphere's particle amount and fog: the current theme's own, the same as the World section's sliders. */
    private static float themeAmounts(Gfx g, Ui ui, Tokens tk, float x, float w, float y) {
        Customisation c = ThemeState.customisation();
        SolFont f = g.font(SolFont.Family.MEDIUM, 13.5f);
        g.textMid(f, SolMenu.tr("sol.menu.world.particles"), x, y + 21f, 0xFFFFFFFF);
        ui.slider(tk, x + w, y + 21f, c.density() / 3f, Math.round(c.density() * 100f) + "%",
                k -> WorldSection.customise(ThemeState.customisation().withDensity(k * 3f)));
        float fog = c.fog() == null ? ThemeState.baseEntry().base().fog().density() : c.fog();
        g.textMid(f, SolMenu.tr("sol.menu.world.fog"), x, y + 63f, 0xFFFFFFFF);
        ui.slider(tk, x + w, y + 63f, fog, Math.round(fog * 100f) + "%",
                k -> WorldSection.customise(ThemeState.customisation().withFog(k)));
        return y + 84f;
    }

    // ------------------------------------------------------------------ input

    boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    boolean typed(String text) {
        if (search.typed(text)) {
            return true;
        }
        if (picker != null && picker.typed(text)) {
            return true;
        }
        for (TextField field : fields.values()) {
            if (field.typed(text)) {
                return true;
            }
        }
        return false;
    }

    /** While waiting for a key to bind: that key, or Esc to clear it; otherwise the text fields'. */
    boolean key(int key, boolean ctrl) {
        if (binding != null) {
            KeyMapping target = binding;
            binding = null;
            KeyBinds.bind(target, key == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : KeyBinds.keyboard(key, 0));
            Minecraft.getInstance().options.save();
            return true;
        }
        if (search.key(key, ctrl) || picker != null && picker.key(key, ctrl)) {
            return true;
        }
        for (TextField field : fields.values()) {
            if (field.key(key, ctrl)) {
                return true;
            }
        }
        return false;
    }

    void release() {
        if (sky != null) {
            sky.release();
        }
    }
}
