package io.github.georgwav.cosmetics.sol.ui.screens;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.hud.Hud;
import io.github.georgwav.cosmetics.client.hud.HudModule;
import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.menu.SolMenu;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD editor, over the game as it is (no blur): the HUD modules that are on and the game's own HUD parts, where
 * they sit.
 * - Drag one to move it. It snaps to the screen's edges and middle, lines up with the other boxes and sits next to
 *   them ({@link HudSnap}); Shift moves it freely.
 * - The arrow keys nudge the box last clicked (Shift: ten pixels).
 * - Scroll over a module to resize it; right-click it for its settings, or a part of the game's HUD to put it back.
 * - The glass island at the top says how, with Modules (the chips that switch HUD modules on and off), Reset game
 *   HUD and Done.
 */
public final class HudEditor extends SolScreen {
    /** Frames drawn (the in-game check). */
    public static int framesRendered;

    private final Ui ui = new Ui();
    private Box dragging;
    /** The box last clicked: the arrow keys nudge it. */
    private Box selected;
    /** Whether the module chips are open: at first only when no module is on yet. */
    private boolean showModules = Modules.hud().stream().noneMatch(HudModule::enabled);
    private float grabX, grabY;
    /** The guides the dragged box snapped to, GUI pixels, or NaN. */
    private float guideX = Float.NaN, guideY = Float.NaN;

    public HudEditor(Screen parent) {
        super(Component.translatable("cosmeticsmod.hud.title"), parent, Feature.MENU);
    }

    private static String t(String key) {
        return Component.translatable("cosmeticsmod.hud." + key).getString();
    }

    @Override
    protected void init() {
        super.init();
        Hud.editing = true;
    }

    @Override
    protected boolean blurs() {
        return false;
    }

    @Override
    protected void closed() {
        Hud.editing = false;
        Modules.save();
    }

    // ------------------------------------------------------------------ what can be moved (GUI pixels)

    /** Something on the HUD the editor moves: one of the client's modules, or a part of the game's own HUD. */
    private interface Box {
        HudSnap.Rect rect();

        void moveTo(float x, float y);

        boolean same(Box other);

        String label();
    }

    private record ModuleBox(HudModule m, int sw, int sh) implements Box {
        public HudSnap.Rect rect() {
            Minecraft mc = Minecraft.getInstance();
            return new HudSnap.Rect(m.x(sw, mc), m.y(sh, mc), m.width(mc) * m.scale(), m.height(mc) * m.scale());
        }

        public void moveTo(float x, float y) {
            m.moveTo(x, y, sw, sh, Minecraft.getInstance());
        }

        public boolean same(Box other) {
            return other instanceof ModuleBox o && o.m == m;
        }

        public String label() {
            return m.name();
        }
    }

    private record PartBox(VanillaHud.Part part, int sw, int sh) implements Box {
        public HudSnap.Rect rect() {
            float[] b = part.box(sw, sh);
            return new HudSnap.Rect(b[0] + VanillaHud.dx(part), b[1] + VanillaHud.dy(part), b[2], b[3]);
        }

        public void moveTo(float x, float y) {
            float[] home = part.box(sw, sh);
            VanillaHud.move(part, x - home[0], y - home[1]);
        }

        public boolean same(Box other) {
            return other instanceof PartBox o && o.part == part;
        }

        public String label() {
            return t("part." + part.id);
        }
    }

    /** What can be moved now, bottom to top: the game's HUD parts (when this version allows), then the modules on. */
    private List<Box> boxes() {
        List<Box> boxes = new ArrayList<>();
        if (VanillaHud.supported()) {
            for (VanillaHud.Part part : VanillaHud.Part.values()) {
                boxes.add(new PartBox(part, width, height));
            }
        }
        for (HudModule m : Modules.hud()) {
            if (m.enabled()) {
                boxes.add(new ModuleBox(m, width, height));
            }
        }
        return boxes;
    }

    /** The box on top under (x, y) in GUI pixels, or null. */
    private Box boxAt(float x, float y) {
        List<Box> all = boxes();
        for (int i = all.size() - 1; i >= 0; i--) {
            HudSnap.Rect r = all.get(i).rect();
            if (x >= r.x() && x < r.x() + r.w() && y >= r.y() && y < r.y() + r.h()) {
                return all.get(i);
            }
        }
        return null;
    }

    private float guiX(float designX) {
        return d.sx(designX) / d.gs;
    }

