package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.styles.Choices;
import io.github.georgwav.cosmetics.sol.styles.Mixer;
import io.github.georgwav.cosmetics.sol.styles.PartTable;
import io.github.georgwav.cosmetics.sol.styles.StyleSetup;
import io.github.georgwav.cosmetics.sol.styles.StylesIndex;
import io.github.georgwav.cosmetics.sol.styles.Thumbs;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.util.Colors;

/**
 * The Styles tab's preview (B.2.9): the tile you point at (else the part's current choice), shown the way the part
 * appears: an item turning on a turntable, armour on a mannequin turning, a block as a little cube, fire burning on a
 * block, the sun in a sky, a GUI texture laid out, particles bursting. Drawn on whole screen pixels per texel, in the
 * pixel window's preview pane. Pictures come from {@link Thumbs} (made off the render thread).
 */
final class StylesPreview {
    private StylesPreview() {
    }

    /** The texture editor's picture, shown for its texture wherever the preview needs that texture (or null). */
    record Edited(String path, Thumbs.Thumb full, Thumbs.Thumb cube) {
    }

    static Edited edited;

    /** A source's picture: the editor's for the texture it edits, else the made one. */
    private static Thumbs.Thumb pic(Mixer.Source s, Thumbs.Mode mode) {
        Edited e = edited;
        if (e != null && s != null && e.path().equals(pathOf(s))) {
            return mode == Thumbs.Mode.CUBE ? e.cube() : e.full();
        }
        return Thumbs.get(s, mode);
    }

    /** The texture path a source is (a made file's, its input's). */
    static String pathOf(Mixer.Source s) {
        return switch (s) {
            case Mixer.FromGame f -> f.path();
            case Mixer.FromMod f -> "cosmeticsmod:" + f.path();
            case Mixer.FromPack f -> f.entry().startsWith("assets/minecraft/") ? f.entry().substring("assets/minecraft/".length()) : f.entry();
            case Mixer.Made m -> pathOf(m.input());
        };
    }

    /** Draws the pane at (x, y, w, h) for the part and tile ({@code tile} may be null). */
    static void draw(Gfx g, Tokens tk, float x, float y, float w, float h, PartTable.Part part, Choices.Tile tile,
                     StylesIndex.Snapshot idx, StyleSetup setup, int themeTint, float time, String title, String line) {
        draw(g, tk, x, y, w, h, part, tile, idx, setup, themeTint, time, title, line, null);
    }

    /** As above, shown as {@code as} (the editor shows the texture it edits the way that texture appears). */
    static void draw(Gfx g, Tokens tk, float x, float y, float w, float h, PartTable.Part part, Choices.Tile tile,
                     StylesIndex.Snapshot idx, StyleSetup setup, int themeTint, float time, String title, String line, Look as) {
        PixelTokens p = PixelTokens.get();
        Pix.pane(g, p, x, y, w, h);
        // A little sky behind it: the theme's top to its horizon, the floor below.
        // The floor above the caption, so what stands on it is never under the caption.
        float captionH = line == null || line.isEmpty() ? 40f : 58f;
        float floor = y + h - 12f - captionH - 14f;
        g.vgradient(x + Pix.E, y + Pix.E, w - 2f * Pix.E, floor - y - Pix.E, Colors.mixRgb(tk.skyTop, 0xFF000000, 0.45f),
                Colors.mixRgb(tk.skyHorizon, 0xFF000000, 0.25f));
        g.vgradient(x + Pix.E, floor, w - 2f * Pix.E, y + h - Pix.E - floor, Colors.mixRgb(p.c1, 0xFF000000, 0.2f), 0xFF050506);
        Pix.fill(g, x + Pix.E, floor, w - 2f * Pix.E, Pix.E, Colors.withAlpha(tk.glow, 0.55f));
        float t = SolScreen.calm() ? 0.6f : time;
        if (tile != null && part != null) {
            g.clip(x + Pix.E, y + Pix.E, x + w - Pix.E, y + h - Pix.E);
            float cx = x + w / 2f, room = Math.min(w - 40f, floor - y - 16f);
            switch (as != null ? as : look(part)) {
                case ARMOUR -> mannequin(g, p, part, tile, idx, setup, themeTint, cx, floor, room, t);
                case BLOCK -> cube(g, tile.picture(), cx, floor, room, t);
                case FIRE -> fire(g, tile, idx, setup, themeTint, part, cx, floor, room, t);
                case SKY -> sky(g, tk, tile.picture(), cx, y + (floor - y) * 0.45f, room, t);
                case GUI -> gui(g, tile.picture(), cx, y + (floor - y) * 0.5f + 6f, w - 30f, floor - y - 16f);
                case PARTICLE -> burst(g, tile.picture(), cx, y + (floor - y) * 0.55f, room, t);
                default -> turntable(g, part, tile.picture(), cx, floor, room, t);
            }
            g.endClip();
        }
        Pix.paneEdge(g, p, x, y, w, h);
        Pix.caption(g, p, x, y, w, h, title, line);
    }

