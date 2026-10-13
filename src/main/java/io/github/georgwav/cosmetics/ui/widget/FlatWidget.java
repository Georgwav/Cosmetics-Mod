package io.github.georgwav.cosmetics.ui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
//? if >=1.21.9 {
import net.minecraft.client.input.MouseButtonEvent;
//?}

/**
 * Base for the settings screen's flat widgets. All version-specific widget plumbing (the click and drag
 * signatures changed in 1.21.9) lives here, so subclasses only implement {@link #draw},
 * {@link #pressed} and {@link #dragged}.
 *
 * <p>Widgets can be clipped to a viewport (the scrolling list): outside it they neither highlight nor
 * react to the mouse.
 */
public abstract class FlatWidget extends AbstractWidget {
    private int clipTop = Integer.MIN_VALUE;
    private int clipBottom = Integer.MAX_VALUE;

    protected FlatWidget(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    /** Draws the widget. {@code hovered} already accounts for the clip area. */
    protected abstract void draw(GuiGraphics g, Font font, int mouseX, int mouseY, boolean hovered);

    /** Left click inside the widget. */
    protected void pressed(double mouseX, double mouseY) {
    }

    /** Mouse moved while the left button is held after pressing this widget. */
    protected void dragged(double mouseX, double mouseY) {
    }

    public void setClip(int top, int bottom) {
        this.clipTop = top;
        this.clipBottom = bottom;
    }

    protected boolean inClip(double mouseY) {
        return mouseY >= clipTop && mouseY < clipBottom;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return super.isMouseOver(mouseX, mouseY) && inClip(mouseY);
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        boolean hovered = active && mouseX >= getX() && mouseX < getX() + getWidth()
                && mouseY >= getY() && mouseY < getY() + getHeight() && inClip(mouseY);
        draw(g, Minecraft.getInstance().font, mouseX, mouseY, hovered);
    }

    //? if >=1.21.9 {
    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (inClip(event.y())) {
            pressed(event.x(), event.y());
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dragX, double dragY) {
        dragged(event.x(), event.y());
    }
    //?} else {
    /*@Override
    public void onClick(double mouseX, double mouseY) {
        if (inClip(mouseY)) {
            pressed(mouseX, mouseY);
        }
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        dragged(mouseX, mouseY);
    }
    *///?}

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    // ------------------------------------------------------------------ drawing helpers

    protected static void text(GuiGraphics g, Font font, String text, int x, int y, int color) {
        g.drawString(font, text, x, y, color, false);
    }

    protected static void textRight(GuiGraphics g, Font font, String text, int right, int y, int color) {
        g.drawString(font, text, right - font.width(text), y, color, false);
    }

    protected static void rect(GuiGraphics g, int x, int y, int width, int height, int color) {
        g.fill(x, y, x + width, y + height, color);
    }

    /** Shortens text with an ellipsis so it fits in {@code maxWidth} pixels. */
    protected static String fit(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
