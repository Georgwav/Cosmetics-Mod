package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * The quick wheel (a key of its own, unbound until you bind it): your pinned modules in a ring round the crosshair.
 * Hold the key, point at one and let go to switch it on or off; or tap the key and click. Nothing else on the screen,
 * so it works mid-fight.
 */
public final class QuickWheel extends SolScreen {
    private static final long TAP_NANOS = 300_000_000L;
    private static final float CX = 640f, CY = 360f, RING = 120f, R = 34f;
    /** The key that opens the wheel (set when it is registered). */
    public static KeyMapping key;
    /** Frames drawn (the in-game check). */
    public static int framesRendered;

    private final long openedNanos = System.nanoTime();
    private final Ui ui = new Ui();
    private Module pointed;

    public QuickWheel() {
        super(Component.translatable("sol.quickwheel.title"), null, Feature.MENU);
    }

    /** Opens the wheel unless another screen is open. */
    public static void openIfAllowed(Minecraft client) {
        if (ScreenOpener.current() == null && client.player != null) {
            ScreenOpener.open(new QuickWheel());
        }
    }

    @Override
    protected boolean blurs() {
        return false;
    }

    private static List<Module> modules() {
        List<Module> out = new ArrayList<>();
        for (String id : SolProfiles.pinned()) {
            Module m = Modules.byId(id);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        pointed = null;
        List<Module> all = modules();
        SolFont small = g.font(SolFont.Family.MEDIUM, 12f);
        if (all.isEmpty()) {
            String none = Component.translatable("sol.quickwheel.empty").getString();
            float w = g.width(small, none) + 32f;
            g.round(CX - w / 2f, CY + 40f, w, 32f, 16f, tk.panel);
            g.textCentre(small, none, CX, CY + 56f, 0xCCFFFFFF);
            return;
        }
        g.ring(CX, CY, RING, Math.max(1f, g.d.u) * 1.5f, Colors.withAlpha(tk.rim, 0.3f));
        for (int i = 0; i < all.size(); i++) {
            Module m = all.get(i);
            double a = -Math.PI / 2 + i * 2 * Math.PI / all.size();
            float x = CX + (float) Math.cos(a) * RING, y = CY + (float) Math.sin(a) * RING;
            boolean hover = ui.over(x - R, y - R, R * 2f, R * 2f);
            if (hover) {
                pointed = m;
            }
            ui.click(x - R, y - R, R * 2f, R * 2f, () -> toggle(m));
            boolean on = m.enabled();
            if (on || hover) {
                g.glow(x - R, y - R, R * 2f, R * 2f, R, 22f, Colors.withAlpha(tk.glow, hover ? 0.4f : 0.25f));
            }
            g.circle(x, y, R, hover ? Colors.mix(tk.panel, 0xFFFFFFFF, 0.08f) : tk.panel);
            g.ring(x, y, R, Math.max(1f, g.d.u), on ? tk.accent : 0x33FFFFFF);
            String icon = ModuleIcons.module(m.id);
            Icons.draw(g, icon, x - 11f, y - 11f, 22f, on ? tk.accent : 0x99FFFFFF, on ? Icons.FILL_ON : Icons.FILL, 0);
        }
        // The name of the one pointed at, and whether it's on, under the ring.
        if (pointed != null) {
            String text = pointed.name() + " · " + Component.translatable(pointed.enabled() ? "sol.quickwheel.on" : "sol.quickwheel.off").getString();
            float w = g.width(small, text) + 28f;
            g.round(CX - w / 2f, CY + RING + R + 14f, w, 30f, 15f, tk.panel);
            g.stroke(CX - w / 2f, CY + RING + R + 14f, w, 30f, 15f, Math.max(1f, g.d.u), tk.rim);
            g.textCentre(small, text, CX, CY + RING + R + 29f, 0xFFFFFFFF);
        }
    }

    private void toggle(Module m) {
        m.setEnabled(!m.enabled());
        Modules.save();
        onClose();
    }

    @Override
    protected boolean press(float x, float y, int button) {
        return ui.press(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    /** Letting go of the key: switches the module pointed at; held a while and pointing at nothing closes the wheel. */
    private boolean keyLetGo() {
        if (pointed != null) {
            toggle(pointed);
            return true;
        }
        if (System.nanoTime() - openedNanos > TAP_NANOS) {
            onClose();
            return true;
        }
        return false;
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyReleased(KeyEvent event) {
        if (key != null && key.matches(event) && keyLetGo()) {
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (key != null && key.matches(keyCode, scanCode) && keyLetGo()) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}
}
