package io.github.georgwav.cosmetics.client.hud;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;

/**
 * A module drawn on the HUD: where it sits (pinned to the nearest screen edge or middle, so it stays put at any
 * window size and GUI scale), how large, and whether it has a dark backing. Moved and scaled in the HUD editor.
 */
public abstract class HudModule extends Module {
    /** 0 = pinned to the left (or top), 0.5 = the middle, 1 = the right (or bottom). */
    float anchorX;
    float anchorY;
    /** Offset from the anchor point, in GUI pixels. */
    float offsetX;
    float offsetY;
    protected final Setting.Slider scale = add(new Setting.Slider("scale", 1.0, 0.5, 2.0, 0.05,
            v -> Math.round(v * 100) + "%"));
    protected final Setting.Toggle backing = add(new Setting.Toggle("backing", true));

    protected HudModule(String id, String icon, float anchorX, float anchorY, float offsetX, float offsetY) {
        super(id, Category.HUD, icon);
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
    }

    /** Whether there is nothing to show right now (no effects, nothing worn): then it isn't drawn. */
    public boolean isEmpty(Minecraft mc) {
        return width(mc) <= 0 || height(mc) <= 0;
    }

    /** Whether the dark backing goes behind the whole module (Keystrokes backs each key instead). */
    protected boolean wholeBacking() {
        return true;
    }

    /** In the HUD editor: shows example contents where it would show nothing. */
    protected static boolean preview() {
        return Hud.editing;
    }

    /** Its size before scaling, in GUI pixels. */
    public abstract int width(Minecraft mc);

    public abstract int height(Minecraft mc);

    /** Draws it with its top left at (0, 0), before scaling. */
    protected abstract void draw(Canvas g, Minecraft mc);

    public float scale() {
        return scale.get().floatValue();
    }

    public void setScale(double value) {
        scale.set(value);
    }

    /** Its top left on a screen of this size (GUI pixels). */
    public float x(int screenW, Minecraft mc) {
        return anchorX * screenW + offsetX - anchorX * width(mc) * scale();
    }

    public float y(int screenH, Minecraft mc) {
        return anchorY * screenH + offsetY - anchorY * height(mc) * scale();
    }

    /** Puts its top left at (x, y), pinned to the edge or middle nearest its centre. */
    public void moveTo(float x, float y, int screenW, int screenH, Minecraft mc) {
        float w = width(mc) * scale();
        float h = height(mc) * scale();
        float cx = (x + w / 2f) / screenW;
        float cy = (y + h / 2f) / screenH;
        anchorX = cx < 1f / 3f ? 0f : cx > 2f / 3f ? 1f : 0.5f;
        anchorY = cy < 1f / 3f ? 0f : cy > 2f / 3f ? 1f : 0.5f;
        offsetX = x - anchorX * screenW + anchorX * w;
        offsetY = y - anchorY * screenH + anchorY * h;
    }

    void render(Canvas g, Minecraft mc, int screenW, int screenH) {
        if (isEmpty(mc)) {
            return;
        }
        float s = scale();
        g.push();
        g.translate(Math.round(x(screenW, mc)), Math.round(y(screenH, mc)));
        g.scale(s, s);
        if (backing.get() && wholeBacking()) {
            g.fill(0, 0, width(mc), height(mc), 0x70000000);
        }
        draw(g, mc);
        g.pop();
    }

    @Override
    protected void saveState(JsonObject state) {
        state.addProperty("anchorX", anchorX);
        state.addProperty("anchorY", anchorY);
        state.addProperty("x", offsetX);
        state.addProperty("y", offsetY);
    }

    @Override
    protected void loadState(JsonObject state) {
        if (state.has("x")) {
            anchorX = state.get("anchorX").getAsFloat();
            anchorY = state.get("anchorY").getAsFloat();
            offsetX = state.get("x").getAsFloat();
            offsetY = state.get("y").getAsFloat();
        }
    }
}
