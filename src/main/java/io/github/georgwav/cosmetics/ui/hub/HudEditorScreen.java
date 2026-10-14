package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.hud.Hud;
import io.github.georgwav.cosmetics.client.hud.HudModule;
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
import net.minecraft.client.input.MouseButtonEvent;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD editor, over the game as it is: the HUD modules that are on, where they sit. Drag one to move it (it
 * snaps to the screen's edges and middle and to the other modules), scroll over one to resize it, right-click it
 * for its settings. The bar at the bottom switches modules on and off.
 */
public final class HudEditorScreen extends Screen {
    private static final float DESIGN_W = 1440f;
    private static final float DESIGN_H = 810f;
    private static final float CX = 720f;
    /** How close (GUI pixels) an edge comes to a guide before it snaps to it. */
    private static final float SNAP = 4f;
    /** The gap kept from the screen's edges when snapping to them. */
    private static final float MARGIN = 2f;

    /** Frames drawn (the in-game check). */
    public static int framesRendered;

    private final Screen parent;
    private HudModule dragging;
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

    // ------------------------------------------------------------------ modules

    private float left(HudModule m) {
        return m.x(width, Minecraft.getInstance());
    }

    private float top(HudModule m) {
        return m.y(height, Minecraft.getInstance());
    }

    private float w(HudModule m) {
        return m.width(Minecraft.getInstance()) * m.scale();
    }

    private float h(HudModule m) {
        return m.height(Minecraft.getInstance()) * m.scale();
    }

    /** The module on top under (x, y) in GUI pixels, or null. */
    private HudModule moduleAt(double x, double y) {
        List<HudModule> all = Modules.hud();
        for (int i = all.size() - 1; i >= 0; i--) {
            HudModule m = all.get(i);
            if (m.enabled() && x >= left(m) && x < left(m) + w(m) && y >= top(m) && y < top(m) + h(m)) {
                return m;
            }
        }
        return null;
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
        HudModule m = dragging;
        float w = w(m);
        float h = h(m);
        List<Float> xs = new ArrayList<>(List.of(MARGIN, width / 2f, width - MARGIN));
        List<Float> ys = new ArrayList<>(List.of(MARGIN, height / 2f, height - MARGIN));
        for (HudModule other : Modules.hud()) {
            if (other != m && other.enabled()) {
                xs.addAll(List.of(left(other), left(other) + w(other) / 2f, left(other) + w(other)));
                ys.addAll(List.of(top(other), top(other) + h(other) / 2f, top(other) + h(other)));
            }
        }
        float[] x = snap((float) mouseX - grabX, w, xs);
        float[] y = snap((float) mouseY - grabY, h, ys);
        guideX = x[1];
        guideY = y[1];
        float nx = Math.max(0f, Math.min(width - w, x[0]));
        float ny = Math.max(0f, Math.min(height - h, y[0]));
        m.moveTo(nx, ny, width, height, Minecraft.getInstance());
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
        HudModule m = moduleAt(mouseX, mouseY);
        if (m == null) {
            return false;
        }
        if (button == 1) {
            ScreenOpener.open(HubScreen.moduleSettings(m.id));
            return true;
        }
        if (button == 0) {
            dragging = m;
            grabX = (float) mouseX - left(m);
            grabY = (float) mouseY - top(m);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        HudModule m = moduleAt(mouseX, mouseY);
        if (m == null || scrollY == 0) {
            return false;
        }
        // Resized about its middle.
        float cx = left(m) + w(m) / 2f;
        float cy = top(m) + h(m) / 2f;
        m.setScale(Math.max(0.5, Math.min(2.0, m.scale() + (scrollY > 0 ? 0.05 : -0.05))));
        m.moveTo(cx - w(m) / 2f, cy - h(m) / 2f, width, height, Minecraft.getInstance());
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
        HudModule hot = dragging != null ? dragging : moduleAt(mouseX, mouseY);
        for (HudModule m : Modules.hud()) {
            if (!m.enabled()) {
                continue;
            }
            Hud.drawModule(g, m, width, height);
            int x0 = Math.round(left(m));
            int y0 = Math.round(top(m));
            int x1 = Math.round(left(m) + w(m));
            int y1 = Math.round(top(m) + h(m));
            g.outline(x0 - 1, y0 - 1, x1 + 1, y1 + 1, m == hot ? 0xE0FFFFFF : 0x50FFFFFF);
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
        float bw = ui.buttonWidth(done);
        float textW = Math.max(ui.textWidth(name, Ui.NAME, true), ui.textWidth(line, Ui.SMALL, false));
        float w = Math.max(440f, Math.min(760f, 62f + textW + 12f + bw + 18f));
        float left = CX - w / 2f;
        float top = 20f;
        ui.roundRect(left - 4f, top + 10f, w + 8f, 68f, 34f, 0x30000000);
        ui.roundRect(left, top, w, 60f, 30f, 0xFF000000);
        ui.circle(left + 30f, top + 30f, 20f, Ui.WELL);
        ui.icon("layout", left + 30f, top + 30f, 20f, false);
        float right = ui.buttonRight(done, left + w - 8f, top + 8f, 44f, false, this::onClose) - 12f;
        float room = right - (left + 62f);
        ui.text(ui.fit(name, room, Ui.NAME, true), left + 62f, top + 17f, Ui.TEXT, Ui.NAME, true);
        ui.text(ui.fit(line, room, Ui.SMALL, false), left + 62f, top + 34f, Ui.TEXT_DIM, Ui.SMALL, false);
    }

    /** At the bottom: every HUD module, lit when on; clicking one switches it. */
    private void drawBar(Ui ui) {
        List<HudModule> all = Modules.hud();
        float w = 4f;
        for (HudModule m : all) {
            w += ui.buttonWidth(m.name()) + 6f;
        }
        w = w - 6f + 4f;
        float left = CX - w / 2f;
        float top = 744f;
        ui.roundRect(left - 4f, top + 8f, w + 8f, 60f, 30f, 0x30000000);
        ui.roundRect(left, top, w, 52f, 26f, 0xFF000000);
        float x = left + 4f;
        for (HudModule m : all) {
            x += ui.button(m.name(), x, top + 4f, 44f, m.enabled(), () -> m.setEnabled(!m.enabled())) + 6f;
        }
    }
}
