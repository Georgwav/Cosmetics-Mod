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
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * A module's settings in the side drawer: the switch and a live preview at the top, its settings as rows, its key,
 * the advanced settings folded away, and "Saved in the profile …" with Reset at the bottom. The grid stays where it
 * was behind it.
 */
public final class ModuleDrawer {
    private final Module module;
    private final Scroll scroll = new Scroll();
    private boolean advancedOpen;
    private float knob;
    /** The key waiting for a new binding, or null. */
    private KeyMapping binding;
    /** The text settings' fields. */
    private final java.util.Map<Setting.Text, io.github.georgwav.cosmetics.sol.ui.kit.TextField> fields = new java.util.HashMap<>();
    private SolTexture sky;
    private String skyFor;
    private int skyGeneration = -1;

    ModuleDrawer(Module module) {
        this.module = module;
        this.knob = module.enabled() ? 1f : 0f;
    }

    public Module module() {
        return module;
    }

    void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        g.glow(x, y, w, h, 18f, 34f, Colors.withAlpha(tk.glow, 0.2f));
        g.round(x, y, w, h, 18f, 0xE609090D);
        g.stroke(x, y, w, h, 18f, Math.max(1f, g.d.u), tk.rim);
        g.highlight(x, y, w, h, 18f, 0x40FFFFFF);
        ui.area(x, y, w, h, () -> { });
        boolean on = module.enabled();
        knob += ((on ? 1f : 0f) - knob) * Math.min(1f, menu.dt() * 16f);
        // Header.
        float hx = x + 14f, hy = y + 14f;
        g.round(hx, hy, 40f, 40f, 11f, 0x1AFFFFFF);
        Icons.draw(g, ModuleIcons.module(module.id), hx + 10f, hy + 10f, 20f, tk.accent, Icons.FILL_ON, Colors.withAlpha(tk.glow, 0.5f));
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 16f), s = g.font(SolFont.Family.MEDIUM, 12f);
        float room = w - 14f - 40f - 12f - 36f - 10f - 26f - 14f - 10f;
        g.text(b, g.fit(b, module.name(), room), hx + 52f, hy + 17f, 0xFFFFFFFF);
        g.text(s, g.fit(s, ModulesSection.description(module), room), hx + 52f, hy + 35f, 0x94FFFFFF);
        ui.bigSwitch(tk, x + w - 14f - 26f - 10f - 36f, hy + 9f, on, knob, () -> ModulesSection.toggle(menu, module));
        ui.tool(tk, x + w - 14f - 26f, hy + 7f, "close", false, menu::closeDrawer);
        // Preview.
        float px = x + 14f, py = y + 66f, pw = w - 28f, ph = 118f;
        preview(menu, g, tk, px, py, pw, ph);
        // Body.
        float by = py + ph + 12f, bottom = y + h - 44f;
        g.clip(x + 1f, by - 2f, x + w - 1f, bottom);
        float rowY = by - scroll.value();
        // The key sits on top when it's bound (the prototype's Zoom on C); an unbound one waits under Advanced.
        KeyMapping key = module.toggleKey();
        boolean keyOnTop = key != null && (binding == key || !key.isUnbound());
        if (keyOnTop) {
            rowY = keyRow(g, ui, tk, key, x, w, rowY);
        }
        int advanced = (key != null && !keyOnTop ? 1 : 0) + module.keyNames().size();
        for (Setting<?> st : module.settings()) {
            if (st.advanced) {
                advanced++;
                continue;
            }
            rowY = setting(menu, g, ui, tk, st, x, w, rowY);
        }
        if (module == Atmosphere.INSTANCE) {
            rowY = themeAmounts(g, ui, tk, x, w, rowY);
        }
        if (advanced > 0) {
            rowY += 4f;
            g.rect(x + 14f, rowY, w - 28f, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
            SolFont af = g.font(SolFont.Family.MEDIUM, 12f);
            float ay = rowY + 18f;
            ui.click(x + 14f, rowY + 4f, w - 28f, 24f, () -> advancedOpen = !advancedOpen);
            g.textMid(af, SolMenu.tr("sol.menu.drawer.advanced"), x + 14f, ay, 0xB3FFFFFF);
            g.textRight(af, (advancedOpen ? "▾ " : "▸ ") + advanced, x + w - 14f, ay, 0xB3FFFFFF);
            rowY += 32f;
            if (advancedOpen) {
                if (key != null && !keyOnTop) {
                    rowY = keyRow(g, ui, tk, key, x, w, rowY);
                }
                for (String name : module.keyNames()) {
                    KeyMapping other = module.key(name);
                    if (other != null) {
                        rowY = keyRow(g, ui, tk, other, x, w, rowY,
                                net.minecraft.network.chat.Component.translatable(other.getName()).getString());
                    }
                }
                for (Setting<?> st : module.settings()) {
                    if (st.advanced) {
                        rowY = setting(menu, g, ui, tk, st, x, w, rowY);
                    }
                }
            }
        }
        g.endClip();
        scroll.setMax(rowY + scroll.value() - bottom + 8f);
        scroll.step(menu.dt());
        scroll.bar(g, x + w - 4f, by, bottom - by);
        // Footer.
        g.rect(x + 1f, bottom, w - 2f, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        SolFont ff = g.font(SolFont.Family.MEDIUM, 11.5f);
        float fy = bottom + 22f;
        g.textMid(ff, SolMenu.tr("sol.menu.drawer.saved", SolProfiles.active()), x + 14f, fy, 0x8CFFFFFF);
        ui.button(tk, x + w - 14f, fy, SolMenu.tr("sol.menu.drawer.reset"), false, () -> {
            for (Setting<?> st : module.settings()) {
                st.reset();
            }
            Modules.save();
            menu.toast(SolMenu.tr("sol.notify.reset", module.name()));
        });
    }

    private float keyRow(Gfx g, Ui ui, Tokens tk, KeyMapping key, float x, float w, float y) {
        return keyRow(g, ui, tk, key, x, w, y, SolMenu.tr("sol.menu.drawer.key"));
    }

    private float keyRow(Gfx g, Ui ui, Tokens tk, KeyMapping key, float x, float w, float y, String name) {
        float next = row(g, x, w, y, name);
        boolean waiting = binding == key;
        String label = waiting ? SolMenu.tr("sol.menu.drawer.press_key") : KeyBinds.name(key);
        ui.button(tk, x + w - 14f, y + 13f, label, waiting, () -> binding = waiting ? null : key);
        return next;
    }

    /** Atmosphere's particle amount and fog: the current theme's own, the same as the World section's sliders. */
    private static float themeAmounts(Gfx g, Ui ui, Tokens tk, float x, float w, float y) {
        Customisation c = ThemeState.customisation();
        float right = x + w - 14f;
        float next = row(g, x, w, y, SolMenu.tr("sol.menu.world.particles"));
        ui.slider(tk, right, y + 13f, c.density() / 3f, Math.round(c.density() * 100f) + "%",
                k -> WorldSection.customise(ThemeState.customisation().withDensity(k * 3f)));
        float fog = c.fog() == null ? ThemeState.baseEntry().base().fog().density() : c.fog();
        float after = row(g, x, w, next, SolMenu.tr("sol.menu.world.fog"));
        ui.slider(tk, right, next + 13f, fog, Math.round(fog * 100f) + "%",
                k -> WorldSection.customise(ThemeState.customisation().withFog(k)));
        return after;
    }

    private static float row(Gfx g, float x, float w, float y, String label) {
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        g.textMid(f, g.fit(f, label, w * 0.45f), x + 14f, y + 13f, 0xFFFFFFFF);
        return y + 37f;
    }

    @SuppressWarnings("unchecked")
    private float setting(SolMenu menu, Gfx g, Ui ui, Tokens tk, Setting<?> st, float x, float w, float y) {
        float next = row(g, x, w, y, module.settingName(st));
        float cy = y + 13f, right = x + w - 14f;
        if (st instanceof Setting.Toggle t) {
            ui.smallSwitch(tk, right - 30f, cy - 9f, t.get(), () -> t.set(!t.get()));
        } else if (st instanceof Setting.Slider sl) {
            float v = (float) ((sl.get() - sl.min) / Math.max(1e-9, sl.max - sl.min));
            ui.slider(tk, right, cy, v, sl.text(), k -> sl.set(sl.min + k * (sl.max - sl.min)));
        } else if (st instanceof Setting.Text t) {
            var field = fields.computeIfAbsent(t, k -> {
                var made = new io.github.georgwav.cosmetics.sol.ui.kit.TextField(k.max, k::set);
                return k instanceof Setting.Secret ? made.secret() : made;
            });
            if (!field.focused()) {
                field.set(t.get());
            }
            float fw = w * 0.5f;
            field.draw(g, ui, tk, right - fw, cy - 13f, fw, 26f, null, SolMenu.tr("sol.menu.drawer.text_hint"), menu.dt());
        } else if (st instanceof Setting.Choice c) {
            String[] names = new String[c.options.size()];
            float total = 0f;
            SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
            for (int i = 0; i < names.length; i++) {
                names[i] = module.optionName(c, c.options.get(i));
                total += g.width(f, names[i]) + 20f;
            }
            int sel = c.options.indexOf(c.get());
            if (total <= w * 0.58f) {
                ui.segmented(tk, right, cy, names, sel, i -> c.set(c.options.get(i)));
            } else {
                // Too many to sit side by side: one button that steps through them.
                ui.button(tk, right, cy, names[Math.max(0, sel)] + "  ▸", false,
                        () -> c.set(c.options.get((Math.max(0, sel) + 1) % c.options.size())));
            }
        }
        return next;
    }

    private void preview(SolMenu menu, Gfx g, Tokens tk, float x, float y, float w, float h) {
        g.round(x, y, w, h, 12f, 0x0AFFFFFF);
        g.stroke(x, y, w, h, 12f, Math.max(1f, g.d.u), 0x14FFFFFF);
        SolFont cap = g.font(SolFont.Family.MONO, 10.5f);
        switch (module.id) {
            case "atmosphere" -> {
                var e = ThemeState.baseEntry();
                if (sky == null || sky.released() || !e.id().equals(skyFor) || skyGeneration != ThemeState.tokens().generation) {
                    if (sky != null) {
                        sky.release();
                    }
                    skyFor = e.id();
                    skyGeneration = ThemeState.tokens().generation;
                    int pw = 374 * 2, ph = 118 * 2;
                    sky = SolTexture.of("drawer_sky", pw, ph, true, MiniSky.render(e.base(), e.id(), pw, ph, 2f).toArgb(null));
                }
                g.clip(x, y, x + w, y + h);
                g.image(sky, x, y, w, h, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
                g.endClip();
                g.text(cap, io.github.georgwav.cosmetics.sol.ui.kit.Widgets.themeName(e), x + 10f, y + h - 8f, 0x8CFFFFFF);
            }
            case "crosshair" -> {
                float cx = x + w / 2f, cy = y + h / 2f;
                g.glowSprite(cx, cy, 36f, 36f, Colors.withAlpha(tk.accent, 0.35f));
                float u = 64f / 18f;
                g.rect(cx - u, cy - 8f * u, 2f * u, 5f * u, tk.accent);
                g.rect(cx - u, cy + 3f * u, 2f * u, 5f * u, tk.accent);
                g.rect(cx - 8f * u, cy - u, 5f * u, 2f * u, tk.accent);
                g.rect(cx + 3f * u, cy - u, 5f * u, 2f * u, tk.accent);
                g.text(cap, SolMenu.tr("sol.menu.drawer.live"), x + 10f, y + h - 8f, 0x8CFFFFFF);
            }
            default -> {
                g.glowSprite(x + w / 2f, y + h / 2f, 40f, 40f, Colors.withAlpha(tk.glow, 0.35f));
                Icons.draw(g, ModuleIcons.module(module.id), x + w / 2f - 28f, y + h / 2f - 28f, 56f, tk.accent, Icons.FILL_ON,
                        Colors.withAlpha(tk.glow, 0.6f));
                g.text(cap, SolMenu.tr("sol.menu.drawer.live_game"), x + 10f, y + h - 8f, 0x8CFFFFFF);
            }
        }
    }

    boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    boolean typed(String text) {
        for (var field : fields.values()) {
            if (field.typed(text)) {
                return true;
            }
        }
        return false;
    }

    /** While waiting for a key to bind: that key, or Esc to clear it. */
    boolean key(int key, boolean ctrl) {
        for (var field : fields.values()) {
            if (field.key(key, ctrl)) {
                return true;
            }
        }
        if (binding == null) {
            return false;
        }
        KeyMapping target = binding;
        binding = null;
        KeyBinds.bind(target, key == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : KeyBinds.keyboard(key, 0));
        Minecraft.getInstance().options.save();
        return true;
    }

    void release() {
        if (sky != null) {
            sky.release();
        }
    }
}
