package io.github.georgwav.cosmetics.sol.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.styles.Builder;
import io.github.georgwav.cosmetics.sol.styles.Choices;
import io.github.georgwav.cosmetics.sol.styles.EditorImage;
import io.github.georgwav.cosmetics.sol.styles.Mixer;
import io.github.georgwav.cosmetics.sol.styles.PartTable;
import io.github.georgwav.cosmetics.sol.styles.Pixels;
import io.github.georgwav.cosmetics.sol.styles.StyleSetup;
import io.github.georgwav.cosmetics.sol.styles.Styles;
import io.github.georgwav.cosmetics.sol.styles.Thumbs;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.ColorPicker;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Sol's texture editor (B.2.12): the texture showing for a part, on a pixel grid that zooms and pans over a
 * checkerboard; pencil, eraser, fill, line, rectangle, eyedropper, select and move, mirror, replace colour, hue,
 * saturation and brightness, gradient and outline; the texture's own colours, recent ones and the theme's; a frame
 * strip with onion skin and each frame's time; 16, 32 or 64 pixels; 200 steps of undo. Every stroke goes into the
 * game live (when the size stays the same), so the world behind shows it too; the preview beside it is the menu's,
 * and on armour a click on the mannequin paints its texel. Saving makes a small style of your own in client/styles
 * and puts it on that one texture; a source pack is never written.
 */
public final class TextureEditor extends SolScreen {
    /** The tools, in the column's order: id, icon, key. */
    private static final String[][] TOOLS = {{"pencil", "brush", "B"}, {"eraser", "x", "E"}, {"fill", "drop", "G"}, {"line", "lines", "L"},
            {"rect", "card", "U"}, {"pick", "eye", "I"}, {"select", "grid", "M"}, {"move", "fist", "V"}, {"replace", "spark", "R"},
            {"hsb", "sun", "H"}, {"gradient", "bars", "D"}, {"outline", "ring", "O"}};
    private static final float CX = 140f, CY = 141f, CW = 700f, CH = 488f, RX = 856f, RW = 359f;

    private final PartTable.Part part;
    private final Function<String, Mixer.Source> sourceFor;
    private final List<String> paths;
    private String path;
    private volatile EditorImage img;
    private Pixels.Image original;
    private volatile String loadError;
    private String tool = "pencil";
    private int primary = 0xFFFFFFFF, secondary = 0xFF000000, size = 1, tolerance = 16;
    private boolean mirrorX, mirrorY, grid = true, onion, filled, uv = true;
    private int hue, satPct = 100, brightPct = 100;
    private final List<Integer> recent = new ArrayList<>();
    /** Screen pixels per texel, and the picture's top left on screen (pan). */
    private int zoom;
    private float panX = Float.NaN, panY;
    private boolean spaceDown, panning;
    private float panFromX, panFromY, panAtX, panAtY;
    /** The stroke under way: where it started and its last texel. */
    private int downX = -1, downY = -1, lastX, lastY, curX = -1, curY = -1;
    private int downButton;
    private boolean live, liveTried;
    private boolean dirty = true;
    private SolTexture strip, checker, cube;
    private int stripW, stripH;
    private ColorPicker picker;
    private boolean pickingSecondary;
    private boolean saved;
    private String styleName;

    /**
     * @param sourceFor where each of the part's textures comes from now (the tile it was opened from, or the look now)
     * @param start     the texture to edit first, or null for the part's own picture
     */
    public TextureEditor(Screen parent, PartTable.Part part, Function<String, Mixer.Source> sourceFor, String start) {
        super(Component.translatable("sol.editor.title"), parent, Feature.STYLES);
        this.part = part;
        this.sourceFor = sourceFor;
        this.paths = new ArrayList<>();
        for (String t : part.textures()) {
            if (sourceFor.apply(t) != null) {
                paths.add(t);
            }
        }
        if (paths.isEmpty()) {
            paths.addAll(part.textures());
        }
        this.path = start != null && paths.contains(start) ? start : part.icon() != null && paths.contains(part.icon()) ? part.icon()
                : paths.isEmpty() ? null : paths.get(0);
        load();
    }

    /** The editor on a part's look as it is now. */
    public static TextureEditor current(Screen parent, PartTable.Part part) {
        return new TextureEditor(parent, part, Styles::current, null);
    }

    /** The editor on a tile's look (its pack's textures, else the base's, else the game's). */
    public static TextureEditor ofTile(Screen parent, PartTable.Part part, Choices.Tile tile) {
        int tint = ThemeState.tokens().accent & 0xFFFFFF;
        return new TextureEditor(parent, part, p -> {
            var idx = Styles.index();
            return idx == null ? Styles.current(p) : Choices.file(tile, p, part, idx, Styles.setup(), tint);
        }, null);
    }

