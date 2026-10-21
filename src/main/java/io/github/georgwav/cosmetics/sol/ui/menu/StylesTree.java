package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.sol.styles.Mixer;
import io.github.georgwav.cosmetics.sol.styles.PackInfo;
import io.github.georgwav.cosmetics.sol.styles.PartTable;
import io.github.georgwav.cosmetics.sol.styles.StyleSetup;
import io.github.georgwav.cosmetics.sol.styles.Styles;
import io.github.georgwav.cosmetics.sol.styles.StylesIndex;
import io.github.georgwav.cosmetics.sol.styles.TextureTree;
import io.github.georgwav.cosmetics.sol.styles.Thumbs;
import io.github.georgwav.cosmetics.sol.styles.VanillaIndex;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * The texture tree under More (B.2.11): every texture of the game (and every one a pack adds), on its branch:
 * combat, weapons and tools, armour, blocks by kind, items, mobs, GUI by kind, particles, sky and weather, fonts. The
 * branches on the left; the branch's textures on the right, each with what it shows now and the packs that have it.
 * Picking one puts that single texture on from that pack (a leaf, which wins over parts and the base); picking it
 * again drops it. With a pack filter, the branches it changes are marked and the rest dimmed. Only the rows in view
 * are drawn.
 */
final class StylesTree {
    private static final float ROW = 44f, LIST_W = 230f;

    private String branch = "blocks";
    /** Branch ("trunk" and "trunk/sub") to its textures, sorted; made again when the packs change. */
    private final Map<String, List<String>> leaves = new LinkedHashMap<>();
    private final Map<String, List<String>> subs = new LinkedHashMap<>();
    private StylesIndex.Snapshot builtFor;
    private final Scroll list = new Scroll(), rows = new Scroll();
    private float listX, listW, listY, listH;

    private void build(PartTable table, StylesIndex.Snapshot idx) {
        builtFor = idx;
        leaves.clear();
        subs.clear();
        Map<String, java.util.Set<String>> by = new TreeMap<>();
        java.util.Set<String> all = new java.util.TreeSet<>();
        for (String p : idx.vanilla().all().keySet()) {
            if (VanillaIndex.isTexture(p)) {
                all.add(p);
            }
        }
        for (PackInfo pk : idx.packs()) {
            for (String p : pk.files().keySet()) {
                if (VanillaIndex.isTexture(p) && !p.contains(":")) {
                    all.add(p);
                }
            }
        }
        for (String p : all) {
            String b = TextureTree.branch(p, table);
            by.computeIfAbsent(b, k -> new java.util.TreeSet<>()).add(p);
            String trunk = b.split("/")[0];
            if (!b.equals(trunk)) {
                by.computeIfAbsent(trunk, k -> new java.util.TreeSet<>()).add(p);
                subs.computeIfAbsent(trunk, k -> new ArrayList<>());
                if (!subs.get(trunk).contains(b)) {
                    subs.get(trunk).add(b);
                }
            }
        }
        for (String trunk : TextureTree.TRUNKS) {
            if (by.containsKey(trunk)) {
                leaves.put(trunk, new ArrayList<>(by.get(trunk)));
            }
            for (String sub : subs.getOrDefault(trunk, List.of())) {
                leaves.put(sub, new ArrayList<>(by.get(sub)));
            }
        }
        if (!leaves.containsKey(branch)) {
            branch = leaves.isEmpty() ? "blocks" : leaves.keySet().iterator().next();
        }
    }