    private float guiY(float designY) {
        return d.sy(designY) / d.gs;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        // A faint veil and the screen's middle, so the boxes read over any scene.
        g.rectScreen(g.d.left(), g.d.top(), g.d.right(), g.d.bottom(), 0x30000000, 0x30000000);
        float midX = d.mouseX(width / 2f), midY = d.mouseY(height / 2f);
        float hair = 1f / g.d.u;
        g.rect(midX, g.d.top(), hair, g.d.bottom() - g.d.top(), 0x14FFFFFF);
        g.rect(g.d.left(), midY, g.d.right() - g.d.left(), hair, 0x14FFFFFF);
        // The modules themselves, drawn by the HUD in GUI pixels, under the editor's frames.
        Canvas c = g.game();
        c.push();
        c.scale(g.d.gs, g.d.gs);
        for (Box b : boxes()) {
            if (b instanceof ModuleBox mb) {
                Hud.drawModule(c, mb.m(), width, height);
            }
        }
        c.pop();
        g.layer();
        Box hot = dragging != null ? dragging : boxAt(guiX(mx), guiY(my));
        SolFont label = g.font(SolFont.Family.MEDIUM, 11f);
        for (Box b : boxes()) {
            HudSnap.Rect r = b.rect();
            float x0 = d.mouseX(r.x()), y0 = d.mouseY(r.y()), x1 = d.mouseX(r.x() + r.w()), y1 = d.mouseY(r.y() + r.h());
            boolean lit = hot != null && b.same(hot) || selected != null && b.same(selected);
            float rad = Math.min(6f, Math.min(x1 - x0, y1 - y0) / 2f);
            if (b instanceof PartBox) {
                // The game's own parts: a tinted glass frame; the game draws them (moved) underneath.
                Pix.fill(g, x0, y0, x1 - x0, y1 - y0, Colors.withAlpha(tk.accent, lit ? 0.16f : 0.07f));
            }
            if (lit) {
                Pix.frame(g, x0 - 1f, y0 - 1f, x1 - x0 + 2f, y1 - y0 + 2f, Colors.withAlpha(tk.glow, 0.6f));
            }
            Pix.frame(g, x0 - 1f, y0 - 1f, x1 - x0 + 2f, y1 - y0 + 2f, lit ? tk.accent : b instanceof PartBox ? Colors.withAlpha(tk.accent, 0.45f) : 0x59FFFFFF);
            if (lit) {
                String name = b.label();
                float lw = g.width(label, name) + 14f, ly = y0 - 22f < 4f ? y1 + 4f : y0 - 22f;
                Pix.fill(g, x0, ly, lw, 18f, Colors.withAlpha(tk.panel, 0.9f));
                Pix.frame(g, x0, ly, lw, 18f, tk.rim);
                g.textMid(label, name, x0 + 7f, ly + 9f, 0xFFFFFFFF);
            }
        }
        // Where the dragged box snapped.
        if (!Float.isNaN(guideX)) {
            float gx = d.mouseX(guideX);
            g.rect(gx, g.d.top(), hair, g.d.bottom() - g.d.top(), Colors.withAlpha(tk.accent, 0.85f));
        }
        if (!Float.isNaN(guideY)) {
            float gy = d.mouseY(guideY);
            g.rect(g.d.left(), gy, g.d.right() - g.d.left(), hair, Colors.withAlpha(tk.accent, 0.85f));
        }
        island(g, tk);
        if (showModules && dragging == null) {
            chips(g, tk);
        }
    }

    /** At the top: what this is, how it works, Modules, Reset game HUD and Done. */
    private void island(Gfx g, Tokens tk) {
        SolFont name = g.font(SolFont.Family.SEMIBOLD, 15f), small = g.font(SolFont.Family.MEDIUM, 11.5f);
        boolean any = Modules.hud().stream().anyMatch(HudModule::enabled);
        String title = t("title"), line = any ? t("hint") : t("empty");
        String done = Component.translatable("cosmeticsmod.hub.done").getString();
        String reset = VanillaHud.moved() ? t("reset") : null;
        String modules = t("modules");
        float textW = Math.min(560f, Math.max(g.width(name, title), g.width(small, line)));
        float bw = g.width(small, done) + g.width(small, modules) + 80f + (reset != null ? g.width(small, reset) + 40f : 0f);
        float w = 16f + 34f + 12f + textW + 20f + bw, h = 54f;
        float x = 640f - w / 2f, y = 18f;
        io.github.georgwav.cosmetics.sol.ui.kit.Pix.card(g, io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens.get(), x, y, w, h);
        ui.area(x, y, w, h, () -> { });
        g.circle(x + 16f + 17f, y + h / 2f, 17f, 0x14FFFFFF);
        Icons.draw(g, "hud", x + 16f + 8f, y + h / 2f - 9f, 18f, tk.accent, Icons.FILL_ON, 0);
        g.text(name, g.fit(name, title, textW), x + 62f, y + 23f, 0xFFFFFFFF);
        g.text(small, g.fit(small, line, textW), x + 62f, y + 40f, 0x99FFFFFF);
        float r = x + w - 12f;
        r -= ui.button(tk, r, y + h / 2f, done, true, this::onClose) + 6f;
        if (reset != null) {
            r -= ui.button(tk, r, y + h / 2f, reset, false, () -> {
                VanillaHud.reset();
                Modules.save();
            }) + 6f;
        }
        ui.button(tk, r, y + h / 2f, modules, showModules, () -> showModules = !showModules);
    }

