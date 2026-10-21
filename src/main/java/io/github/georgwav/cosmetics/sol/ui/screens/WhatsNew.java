package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.client.ReleaseNotes;
import io.github.georgwav.cosmetics.sol.client.Updates;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * What's new in a Sol Client release: its notes from GitHub, before you install it (Install, or Later) or once
 * after it took over (Done). Long lines wrap; the notes scroll.
 */
public final class WhatsNew extends SolScreen {
    private final Ui ui = new Ui();
    private final Scroll scroll = new Scroll();
    private final String version;
    private final List<ReleaseNotes.Line> notes;
    /** Before installing (Install and Later), or after (Done). */
    private final boolean offer;
    /** Times the notes were drawn (for the self-test). */
    public static int shown;

    public WhatsNew(Screen parent, String version, String markdown, boolean offer) {
        super(Component.translatable("sol.whats_new.title", version), parent, Feature.MENU);
        this.version = version;
        this.notes = ReleaseNotes.lines(markdown);
        this.offer = offer;
    }

    @Override
    protected boolean press(float x, float y, int button) {
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

    /** {@code text} broken into lines that fit {@code room}, at spaces. */
    private static List<String> wrap(Gfx g, SolFont f, String text, float room) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (g.width(f, next) > room && !line.isEmpty()) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        out.add(line.toString());
        return out;
    }

    @Override
    protected void frame(Gfx g) {
        shown++;
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0xA6050508);
        SolFont h1 = g.font(SolFont.Family.PIXEL_BOLD, 16f), h2 = g.font(SolFont.Family.PIXEL_BOLD, 13f);
        SolFont body = g.font(SolFont.Family.MEDIUM, 13f), mono = g.font(SolFont.Family.PIXEL, 12f);
        float x0 = 240f, w = 800f, top = 60f;
        g.textMid(h1, Component.translatable("sol.whats_new.heading").getString(), x0, top, 0xFFFFFFFF);
        g.textMid(mono, "Sol Client " + version, x0 + g.width(h1, Component.translatable("sol.whats_new.heading").getString()) + 14f, top,
                tk.accent);
        float right = x0 + w;
        if (offer) {
            float bw = ui.button(tk, right, top, Component.translatable("sol.whats_new.install").getString(), true, () -> {
                Updates.install();
                onClose();
            });
            ui.button(tk, right - bw - 8f, top, Component.translatable("sol.whats_new.later").getString(), false, this::onClose);
        } else {
            ui.button(tk, right, top, Component.translatable("gui.done").getString(), true, this::onClose);
        }
        float ly = top + 44f, bottom = 690f;
        Pix.fill(g, x0 - 16f, ly - 12f, w + 32f, bottom - ly + 24f, 0x0DFFFFFF);
        if (notes.isEmpty()) {
            g.textMid(body, Component.translatable("sol.whats_new.none").getString(), x0, ly + 16f, 0x8CFFFFFF);
            return;
        }
        g.clip(x0 - 8f, ly - 4f, x0 + w + 8f, bottom);
        float y = ly + 10f - scroll.value();
        for (ReleaseNotes.Line line : notes) {
            if (line.text().isEmpty()) {
                y += 10f;
                continue;
            }
            SolFont f = line.heading() ? h2 : body;
            for (String part : wrap(g, f, line.text(), w)) {
                if (y > ly - 30f && y < bottom + 20f) {
                    g.textMid(f, part, x0, y, line.heading() ? tk.accent : 0xE6FFFFFF);
                }
                y += line.heading() ? 26f : 21f;
            }
        }
        g.endClip();
        scroll.setMax(y + scroll.value() - bottom + 10f);
        scroll.step(dt);
    }
}
