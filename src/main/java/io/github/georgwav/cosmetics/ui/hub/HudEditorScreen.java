package io.github.georgwav.cosmetics.ui.hub;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.hud.Hud;
import io.github.georgwav.cosmetics.client.hud.HudModule;
import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
//? if <26.3 {
import org.lwjgl.glfw.GLFW;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD editor, over the game as it is: the HUD modules that are on and the game's own HUD parts, where they sit.
 * Drag one to move it: it snaps to the screen's edges and middle, lines up with the other boxes' edges and middles
 * and sits next to them with a small gap (hold Shift to move it freely). The arrow keys nudge the box last clicked
 * (Shift: ten pixels). Scroll over a module to resize it, right-click it for its settings. Modules in the island
 * opens the list that switches modules on and off.
 */
public final class HudEditorScreen extends Screen {
    private static final float DESIGN_W = 1440f;
    private static final float DESIGN_H = 810f;
    private static final float CX = 720f;
    /** How close (GUI pixels) an edge comes to a guide before it snaps to it. */
    private static final float SNAP = 4f;
    /** The gap kept from the screen's edges, and between boxes put next to each other, when snapping. */
    private static final float MARGIN = 2f;

    /** Frames drawn (the in-game check). */
    public static int framesRendered;

    private final Screen parent;
    private Box dragging;
    /** The box last clicked: the arrow keys nudge it. */
    private Box selected;
    /** Whether the module list under the island is open: at first only when no module is on yet. */
    private boolean showModules = Modules.hud().stream().noneMatch(HudModule::enabled);
    private float grabX;
    private float grabY;
    // The guides a dragged module snapped to, GUI pixels, or NaN.
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;

    // This frame's layout of the island and bar: design pixel -> screen pixel, and what can be clicked.
    private float gs = 1f;
    private float u = 1f;
    private float ox;
    private float oy;
    private final List<Ui.Hit> hits = new ArrayList<>();

    public HudEditorScreen(Screen parent) {
        super(Component.translatable("cosmeticsmod.hud.title"));
        this.parent = parent;
    }

    private static String t(String key) {
        return Component.translatable("cosmeticsmod.hud." + key).getString();
    }

    @Override
    protected void init() {
        Hud.editing = true;
    }

    @Override
    public void removed() {
        Hud.editing = false;
        Modules.save();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ScreenOpener.open(parent);
    }

    // ------------------------------------------------------------------ what can be moved

    /** Something on the HUD the editor moves: one of the client's modules, or a part of the game's own HUD. */
    private interface Box {
        float x();

        float y();

        float w();

        float h();

        void moveTo(float x, float y);
    }

    private final class ModuleBox implements Box {
        final HudModule m;

        ModuleBox(HudModule m) {
            this.m = m;
        }

        public float x() {
            return m.x(width, Minecraft.getInstance());
        }

        public float y() {
            return m.y(height, Minecraft.getInstance());
        }

        public float w() {
            return m.width(Minecraft.getInstance()) * m.scale();
        }

        public float h() {
            return m.height(Minecraft.getInstance()) * m.scale();
        }

        public void moveTo(float x, float y) {
            m.moveTo(x, y, width, height, Minecraft.getInstance());
        }
    }

    private final class PartBox implements Box {
        final VanillaHud.Part part;

        PartBox(VanillaHud.Part part) {
            this.part = part;
        }

        public float x() {
            return part.box(width, height)[0] + VanillaHud.dx(part);
        }

        public float y() {
            return part.box(width, height)[1] + VanillaHud.dy(part);
        }

        public float w() {
            return part.box(width, height)[2];
        }

        public float h() {
            return part.box(width, height)[3];
        }

        public void moveTo(float x, float y) {
            float[] home = part.box(width, height);
            VanillaHud.move(part, x - home[0], y - home[1]);
        }
    }

    /** What can be moved now, bottom to top: the game's HUD parts (when this version allows), then the modules on. */
    private List<Box> boxes() {
        List<Box> boxes = new ArrayList<>();
        if (VanillaHud.supported()) {
            for (VanillaHud.Part part : VanillaHud.Part.values()) {
                boxes.add(new PartBox(part));
            }
        }
        for (HudModule m : Modules.hud()) {
            if (m.enabled()) {
                boxes.add(new ModuleBox(m));
            }
        }
        return boxes;
    }