    /**
     * Under the island, while Modules is on (and nothing is being dragged): every HUD module as a chip, lit when on;
     * clicking one switches it. Up there it never covers the hotbar.
     */
    private void chips(Gfx g, Tokens tk) {
        List<HudModule> all = Modules.hud();
        float w = 12f;
        for (HudModule m : all) {
            w += ui.chipWidth(m.name()) + 6f;
        }
        w += 6f;
        float x = 640f - w / 2f, y = 82f, h = 38f;
        io.github.georgwav.cosmetics.sol.ui.kit.Pix.recess(g, io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens.get(), x, y, w, h);
        ui.area(x, y, w, h, () -> { });
        float cx = x + 12f;
        for (HudModule m : all) {
            cx += ui.chip(tk, cx, y + h / 2f, m.name(), m.enabled(), () -> m.setEnabled(!m.enabled())) + 6f;
        }
    }

    // ------------------------------------------------------------------ input (design pixels in, GUI pixels for boxes)

    @Override
    protected boolean press(float x, float y, int button) {
        if (ui.press(x, y)) {
            return true;
        }
        float gx = guiX(x), gy = guiY(y);
        Box box = boxAt(gx, gy);
        if (box == null) {
            return false;
        }
        if (button == 1) {
            if (box instanceof ModuleBox mb) {
                SolMenu menu = SolMenu.create(this, "modules", false);
                menu.openDrawer(mb.m());
                ScreenOpener.open(menu);
            } else if (box instanceof PartBox pb) {
                // A part of the game's HUD goes back where the game puts it.
                VanillaHud.move(pb.part(), 0f, 0f);
                Modules.save();
            }
            return true;
        }
        if (button == 0) {
            HudSnap.Rect r = box.rect();
            dragging = box;
            selected = box;
            grabX = gx - r.x();
            grabY = gy - r.y();
            return true;
        }
        return false;
    }

    @Override
    protected boolean drag(float x, float y, int button) {
        if (ui.dragTo(x, y)) {
            return true;
        }
        if (dragging == null) {
            return false;
        }
        HudSnap.Rect r = dragging.rect();
        List<HudSnap.Rect> others = new ArrayList<>();
        for (Box b : boxes()) {
            if (!b.same(dragging)) {
                others.add(b.rect());
            }
        }
        HudSnap.Result to = HudSnap.place(guiX(x) - grabX, guiY(y) - grabY, r.w(), r.h(), width, height, others, shiftHeld());
        guideX = to.guideX();
        guideY = to.guideY();
        dragging.moveTo(to.x(), to.y());
        return true;
    }

    @Override
    protected boolean release(float x, float y, int button) {
        if (dragging == null) {
            return ui.release();
        }
        dragging = null;
        guideX = Float.NaN;
        guideY = Float.NaN;
        Modules.save();
        return true;
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        if (!(boxAt(guiX(x), guiY(y)) instanceof ModuleBox box) || amount == 0f) {
            return false;
        }
        // Resized about its middle.
        HudSnap.Rect before = box.rect();
        float cx = before.x() + before.w() / 2f, cy = before.y() + before.h() / 2f;
        HudModule m = box.m();
        m.setScale(Math.max(0.5, Math.min(2.0, m.scale() + (amount > 0 ? 0.05 : -0.05))));
        HudSnap.Rect after = box.rect();
        box.moveTo(cx - after.w() / 2f, cy - after.h() / 2f);
        return true;
    }

    /** The arrow keys nudge the box last clicked: one pixel, ten with Shift. */
    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        Box box = selected;
        if (box == null || dragging != null || boxes().stream().noneMatch(b -> b.same(box))) {
            return false;
        }
        int dx = key == InputConstants.KEY_LEFT ? -1 : key == InputConstants.KEY_RIGHT ? 1 : 0;
        int dy = key == InputConstants.KEY_UP ? -1 : key == InputConstants.KEY_DOWN ? 1 : 0;
        if (dx == 0 && dy == 0) {
            return false;
        }
        HudSnap.Rect to = HudSnap.nudge(box.rect(), dx, dy, shift, width, height);
        box.moveTo(to.x(), to.y());
        Modules.save();
        return true;
    }

    /**
     * Whether Shift is held: asked of the game on 26.3 (which no longer ships GLFW), else of the game's window through
     * GLFW (the current context on the render thread, where input arrives).
     */
    private static boolean shiftHeld() {
        //? if >=26.3 {
        /*return Minecraft.getInstance().hasShiftDown();
        *///?} else {
        long window = GLFW.glfwGetCurrentContext();
        return window != 0L && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
        //?}
    }
}
