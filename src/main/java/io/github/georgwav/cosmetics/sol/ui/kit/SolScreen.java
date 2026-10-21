package io.github.georgwav.cosmetics.sol.ui.kit;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.util.Spring;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.9 {
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}

/**
 * The base of every Sol screen: one place for each version's draw, background and input methods, so a screen is
 * written once in the prototypes' 1280 x 720 design space.
 *
 * <ul>
 *   <li><b>Drawing:</b> the screen draws through {@link Gfx} in screen pixels. Shapes are smooth and text is sharp
 *   at every GUI scale.</li>
 *   <li><b>Background:</b> never a blur and never the game's dark menu wash: the pixel UI dims the world with flat
 *   black at 24% (screens that show the world as it is, such as the HUD editor, don't).</li>
 *   <li><b>Input:</b> arrives in design pixels, with Ctrl and Shift.</li>
 *   <li><b>Errors:</b> a screen that throws while drawing is closed (Guard switches the Sol UI off and says so
 *   once), never the game.</li>
 * </ul>
 */
public abstract class SolScreen extends Screen {
    protected final Screen parent;
    /** The guarded feature this screen belongs to (the quick menu, the full menu, ...). */
    protected final Feature feature;
    protected final Design d = new Design();
    protected final Gfx g = new Gfx(d);
    /** Seconds since the screen opened, and the last frame's length (0 on the first frame and with calm motion). */
    protected float time;
    protected float dt;
    /** The pointer in design pixels. */
    protected float mx = -1000f;
    protected float my = -1000f;
    private long lastNanos;
    /** For the dev scenario's shots: the pointer is ignored, so a staged state isn't undone by where the cursor sits. */
    public static boolean pointerOff;

    protected SolScreen(Component title, Screen parent, Feature feature) {
        super(title);
        this.parent = parent;
        this.feature = feature;
    }

    // ------------------------------------------------------------------ what a screen implements

    /** Draws one frame. {@link #d} already fits the window. */
    protected abstract void frame(Gfx g);

    protected boolean press(float x, float y, int button) {
        return false;
    }

    protected boolean drag(float x, float y, int button) {
        return false;
    }

    protected boolean release(float x, float y, int button) {
        return false;
    }

    /** {@code amount} > 0 is scrolling up (or left). */
    protected boolean scroll(float x, float y, float amount) {
        return false;
    }

    protected void moved(float x, float y) {
    }

    /** A key (GLFW key codes, as {@code InputConstants.KEY_*}). Esc is handled by {@link #back()} unless this takes it. */
    protected boolean key(int key, boolean ctrl, boolean shift) {
        return false;
    }

    /** A typed character, for type-to-search. */
    protected boolean typed(String text) {
        return false;
    }

    /** Esc: steps back inside the screen first; when there's nothing to step back from, closes it. */
    protected boolean back() {
        return false;
    }

    /**
     * After the frame, in the game's GUI pixels and drawing tools: for what only the game draws (the 3D player
     * preview). Drawn over the frame.
     */
    protected void game(Canvas canvas, int mouseX, int mouseY) {
    }

    /**
     * Whether the world behind is dimmed with flat black at 24% (Settings › "Dim the world behind menus"; the HUD
     * editor shows the game as it is).
     */
    protected boolean blurs() {
        return io.github.georgwav.cosmetics.sol.config.SolConfig.get().menuBlur;
    }

    /** Sol's menus never blur the world (the pixel UI's rule); counted for the checks. */
    public static int blursRun;

    /** Called when the screen is closed or replaced; free textures here. */
    protected void closed() {
    }

    // ------------------------------------------------------------------ the shared parts

    public static boolean calm() {
        return Spring.reducedMotion;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ScreenOpener.open(parent);
    }

    @Override
    public void removed() {
        closed();
        super.removed();
    }

    private void draw(Canvas canvas, int mouseX, int mouseY) {
        long now = System.nanoTime();
        dt = lastNanos == 0L ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        time += dt;
        double gs = minecraft.getWindow().getGuiScale();
        d.update(width, height, gs);
        mx = pointerOff ? -1000f : d.mouseX(mouseX);
        my = pointerOff ? -1000f : d.mouseY(mouseY);
        canvas.push();
        canvas.scale((float) (1.0 / gs), (float) (1.0 / gs));
        g.begin(canvas);
        if (minecraft.level == null) {
            // Before any world (the title screen at launch): the theme's colours from the first frame, and a theme
            // change animating every frame.
            io.github.georgwav.cosmetics.sol.theme.ThemeState.update(Float.NaN);
        }
        if (blurs() && minecraft.level != null) {
            g.rectScreen(0f, 0f, d.screenW, d.screenH, 0x3D000000, 0x3D000000);
        }
        boolean ok = Guard.on(feature) && Guard.run(feature, () -> frame(g));
        g.end();
        canvas.pop();
        if (ok) {
            ok = Guard.run(feature, () -> game(canvas, mouseX, mouseY));
        }
        if (!ok) {
            // The screen's feature switched itself off: leave for the screen underneath rather than draw half a menu.
            minecraft.execute(this::onClose);
        }
    }

    private boolean onKey(int key, boolean ctrl, boolean shift) {
        if (key(key, ctrl, shift)) {
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE) {
            if (!back()) {
                onClose();
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return scroll(d.mouseX(mouseX), d.mouseY(mouseY), (float) (scrollY != 0 ? scrollY : scrollX));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (!pointerOff) {
            moved(d.mouseX(mouseX), d.mouseY(mouseY));
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    // Click, drag, key and character signatures changed in 1.21.9; 26.3's characters carry no modifiers.
    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (SolOverlay.click(event.x(), event.y())) {
            return true;
        }
        return press(d.mouseX(event.x()), d.mouseY(event.y()), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return drag(d.mouseX(event.x()), d.mouseY(event.y()), event.button()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return release(d.mouseX(event.x()), d.mouseY(event.y()), event.button()) || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return onKey(event.key(), event.hasControlDown(), event.hasShiftDown()) || super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return typed(event.codepointAsString()) || super.charTyped(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (SolOverlay.click(mouseX, mouseY)) {
            return true;
        }
        return press(d.mouseX(mouseX), d.mouseY(mouseY), button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return drag(d.mouseX(mouseX), d.mouseY(mouseY), button) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return release(d.mouseX(mouseX), d.mouseY(mouseY), button) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return onKey(keyCode, Screen.hasControlDown(), Screen.hasShiftDown()) || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return typed(String.valueOf(c)) || super.charTyped(c, modifiers);
    }
    *///?}

    // The game's draw entry points (26.1 renamed them) and the background: the blur, never the dark menu wash.
    // From 1.21.6 the game draws the background before the screen; before that the screen draws it, once.
    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(graphics), mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            extractPanorama(graphics, partialTick);
        }
    }
    *///?} elif >=1.21.6 {
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(graphics), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            renderPanorama(graphics, partialTick);
        }
    }
    //?} elif >=1.21.2 {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        draw(new Canvas(graphics), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            renderPanorama(graphics, partialTick);
        }
    }
    *///?} else {
    /*@Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        draw(new Canvas(graphics), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (minecraft.level == null) {
            renderPanorama(graphics, partialTick);
        }
    }
    *///?}

    /** The keyboard key as the game names keys, for hints ("Right Shift"). */
    protected static String keyName(net.minecraft.client.KeyMapping mapping) {
        return KeyBinds.name(mapping);
    }
}
