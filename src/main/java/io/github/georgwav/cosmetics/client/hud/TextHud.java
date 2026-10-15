package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.GeistFont;
import net.minecraft.client.Minecraft;

import java.util.List;

/** A HUD module that shows a few lines of text. */
public abstract class TextHud extends HudModule {
    private static final int PAD = 3;
    protected final Setting.Toggle shadow = add(new Setting.Toggle("shadow", false));
    /** The game's pixel font, or the client's own (Geist, sharp at any size). */
    protected final Setting.Choice typeface = add(new Setting.Choice("font", "minecraft", List.of("minecraft", "geist")));
    private static final List<String> COLORS = List.of("white", "grey", "yellow", "green", "cyan", "pink", "red");
    private static final int[] RGB = {0xFFFFFF, 0xC8C8C8, 0xFFE040, 0x4CFF4C, 0x40E8FF, 0xFF60D0, 0xFF4040};
    protected final Setting.Choice color = add(new Setting.Choice("color", "white", COLORS));

    protected TextHud(String id, String icon, float anchorX, float anchorY, float offsetX, float offsetY) {
        super(id, icon, anchorX, anchorY, offsetX, offsetY);
    }

    /** What it shows now, one entry per line; none when there's nothing to show. */
    protected abstract List<String> lines(Minecraft mc);

    /** What it shows in the HUD editor when it would show nothing. */
    protected List<String> sample(Minecraft mc) {
        return List.of(name());
    }

    /** Room before each line for an icon, in GUI units; none unless a module draws icons. */
    protected int iconRoom() {
        return 0;
    }

    /** Draws the icon of shown line {@code index} with its top left at (x, y), {@link #iconRoom()} wide. */
    protected void drawIcon(Canvas g, Minecraft mc, int index, int x, int y) {
    }

    private List<String> shown(Minecraft mc) {
        List<String> lines = lines(mc);
        return lines.isEmpty() && preview() ? sample(mc) : lines;
    }

    /** The text's colour, ARGB. */
    protected int textColor() {
        return 0xFF000000 | RGB[Math.max(0, COLORS.indexOf(color.get()))];
    }

    private boolean geist() {
        return typeface.get().equals("geist");
    }

    /** Geist at the size the game's 9 pixel line has on screen now (GUI scale and this module's size). */
    private GeistFont geistFont(Minecraft mc) {
        float screenPerUnit = (float) mc.getWindow().getGuiScale() * scale();
        return GeistFont.of(mc.font, 9.5f * screenPerUnit, true);
    }

    private int lineWidth(Minecraft mc, String line) {
        if (!geist()) {
            return mc.font.width(line);
        }
        float screenPerUnit = (float) mc.getWindow().getGuiScale() * scale();
        return (int) Math.ceil(geistFont(mc).width(line) / screenPerUnit);
    }

    @Override
    public int width(Minecraft mc) {
        List<String> lines = shown(mc);
        if (lines.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (String line : lines) {
            w = Math.max(w, lineWidth(mc, line));
        }
        return w + iconRoom() + PAD * 2;
    }

    @Override
    public int height(Minecraft mc) {
        List<String> lines = shown(mc);
        return lines.isEmpty() ? 0 : lines.size() * 10 + PAD * 2 - 1;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int icons = iconRoom();
        if (icons > 0) {
            List<String> lines = shown(mc);
            for (int i = 0; i < lines.size(); i++) {
                drawIcon(g, mc, i, PAD, PAD + i * 10 - 1);
            }
        }
        int textX = PAD + icons;
        if (geist()) {
            // In screen pixels, so Geist lands on them one to one.
            float k = (float) mc.getWindow().getGuiScale() * scale();
            GeistFont f = geistFont(mc);
            g.push();
            g.scale(1f / k, 1f / k);
            float y = PAD;
            for (String line : shown(mc)) {
                // The capitals' middle where the game's font has its.
                float baseline = (y + 3.5f) * k + f.cap() / 2f;
                if (shadow.get()) {
                    f.draw(g, line, textX * k + Math.max(1, Math.round(k / 2f)), baseline + Math.max(1, Math.round(k / 2f)), 0xFF3F3F3F);
                }
                f.draw(g, line, textX * k, baseline, textColor());
                y += 10;
            }
            g.pop();
            return;
        }
        int y = PAD;
        for (String line : shown(mc)) {
            g.text(mc.font, line, textX, y, textColor(), shadow.get());
            y += 10;
        }
    }
}
