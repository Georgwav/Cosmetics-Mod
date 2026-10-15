package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.client.CrashHelper;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Shown once after the game crashed: what the report says, the mod it points at, and what to do: copy the report
 * (to share it), open the folder, turn that mod off for the next start, or carry on.
 */
public final class CrashHelperScreen extends SolScreen {
    private final CrashHelper.Crash crash;
    private final Ui ui = new Ui();
    private boolean disabled;

    public CrashHelperScreen(Screen parent, CrashHelper.Crash crash) {
        super(Component.translatable("sol.crash.title"), parent, Feature.MENU);
        this.crash = crash;
        CrashHelper.seen(crash);
    }

    @Override
    protected boolean press(float x, float y, int button) {
        return ui.press(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    @Override
    protected void frame(Gfx g) {
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0xB3050508);
        float w = 720f, h = 330f, x = 640f - w / 2f, y = 360f - h / 2f;
        g.glow(x, y, w, h, 18f, 26f, Colors.withAlpha(0xFFFF6A5A, 0.18f));
        g.round(x, y, w, h, 18f, Colors.withAlpha(tk.panel, Math.max(0.85f, tk.glass)));
        g.stroke(x, y, w, h, 18f, Math.max(1f, g.d.u), 0x40FF8A7A);
        g.highlight(x, y, w, h, 18f, 0x30FFFFFF);
        Icons.draw(g, "bolt", x + 28f, y + 28f, 28f, 0xFFFF8A7A, Icons.FILL_ON, 0);
        SolFont h1 = g.font(SolFont.Family.SEMIBOLD, 22f), text = g.font(SolFont.Family.MEDIUM, 14f);
        SolFont mono = g.font(SolFont.Family.MONO, 12f);
        float tx = x + 70f, room = w - 98f;
        g.textMid(h1, tr("sol.crash.title"), tx, y + 42f, 0xFFFFFFFF);
        g.textMid(mono, g.fit(mono, crash.report().time(), room), tx, y + 66f, 0x8CFFFFFF);
        String said = crash.report().description().isEmpty() ? tr("sol.crash.no_description") : crash.report().description();
        g.textMid(text, g.fit(text, said, room), tx, y + 104f, 0xFFFFFFFF);
        g.textMid(mono, g.fit(mono, crash.report().exception(), room), tx, y + 128f, 0xB3FFFFFF);
        String who = crash.modId() == null ? tr("sol.crash.game") : tr("sol.crash.mod", crash.modName(), crash.modId());
        g.round(tx, y + 152f, room, 44f, 10f, 0x14FFFFFF);
        g.textMid(text, g.fit(text, who, room - 24f), tx + 12f, y + 174f, crash.modId() == null ? 0xCCFFFFFF : 0xFFFFC35C);
        if (disabled) {
            g.textMid(g.font(SolFont.Family.MEDIUM, 12f), tr("sol.crash.disabled", crash.modName()), tx, y + 214f, Colors.withAlpha(tk.accent, 0.9f));
        }
        float by = y + h - 40f, right = x + w - 24f;
        right -= ui.button(tk, right, by, tr("sol.crash.continue"), true, this::onClose) + 8f;
        right -= ui.button(tk, right, by, tr("sol.crash.copy"), false, this::copy) + 8f;
        right -= ui.button(tk, right, by, tr("sol.crash.folder"), false,
                () -> io.github.georgwav.cosmetics.sol.util.Browser.open(CrashHelper.folder().toUri().toString())) + 8f;
        boolean canDisable = crash.modJar() != null && !disabled && !"cosmeticsmod".equals(crash.modId())
                && !crash.modId().startsWith("fabric");
        if (canDisable) {
            ui.button(tk, right, by, tr("sol.crash.disable", crash.modName()), false, () -> {
                disabled = CrashHelper.disable(crash.modJar());
                if (!disabled) {
                    io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.crash.disable_failed"));
                }
            });
        }
    }

    private void copy() {
        try {
            minecraft.keyboardHandler.setClipboard(java.nio.file.Files.readString(crash.file()));
            io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.crash.copied"));
        } catch (java.io.IOException e) {
            io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.crash.copy_failed"));
        }
    }
}