    enum Look {
        ITEM, ARMOUR, BLOCK, FIRE, SKY, GUI, PARTICLE
    }

    static Look look(PartTable.Part part) {
        if (armourLayer(part, true) != null || armourLayer(part, false) != null) {
            return Look.ARMOUR;
        }
        return switch (part.kind()) {
            case BLOCK -> Look.BLOCK;
            case OVERLAY -> part.id().equals("fire") ? Look.FIRE : Look.ITEM;
            case SKY -> Look.SKY;
            case GUI -> Look.GUI;
            case PARTICLE -> Look.PARTICLE;
            default -> Look.ITEM;
        };
    }

    /** How a single texture appears (the editor's preview): by where it lives. */
    static Look lookOf(String path) {
        if (path.contains("equipment/humanoid") || path.contains("models/armor/")) {
            return Look.ARMOUR;
        }
        if (path.startsWith("textures/item/")) {
            return Look.ITEM;
        }
        if (path.startsWith("textures/block/fire_") || path.startsWith("textures/block/soul_fire_")) {
            return Look.FIRE;
        }
        if (path.startsWith("textures/block/")) {
            return Look.BLOCK;
        }
        if (path.startsWith("textures/environment/")) {
            return Look.SKY;
        }
        if (path.startsWith("textures/particle/")) {
            return Look.PARTICLE;
        }
        return Look.GUI;
    }

    /** The part's worn armour texture: the body layer, or the leggings' layer. */
    static String armourLayer(PartTable.Part part, boolean body) {
        String first = null;
        Edited e = edited;
        for (String path : part.paths()) {
            if (body ? path.contains("equipment/humanoid/") || path.endsWith("_layer_1.png")
                    : path.contains("equipment/humanoid_leggings/") || path.endsWith("_layer_2.png")) {
                // The texture being edited first, then diamond (the tiles' picture is the diamond chestplate).
                if (e != null && e.path().equals(path)) {
                    return path;
                }
                if (first == null || path.contains("diamond") && !first.contains("diamond")) {
                    first = path;
                }
            }
        }
        return first;
    }

    /** Where the mannequin was last drawn (screen pixels), so a click on it can find its texel. */
    private static float manX, manTop, manC;
    private static int manK;
    private static boolean manShown;

    /**
     * The texel of the body ({@code body}) or leggings layer under a screen point on the mannequin last drawn, as
     * {u, v}, or null when the point isn't on it (B.2.12: paint on the model).
     */
    static int[] texelAt(float sx, float sy, boolean body, int texW, int texH) {
        if (!manShown || Math.abs(manC) < 0.2f) {
            return null;
        }
        boolean back = manC < 0f;
        // Front to back as drawn: the body layer's boxes over the leggings'.
        int from = body ? 0 : 6, to = body ? 6 : FIGURE.length;
        for (int i = to - 1; i >= from; i--) {
            Box bx = FIGURE[i];
            float x0 = manX + (bx.x() - 8f) * manK * manC, x1 = manX + (bx.x() + bx.w() - 8f) * manK * manC;
            float lo = Math.min(x0, x1), hi = Math.max(x0, x1);
            float y0 = manTop + bx.y() * manK, y1 = y0 + bx.h() * manK;
            if (sx < lo || sx >= hi || sy < y0 || sy >= y1) {
                continue;
            }
            float fx = (sx - lo) / (hi - lo);
            if (back) {
                fx = 1f - fx;
            }
            int u = (back ? bx.bu() : bx.fu()) + Math.min(bx.w() - 1, (int) (fx * bx.w()));
            int v = (back ? bx.bv() : bx.fv()) + Math.min(bx.h() - 1, (int) ((sy - y0) / manK));
            // The layer's own size (a 2x texture has its boxes at twice the place).
            return new int[]{u * texW / 64, v * texH / 32};
        }
        return null;
    }