    /** The box on top under (x, y) in GUI pixels, or null. */
    private Box boxAt(double x, double y) {
        List<Box> all = boxes();
        for (int i = all.size() - 1; i >= 0; i--) {
            Box b = all.get(i);
            if (x >= b.x() && x < b.x() + b.w() && y >= b.y() && y < b.y() + b.h()) {
                return b;
            }
        }
        return null;
    }

    private static boolean same(Box a, Box b) {
        if (a instanceof ModuleBox ma && b instanceof ModuleBox mb) {
            return ma.m == mb.m;
        }
        return a instanceof PartBox pa && b instanceof PartBox pb && pa.part == pb.part;
    }

    /**
     * Moves {@code start} (the box's left or top edge, {@code size} long) so that its start, middle or end lines up
     * with the nearest of the targets, when one is within {@link #SNAP}; returns the new start and the target in [1]
     * (NaN when nothing is near).
     */
    private static float[] snap(float start, float size, List<Float> targets) {
        float best = Float.NaN;
        float shift = 0f;
        float[] points = {start, start + size / 2f, start + size};
        for (float target : targets) {
            for (float point : points) {
                float d = target - point;
                if (Math.abs(d) <= SNAP && (Float.isNaN(best) || Math.abs(d) < Math.abs(shift))) {
                    best = target;
                    shift = d;
                }
            }
        }
        return new float[]{start + shift, best};
    }

    private void moveDragged(double mouseX, double mouseY) {
        Box box = dragging;
        float w = box.w();
        float h = box.h();
        List<Float> xs = new ArrayList<>(List.of(MARGIN, width / 2f, width - MARGIN));
        List<Float> ys = new ArrayList<>(List.of(MARGIN, height / 2f, height - MARGIN));
        for (Box other : boxes()) {
            if (!same(other, box)) {
                // Its edges and middle, and just outside its edges (to sit next to it).
                xs.addAll(List.of(other.x(), other.x() + other.w() / 2f, other.x() + other.w(),
                        other.x() - MARGIN, other.x() + other.w() + MARGIN));
                ys.addAll(List.of(other.y(), other.y() + other.h() / 2f, other.y() + other.h(),
                        other.y() - MARGIN, other.y() + other.h() + MARGIN));
            }
        }
        boolean free = shiftHeld();
        float[] x = free ? new float[]{(float) mouseX - grabX, Float.NaN} : snap((float) mouseX - grabX, w, xs);
        float[] y = free ? new float[]{(float) mouseY - grabY, Float.NaN} : snap((float) mouseY - grabY, h, ys);
        guideX = x[1];
        guideY = y[1];
        box.moveTo(Math.max(0f, Math.min(width - w, x[0])), Math.max(0f, Math.min(height - h, y[0])));
    }

    // ------------------------------------------------------------------ input

    private float designX(double guiX) {
        return ((float) guiX * gs - ox) / u;
    }

    private float designY(double guiY) {
        return ((float) guiY * gs - oy) / u;
    }