    void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, PartTable table, StylesIndex.Snapshot idx,
              StyleSetup setup, String filter) {
        if (idx != builtFor) {
            build(table, idx);
        }
        PixelTokens p = PixelTokens.get();
        PackInfo fp = filter == null ? null : idx.get(filter);
        // The branches.
        Pix.recess(g, p, x, y, LIST_W, h);
        listX = x;
        listW = LIST_W;
        listY = y;
        listH = h;
        g.clip(x + Pix.E, y + Pix.E, x + LIST_W - Pix.E, y + h - Pix.E);
        float ry = y + Pix.E + 6f - list.value();
        SolFont tf = g.font(SolFont.Family.PIXEL_BOLD, 11f), sf = g.font(SolFont.Family.MEDIUM, 12.5f), cf = g.font(SolFont.Family.PIXEL, 10f);
        String openTrunk = branch.split("/")[0];
        for (Map.Entry<String, List<String>> e : leaves.entrySet()) {
            String b = e.getKey();
            boolean trunk = !b.contains("/");
            if (!trunk && !b.startsWith(openTrunk + "/")) {
                continue;
            }
            float rh = trunk ? 32f : 28f;
            boolean on = b.equals(branch), over = ui.over(x + Pix.E, ry, LIST_W - 2f * Pix.E, rh);
            if (on || over) {
                Pix.fill(g, x + Pix.E + 3f, ry, LIST_W - 2f * Pix.E - 6f, rh, on ? 0x26FFFFFF : 0x14FFFFFF);
            }
            if (on) {
                Pix.fill(g, x + Pix.E + 3f, ry + 6f, 3f, rh - 12f, p.acc);
            }
            boolean changes = fp != null && changes(fp, e.getValue());
            int c = fp != null && !changes ? PixelTokens.INK3 : on ? p.acc : PixelTokens.INK;
            String name = SolMenu.tr("sol.styles.tree." + b.replace('/', '.'));
            float tx = x + Pix.E + (trunk ? 14f : 30f);
            if (trunk) {
                String up = name.toUpperCase(Locale.ROOT);
                SolFont ff = g.fitting(up, LIST_W - 84f, 11f, 10f, 8f);
                g.textMid(ff, g.fitLabel(ff, up, LIST_W - 84f), tx, ry + rh / 2f, c);
            } else {
                g.textMid(sf, g.fit(sf, name, LIST_W - 100f), tx, ry + rh / 2f, c);
            }
            String count = String.format(Locale.ROOT, "%,d", e.getValue().size());
            g.textRight(cf, count, x + LIST_W - Pix.E - 22f, ry + rh / 2f, PixelTokens.INK3);
            if (changes) {
                Pix.fill(g, x + LIST_W - Pix.E - 14f, ry + rh / 2f - 2f, 4f, 4f, p.acc);
            }
            ui.click(x + Pix.E, ry, LIST_W - 2f * Pix.E, rh, () -> {
                branch = b;
                rows.reset();
            });
            ry += rh;
        }
        g.endClip();
        list.setMax(ry + list.value() - (y + h) + 10f);
        list.step(0.016f);
        list.bar(g, x + LIST_W - Pix.E - 4f, y + Pix.E + 4f, h - 2f * Pix.E - 8f);
        // The branch's textures, only the rows in view.
        float lx = x + LIST_W + 12f, lw = w - LIST_W - 12f;
        Pix.recess(g, p, lx, y, lw, h);
        List<String> ls = leaves.getOrDefault(branch, List.of());
        g.clip(lx + Pix.E, y + Pix.E, lx + lw - Pix.E, y + h - Pix.E);
        float top = y + Pix.E + 6f;
        int first = Math.max(0, (int) (rows.value() / ROW));
        int last = Math.min(ls.size(), first + (int) (h / ROW) + 2);
        for (int i = first; i < last; i++) {
            leaf(g, ui, tk, p, lx + Pix.E + 8f, top + i * ROW - rows.value(), lw - 2f * Pix.E - 24f, ls.get(i), idx, setup, fp);
        }
        g.endClip();
        rows.setMax(ls.size() * ROW + 12f - h);
        rows.step(0.016f);
        rows.bar(g, lx + lw - Pix.E - 4f, y + Pix.E + 4f, h - 2f * Pix.E - 8f);
    }

    private static boolean changes(PackInfo pack, List<String> paths) {
        for (String s : paths) {
            if (pack.has(s)) {
                return true;
            }
        }
        return false;
    }

    /** One texture: what it shows now, its name, where it comes from, and the packs that have it. */
    private void leaf(Gfx g, Ui ui, Tokens tk, PixelTokens p, float x, float y, float w, String path, StylesIndex.Snapshot idx,
                      StyleSetup setup, PackInfo fp) {
        float cy = y + ROW / 2f - 2f;
        Mixer.Source now = Styles.current(path);
        Thumbs.Thumb th = Thumbs.get(now, Thumbs.Mode.TILE);
        float box = 32f;
        Pix.fill(g, x, cy - box / 2f, box, box, 0xFF1E1E22);
        if (th != null) {
            int s = Math.max(16, (int) Math.floor(box * g.d.u / 16f) * 16);
            float sx = Math.round(g.d.sx(x + box / 2f) - s / 2f), sy = Math.round(g.d.sy(cy) - s / 2f);
            g.imageScreen(th.texture(), sx, sy, sx + s, sy + s, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        }
        SolFont nf = g.font(SolFont.Family.MEDIUM, 13f), sf = g.font(SolFont.Family.MEDIUM, 11.5f);
        String name = path.substring(path.lastIndexOf('/') + 1).replace(".png", "");
        String chosen = setup.leaves().get(path);
        float nx = x + box + 12f;
        g.text(nf, g.fit(nf, name, 200f), nx, cy - 2f, PixelTokens.INK);
        String from = chosen == null ? SolMenu.tr("sol.styles.tree.from_look") : chosen.equals("game") ? SolMenu.tr("sol.styles.vanilla")
                : idx.get(chosen.substring(5)) == null ? SolMenu.tr("sol.styles.missing") : StylesSection.clean(idx.get(chosen.substring(5)).name());
        g.text(sf, g.fit(sf, from, 200f), nx, cy + 13f, chosen != null ? p.acc : PixelTokens.INK3);
        // The sources: the game's own, then each pack that has its own copy.
        float cx = nx + 214f;
        List<String> sources = new ArrayList<>();
        sources.add("game");
        for (PackInfo pk : idx.packs()) {
            if (pk.has(path)) {
                sources.add("pack:" + pk.key());
            }
        }
        for (String src : sources) {
            String label = src.equals("game") ? SolMenu.tr("sol.styles.vanilla") : StylesSection.clean(idx.get(src.substring(5)).name());
            float cw = ui.chipWidth(label);
            if (cx + cw > x + w) {
                SolFont mf = g.font(SolFont.Family.PIXEL, 10f);
                g.textMid(mf, "+" + (sources.size() - sources.indexOf(src)), cx, cy, PixelTokens.INK3);
                break;
            }
            boolean on = src.equals(chosen);
            cx += ui.chip(tk, cx, cy, label, on, () -> Styles.set(Styles.setup().withLeaf(path, on ? null : src))) + 6f;
            if (fp != null && src.equals("pack:" + fp.key())) {
                Pix.fill(g, cx - 12f, cy + 9f, 6f, 3f, p.acc);
            }
        }
        Pix.fill(g, x, y + ROW - 1f, w, 1f, 0x14FFFFFF);
    }

    boolean scroll(float amount, float mx) {
        if (mx >= listX && mx <= listX + listW) {
            return list.wheel(amount);
        }
        return rows.wheel(amount);
    }
}