    /** Whole screen pixels per texel so a {@code tw} x {@code th} picture fits {@code room} design pixels. */
    private static int scale(Gfx g, int tw, int th, float room) {
        float px = room * g.d.u;
        return Math.max(1, (int) Math.floor(Math.min(px / tw, px / th)));
    }

    /** An item standing on the floor, turning: its back layer darker beside it as its depth. */
    private static void turntable(Gfx g, PartTable.Part part, Mixer.Source src, float cx, float floor, float room, float t) {
        Thumbs.Thumb th = pic(src, Thumbs.Mode.FULL);
        if (th == null) {
            return;
        }
        // The first frame; the shield shows its front plate.
        float u0 = 0f, v0 = 0f, u1 = 1f, v1 = Math.min(1f, th.w() / (float) th.h());
        int tw = th.w(), tht = Math.min(th.w(), th.h());
        if (part.id().equals("shield") && th.w() >= 64) {
            u0 = 1f / 64f;
            v0 = 1f / 64f;
            u1 = 13f / 64f;
            v1 = 23f / 64f;
            tw = 12;
            tht = 22;
        }
        int k = scale(g, tw, tht, room);
        float sw = tw * k, sh = tht * k;
        // Swaying a little either way (never edge on), so the picture always reads.
        float turn = (float) Math.sin(t * 0.7f) * 0.95f;
        float c = (float) Math.cos(turn), s = (float) Math.sin(turn);
        float scx = Math.round(g.d.sx(cx)), base = Math.round(g.d.sy(floor)) - Math.round(4f * g.d.u);
        float bob = Math.round((float) Math.sin(t * 2.2f) * k);
        float top = base - sh + bob;
        // Its shadow on the floor.
        float shw = Math.max(sw * 0.35f, sw * Math.abs(c) * 0.8f);
        g.rectPx(Math.round(scx - shw / 2f), base + Math.round(2f * g.d.u), Math.round(scx + shw / 2f), base + Math.round(5f * g.d.u), 0x66000000);
        float half = sw * Math.abs(c) / 2f;
        float depth = s * k * 1.5f;
        boolean back = c < 0f;
        float a0 = back ? u1 : u0, a1 = back ? u0 : u1;
        // Depth: the same picture darker, one texel and a half behind.
        g.imageScreen(th.texture(), Math.round(scx - half + depth), top, Math.round(scx + half + depth), top + sh, a0, v0, a1, v1, 0xFF5A5A60, false);
        if (half >= 1f) {
            g.imageScreen(th.texture(), Math.round(scx - half), top, Math.round(scx + half), top + sh, a0, v0, a1, v1,
                    back ? 0xFFB0B0B8 : 0xFFFFFFFF, false);
        }
    }