    private boolean press(double mouseX, double mouseY, int button) {
        float dx = designX(mouseX);
        float dy = designY(mouseY);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = hits.get(i);
            if (hit.contains(dx, dy)) {
                if (button == 0 && hit.action() != null) {
                    hit.action().run();
                }
                return true;
            }
        }
        Box box = boxAt(mouseX, mouseY);
        if (box == null) {
            return false;
        }
        if (button == 1) {
            if (box instanceof ModuleBox mb) {
                ScreenOpener.open(HubScreen.moduleSettings(mb.m.id));
            } else if (box instanceof PartBox pb) {
                // A part of the game's HUD goes back where the game puts it.
                VanillaHud.move(pb.part, 0f, 0f);
                Modules.save();
            }
            return true;
        }
        if (button == 0) {
            dragging = box;
            selected = box;
            grabX = (float) mouseX - box.x();
            grabY = (float) mouseY - box.y();
            return true;
        }
        return false;
    }

    private boolean drag(double mouseX, double mouseY) {
        if (dragging == null) {
            return false;
        }
        moveDragged(mouseX, mouseY);
        return true;
    }

    private boolean release() {
        if (dragging == null) {
            return false;
        }
        dragging = null;
        guideX = Float.NaN;
        guideY = Float.NaN;
        Modules.save();
        return true;
    }

    /**
     * Whether Shift is held, asked of the game's window through GLFW (the current context on the render thread, where
     * input arrives). 26.3 no longer ships GLFW: there Shift isn't read yet.
     */
    private static boolean shiftHeld() {
        //? if >=26.3 {
        /*return false;
        *///?} else {
        long window = GLFW.glfwGetCurrentContext();
        return window != 0L && (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS);
        //?}
    }

    /** The arrow keys nudge the box last clicked: one pixel, ten with Shift. */
    private boolean key(int key) {
        Box box = selected;
        if (box == null || dragging != null || boxes().stream().noneMatch(b -> same(b, box))) {
            return false;
        }
        float step = shiftHeld() ? 10f : 1f;
        float dx = key == InputConstants.KEY_LEFT ? -step : key == InputConstants.KEY_RIGHT ? step : 0f;
        float dy = key == InputConstants.KEY_UP ? -step : key == InputConstants.KEY_DOWN ? step : 0f;
        if (dx == 0f && dy == 0f) {
            return false;
        }
        box.moveTo(Math.max(0f, Math.min(width - box.w(), box.x() + dx)),
                Math.max(0f, Math.min(height - box.h(), box.y() + dy)));
        Modules.save();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!(boxAt(mouseX, mouseY) instanceof ModuleBox box) || scrollY == 0) {
            return false;
        }
        // Resized about its middle.
        HudModule m = box.m;
        float cx = box.x() + box.w() / 2f;
        float cy = box.y() + box.h() / 2f;
        m.setScale(Math.max(0.5, Math.min(2.0, m.scale() + (scrollY > 0 ? 0.05 : -0.05))));
        box.moveTo(cx - box.w() / 2f, cy - box.h() / 2f);
        return true;
    }

    // Click and drag signatures changed in 1.21.9.
    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return press(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return drag(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return release() || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return key(event.key()) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return press(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return drag(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return release() || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return key(keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}

    // ------------------------------------------------------------------ drawing

    // No menu background: the HUD is edited over the game as it is. 26.1 renamed the draw entry points.
    //? if >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(g), mouseX, mouseY);
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(g), mouseX, mouseY);
    }
    //?}

    private void draw(Canvas g, int mouseX, int mouseY) {
        framesRendered++;
        Minecraft mc = Minecraft.getInstance();
        g.fill(0, 0, width, height, 0x38000000);
        // The middle of the screen, faintly; a guide line where the dragged module snapped.
        g.fill(width / 2, 0, width / 2 + 1, height, 0x18FFFFFF);
        g.fill(0, height / 2, width, height / 2 + 1, 0x18FFFFFF);
        Box hot = dragging != null ? dragging : boxAt(mouseX, mouseY);
        for (Box box : boxes()) {
            if (box instanceof ModuleBox mb) {
                Hud.drawModule(g, mb.m, width, height);
            }
            int x0 = Math.round(box.x());
            int y0 = Math.round(box.y());
            int x1 = Math.round(box.x() + box.w());
            int y1 = Math.round(box.y() + box.h());
            boolean lit = hot != null && same(box, hot) || selected != null && same(box, selected);
            if (box instanceof PartBox pb) {
                // The game's own parts: a blue frame and their name, the game draws them (moved) underneath.
                g.fill(x0, y0, x1, y1, lit ? 0x2870A8FF : 0x1470A8FF);
                g.outline(x0 - 1, y0 - 1, x1 + 1, y1 + 1, lit ? 0xE090C8FF : 0x6090C8FF);
                if (lit) {
                    String label = t("part." + pb.part.id);
                    g.push();
                    g.translate(x0 + 1, y0 - 6);
                    g.scale(0.5f, 0.5f);
                    g.text(font, label, 0, 0, 0xFFB8DCFF, true);
                    g.pop();
                }
            } else {
                g.outline(x0 - 1, y0 - 1, x1 + 1, y1 + 1, lit ? 0xE0FFFFFF : 0x50FFFFFF);
            }
        }
        if (!Float.isNaN(guideX)) {
            int x = Math.round(guideX);
            g.fill(x, 0, x + 1, height, 0xB0FFFFFF);
        }
        if (!Float.isNaN(guideY)) {
            int y = Math.round(guideY);
            g.fill(0, y, width, y + 1, 0xB0FFFFFF);
        }

        // The island and the bar, in screen pixels like the hub.
        gs = (float) mc.getWindow().getGuiScale();
        float screenW = width * gs;
        float screenH = height * gs;
        u = Math.min(screenW / DESIGN_W, screenH / DESIGN_H);
        ox = (screenW - DESIGN_W * u) / 2f;
        oy = (screenH - DESIGN_H * u) / 2f;
        hits.clear();
        g.nextLayer();
        g.push();
        g.scale(1f / gs, 1f / gs);
        Ui ui = new Ui(g, font, u, ox, oy, gs, designX(mouseX + 0.5), designY(mouseY + 0.5), HubScreen.ACCENT,
                HubScreen.INK, hits, Ui.newEasing());
        drawIsland(ui);
        drawBar(ui);
        g.pop();
    }

    /** At the top: what this is, how it works, and Done. */
    private void drawIsland(Ui ui) {
        String name = t("title");
        boolean any = Modules.hud().stream().anyMatch(HudModule::enabled);
        String line = any ? t("hint") : t("empty");
        String done = Component.translatable("cosmeticsmod.hub.done").getString();
        String reset = VanillaHud.moved() ? t("reset") : null;
        String modules = t("modules");
        float bw = ui.buttonWidth(done) + ui.buttonWidth(modules) + 8f + (reset != null ? ui.buttonWidth(reset) + 8f : 0f);
        float textW = Math.max(ui.textWidth(name, Ui.NAME, true), ui.textWidth(line, Ui.SMALL, false));
        float w = Math.max(440f, Math.min(1000f, 62f + textW + 12f + bw + 18f));
        float left = CX - w / 2f;
        float top = 20f;
        ui.roundRect(left - 4f, top + 10f, w + 8f, 68f, 34f, 0x30000000);
        ui.roundRect(left, top, w, 60f, 30f, 0xFF000000);
        ui.circle(left + 30f, top + 30f, 20f, Ui.WELL);
        ui.icon("layout", left + 30f, top + 30f, 20f, false);
        float right = ui.buttonRight(done, left + w - 8f, top + 8f, 44f, false, this::onClose) - 12f;
        if (reset != null) {
            right = ui.buttonRight(reset, right + 4f, top + 8f, 44f, false, () -> {
                VanillaHud.reset();
                Modules.save();
            }) - 12f;
        }
        right = ui.buttonRight(modules, right + 4f, top + 8f, 44f, showModules, () -> showModules = !showModules) - 12f;
        float room = right - (left + 62f);
        ui.text(ui.fit(name, room, Ui.NAME, true), left + 62f, top + 17f, Ui.TEXT, Ui.NAME, true);
        ui.text(ui.fit(line, room, Ui.SMALL, false), left + 62f, top + 34f, Ui.TEXT_DIM, Ui.SMALL, false);
    }

    /**
     * Under the island, while Modules is on (and nothing is being dragged): every HUD module, lit when on; clicking
     * one switches it. Up there it never covers the hotbar.
     */
    private void drawBar(Ui ui) {
        if (!showModules || dragging != null) {
            return;
        }
        List<HudModule> all = Modules.hud();
        float w = 4f;
        for (HudModule m : all) {
            w += ui.buttonWidth(m.name()) + 6f;
        }
        w = w - 6f + 4f;
        float left = CX - w / 2f;
        float top = 94f;
        ui.roundRect(left - 4f, top + 8f, w + 8f, 60f, 30f, 0x30000000);
        ui.roundRect(left, top, w, 52f, 26f, 0xFF000000);
        float x = left + 4f;
        for (HudModule m : all) {
            x += ui.button(m.name(), x, top + 4f, 44f, m.enabled(), () -> m.setEnabled(!m.enabled())) + 6f;
        }
    }
}