    /** Reads the texture (and its .mcmeta) off the render thread. */
    private void load() {
        img = null;
        loadError = null;
        String p = path;
        if (p == null) {
            loadError = "none";
            return;
        }
        Mixer.Source src = sourceFor.apply(p), meta = sourceFor.apply(p + ".mcmeta");
        SolExecutors.IO.execute(() -> {
            try {
                byte[] data = src == null ? null : Styles.read(src);
                Pixels.Image raw = data == null ? null : Builder.decode(data);
                if (raw == null) {
                    loadError = "unreadable";
                    return;
                }
                byte[] m = meta == null ? null : Styles.read(meta);
                EditorImage e = EditorImage.of(raw, part.opaque(), m == null ? null : new String(m, java.nio.charset.StandardCharsets.UTF_8));
                original = e.texture();
                img = e;
                dirty = true;
            } catch (Exception | LinkageError e) {
                loadError = e.toString();
            }
        });
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void frame(Gfx g) {
        Tokens tk = ThemeState.tokens();
        PixelTokens p = PixelTokens.get();
        ui.begin(g, mx, my);
        Pix.window(g, p);
        bar(g, tk, p);
        EditorImage e = img;
        if (e != null && dirty) {
            upload(e);
        }
        tools(g, tk, p);
        canvas(g, tk, p, e);
        frames(g, tk, p, e);
        right(g, tk, p, e);
        if (picker != null) {
            ui.area(-10000f, -10000f, 30000f, 30000f, () -> picker = null);
            float w = 470f, h = ColorPicker.HEIGHT + 50f, x = CX + (CW - w) / 2f, y = CY + 60f;
            Pix.card(g, p, x, y, w, h);
            ui.area(x, y, w, h, () -> {
            });
            g.textMid(g.font(SolFont.Family.PIXEL_BOLD, 11f), tr(pickingSecondary ? "sol.editor.secondary" : "sol.editor.primary").toUpperCase(Locale.ROOT),
                    x + 14f, y + 18f, PixelTokens.INK2);
            ui.tool(tk, x + w - 34f, y + 6f, "x", false, () -> picker = null);
            List<Integer> presets = new ArrayList<>();
            for (int c : Pixels.PRESET_RGB) {
                presets.add(c);
            }
            picker.draw(g, ui, tk, x + 14f, y + 34f, w - 28f, presets, null, null, dt);
        }
        if (tip != null) {
            Pix.tooltip(g, p, tipX, tipY, tip);
            tip = null;
        }
    }

    private final Ui ui = new Ui();
    private String tip;
    private float tipX, tipY;

    private void bar(Gfx g, Tokens tk, PixelTokens p) {
        float top = Pix.WIN_Y + Pix.E, y = top + 3f, barH = Pix.BAR_H - Pix.E;
        boolean over = ui.over(Pix.WIN_X + 11f, y, 48f, 48f);
        Pix.slab(g, p, Pix.WIN_X + 11f, y, 48f, 48f, over ? Pix.HOVER : Pix.NORMAL);
        Icons.draw(g, "left", Pix.WIN_X + 23f, y + 12f, 24f, Pix.text(p, over ? Pix.HOVER : Pix.NORMAL), 0f, 0);
        ui.click(Pix.WIN_X + 11f, y, 48f, 48f, this::leave);
        SolFont hf = g.font(SolFont.Family.PIXEL_BOLD, 15f), sf = g.font(SolFont.Family.MEDIUM, 12.5f);
        float tx = Pix.WIN_X + 76f, cy = top + barH / 2f;
        String title = tr("sol.editor.title").toUpperCase(Locale.ROOT);
        g.textMid(hf, title, tx, cy, PixelTokens.INK);
        float sx = tx + g.width(hf, title) + 14f;
        String sub = tr("sol.styles.part." + part.id()) + " · " + (path == null ? "" : path.substring(path.lastIndexOf('/') + 1));
        float right = Pix.WIN_X + Pix.WIN_W - 14f;
        float r = right;
        r -= ui.button(tk, r, cy, tr("sol.editor.export"), false, this::export) + 8f;
        r -= ui.button(tk, r, cy, tr("sol.editor.save_copy"), false, () -> save(true)) + 8f;
        r -= ui.button(tk, r, cy, tr("sol.editor.save"), true, () -> save(false)) + 8f;
        g.textMid(sf, g.fit(sf, sub, r - sx - 10f), sx, cy, PixelTokens.INK2);
    }

    private void tools(Gfx g, Tokens tk, PixelTokens p) {
        float x0 = Pix.WIN_X + Pix.E + 22f, y = CY;
        for (int i = 0; i < TOOLS.length; i++) {
            String[] t = TOOLS[i];
            float x = x0 + (i % 2) * 32f, ty = y + (i / 2) * 32f;
            ui.tool(tk, x, ty, t[1], tool.equals(t[0]), () -> tool = t[0]);
            if (ui.over(x, ty, 26f, 26f)) {
                tipFor(tr("sol.editor.tool." + t[0]) + "  (" + t[2] + ")", x + 34f, ty);
            }
        }
        y += (TOOLS.length / 2) * 32f + 14f;
        String[][] toggles = {{"mirror_x", "prev"}, {"mirror_y", "next"}, {"grid", "chunk"}, {"onion", "moon"}, {"uv", "cube"}, {"filled", "card"}};
        for (int i = 0; i < toggles.length; i++) {
            String id = toggles[i][0];
            float x = x0 + (i % 2) * 32f, ty = y + (i / 2) * 32f;
            boolean on = switch (id) {
                case "mirror_x" -> mirrorX;
                case "mirror_y" -> mirrorY;
                case "grid" -> grid;
                case "onion" -> onion;
                case "uv" -> uv;
                default -> filled;
            };
            ui.tool(tk, x, ty, toggles[i][1], on, () -> toggle(id));
            if (ui.over(x, ty, 26f, 26f)) {
                tipFor(tr("sol.editor.toggle." + id), x + 34f, ty);
            }
        }
        y += 3 * 32f + 14f;
        EditorImage e = img;
        ui.tool(tk, x0, y, "left", false, this::undo);
        if (ui.over(x0, y, 26f, 26f)) {
            tipFor(tr("sol.editor.undo") + (e == null ? "" : " (" + e.undoSteps() + ")"), x0 + 34f, y);
        }
        ui.tool(tk, x0 + 32f, y, "next", false, this::redo);
        if (ui.over(x0 + 32f, y, 26f, 26f)) {
            tipFor(tr("sol.editor.redo"), x0 + 66f, y);
        }
    }

    private void toggle(String id) {
        switch (id) {
            case "mirror_x" -> mirrorX = !mirrorX;
            case "mirror_y" -> mirrorY = !mirrorY;
            case "grid" -> grid = !grid;
            case "onion" -> onion = !onion;
            case "uv" -> uv = !uv;
            default -> filled = !filled;
        }
    }

    private void tipFor(String s, float x, float y) {
        tip = s;
        tipX = x;
        tipY = y;
    }

    /** Uploads the picture (every frame, as one strip), its checkerboard and the preview's cube. */
    private void upload(EditorImage e) {
        dirty = false;
        Pixels.Image t = e.texture();
        if (strip == null || stripW != t.w() || stripH != t.h()) {
            release();
            stripW = t.w();
            stripH = t.h();
            strip = new SolTexture("styles_editor", t.w(), t.h(), false);
            int cw = e.width() * 2, ch = e.height() * 2;
            int[] check = new int[cw * ch];
            for (int y = 0; y < ch; y++) {
                for (int x = 0; x < cw; x++) {
                    check[y * cw + x] = ((x + y) & 1) == 0 ? 0xFF3A3A40 : 0xFF2A2A2F;
                }
            }
            checker = SolTexture.of("styles_editor_check", cw, ch, false, check);
        }
        strip.upload(t.argb());
        Pixels.Image c = Pixels.isoCube(e.image());
        if (cube == null || cube.width != c.w() || cube.height != c.h()) {
            if (cube != null) {
                cube.release();
            }
            cube = new SolTexture("styles_editor_cube", c.w(), c.h(), false);
        }
        cube.upload(c.argb());
    }

    private void release() {
        if (strip != null) {
            strip.release();
        }
        if (checker != null) {
            checker.release();
        }
        strip = null;
        checker = null;
    }

    /** The canvas: the checkerboard, the onion skin, the frame, the grid, the UV islands, the selection and the cursor. */
    private void canvas(Gfx g, Tokens tk, PixelTokens p, EditorImage e) {
        Pix.recess(g, p, CX, CY, CW, CH);
        if (e == null || strip == null) {
            SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 13f);
            g.textCentre(f, tr(loadError == null ? "sol.editor.loading" : "sol.editor.cant_read"), CX + CW / 2f, CY + CH / 2f, PixelTokens.INK2);
            return;
        }
        float bx0 = Math.round(g.d.sx(CX + Pix.E)), by0 = Math.round(g.d.sy(CY + Pix.E));
        float bx1 = Math.round(g.d.sx(CX + CW - Pix.E)), by1 = Math.round(g.d.sy(CY + CH - Pix.E));
        int w = e.width(), h = e.height();
        int fit = Math.max(1, (int) Math.floor(Math.min((bx1 - bx0 - 40f) / w, (by1 - by0 - 40f) / h)));
        if (zoom <= 0) {
            zoom = fit;
        }
        if (Float.isNaN(panX)) {
            panX = Math.round((bx0 + bx1 - w * zoom) / 2f);
            panY = Math.round((by0 + by1 - h * zoom) / 2f);
        }
        g.clip(CX + Pix.E, CY + Pix.E, CX + CW - Pix.E, CY + CH - Pix.E);
        float ix0 = panX, iy0 = panY, ix1 = panX + w * zoom, iy1 = panY + h * zoom;
        g.imageScreen(checker, ix0, iy0, ix1, iy1, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        int n = e.frames();
        float fv0 = e.frame() / (float) n, fv1 = (e.frame() + 1f) / n;
        if (onion && e.frame() > 0) {
            float ov0 = (e.frame() - 1f) / n;
            g.imageScreen(strip, ix0, iy0, ix1, iy1, 0f, ov0, 1f, fv0, 0x59FFFFFF, false);
        }
        g.imageScreen(strip, ix0, iy0, ix1, iy1, 0f, fv0, 1f, fv1, onion && e.frame() > 0 ? 0xD9FFFFFF : 0xFFFFFFFF, false);
        if (grid && zoom >= 6) {
            int line = 0x33FFFFFF;
            for (int x = 0; x <= w; x++) {
                g.rectPx(ix0 + x * zoom, iy0, ix0 + x * zoom + 1, iy1, x % 8 == 0 ? 0x55FFFFFF : line);
            }
            for (int y = 0; y <= h; y++) {
                g.rectPx(ix0, iy0 + y * zoom, ix1, iy0 + y * zoom + 1, y % 8 == 0 ? 0x55FFFFFF : line);
            }
        }
        if (uv) {
            for (int[] r : islands(w, h)) {
                g.framePx(ix0 + r[0] * zoom, iy0 + r[1] * zoom, ix0 + (r[0] + r[2]) * zoom, iy0 + (r[1] + r[3]) * zoom, Math.max(1, zoom / 6),
                        Colors.withAlpha(tk.accent, 0.55f));
            }
        }
        int[] sel = e.selection();
        if (sel != null) {
            g.framePx(ix0 + sel[0] * zoom, iy0 + sel[1] * zoom, ix0 + sel[2] * zoom, iy0 + sel[3] * zoom, Math.max(1, zoom / 5), p.acc);
        }
        // What a line, rectangle, selection, gradient or move being dragged will do.
        if (downX >= 0 && curX >= 0 && !panning) {
            int c = downButton == 1 ? secondary : primary;
            switch (tool) {
                case "line", "gradient" -> {
                    int dx = Math.abs(curX - downX), dy = -Math.abs(curY - downY), sx = downX < curX ? 1 : -1, sy = downY < curY ? 1 : -1, err = dx + dy;
                    int x = downX, y = downY;
                    for (int guard = 0; guard < 512; guard++) {
                        g.rectPx(ix0 + x * zoom, iy0 + y * zoom, ix0 + (x + 1) * zoom, iy0 + (y + 1) * zoom, tool.equals("line") ? c : 0xAAFFFFFF);
                        if (x == curX && y == curY) {
                            break;
                        }
                        int e2 = 2 * err;
                        if (e2 >= dy) {
                            err += dy;
                            x += sx;
                        }
                        if (e2 <= dx) {
                            err += dx;
                            y += sy;
                        }
                    }
                }
                case "rect", "select" -> {
                    int ax = Math.min(downX, curX), ay = Math.min(downY, curY), bx = Math.max(downX, curX) + 1, by = Math.max(downY, curY) + 1;
                    g.framePx(ix0 + ax * zoom, iy0 + ay * zoom, ix0 + bx * zoom, iy0 + by * zoom, Math.max(1, zoom / 5),
                            tool.equals("rect") ? c : p.acc);
                }
                case "move" -> {
                    if (sel != null) {
                        int ox = curX - downX, oy = curY - downY;
                        g.framePx(ix0 + (sel[0] + ox) * zoom, iy0 + (sel[1] + oy) * zoom, ix0 + (sel[2] + ox) * zoom, iy0 + (sel[3] + oy) * zoom,
                                Math.max(1, zoom / 5), 0xCCFFFFFF);
                    }
                }
                default -> {
                }
            }
        }
        // The texel under the pointer.
        int[] at = texel(e, g);
        if (at != null) {
            int b = Math.max(1, size), o = (b - 1) / 2;
            g.framePx(ix0 + (at[0] - o) * zoom, iy0 + (at[1] - o) * zoom, ix0 + (at[0] - o + b) * zoom, iy0 + (at[1] - o + b) * zoom,
                    Math.max(1, zoom / 8), 0xCCFFFFFF);
        }
        g.endClip();
        // Size and zoom, under the canvas's corner.
        SolFont f = g.font(SolFont.Family.PIXEL, 10f);
        String info = w + " x " + h + (n > 1 ? " · " + n + " " + tr("sol.editor.frames") : "") + " · " + zoom + "x"
                + (at != null ? " · " + at[0] + ", " + at[1] : "");
        g.textRight(f, info, CX + CW - 12f, CY + CH - 12f, PixelTokens.INK3);
        if (liveTried && !live) {
            g.text(f, tr("sol.editor.not_live"), CX + 12f, CY + CH - 8f, PixelTokens.INK3);
        }
    }

    /** The texel under the pointer, or null. */
    private int[] texel(EditorImage e, Gfx g) {
        if (e == null || zoom <= 0 || mx < CX || my < CY || mx > CX + CW || my > CY + CH) {
            return null;
        }
        float sx = g.d.sx(mx), sy = g.d.sy(my);
        int x = (int) Math.floor((sx - panX) / zoom), y = (int) Math.floor((sy - panY) / zoom);
        return x < 0 || y < 0 || x >= e.width() || y >= e.height() ? null : new int[]{x, y};
    }

    /** The UV islands of the texture's model (armour, the crystal, the shield), in texels, scaled to its size. */
    private List<int[]> islands(int w, int h) {
        List<int[]> out = new ArrayList<>();
        int[][] boxes;
        float bw, bh;
        if (path.contains("equipment/humanoid") || path.contains("models/armor/")) {
            // Head 8x8x8 at 0,0; body 8x12x4 at 16,16; arm 4x12x4 at 40,16; leg 4x12x4 at 0,16 (64 x 32).
            boxes = new int[][]{{0, 0, 8, 8, 8}, {16, 16, 8, 12, 4}, {40, 16, 4, 12, 4}, {0, 16, 4, 12, 4}};
            bw = 64f;
            bh = 32f;
        } else if (path.endsWith("entity/end_crystal/end_crystal.png")) {
            boxes = new int[][]{{0, 0, 8, 8, 8}, {32, 0, 8, 8, 8}, {0, 16, 12, 4, 12}};
            bw = 64f;
            bh = 32f;
        } else if (path.contains("shield_base")) {
            boxes = new int[][]{{0, 0, 12, 22, 1}, {26, 0, 2, 6, 6}};
            bw = 64f;
            bh = 64f;
        } else {
            return out;
        }
        for (int[] b : boxes) {
            int u = b[0], v = b[1], bxw = b[2], bxh = b[3], d = b[4];
            int[][] faces = {{u + d, v, bxw, d}, {u + d + bxw, v, bxw, d}, {u, v + d, d, bxh}, {u + d, v + d, bxw, bxh},
                    {u + d + bxw, v + d, d, bxh}, {u + d + bxw + d, v + d, bxw, bxh}};
            for (int[] f : faces) {
                out.add(new int[]{Math.round(f[0] * w / bw), Math.round(f[1] * h / bh), Math.max(1, Math.round(f[2] * w / bw)),
                        Math.max(1, Math.round(f[3] * h / bh))});
            }
        }
        return out;
    }

    /** The frame strip under the canvas: each frame (pick it), add and remove, the frame's time. */
    private void frames(Gfx g, Tokens tk, PixelTokens p, EditorImage e) {
        float y = CY + CH + 8f, x = CX;
        if (e == null || strip == null) {
            return;
        }
        int n = e.frames();
        float fs = 32f;
        for (int i = 0; i < Math.min(n, 14); i++) {
            float fx = x + i * (fs + 6f);
            boolean on = i == e.frame();
            Pix.fill(g, fx, y, fs, fs, 0xFF2A2A2F);
            g.image(strip, fx + 3f, y + 3f, fs - 6f, fs - 6f, 0f, i / (float) n, 1f, (i + 1f) / n, 0xFFFFFFFF, false);
            Pix.frame(g, fx, y, fs, fs, on ? p.acc : p.edge);
            final int idx = i;
            ui.click(fx, y, fs, fs, () -> e.showFrame(idx));
        }
        float ax = x + Math.min(n, 14) * (fs + 6f);
        ui.tool(tk, ax, y + 3f, "plus", false, () -> {
            e.addFrame();
            changed(e);
        });
        if (ui.over(ax, y + 3f, 26f, 26f)) {
            tipFor(tr("sol.editor.add_frame"), ax + 30f, y);
        }
        if (n > 1) {
            ui.tool(tk, ax + 32f, y + 3f, "x", false, () -> {
                e.removeFrame();
                changed(e);
            });
            ui.slider(tk, CX + CW, y + fs / 2f, (e.frameTime(e.frame()) - 1) / 39f, e.frameTime(e.frame()) + " " + tr("sol.editor.ticks"),
                    v -> e.setFrameTime(e.frame(), 1 + Math.round(v * 39f)));
        }
    }

    /** The preview, the colours, the tool's settings and the texture's size. */
    private void right(Gfx g, Tokens tk, PixelTokens p, EditorImage e) {
        float y = CY;
        var idx = Styles.index();
        if (e != null && strip != null && idx != null) {
            Thumbs.Thumb full = new Thumbs.Thumb(strip, stripW, stripH, 2f, null);
            Thumbs.Thumb cubeTh = cube == null ? null : new Thumbs.Thumb(cube, cube.width, cube.height, 2f, null);
            StylesPreview.edited = new StylesPreview.Edited(path, full, cubeTh);
            Choices.Tile here = new Choices.Tile("", "game", "", null, Choices.Group.VANILLA, List.of(), sourceFor.apply(path));
            StylesPreview.draw(g, tk, RX, y, RW, 200f, part, here, idx, Styles.setup(), tk.accent & 0xFFFFFF, time,
                    tr("sol.styles.part." + part.id()), tr(armourLayer() ? "sol.editor.paint_model" : "sol.editor.preview"),
                    StylesPreview.lookOf(path));
            StylesPreview.edited = null;
        } else {
            Pix.pane(g, p, RX, y, RW, 200f);
            Pix.paneEdge(g, p, RX, y, RW, 200f);
        }
        y += 210f;
        Pix.recess(g, p, RX, y, RW, CY + CH + 40f - y);
        float ix = RX + 14f, right = RX + RW - 14f;
        SolFont lf = g.font(SolFont.Family.PIXEL, 10f), mf = g.font(SolFont.Family.MEDIUM, 12f);
        float cy = y + 16f;
        g.textMid(lf, tr("sol.editor.colours").toUpperCase(Locale.ROOT), ix, cy, PixelTokens.INK3);
        cy += 12f;
        // Primary over secondary, the swap, the picker, the theme's colour.
        Pix.fill(g, ix + 16f, cy + 14f, 30f, 30f, secondary);
        Pix.frame(g, ix + 16f, cy + 14f, 30f, 30f, p.edge);
        ui.click(ix + 16f, cy + 14f, 30f, 30f, () -> openPicker(true));
        Pix.fill(g, ix, cy, 30f, 30f, primary);
        Pix.frame(g, ix, cy, 30f, 30f, 0xFFFFFFFF);
        ui.click(ix, cy, 30f, 30f, () -> openPicker(false));
        ui.tool(tk, ix + 56f, cy + 8f, "dots", false, this::swap);
        if (ui.over(ix + 56f, cy + 8f, 26f, 26f)) {
            tipFor(tr("sol.editor.swap") + "  (X)", ix + 86f, cy);
        }
        int theme = 0xFF000000 | tk.accent;
        Pix.swatch(g, p, ix + 92f, cy + 10f, 22f, 22f, theme, primary == theme);
        Icons.draw(g, "sun", ix + 95f, cy + 13f, 16f, Colors.onColour(theme), 0f, 0);
        ui.click(ix + 92f, cy + 10f, 22f, 22f, () -> use(theme));
        cy += 52f;
        // The texture's own colours, then the recent ones.
        List<Integer> pal = e == null ? List.of() : e.palette(26);
        cy = swatchRow(g, p, ix, cy, right, pal);
        if (!recent.isEmpty()) {
            g.textMid(lf, tr("sol.editor.recent").toUpperCase(Locale.ROOT), ix, cy + 6f, PixelTokens.INK3);
            cy = swatchRow(g, p, ix, cy + 14f, right, recent);
        }
        cy += 8f;
        // The tool's own settings.
        String label = tr("sol.editor.tool." + tool).toUpperCase(Locale.ROOT);
        g.textMid(lf, label, ix, cy, PixelTokens.INK3);
        cy += 20f;
        switch (tool) {
            case "pencil", "eraser", "line", "rect" -> {
                g.textMid(mf, tr("sol.editor.size"), ix, cy, PixelTokens.INK2);
                ui.slider(tk, right, cy, (size - 1) / 7f, size + " px", v -> size = 1 + Math.round(v * 7f));
                cy += 30f;
            }
            case "fill", "replace" -> {
                g.textMid(mf, tr("sol.editor.tolerance"), ix, cy, PixelTokens.INK2);
                ui.slider(tk, right, cy, tolerance / 128f, String.valueOf(tolerance), v -> tolerance = Math.round(v * 128f));
                cy += 30f;
            }
            case "hsb" -> {
                g.textMid(mf, tr("sol.styles.hue"), ix, cy, PixelTokens.INK2);
                ui.slider(tk, right, cy, (hue + 180f) / 360f, hue + "°", v -> hue = Math.round(v * 360f - 180f));
                cy += 28f;
                g.textMid(mf, tr("sol.styles.saturation"), ix, cy, PixelTokens.INK2);
                ui.slider(tk, right, cy, satPct / 200f, satPct + "%", v -> satPct = Math.round(v * 200f));
                cy += 28f;
                g.textMid(mf, tr("sol.styles.brightness"), ix, cy, PixelTokens.INK2);
                ui.slider(tk, right, cy, brightPct / 200f, brightPct + "%", v -> brightPct = Math.round(v * 200f));
                cy += 28f;
                ui.button(tk, right, cy, tr("sol.editor.apply"), true, () -> {
                    if (e != null) {
                        e.begin();
                        e.hsb(hue, satPct / 100f, brightPct / 100f);
                        if (e.end()) {
                            changed(e);
                        }
                    }
                });
                cy += 34f;
            }
            default -> {
                g.textMid(mf, g.fit(mf, tr("sol.editor.hint." + tool), right - ix), ix, cy, PixelTokens.INK2);
                cy += 26f;
            }
        }
        // Which texture, and its size.
        if (paths.size() > 1) {
            String name = path.substring(path.lastIndexOf('/') + 1).replace(".png", "");
            ui.dropButton(tk, ix + ui.buttonWidth(name) + 20f, cy, name, false, this::nextTexture);
        }
        if (e != null) {
            String[] sizes = {"16", "32", "64"};
            int at = e.width() == 16 ? 0 : e.width() == 32 ? 1 : e.width() == 64 ? 2 : -1;
            ui.segmented(tk, right, cy, sizes, at, i -> {
                e.resize(EditorImage.SIZES[i]);
                zoom = 0;
                panX = Float.NaN;
                changed(e);
            });
        }
    }

    private boolean armourLayer() {
        return path != null && (path.contains("equipment/humanoid") || path.contains("models/armor/"));
    }

    /** A row (or rows) of colour swatches to paint with; returns the next row's top. */
    private float swatchRow(Gfx g, PixelTokens p, float x, float y, float right, List<Integer> colours) {
        float s = 20f, gap = 5f;
        int per = Math.max(1, (int) ((right - x + gap) / (s + gap)));
        for (int i = 0; i < colours.size(); i++) {
            int c = colours.get(i);
            float sx = x + (i % per) * (s + gap), sy = y + (i / per) * (s + gap);
            Pix.swatch(g, p, sx, sy, s, s, 0xFF000000 | c, (c | 0xFF000000) == (primary | 0xFF000000));
            ui.click(sx, sy, s, s, () -> use(c));
        }
        return y + ((colours.size() + per - 1) / per) * (s + gap) + 4f;
    }

    private void nextTexture() {
        int i = paths.indexOf(path);
        restoreLive();
        path = paths.get((i + 1) % paths.size());
        zoom = 0;
        panX = Float.NaN;
        saved = false;
        styleName = null;
        load();
    }

    private void openPicker(boolean second) {
        pickingSecondary = second;
        picker = new ColorPicker(() -> (second ? secondary : primary) & 0xFFFFFF, rgb -> {
            if (second) {
                secondary = 0xFF000000 | rgb;
            } else {
                primary = 0xFF000000 | rgb;
            }
        });
    }

    private void use(int c) {
        primary = c | 0xFF000000;
        recent.remove((Integer) primary);
        recent.add(0, primary);
        while (recent.size() > 13) {
            recent.remove(recent.size() - 1);
        }
    }

    private void swap() {
        int t = primary;
        primary = secondary;
        secondary = t;
    }

    // ------------------------------------------------------------------ painting

    /** A change made: the picture uploaded again and sent into the game. */
    private void changed(EditorImage e) {
        dirty = true;
        saved = false;
        liveTried = true;
        live = Styles.writeLive(path, e.texture());
    }

    @Override
    protected boolean press(float x, float y, int button) {
        if (picker == null && (button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT) && !spaceDown && mx >= CX && my >= CY && mx <= CX + CW && my <= CY + CH && img != null) {
            int[] at = texel(img, g);
            downButton = button == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 0;
            if (at == null && !tool.equals("select")) {
                return true;
            }
            startStroke(at == null ? new int[]{0, 0} : at);
            return true;
        }
        if (picker == null && (button == InputConstants.MOUSE_BUTTON_MIDDLE || spaceDown && button == InputConstants.MOUSE_BUTTON_LEFT) && mx >= CX && my >= CY && mx <= CX + CW && my <= CY + CH) {
            panning = true;
            panFromX = g.d.sx(x);
            panFromY = g.d.sy(y);
            panAtX = panX;
            panAtY = panY;
            return true;
        }
        // Paint on the model: a click on the mannequin paints its texel.
        if (picker == null && button == InputConstants.MOUSE_BUTTON_LEFT && img != null && armourLayer() && mx >= RX && my >= CY && mx <= RX + RW && my <= CY + 200f
                && (tool.equals("pencil") || tool.equals("eraser"))) {
            int[] uvAt = StylesPreview.texelAt(g.d.sx(x), g.d.sy(y), path.contains("humanoid/") || path.endsWith("_layer_1.png"), img.width(), img.height());
            if (uvAt != null) {
                downButton = 0;
                startStroke(uvAt);
                endStroke();
                return true;
            }
        }
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        return ui.press(x, y);
    }

    private void startStroke(int[] at) {
        EditorImage e = img;
        downX = at[0];
        downY = at[1];
        lastX = at[0];
        lastY = at[1];
        curX = at[0];
        curY = at[1];
        int c = downButton == 1 ? secondary : primary;
        switch (tool) {
            case "pencil" -> {
                e.begin();
                e.pencil(at[0], at[1], size, c, mirrorX, mirrorY);
                changedLight(e);
            }
            case "eraser" -> {
                e.begin();
                e.erase(at[0], at[1], size, mirrorX, mirrorY);
                changedLight(e);
            }
            case "fill" -> {
                e.begin();
                e.fill(at[0], at[1], c, tolerance);
                endStroke();
            }
            case "pick" -> {
                use(e.get(at[0], at[1]));
                downX = -1;
            }
            case "replace" -> {
                e.begin();
                e.replace(e.get(at[0], at[1]), c, tolerance);
                endStroke();
            }
            case "outline" -> {
                e.begin();
                e.outline(c);
                endStroke();
            }
            case "move" -> {
                int[] sel = e.selection();
                if (sel == null || at[0] < sel[0] || at[1] < sel[1] || at[0] >= sel[2] || at[1] >= sel[3]) {
                    downX = -1;
                }
            }
            default -> {
            }
        }
    }

    /** During a pencil stroke: shown on the canvas now, sent live at most once a frame. */
    private void changedLight(EditorImage e) {
        dirty = true;
        pendingLive = true;
    }

    private boolean pendingLive;

    @Override
    public void tick() {
        super.tick();
        EditorImage e = img;
        if (pendingLive && e != null) {
            pendingLive = false;
            liveTried = true;
            live = Styles.writeLive(path, e.texture());
        }
    }

    @Override
    protected boolean drag(float x, float y, int button) {
        if (panning) {
            panX = Math.round(panAtX + g.d.sx(x) - panFromX);
            panY = Math.round(panAtY + g.d.sy(y) - panFromY);
            return true;
        }
        EditorImage e = img;
        if (downX >= 0 && e != null) {
            int[] at = texel(e, g);
            if (at == null) {
                // Off the picture: clamp to its edge, so a line or selection can reach it.
                float sx = g.d.sx(x), sy = g.d.sy(y);
                at = new int[]{Math.max(0, Math.min(e.width() - 1, (int) Math.floor((sx - panX) / zoom))),
                        Math.max(0, Math.min(e.height() - 1, (int) Math.floor((sy - panY) / zoom)))};
            }
            curX = at[0];
            curY = at[1];
            int c = downButton == 1 ? secondary : primary;
            if (tool.equals("pencil") && (at[0] != lastX || at[1] != lastY)) {
                e.line(lastX, lastY, at[0], at[1], size, c, mirrorX, mirrorY);
                changedLight(e);
            } else if (tool.equals("eraser") && (at[0] != lastX || at[1] != lastY)) {
                e.line(lastX, lastY, at[0], at[1], size, null, mirrorX, mirrorY);
                changedLight(e);
            }
            lastX = at[0];
            lastY = at[1];
            return true;
        }
        return ui.dragTo(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        if (panning) {
            panning = false;
            return true;
        }
        EditorImage e = img;
        if (downX >= 0 && e != null) {
            int c = downButton == 1 ? secondary : primary;
            switch (tool) {
                case "line" -> {
                    e.begin();
                    e.line(downX, downY, curX, curY, size, c, mirrorX, mirrorY);
                }
                case "rect" -> {
                    e.begin();
                    e.rect(downX, downY, curX, curY, size, c, filled);
                }
                case "gradient" -> {
                    e.begin();
                    e.gradient(downX, downY, curX, curY, primary, secondary);
                }
                case "select" -> {
                    if (downX == curX && downY == curY) {
                        e.selectNone();
                    } else {
                        e.select(downX, downY, curX, curY);
                    }
                }
                case "move" -> e.move(curX - downX, curY - downY);
                default -> {
                }
            }
            endStroke();
            return true;
        }
        return ui.release();
    }

    private void endStroke() {
        EditorImage e = img;
        downX = -1;
        curX = -1;
        if (e != null) {
            e.end();
            changed(e);
            if (!tool.equals("pick") && !tool.equals("select")) {
                int c = downButton == 1 ? secondary : primary;
                recent.remove((Integer) c);
                recent.add(0, c);
                while (recent.size() > 13) {
                    recent.remove(recent.size() - 1);
                }
            }
        }
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        if (img == null || mx < CX || my < CY || mx > CX + CW || my > CY + CH) {
            return false;
        }
        // Zoom round the pointer, in whole screen pixels per texel.
        float sx = g.d.sx(x), sy = g.d.sy(y);
        int old = zoom, next = amount > 0 ? Math.min(64, Math.max(old + 1, Math.round(old * 1.25f))) : Math.max(1, Math.min(old - 1, Math.round(old / 1.25f)));
        float tx = (sx - panX) / old, ty = (sy - panY) / old;
        zoom = next;
        panX = Math.round(sx - tx * next);
        panY = Math.round(sy - ty * next);
        return true;
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        if (picker != null && picker.key(key, ctrl)) {
            return true;
        }
        if (ctrl && key == InputConstants.KEY_Z) {
            if (shift) {
                redo();
            } else {
                undo();
            }
            return true;
        }
        if (ctrl && key == InputConstants.KEY_Y) {
            redo();
            return true;
        }
        if (ctrl && key == InputConstants.KEY_S) {
            save(false);
            return true;
        }
        if (key == InputConstants.KEY_SPACE) {
            spaceDown = true;
            return true;
        }
        if (ctrl) {
            return false;
        }
        String to = switch (key) {
            case InputConstants.KEY_B -> "pencil";
            case InputConstants.KEY_E -> "eraser";
            case InputConstants.KEY_G -> "fill";
            case InputConstants.KEY_I -> "pick";
            case InputConstants.KEY_M -> "select";
            case InputConstants.KEY_L -> "line";
            case InputConstants.KEY_U -> "rect";
            case InputConstants.KEY_V -> "move";
            case InputConstants.KEY_R -> "replace";
            case InputConstants.KEY_H -> "hsb";
            case InputConstants.KEY_D -> "gradient";
            case InputConstants.KEY_O -> "outline";
            default -> null;
        };
        if (to != null) {
            tool = to;
            return true;
        }
        if (key == InputConstants.KEY_X) {
            swap();
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            ui.tab(shift);
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            return ui.activate();
        }
        return false;
    }

    @Override
    protected boolean keyUp(int key) {
        if (key == InputConstants.KEY_SPACE) {
            spaceDown = false;
            return true;
        }
        return false;
    }

    @Override
    protected boolean typed(String text) {
        return picker != null && picker.typed(text);
    }

    private void undo() {
        EditorImage e = img;
        if (e != null && e.undo()) {
            changed(e);
        }
    }

    private void redo() {
        EditorImage e = img;
        if (e != null && e.redo()) {
            changed(e);
        }
    }

    // ------------------------------------------------------------------ files

    @Override
    protected void dropped(List<Path> files) {
        EditorImage e = img;
        if (e == null) {
            return;
        }
        for (Path f : files) {
            if (!f.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")) {
                continue;
            }
            try {
                Pixels.Image in = Builder.decode(Files.readAllBytes(f));
                if (in != null) {
                    e.replaceAll(in);
                    zoom = 0;
                    panX = Float.NaN;
                    changed(e);
                    io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.notify.editor.imported", f.getFileName()));
                }
            } catch (java.io.IOException | RuntimeException ex) {
                io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.notify.editor.cant_import", f.getFileName()));
            }
            return;
        }
    }

    /** The style's folder name: the part's name and a number not used yet in its tab. */
    private String freeName(String tab) {
        String base = tr("sol.styles.part." + part.id());
        Path folder = Styles.userFolder().resolve(tab);
        for (int i = 1; i < 1000; i++) {
            String n = base + " " + i;
            if (!Files.exists(folder.resolve(n))) {
                return n;
            }
        }
        return base + " " + System.currentTimeMillis();
    }

    private String tabFolder() {
        return part.tab() != null ? part.tab() : part.group();
    }

    /**
     * Saves the texture as a style of your own and puts it on that one texture (the part keeps its other textures).
     * Saving again overwrites the style this editor saved; "Save as copy" always makes a new one.
     */
    private void save(boolean copy) {
        EditorImage e = img;
        if (e == null || path == null) {
            return;
        }
        String tab = tabFolder();
        String name = copy || styleName == null ? freeName(tab) : styleName;
        Pixels.Image tex = e.texture();
        String meta = e.mcmeta();
        String p = path;
        SolExecutors.IO.execute(() -> {
            try {
                String key = Styles.saveStyle(tab, name, p, Builder.encode(tex), meta);
                minecraft.execute(() -> {
                    styleName = name;
                    saved = true;
                    Styles.set(Styles.setup().withLeaf(p, "pack:" + key));
                    io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.notify.editor.saved", name));
                });
            } catch (java.io.IOException | RuntimeException ex) {
                CosmeticsMod.LOGGER.warn("[Sol] Couldn't save the texture: {}", ex.toString());
                minecraft.execute(() -> io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.notify.editor.cant_save")));
            }
        });
    }

    /** The texture as a PNG in client/styles/exports. */
    private void export() {
        EditorImage e = img;
        if (e == null || path == null) {
            return;
        }
        Pixels.Image tex = e.texture();
        String file = path.substring(path.lastIndexOf('/') + 1).replace(".png", "") + "-" + System.currentTimeMillis() / 1000L + ".png";
        SolExecutors.IO.execute(() -> {
            try {
                Path out = Styles.userFolder().resolve("exports").resolve(file);
                Files.createDirectories(out.getParent());
                io.github.georgwav.cosmetics.sol.util.AtomicFiles.write(out, Builder.encode(tex));
                minecraft.execute(() -> io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.notify.editor.exported", out.toString())));
            } catch (java.io.IOException | RuntimeException ex) {
                minecraft.execute(() -> io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(tr("sol.notify.editor.cant_save")));
            }
        });
    }

    /** Leaving without saving: the game's texture put back as it was. */
    private void restoreLive() {
        if (!saved && liveTried && original != null && path != null) {
            Styles.writeLive(path, original);
        }
        liveTried = false;
    }

    private void leave() {
        onClose();
    }

    @Override
    protected boolean back() {
        if (picker != null) {
            picker = null;
            return true;
        }
        EditorImage e = img;
        if (e != null && e.selection() != null) {
            e.selectNone();
            return true;
        }
        return false;
    }

    @Override
    protected void closed() {
        restoreLive();
        release();
        if (cube != null) {
            cube.release();
            cube = null;
        }
    }

    @Override
    protected boolean blurs() {
        return false;
    }

    /** For the dev scenario: a few pixels painted in, as a stroke would. */
    public void paintForShot(int colour) {
        EditorImage e = img;
        if (e == null) {
            return;
        }
        e.begin();
        for (int i = 0; i < Math.min(e.width(), e.height()); i += 2) {
            e.pencil(i, i, 1, colour, true, false);
        }
        e.end();
        changed(e);
    }

    /** Whether the texture has been read (the scenario waits for it). */
    public boolean ready() {
        return img != null;
    }

    /** Whether the last change went into the game live. */
    public boolean live() {
        return live;
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
