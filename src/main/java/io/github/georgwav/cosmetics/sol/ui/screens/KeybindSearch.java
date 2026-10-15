package io.github.georgwav.cosmetics.sol.ui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every key bind (the game's, every mod's, Sol's) in one searchable list: type to find one by its name, its group or
 * its key; keys shared by two binds show in amber ("Clashes" shows only those). Click a key, then press the new one
 * (a mouse button works too; Escape clears it); Reset puts the default back. The same binds as in Controls.
 */
public final class KeybindSearch extends SolScreen {
    private final Ui ui = new Ui();
    private final Scroll scroll = new Scroll();
    private final TextField search = new TextField(60, t -> scroll.reset());
    private boolean clashesOnly;
    private KeyMapping listening;

    public KeybindSearch(Screen parent) {
        super(Component.translatable("sol.keys.title"), parent, Feature.MENU);
        search.focus(true);
    }

    @Override
    protected boolean press(float x, float y, int button) {
        if (listening != null) {
            KeyBinds.bind(listening, KeyBinds.mouse(button));
            listening = null;
            return true;
        }
        return ui.press(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        return scroll.wheel(amount);
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        if (listening != null) {
            KeyBinds.bind(listening, key == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : KeyBinds.keyboard(key, 0));
            listening = null;
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            return false;
        }
        return search.key(key, ctrl);
    }

    @Override
    protected boolean typed(String text) {
        return listening == null && search.typed(text);
    }

    /** Whether every word of the search is in the bind's name, group or key. */
    static boolean matches(String query, String... fields) {
        String hay = String.join(" ", fields).toLowerCase(Locale.ROOT);
        for (String word : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!word.isEmpty() && !hay.contains(word)) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void frame(Gfx g) {
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0xA6050508);
        SolFont h1 = g.font(SolFont.Family.SEMIBOLD, 26f), mono = g.font(SolFont.Family.MONO, 12f);
        SolFont name = g.font(SolFont.Family.MEDIUM, 14f), small = g.font(SolFont.Family.MEDIUM, 12f);
        float x0 = 160f, w = 960f, top = 44f;
        String title = Component.translatable("sol.keys.title").getString();
        g.textMid(h1, title, x0, top, 0xFFFFFFFF);
        KeyMapping[] all = minecraft.options.keyMappings.clone();
        Arrays.sort(all);
        List<KeyMapping> shown = new ArrayList<>();
        int clashes = 0;
        for (KeyMapping m : all) {
            boolean clash = KeyBinds.conflicts(m);
            clashes += clash ? 1 : 0;
            String n = Component.translatable(m.getName()).getString();
            if ((!clashesOnly || clash) && matches(search.text(), n, KeyBinds.category(m), KeyBinds.name(m))) {
                shown.add(m);
            }
        }
        g.textMid(mono, Component.translatable("sol.keys.count", all.length, clashes).getString(), x0 + g.width(h1, title) + 14f, top, 0x8CFFFFFF);
        ui.button(tk, x0 + w, top, Component.translatable("gui.done").getString(), true, this::onClose);
        search.draw(g, ui, tk, x0, top + 30f, w - 140f, 34f, "search", Component.translatable("sol.keys.search").getString(), dt);
        ui.chip(tk, x0 + w - 128f, top + 47f, Component.translatable("sol.keys.clashes").getString(), clashesOnly,
                () -> clashesOnly = !clashesOnly);

        float ly = top + 82f, bottom = 700f;
        g.clip(x0 - 8f, ly - 4f, x0 + w + 8f, bottom);
        float y = ly - scroll.value();
        String group = null;
        for (KeyMapping m : shown) {
            String cat = KeyBinds.category(m);
            if (!cat.equals(group)) {
                group = cat;
                if (y > ly - 40f && y < bottom + 20f) {
                    g.textMid(g.font(SolFont.Family.SEMIBOLD, 12f), cat.toUpperCase(Locale.ROOT), x0, y + 14f, Colors.withAlpha(tk.accent, 0.9f));
                }
                y += 30f;
            }
            if (y > ly - 40f && y < bottom + 20f) {
                row(g, tk, m, name, small, x0, y, w);
            }
            y += 38f;
        }
        if (shown.isEmpty()) {
            g.textMid(name, Component.translatable("sol.keys.none").getString(), x0, ly + 20f, 0x8CFFFFFF);
        }
        g.endClip();
        scroll.setMax(y + scroll.value() - bottom);
        scroll.step(dt);
    }

    private void row(Gfx g, Tokens tk, KeyMapping m, SolFont name, SolFont small, float x, float y, float w) {
        boolean clash = KeyBinds.conflicts(m);
        g.round(x, y, w, 32f, 8f, 0x0DFFFFFF);
        g.textMid(name, g.fit(name, Component.translatable(m.getName()).getString(), w - 320f), x + 12f, y + 16f, 0xFFFFFFFF);
        String key = listening == m ? Component.translatable("sol.keys.press").getString() : KeyBinds.name(m);
        float kw = Math.max(120f, g.width(small, key) + 28f), kx = x + w - 8f - kw;
        boolean hover = ui.over(kx, y + 4f, kw, 24f);
        ui.click(kx, y + 4f, kw, 24f, () -> listening = listening == m ? null : m);
        int edge = listening == m ? tk.accent : clash ? 0xFFFFB347 : hover ? tk.rim : 0x33FFFFFF;
        g.round(kx, y + 4f, kw, 24f, 7f, listening == m ? Colors.withAlpha(tk.accent, 0.2f) : 0x1AFFFFFF);
        g.stroke(kx, y + 4f, kw, 24f, 7f, Math.max(1f, g.d.u), edge);
        g.textCentre(small, key, kx + kw / 2f, y + 16f, clash ? 0xFFFFC35C : m.isUnbound() ? 0x80FFFFFF : 0xFFFFFFFF);
        if (!m.isDefault()) {
            String reset = Component.translatable("controls.reset").getString();
            float rw = g.width(small, reset) + 18f, rx = kx - 8f - rw;
            ui.click(rx, y + 4f, rw, 24f, () -> KeyBinds.bind(m, m.getDefaultKey()));
            g.round(rx, y + 4f, rw, 24f, 7f, ui.over(rx, y + 4f, rw, 24f) ? 0x29FFFFFF : 0x0DFFFFFF);
            g.textCentre(small, reset, rx + rw / 2f, y + 16f, 0xB3FFFFFF);
        }
        if (clash) {
            g.textRight(small, Component.translatable("sol.keys.clash").getString(), kx - (m.isDefault() ? 10f : 100f), y + 16f, 0xCCFFC35C);
        }
    }
}
