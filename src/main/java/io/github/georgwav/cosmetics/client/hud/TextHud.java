package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

import java.util.List;

/** A HUD module that shows a few lines of text. */
public abstract class TextHud extends HudModule {
    private static final int PAD = 3;

    protected TextHud(String id, String icon, float anchorX, float anchorY, float offsetX, float offsetY) {
        super(id, icon, anchorX, anchorY, offsetX, offsetY);
    }

    /** What it shows now, one entry per line. */
    protected abstract List<String> lines(Minecraft mc);

    @Override
    public int width(Minecraft mc) {
        int w = 0;
        for (String line : lines(mc)) {
            w = Math.max(w, mc.font.width(line));
        }
        return w + PAD * 2;
    }

    @Override
    public int height(Minecraft mc) {
        return lines(mc).size() * 10 + PAD * 2 - 1;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int y = PAD;
        for (String line : lines(mc)) {
            g.text(mc.font, line, PAD, y, 0xFFFFFFFF);
            y += 10;
        }
    }
}
