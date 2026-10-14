package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

import java.util.List;

/** A HUD module that shows a few lines of text. */
public abstract class TextHud extends HudModule {
    private static final int PAD = 3;
    protected final Setting.Toggle shadow = add(new Setting.Toggle("shadow", false));

    protected TextHud(String id, String icon, float anchorX, float anchorY, float offsetX, float offsetY) {
        super(id, icon, anchorX, anchorY, offsetX, offsetY);
    }

    /** What it shows now, one entry per line; none when there's nothing to show. */
    protected abstract List<String> lines(Minecraft mc);

    /** What it shows in the HUD editor when it would show nothing. */
    protected List<String> sample(Minecraft mc) {
        return List.of(name());
    }

    private List<String> shown(Minecraft mc) {
        List<String> lines = lines(mc);
        return lines.isEmpty() && preview() ? sample(mc) : lines;
    }

    @Override
    public int width(Minecraft mc) {
        List<String> lines = shown(mc);
        if (lines.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, mc.font.width(line));
        }
        return w + PAD * 2;
    }

    @Override
    public int height(Minecraft mc) {
        List<String> lines = shown(mc);
        return lines.isEmpty() ? 0 : lines.size() * 10 + PAD * 2 - 1;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int y = PAD;
        for (String line : shown(mc)) {
            g.text(mc.font, line, PAD, y, 0xFFFFFFFF, shadow.get());
            y += 10;
        }
    }
}