    /** A block as a little cube, bobbing a texel. */
    private static void cube(Gfx g, Mixer.Source src, float cx, float floor, float room, float t) {
        Thumbs.Thumb th = pic(src, Thumbs.Mode.CUBE);
        if (th == null) {
            return;
        }
        int k = scale(g, th.w(), th.h(), room);
        float sw = th.w() * k, sh = th.h() * k;
        float x0 = Math.round(g.d.sx(cx) - sw / 2f), base = Math.round(g.d.sy(floor)) - Math.round(4f * g.d.u);
        float bob = Math.round((float) Math.sin(t * 1.6f) * k);
        g.rectPx(x0 + sw * 0.15f, base + Math.round(2f * g.d.u), x0 + sw * 0.85f, base + Math.round(5f * g.d.u), 0x66000000);
        g.imageScreen(th.texture(), x0, base - sh + bob, x0 + sw, base + bob, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
    }

    /** Fire burning on a block of netherrack, every frame in turn. */
    private static void fire(Gfx g, Choices.Tile tile, StylesIndex.Snapshot idx, StyleSetup setup, int themeTint, PartTable.Part part,
                             float cx, float floor, float room, float t) {
        Mixer.Source block = Choices.file(new Choices.Tile("", "game", "", null, Choices.Group.VANILLA, java.util.List.of(), null),
                "textures/block/netherrack.png", part, idx, setup, themeTint);
        Thumbs.Thumb cubeTh = pic(block, Thumbs.Mode.CUBE);
        Thumbs.Thumb fireTh = pic(tile.picture(), Thumbs.Mode.FULL);
        int k = scale(g, 33, 49, room);
        float scx = Math.round(g.d.sx(cx)), base = Math.round(g.d.sy(floor)) - Math.round(4f * g.d.u);
        float cubeTop = base;
        if (cubeTh != null) {
            float sw = cubeTh.w() * k, sh = cubeTh.h() * k;
            g.imageScreen(cubeTh.texture(), scx - sw / 2f, base - sh, scx + sw / 2f, base, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
            cubeTop = base - sh + 8f * k;
        }
        if (fireTh != null && fireTh.w() > 0) {
            int frames = Math.max(1, fireTh.h() / fireTh.w());
            int f = (int) (t * 20f) % frames;
            float fw = 16f * k, fh = 16f * k;
            float v0 = f / (float) frames, v1 = (f + 1) / (float) frames;
            g.imageScreen(fireTh.texture(), scx - fw / 2f, cubeTop - fh, scx + fw / 2f, cubeTop, 0f, v0, 1f, v1, 0xFFFFFFFF, false);
        }
    }

    /** The sun in the sky, a soft glow round it. */
    private static void sky(Gfx g, Tokens tk, Mixer.Source src, float cx, float cy, float room, float t) {
        Thumbs.Thumb th = pic(src, Thumbs.Mode.FULL);
        g.glowSprite(cx, cy, room * 0.55f, room * 0.55f, Colors.withAlpha(tk.glow, 0.45f));
        if (th == null) {
            return;
        }
        int k = scale(g, th.w(), th.h(), room * 0.6f);
        float sw = th.w() * k, sh = th.h() * k;
        float x0 = Math.round(g.d.sx(cx) - sw / 2f), y0 = Math.round(g.d.sy(cy) - sh / 2f);
        g.imageScreen(th.texture(), x0, y0, x0 + sw, y0 + sh, 0f, 0f, 1f, 1f, 0xFFFFFFFF, true);
    }

    /** A GUI texture laid out at whole pixels (the inventory's used corner only). */
    private static void gui(Gfx g, Mixer.Source src, float cx, float cy, float rw, float rh) {
        Thumbs.Thumb th = pic(src, Thumbs.Mode.FULL);
        if (th == null) {
            return;
        }
        int tw = th.w(), tht = th.h();
        float u1 = 1f, v1 = 1f;
        if (tw == 256 && tht == 256) {
            tw = 176;
            tht = 166;
            u1 = 176f / 256f;
            v1 = 166f / 256f;
        }
        float px = Math.min(rw / tw, rh / tht) * g.d.u;
        float k = px >= 1f ? (float) Math.floor(px) : px;
        float sw = tw * k, sh = tht * k;
        float x0 = Math.round(g.d.sx(cx) - sw / 2f), y0 = Math.round(g.d.sy(cy) - sh / 2f);
        g.imageScreen(th.texture(), x0, y0, x0 + sw, y0 + sh, 0f, 0f, u1, v1, 0xFFFFFFFF, false);
    }

    /** Particles bursting from the middle, over and over. */
    private static void burst(Gfx g, Mixer.Source src, float cx, float cy, float room, float t) {
        Thumbs.Thumb th = pic(src, Thumbs.Mode.FULL);
        if (th == null) {
            return;
        }
        int tw = th.w(), tht = Math.min(th.w(), th.h());
        float v1 = tht / (float) th.h();
        int k = Math.max(1, scale(g, tw, tht, room) / 3);
        float scx = g.d.sx(cx), scy = g.d.sy(cy), r = room * g.d.u * 0.42f;
        for (int i = 0; i < 8; i++) {
            float ph = (t * 0.7f + i * 0.125f) % 1f;
            float ang = i * 0.785f + 0.3f;
            float px = Math.round(scx + (float) Math.cos(ang) * r * ph), py = Math.round(scy + (float) Math.sin(ang) * r * ph);
            float a = 1f - ph;
            int kk = Math.max(1, Math.round(k * (1.2f - 0.5f * ph)));
            float sw = tw * kk, sh = tht * kk;
            g.imageScreen(th.texture(), px - sw / 2f, py - sh / 2f, px + sw / 2f, py + sh / 2f, 0f, 0f, 1f, v1,
                    Colors.withAlpha(0xFFFFFFFF, a), false);
        }
    }

    // ------------------------------------------------------------------ the mannequin

    /** A box of the figure (16 x 32 texels, front view): where it is and its front and back on the armour texture. */
    private record Box(int x, int y, int w, int h, int fu, int fv, int bu, int bv, boolean layer2) {
    }

    private static final Box[] FIGURE = {
            new Box(4, 0, 8, 8, 8, 8, 24, 8, false),     // head
            new Box(4, 8, 8, 12, 20, 20, 32, 20, false), // body
            new Box(0, 8, 4, 12, 44, 20, 52, 20, false), // right arm
            new Box(12, 8, 4, 12, 44, 20, 52, 20, false), // left arm
            new Box(4, 20, 4, 12, 4, 20, 12, 20, false), // right boot
            new Box(8, 20, 4, 12, 4, 20, 12, 20, false), // left boot
            new Box(4, 8, 8, 12, 20, 20, 32, 20, true),  // leggings' belt
            new Box(4, 20, 4, 12, 4, 20, 12, 20, true),  // right leg
            new Box(8, 20, 4, 12, 4, 20, 12, 20, true),  // left leg
    };

    /** Armour on a dark stand-in figure, turning (its back shown when it faces away). */
    private static void mannequin(Gfx g, PixelTokens p, PartTable.Part part, Choices.Tile tile, StylesIndex.Snapshot idx, StyleSetup setup,
                                  int themeTint, float cx, float floor, float room, float t) {
        String l1 = armourLayer(part, true), l2 = armourLayer(part, false);
        Thumbs.Thumb a = l1 == null ? null : pic(Choices.file(tile, l1, part, idx, setup, themeTint), Thumbs.Mode.FULL);
        Thumbs.Thumb b = l2 == null ? null : pic(Choices.file(tile, l2, part, idx, setup, themeTint), Thumbs.Mode.FULL);
        int k = scale(g, 16, 32, room);
        float c = (float) Math.cos(t * 0.9f);
        boolean back = c < 0f;
        float scx = Math.round(g.d.sx(cx)), base = Math.round(g.d.sy(floor)) - Math.round(4f * g.d.u);
        float top = base - 32f * k;
        manX = scx;
        manTop = top;
        manK = k;
        manC = c;
        manShown = true;
        g.rectPx(scx - 6f * k, base + Math.round(2f * g.d.u), scx + 6f * k, base + Math.round(5f * g.d.u), 0x66000000);
        // The figure.
        for (int i = 0; i < 6; i++) {
            Box bx = FIGURE[i];
            box(g, null, bx, scx, top, k, c, back, Colors.mixRgb(p.c1, 0xFF000000, i == 0 ? 0f : 0.15f));
        }
        // The armour over it: the leggings first, the body layer on top.
        if (b != null) {
            for (int i = 6; i < FIGURE.length; i++) {
                box(g, b, FIGURE[i], scx, top, k, c, back, 0);
            }
        }
        if (a != null) {
            for (int i = 0; i < 6; i++) {
                box(g, a, FIGURE[i], scx, top, k, c, back, 0);
            }
        }
    }

    private static void box(Gfx g, Thumbs.Thumb tex, Box bx, float scx, float top, int k, float c, boolean back, int fill) {
        float x0 = scx + (bx.x() - 8f) * k * c, x1 = scx + (bx.x() + bx.w() - 8f) * k * c;
        float y0 = top + bx.y() * k, y1 = y0 + bx.h() * k;
        if (x1 < x0) {
            float s = x0;
            x0 = x1;
            x1 = s;
        }
        x0 = Math.round(x0);
        x1 = Math.round(x1);
        if (x1 - x0 < 1f) {
            return;
        }
        if (tex == null) {
            g.rectPx(x0, y0, x1, y1, fill);
            return;
        }
        float tw = tex.w(), th = tex.h();
        int u = back ? bx.bu() : bx.fu(), v = back ? bx.bv() : bx.fv();
        float u0 = u / tw, u1 = (u + bx.w()) / tw, v0 = v / th, v1 = (v + bx.h()) / th;
        if (back) {
            float s = u0;
            u0 = u1;
            u1 = s;
        }
        g.imageScreen(tex.texture(), x0, y0, x1, y1, u0, v0, u1, v1, back ? 0xFFC8C8D0 : 0xFFFFFFFF, false);
    }
}
