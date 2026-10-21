package io.github.georgwav.cosmetics.sol.ui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.styles.Choices;
import io.github.georgwav.cosmetics.sol.styles.PackInfo;
import io.github.georgwav.cosmetics.sol.styles.PartTable;
import io.github.georgwav.cosmetics.sol.styles.Pixels;
import io.github.georgwav.cosmetics.sol.styles.ShareCode;
import io.github.georgwav.cosmetics.sol.styles.StyleSetup;
import io.github.georgwav.cosmetics.sol.styles.Styles;
import io.github.georgwav.cosmetics.sol.styles.StylesIndex;
import io.github.georgwav.cosmetics.sol.styles.StylesPrefs;
import io.github.georgwav.cosmetics.sol.styles.TextureTree;
import io.github.georgwav.cosmetics.sol.styles.Thumbs;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.ColorPicker;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Styles (B.2.9): restyle one thing at a time, from any pack. The top bar (how many packs the look is mixed from,
 * the apply pill, presets, share codes, a pack filter, Reset all); the base row (the whole pack under everything);
 * Bauloo's tabs in its order, then More (Sol's other parts), Your look, Extras and Cosmetics; each part's tiles in
 * the order of B.2.11 (part packs, then the base, Vanilla and Off, Sol's own, and the full packs folded); the preview
 * of the tile you point at; the colour panel of the part you picked. Every choice goes to {@link Styles#set}, which
 * applies it live when it can and with one short reload when it can't.
 */
final class StylesSection implements Section {
    /** The tabs: Bauloo's ten in its order, then Extras and Cosmetics (B.2.9), More, and Your look. */
    static final String[] TABS = {"crystal", "anchor", "armor", "tools", "inventory", "fire", "sky", "deepslate", "stone", "snow",
            "extras", "cosmetics", "more", "look"};
    private static final String[] SORTS = {"pack", "colour", "recent", "favourites"};
    /** The right column (preview and colour panel), the tiles' size and gap. */
    private static final float RIGHT_W = 304f, TILE_W = 100f, TILE_H = 120f, GAP = 8f, PREVIEW_H = 206f;

    private String tab = "crystal";
    /** The part the colour panel shows. */
    private String part = "crystal";
    /** The tile pointed at this frame, and its part (the preview shows it). */
    private Choices.Tile hover;
    private PartTable.Part hoverPart;
    private final Scroll scroll = new Scroll(), panelScroll = new Scroll(), dropScroll = new Scroll();
    private final TextField search = new TextField(48, q -> scroll.reset());
    /** The list open under a top-bar button ("presets", "filter", "sort", "base"), or null. */
    private String drop;
    private float dropX, dropY;
    /** The pack whose changes are marked, or null. */
    private String filter;
    /** The pack whose card is open, or null; its branches (counted once). */
    private String card;
    private final Map<String, Integer> cardBranches = new LinkedHashMap<>();
    /** The colour being picked ("tint", "core", "frame", "glow"), or null; and its picker. */
    private String picking;
    private ColorPicker picker;
    private long openedAt = -1L;
    /** More shows the whole texture tree instead of Sol's parts. */
    private boolean treeMode;
    private final StylesTree tree = new StylesTree();

    /** The tiles per part, made again only when the packs, the setup, the theme colour or the sort change. */
    private final Map<String, List<Choices.Tile>> tiles = new HashMap<>();
    private StylesIndex.Snapshot tilesFor;
    private StyleSetup tilesSetup;
    private int tilesTint;
    private String tilesSort;
    /** Each part's words for search (its name, its items' and blocks' names, its branch), lower case. */
    private final Map<String, String> words = new HashMap<>();

    @Override
    public void opened() {
        openedAt = System.nanoTime();
        scroll.reset();
    }

    @Override
    public void closed() {
        Thumbs.clear();
        tiles.clear();
        tilesFor = null;
    }

    /** For the dev scenario: open on a tab ("tools", "look"...), the full packs unfolded with "tools_full". */
    void stage(String what) {
        if (what.equals("tree")) {
            tab = "more";
            treeMode = true;
            return;
        }
        String t = what.endsWith("_full") ? what.substring(0, what.length() - 5) : what;
        for (String id : TABS) {
            if (id.equals(t)) {
                tab = id;
            }
        }
        List<PartTable.Part> ps = PartTable.get().inTab(tab);
        if (!ps.isEmpty()) {
            part = ps.get(0).id();
        }
        if (what.endsWith("_full") && !Styles.setup().fullOpen()) {
            Styles.set(Styles.setup().withFullOpen(true));
        }
    }

    // ------------------------------------------------------------------ drawing

    /** The menu drawing the tab (for the pointer when the wheel turns). */
    private SolMenu menu;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        this.menu = menu;
        long drawStart = openedAt >= 0L ? System.nanoTime() : 0L;
        PartTable table = PartTable.get();
        StylesIndex.Snapshot idx = Styles.index();
        StyleSetup setup = Styles.setup();
        int tint = ThemeState.tokens().accent & 0xFFFFFF;
        hover = null;
        hoverPart = null;
        boolean covered = drop != null || card != null || picking != null;
        float smx = ui.mx, smy = ui.my;
        if (covered) {
            // Something is open over the tab: nothing under it lights up.
            ui.mx = -10000f;
            ui.my = -10000f;
        }
        topBar(menu, g, ui, tk, x, y, w, idx, setup);
        if (idx == null) {
            SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 13f);
            g.textCentre(f, SolMenu.tr("sol.styles.loading"), x + w / 2f, y + h / 2f, PixelTokens.INK2);
            ui.mx = smx;
            ui.my = smy;
            return;
        }
        refreshTiles(table, idx, setup, tint);
        baseRow(menu, g, ui, tk, x, y + 56f, w, idx, setup);
        tabs(g, ui, tk, x, y + 82f, w, table, idx, setup);
        float by = y + 126f, bh = h - 126f, tw = w - RIGHT_W - 16f, rx = x + w - RIGHT_W;
        switch (tab) {
            case "extras" -> extras(menu, g, ui, tk, x, by, tw, bh);
            case "cosmetics" -> cosmetics(menu, g, ui, tk, x, by, tw, bh);
            case "look" -> look(g, ui, tk, x, by, tw, bh, table, idx, setup);
            case "more" -> {
                // Sol's other parts, or the whole texture tree.
                String[] modes = {SolMenu.tr("sol.styles.more.parts").toUpperCase(Locale.ROOT), SolMenu.tr("sol.styles.more.tree").toUpperCase(Locale.ROOT)};
                float sw = ui.segmentedWidth(modes);
                ui.segmented(tk, x + sw, by + 14f, modes, treeMode ? 1 : 0, i -> {
                    treeMode = i == 1;
                    scroll.reset();
                });
                if (treeMode && search.text().isBlank()) {
                    tree.draw(menu, g, ui, tk, x, by + 36f, w, bh - 36f, table, idx, setup, filter);
                } else {
                    partTiles(g, ui, tk, x, by + 36f, tw, bh - 36f, table, idx, setup);
                }
            }
            default -> partTiles(g, ui, tk, x, by, tw, bh, table, idx, setup);
        }
        if (!(tab.equals("more") && treeMode && search.text().isBlank())) {
            right(menu, g, ui, tk, rx, by, RIGHT_W, bh, table, idx, setup, tint);
        }
        ui.mx = smx;
        ui.my = smy;
        // What's open over the tab, on top.
        if (drop != null) {
            dropList(menu, g, ui, tk, x, y, w, h, idx, setup);
        }
        if (card != null) {
            packCard(g, ui, tk, x, by, w, bh, table, idx, setup);
        }
        if (picking != null) {
            pickerPopover(g, ui, tk, rx - 470f, by + PREVIEW_H, 456f, setup, menu);
        }
        if (openedAt >= 0L && Styles.index() != null) {
            // B.5: the tab's first frame (the index only; the pictures stream in after).
            Styles.menuOpenMicros = (System.nanoTime() - drawStart) / 1000L;
            openedAt = -1L;
        }
    }

    /** The head, the apply pill, and Presets, the pack filter, Share, Import and Reset all on the right. */
    private void topBar(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, StylesIndex.Snapshot idx, StyleSetup setup) {
        float cy = y + 15f;
        int packs = setup.packsUsed().size();
        String count = packs == 0 ? SolMenu.tr("sol.styles.vanilla_look")
                : packs == 1 ? SolMenu.tr("sol.styles.mixed_one") : SolMenu.tr("sol.styles.mixed", packs);
        float end = SolMenu.head(g, SolMenu.tr("sol.styles.head"), count, x, cy);
        String pill = null;
        boolean accent = false;
        if (Styles.applying()) {
            pill = SolMenu.tr("sol.styles.applying");
            accent = true;
        } else if (!Styles.lastApply.isEmpty() && System.currentTimeMillis() - lastApplySeen() < 5000L) {
            pill = SolMenu.tr(Styles.lastApply.equals("live") ? "sol.styles.live" : "sol.styles.reloaded", Styles.lastApplyMillis);
        }
        if (pill != null) {
            SolFont pf = g.font(SolFont.Family.PIXEL_BOLD, 10f);
            float pw = g.width(pf, pill) + 14f;
            ui.pill(tk, end + 14f + pw, cy, pill, accent);
        }
        float r = x + w;
        r -= ui.button(tk, r, cy, SolMenu.tr("sol.styles.reset_all"), false, () -> {
            Styles.set(setup.cleared());
            menu.toast(SolMenu.tr("sol.notify.styles.reset"));
        }) + 8f;
        r -= ui.button(tk, r, cy, SolMenu.tr("sol.styles.import"), false, () -> importCode(menu, idx)) + 8f;
        r -= ui.button(tk, r, cy, SolMenu.tr("sol.styles.share"), false, () -> share(menu, idx, setup)) + 8f;
        float pw = ui.dropButton(tk, r, cy, SolMenu.tr("sol.styles.presets"), "presets".equals(drop), () -> openDrop("presets"));
        if ("presets".equals(drop)) {
            dropX = r - pw;
            dropY = cy + 18f;
        }
        r -= pw + 8f;
        PackInfo fp = filter == null || idx == null ? null : idx.get(filter);
        String fl = fp == null ? SolMenu.tr("sol.styles.filter_all") : SolMenu.tr("sol.styles.filter", clean(fp.name()));
        SolFont bf = g.font(SolFont.Family.PIXEL_BOLD, 12f);
        if (g.width(bf, fl) > 180f) {
            fl = g.fit(bf, fl, 180f);
        }
        float fw = ui.dropButton(tk, r, cy, fl, "filter".equals(drop) || fp != null, () -> openDrop("filter"));
        if ("filter".equals(drop)) {
            dropX = r - fw;
            dropY = cy + 18f;
        }
    }

    private long lastApplyStamp, lastApplyAt;

    /** When the last apply finished (as the pill sees it). */
    private long lastApplySeen() {
        long stamp = Styles.liveWrites * 31L + Styles.reloads;
        if (stamp != lastApplyStamp) {
            lastApplyStamp = stamp;
            lastApplyAt = System.currentTimeMillis();
        }
        return lastApplyAt;
    }

    private void openDrop(String which) {
        drop = which.equals(drop) ? null : which;
        dropScroll.reset();
    }

    /** The base: Vanilla, the full packs and the packs the pack screen has on; the rest under More. Search and sort on the right. */
    private void baseRow(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float cy, float w, StylesIndex.Snapshot idx, StyleSetup setup) {
        SolFont lf = g.font(SolFont.Family.PIXEL, 11f);
        String label = SolMenu.tr("sol.styles.base").toUpperCase(Locale.ROOT);
        g.textMid(lf, label, x, cy, PixelTokens.INK3);
        float sx = x + g.width(lf, label) + 12f;
        // Search and sort first, from the right, so the chips know their room.
        float searchW = 230f, right = x + w;
        search.draw(g, ui, tk, right - searchW, cy - 16f, searchW, 32f, "search", SolMenu.tr("sol.styles.search"), menu.dt());
        right -= searchW + 8f;
        String sortName = SolMenu.tr("sol.styles.sort." + (setup.sort() == null ? "pack" : setup.sort()));
        float sw = ui.dropButton(tk, right, cy, sortName, "sort".equals(drop), () -> openDrop("sort"));
        if ("sort".equals(drop)) {
            dropX = right - sw;
            dropY = cy + 18f;
        }
        right -= sw + 16f;
        List<PackInfo> bases = bases(idx);
        float cx = sx;
        String current = setup.base();
        float moreW = ui.chipWidth(SolMenu.tr("sol.styles.base.more")) + 14f;
        int shown = 0;
        for (int i = -1; i < bases.size(); i++) {
            PackInfo p = i < 0 ? null : bases.get(i);
            String name = p == null ? SolMenu.tr("sol.styles.base.vanilla") : clean(p.name());
            float cw = ui.chipWidth(name);
            boolean last = i == bases.size() - 1;
            if (cx + cw > right - (last ? 0f : moreW)) {
                break;
            }
            boolean on = p == null ? current == null : p.key().equals(current);
            String key = p == null ? null : p.key();
            cx += ui.chip(tk, cx, cy, name, on, () -> {
                if (!java.util.Objects.equals(key, setup.base())) {
                    Styles.set(setup.withBase(key));
                }
            }) + 6f;
            shown++;
        }
        if (shown < bases.size() + 1) {
            float mw = ui.dropButton(tk, cx + moreW - 14f + 10f, cy, SolMenu.tr("sol.styles.base.more"), "base".equals(drop), () -> openDrop("base"));
            if ("base".equals(drop)) {
                dropX = cx;
                dropY = cy + 18f;
            }
            baseMoreFrom = shown - 1;
            cx += mw;
        }
    }

    private int baseMoreFrom;

    /** The packs that can be the base: the full packs, then any other the pack screen has on. */
    private static List<PackInfo> bases(StylesIndex.Snapshot idx) {
        List<PackInfo> out = new ArrayList<>(idx.full());
        java.util.Set<String> on = new java.util.HashSet<>();
        try {
            for (String id : Minecraft.getInstance().getResourcePackRepository().getSelectedIds()) {
                on.add(id.startsWith("file/") ? id.substring(5) : id);
            }
        } catch (RuntimeException e) {
            // No pack list yet.
        }
        for (PackInfo p : idx.packs()) {
            if (p.kind() != PackInfo.Kind.FULL && p.origin() == PackInfo.Origin.RESOURCE_PACK && p.kind() != PackInfo.Kind.EMPTY) {
                String file = p.key().startsWith("rp:") ? p.key().substring(3) : p.key();
                if (on.contains(file)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** The tabs as slabs, a dot on each that isn't vanilla; with a pack filter, the tabs it changes marked and the rest dimmed. */
    private void tabs(Gfx g, Ui ui, Tokens tk, float x, float y, float w, PartTable table, StylesIndex.Snapshot idx, StyleSetup setup) {
        PixelTokens p = PixelTokens.get();
        float th = 34f;
        float[] sizes = {11f, 10f};
        float pad = 20f;
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        for (float size : sizes) {
            f = g.font(SolFont.Family.PIXEL_BOLD, size);
            float total = 0f;
            for (String id : TABS) {
                total += g.width(f, tabName(id)) + pad + 4f;
            }
            if (total - 4f <= w) {
                break;
            }
            pad = 14f;
        }
        PackInfo fp = filter == null ? null : idx.get(filter);
        boolean bauloo = Styles.bauloo();
        float tx = x;
        for (String id : TABS) {
            String name = tabName(id);
            float tw = g.width(f, name) + pad;
            boolean on = id.equals(tab);
            boolean changes = fp != null && tabChanges(table, fp, id);
            int st = on ? Pix.ON : ui.pressed(tx, y, tw, th) ? Pix.PRESSED : ui.over(tx, y, tw, th) ? Pix.HOVER : Pix.NORMAL;
            Pix.slab(g, p, tx, y, tw, th, st);
            int c = Pix.text(p, st);
            if (fp != null && !changes && !on) {
                c = PixelTokens.INK3;
            } else if (bauloo && isBaulooTab(id) && !on) {
                c = PixelTokens.INK3;
            }
            g.textMid(f, name, tx + pad / 2f, y + th / 2f + (st == Pix.PRESSED ? Pix.E : 0f), c);
            if (tabStyled(table, setup, id)) {
                Pix.fill(g, tx + tw - 10f, y + 6f, 4f, 4f, on ? p.en : p.acc);
            }
            if (changes) {
                Pix.fill(g, tx + 6f, y + th - 9f, tw - 12f, 3f, p.acc);
            }
            final String go = id;
            int fi = ui.click(tx, y, tw, th, () -> {
                if (!go.equals(tab)) {
                    tab = go;
                    scroll.reset();
                    List<PartTable.Part> ps = table.inTab(go);
                    if (!ps.isEmpty()) {
                        part = ps.get(0).id();
                    }
                }
            });
            if (ui.focused(fi)) {
                Pix.focus(g, tx, y, tw, th);
            }
            tx += tw + 4f;
        }
    }

    private static boolean isBaulooTab(String id) {
        for (int i = 0; i < 10; i++) {
            if (TABS[i].equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String tabName(String id) {
        return SolMenu.tr("sol.styles.tab." + id).toUpperCase(Locale.ROOT);
    }

    /** The parts a tab shows (More: every part of Sol's own). */
    private static List<PartTable.Part> partsOf(PartTable table, String tab) {
        if (tab.equals("more")) {
            List<PartTable.Part> out = new ArrayList<>();
            for (List<PartTable.Part> l : table.more().values()) {
                out.addAll(l);
            }
            return out;
        }
        return table.inTab(tab);
    }

    private static boolean tabStyled(PartTable table, StyleSetup setup, String tab) {
        if (tab.equals("look")) {
            return !setup.isEmpty();
        }
        for (PartTable.Part pt : partsOf(table, tab)) {
            if (!setup.choice(pt.id()).isDefault()) {
                return true;
            }
        }
        return false;
    }

    private static boolean tabChanges(PartTable table, PackInfo pack, String tab) {
        for (PartTable.Part pt : partsOf(table, tab)) {
            if (Choices.supplies(pack, pt)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ the tiles

    private void refreshTiles(PartTable table, StylesIndex.Snapshot idx, StyleSetup setup, int tint) {
        String sort = setup.sort() == null ? "pack" : setup.sort();
        if (idx == tilesFor && setup == tilesSetup && tint == tilesTint && sort.equals(tilesSort)) {
            return;
        }
        if (idx != tilesFor) {
            Thumbs.clear();
            words.clear();
        }
        tilesFor = idx;
        tilesSetup = setup;
        tilesTint = tint;
        tilesSort = sort;
        tiles.clear();
        for (PartTable.Part pt : table.parts()) {
            List<Choices.Tile> list = Choices.of(pt, idx, setup, tint);
            tiles.put(pt.id(), Choices.sort(list, sort, t -> Thumbs.hue(t.picture()), t -> StylesPrefs.recent(t.key()), setup.favourites()));
        }
    }

    /** The parts of the tab (or the search's) as sections: a head, its tiles, the full packs folded. */
    private void partTiles(Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, PartTable table, StylesIndex.Snapshot idx,
                           StyleSetup setup) {
        String q = search.text().strip().toLowerCase(Locale.ROOT);
        List<PartTable.Part> parts = q.isEmpty() ? partsOf(table, tab) : found(table, idx, q);
        Pix.recess(g, PixelTokens.get(), x, y, w, h);
        float ix = x + Pix.E + 12f, iw = w - 2f * (Pix.E + 12f) - 8f;
        g.clip(x + Pix.E, y + Pix.E, x + w - Pix.E, y + h - Pix.E);
        float cy = y + Pix.E + 12f - scroll.value();
        if (parts.isEmpty()) {
            SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
            g.textCentre(f, SolMenu.tr("sol.styles.nothing", search.text().strip()), x + w / 2f, y + 60f, PixelTokens.INK2);
        }
        String lastGroup = null;
        for (PartTable.Part pt : parts) {
            if (tab.equals("more") && q.isEmpty() && !pt.group().equals(lastGroup)) {
                // More: the parts under their group's name.
                lastGroup = pt.group();
                SolFont gf = g.font(SolFont.Family.PIXEL, 11f);
                g.textSpaced(gf, SolMenu.tr("sol.styles.group." + pt.group()).toUpperCase(Locale.ROOT), ix, cy + 8f, 1.1f, PixelTokens.INK3);
                cy += 24f;
            }
            cy = partSection(g, ui, tk, ix, cy, iw, pt, idx, setup, q);
            cy += 14f;
        }
        g.endClip();
        float content = cy + scroll.value() - (y + Pix.E + 12f);
        scroll.setMax(content - (h - 2f * Pix.E - 12f));
        scroll.step(0.016f);
        scroll.bar(g, x + w - Pix.E - 4f, y + Pix.E + 4f, h - 2f * Pix.E - 8f);
    }

    /** The parts whose name, items, branch or packs match the search. */
    private List<PartTable.Part> found(PartTable table, StylesIndex.Snapshot idx, String q) {
        List<PartTable.Part> out = new ArrayList<>();
        String[] needles = q.split("\\s+");
        for (PartTable.Part pt : table.parts()) {
            String hay = words.computeIfAbsent(pt.id(), id -> words(table, pt));
            boolean all = true;
            for (String n : needles) {
                if (!hay.contains(n) && !packMatches(pt, n)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                out.add(pt);
            }
        }
        return out;
    }

    private boolean packMatches(PartTable.Part pt, String needle) {
        List<Choices.Tile> ts = tiles.get(pt.id());
        if (ts == null) {
            return false;
        }
        for (Choices.Tile t : ts) {
            if (t.pack() != null && t.label().toLowerCase(Locale.ROOT).contains(needle)) {
                return true;
            }
        }
        return false;
    }

    /** A part's search words: its name, its items' and blocks' names in the game's language, its branch and group. */
    private static String words(PartTable table, PartTable.Part pt) {
        StringBuilder sb = new StringBuilder();
        sb.append(SolMenu.tr("sol.styles.part." + pt.id())).append(' ').append(pt.id().replace('_', ' ')).append(' ');
        sb.append(SolMenu.tr("sol.styles.group." + pt.group())).append(' ');
        if (pt.tab() != null) {
            sb.append(SolMenu.tr("sol.styles.tab." + pt.tab())).append(' ');
        }
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (String path : pt.textures()) {
            sb.append(SolMenu.tr("sol.styles.trunk." + TextureTree.branch(path, table).split("/")[0])).append(' ');
            String name = path.substring(path.lastIndexOf('/') + 1).replace(".png", "");
            String kind = path.startsWith("textures/item/") ? "item" : path.startsWith("textures/block/") ? "block" : null;
            if (kind != null && seen.add(name)) {
                String key = kind + ".minecraft." + name;
                if (io.github.georgwav.cosmetics.sol.ui.kit.Widgets.has(key)) {
                    sb.append(I18n.get(key)).append(' ');
                }
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** One part: its head (name, where it comes from, reset), its tiles, then the full packs' fold. Returns the bottom. */
    private float partSection(Gfx g, Ui ui, Tokens tk, float x, float y, float w, PartTable.Part pt, StylesIndex.Snapshot idx,
                              StyleSetup setup, String q) {
        PixelTokens p = PixelTokens.get();
        StyleSetup.Choice choice = setup.choice(pt.id());
        boolean picked = pt.id().equals(part);
        SolFont hf = g.font(SolFont.Family.PIXEL_BOLD, 13f), sf = g.font(SolFont.Family.MEDIUM, 12f);
        String name = SolMenu.tr("sol.styles.part." + pt.id()).toUpperCase(Locale.ROOT);
        float cy = y + 12f;
        g.textMid(hf, name, x, cy, picked ? p.acc : PixelTokens.INK);
        float nx = x + g.width(hf, name) + 10f;
        if (!choice.isDefault()) {
            Pix.fill(g, nx, cy - 2f, 4f, 4f, p.acc);
            nx += 12f;
        }
        String from = sourceName(choice, idx, setup);
        ui.area(x, y, w, 24f, () -> part = pt.id());
        float right = x + w;
        if (!choice.isDefault()) {
            ui.tool(tk, right - 26f, cy - 13f, "x", false, () -> Styles.set(setup.with(pt.id(), StyleSetup.Choice.DEFAULT)));
            right -= 34f;
        }
        g.text(sf, g.fit(sf, from, right - nx - 8f), nx, cy + 4f, PixelTokens.INK2);
        float ty = y + 30f;
        if (Styles.bauloo() && pt.tab() != null) {
            SolFont f = g.font(SolFont.Family.MEDIUM, 12.5f);
            g.text(f, SolMenu.tr("sol.styles.bauloo"), x, ty + 14f, PixelTokens.INK2);
            return ty + 24f;
        }
        List<Choices.Tile> list = tiles.getOrDefault(pt.id(), List.of());
        int cols = Math.max(1, (int) ((w + GAP) / (TILE_W + GAP)));
        int i = 0;
        boolean anyPart = false;
        for (Choices.Tile t : list) {
            if (t.group() == Choices.Group.FULL || !q.isEmpty() && !tileMatches(t, pt, q)) {
                continue;
            }
            anyPart |= t.group() == Choices.Group.PART;
            float tx = x + (i % cols) * (TILE_W + GAP), tyy = ty + (i / cols) * (TILE_H + GAP);
            tile(g, ui, tk, tx, tyy, pt, t, choice, setup);
            i++;
        }
        float bottom = ty + ((i + cols - 1) / cols) * (TILE_H + GAP);
        int folded = Choices.folded(list);
        if (folded > 0) {
            // The full packs' version of this part, folded; it remembers being open.
            String label = (setup.fullOpen() ? "- " : "+ ") + SolMenu.tr("sol.styles.full_packs", folded).toUpperCase(Locale.ROOT);
            SolFont ff = g.font(SolFont.Family.PIXEL_BOLD, 11f);
            float fw = g.width(ff, label) + 24f;
            boolean over = ui.over(x, bottom, fw, 28f);
            Pix.slab(g, p, x, bottom, fw, 28f, over ? Pix.HOVER : Pix.NORMAL);
            g.textMid(ff, label, x + 12f, bottom + 14f, Pix.text(p, over ? Pix.HOVER : Pix.NORMAL));
            int fi = ui.click(x, bottom, fw, 28f, () -> Styles.set(setup.withFullOpen(!setup.fullOpen())));
            if (ui.focused(fi)) {
                Pix.focus(g, x, bottom, fw, 28f);
            }
            bottom += 28f + GAP;
            if (setup.fullOpen()) {
                int j = 0;
                for (Choices.Tile t : list) {
                    if (t.group() != Choices.Group.FULL || !q.isEmpty() && !tileMatches(t, pt, q)) {
                        continue;
                    }
                    tile(g, ui, tk, x + (j % cols) * (TILE_W + GAP), bottom + (j / cols) * (TILE_H + GAP), pt, t, choice, setup);
                    j++;
                }
                bottom += ((j + cols - 1) / cols) * (TILE_H + GAP);
            }
        }
        if (!anyPart && q.isEmpty()) {
            SolFont nf = g.font(SolFont.Family.MEDIUM, 11.5f);
            g.text(nf, SolMenu.tr("sol.styles.no_packs"), x, bottom + 6f, PixelTokens.INK3);
            bottom += 18f;
        }
        return bottom;
    }

    private boolean tileMatches(Choices.Tile t, PartTable.Part pt, String q) {
        String hay = words.computeIfAbsent(pt.id(), id -> words(PartTable.get(), pt));
        String label = tileLabel(t).toLowerCase(Locale.ROOT);
        for (String n : q.split("\\s+")) {
            if (!hay.contains(n) && !label.contains(n)) {
                return false;
            }
        }
        return true;
    }

    /** One tile: its picture on whole pixels, its name; worn when chosen; a star for favourites; "+N" when in other packs too. */
    private void tile(Gfx g, Ui ui, Tokens tk, float x, float y, PartTable.Part pt, Choices.Tile t, StyleSetup.Choice choice, StyleSetup setup) {
        PixelTokens p = PixelTokens.get();
        boolean on = Choices.chosen(t, choice);
        boolean over = ui.over(x, y, TILE_W, TILE_H);
        if (on) {
            Pix.worn(g, p, x, y, TILE_W, TILE_H);
        } else {
            Pix.slab(g, p, x, y, TILE_W, TILE_H, ui.pressed(x, y, TILE_W, TILE_H) ? Pix.PRESSED : over ? Pix.HOVER : Pix.NORMAL);
        }
        if (filter != null && filter.equals(t.pack())) {
            Pix.frame(g, x - 4f, y - 4f, TILE_W + 8f, TILE_H + 8f, p.acc);
        }
        if (over) {
            hover = t;
            hoverPart = pt;
        }
        // The picture: whole screen pixels per texel.
        float boxY = y + 10f, box = 72f;
        if (t.group() == Choices.Group.OFF && t.picture() == null) {
            Icons.draw(g, "x", x + TILE_W / 2f - 18f, boxY + box / 2f - 18f, 36f, PixelTokens.INK3, 0f, 0);
        } else {
            Thumbs.Thumb th = Thumbs.get(t.picture(), pt.kind() == PartTable.Kind.BLOCK ? Thumbs.Mode.CUBE : Thumbs.Mode.TILE);
            if (th != null) {
                int k = Math.max(1, (int) Math.floor(Math.min(box * g.d.u / th.w(), box * g.d.u / th.h())));
                if (th.w() == Thumbs.SIZE) {
                    // A 64-pixel tile (a 16x texture four times): a multiple of 16 screen pixels keeps its texels whole.
                    int s = Math.max(16, (int) Math.floor(box * g.d.u / 16f) * 16);
                    float sx = Math.round(g.d.sx(x + TILE_W / 2f) - s / 2f), sy = Math.round(g.d.sy(boxY + box / 2f) - s / 2f);
                    g.imageScreen(th.texture(), sx, sy, sx + s, sy + s, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
                } else {
                    float sw = th.w() * k, sh = th.h() * k;
                    float sx = Math.round(g.d.sx(x + TILE_W / 2f) - sw / 2f), sy = Math.round(g.d.sy(boxY + box / 2f) - sh / 2f);
                    g.imageScreen(th.texture(), sx, sy, sx + sw, sy + sh, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
                }
            } else {
                // Still being made: three dots.
                for (int d = 0; d < 3; d++) {
                    Pix.fill(g, x + TILE_W / 2f - 10f + d * 8f, boxY + box / 2f - 2f, 4f, 4f, PixelTokens.INK3);
                }
            }
        }
        // Its name: on one line, else two, else cut (logged).
        String label = tileLabel(t);
        SolFont nf = g.fitting(label, TILE_W - 12f, 10f, 8f);
        float ly = g.width(nf, label) <= TILE_W - 12f ? y + TILE_H - 19f : y + TILE_H - 26f;
        g.labelCentre(nf, label, x + TILE_W / 2f, ly, TILE_W - 12f, 12f, on ? p.en : PixelTokens.INK);
        // Badges: Sol's own, the base, in more packs.
        SolFont bf = g.font(SolFont.Family.PIXEL_BOLD, 8f);
        String badge = t.group() == Choices.Group.BUILTIN ? "SOL" : t.group() == Choices.Group.BASE ? SolMenu.tr("sol.styles.base").toUpperCase(Locale.ROOT)
                : t.alsoIn().isEmpty() ? null : "+" + t.alsoIn().size();
        if (badge != null) {
            float bw = g.width(bf, badge) + 8f;
            Pix.fill(g, x + 6f, y + 6f, bw, 13f, 0xCC000000);
            g.textMid(bf, badge, x + 10f, y + 12.5f, PixelTokens.INK2);
        }
        int fi = ui.click(x, y, TILE_W, TILE_H, () -> pick(pt, t, setup));
        if (ui.focused(fi)) {
            Pix.focus(g, x, y, TILE_W, TILE_H);
            hover = t;
            hoverPart = pt;
        }
        // Edit: the texture editor on this tile's look (not for Off).
        if (over && t.group() != Choices.Group.OFF && menu != null) {
            float ex = x + TILE_W - 24f, ey = y + TILE_H - 50f;
            boolean eo = ui.over(ex, ey, 20f, 20f);
            Pix.fill(g, ex - 2f, ey - 2f, 24f, 24f, 0xCC000000);
            Icons.draw(g, "brush", ex, ey, 20f, eo ? p.acc : PixelTokens.INK2, 0f, 0);
            SolMenu m = menu;
            ui.area(ex, ey, 20f, 20f, () -> io.github.georgwav.cosmetics.ui.ScreenOpener.open(TextureEditor.ofTile(m, pt, t)));
        }
        // The star: shown on the pointed and favourite tiles; on top of the tile's click.
        boolean fav = setup.favourites().contains(t.key());
        if (over || fav) {
            float sx = x + TILE_W - 24f, sy = y + 4f;
            Icons.draw(g, "star", sx, sy, 20f, fav ? p.acc : ui.over(sx, sy, 20f, 20f) ? PixelTokens.INK : PixelTokens.INK3, fav ? Icons.FILL_ON : 0f, 0);
            ui.area(sx, sy, 20f, 20f, () -> Styles.set(Styles.setup().withFavourite(t.key(), !fav)));
        }
    }

    private void pick(PartTable.Part pt, Choices.Tile t, StyleSetup setup) {
        part = pt.id();
        StyleSetup.Choice c = setup.choice(pt.id());
        if (!c.source().equals(t.source())) {
            Styles.set(setup.with(pt.id(), c.withSource(t.source())));
            StylesPrefs.used(t.key());
        }
    }

    /** A tile's name: its pack's, else the built-in's. */
    static String tileLabel(Choices.Tile t) {
        return switch (t.group()) {
            case PART, FULL, BASE -> clean(t.label());
            case VANILLA -> SolMenu.tr("sol.styles.vanilla");
            case OFF -> SolMenu.tr("sol.styles." + t.label());
            case BUILTIN -> SolMenu.tr("sol.styles.builtin." + t.label());
        };
    }

    /** Where a part's look comes from, in words. */
    private static String sourceName(StyleSetup.Choice c, StylesIndex.Snapshot idx, StyleSetup setup) {
        String s = c.source();
        String base = setup.base() == null ? null : idx.get(setup.base()) == null ? null : clean(idx.get(setup.base()).name());
        String out;
        if (s.equals(StyleSetup.VANILLA)) {
            out = base == null ? SolMenu.tr("sol.styles.vanilla") : SolMenu.tr("sol.styles.from_base", base);
        } else if (s.equals("game")) {
            out = SolMenu.tr("sol.styles.vanilla");
        } else if (s.equals(StyleSetup.OFF)) {
            out = SolMenu.tr("sol.styles.off");
        } else if (c.builtin() != null) {
            out = SolMenu.tr("sol.styles.builtin." + c.builtin());
        } else {
            PackInfo p = idx.get(c.packKey());
            out = p == null ? SolMenu.tr("sol.styles.missing") : clean(p.name());
        }
        if (c.recoloured()) {
            out += " · " + SolMenu.tr("sol.styles.paint." + c.paint().name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    /** A pack's name without its colour codes. */
    static String clean(String name) {
        return name == null ? "" : name.replaceAll("§.", "").strip();
    }

    // ------------------------------------------------------------------ the right column: preview and colours

    private void right(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, PartTable table, StylesIndex.Snapshot idx,
                       StyleSetup setup, int tint) {
        PartTable.Part pt = hoverPart != null ? hoverPart : table.part(part);
        Choices.Tile shown = hover;
        if (shown == null && pt != null) {
            StyleSetup.Choice c = setup.choice(pt.id());
            for (Choices.Tile t : tiles.getOrDefault(pt.id(), List.of())) {
                if (Choices.chosen(t, c)) {
                    shown = t;
                    break;
                }
            }
        }
        String title = shown == null ? "" : tileLabel(shown);
        String line = shown == null ? "" : caption(shown, idx);
        if (tab.equals("extras") || tab.equals("cosmetics") || tab.equals("look") && hover == null) {
            pt = table.part(part);
        }
        StylesPreview.draw(g, tk, x, y, w, PREVIEW_H, pt, shown, idx, setup, tint, menu.time(), title, line);
        PartTable.Part sel = table.part(part);
        if (sel != null) {
            panel(menu, g, ui, tk, x, y + PREVIEW_H + 10f, w, h - PREVIEW_H - 10f, sel, setup);
        }
    }

    private static String caption(Choices.Tile t, StylesIndex.Snapshot idx) {
        return switch (t.group()) {
            case PART -> {
                if (!t.alsoIn().isEmpty()) {
                    List<String> names = new ArrayList<>();
                    for (String n : t.alsoIn()) {
                        names.add(clean(n));
                    }
                    yield SolMenu.tr("sol.styles.caption.also_in", String.join(", ", names));
                }
                PackInfo p = idx.get(t.pack());
                yield p == null ? "" : SolMenu.tr("sol.styles.origin." + p.origin().name().toLowerCase(Locale.ROOT));
            }
            case FULL -> SolMenu.tr("sol.styles.caption.full");
            case BASE -> SolMenu.tr("sol.styles.caption.base");
            case VANILLA -> SolMenu.tr("sol.styles.caption.vanilla");
            case OFF -> SolMenu.tr("sol.styles.caption." + t.label());
            case BUILTIN -> SolMenu.tr("sol.styles.caption.builtin");
        };
    }

    /** The colour panel: paint, hue, saturation, brightness, tint, shape, the part's own settings, Reset. */
    private void panel(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, PartTable.Part pt, StyleSetup setup) {
        PixelTokens p = PixelTokens.get();
        Pix.recess(g, p, x, y, w, h);
        StyleSetup.Choice c = setup.choice(pt.id());
        float ix = x + Pix.E + 12f, right = x + w - Pix.E - 14f;
        g.clip(x + Pix.E, y + Pix.E, x + w - Pix.E, y + h - Pix.E);
        float cy = y + Pix.E + 20f - panelScroll.value();
        SolFont hf = g.font(SolFont.Family.PIXEL_BOLD, 12f), lf = g.font(SolFont.Family.MEDIUM, 12f);
        SolFont nf = g.fitting(SolMenu.tr("sol.styles.part." + pt.id()).toUpperCase(Locale.ROOT), right - ix - 70f, 12f, 10f);
        g.textMid(nf, g.fitLabel(nf, SolMenu.tr("sol.styles.part." + pt.id()).toUpperCase(Locale.ROOT), right - ix - 70f), ix, cy, PixelTokens.INK);
        // Edit (the texture editor on the look now) and Reset, as tool slabs.
        ui.tool(tk, right - 26f, cy - 13f, "x", false, () -> Styles.set(Styles.setup().with(pt.id(), StyleSetup.Choice.DEFAULT)));
        if (ui.over(right - 26f, cy - 13f, 26f, 26f)) {
            panelTip = SolMenu.tr("sol.styles.reset_part");
            panelTipX = right - 120f;
            panelTipY = cy + 16f;
        }
        ui.tool(tk, right - 58f, cy - 13f, "brush", false,
                () -> io.github.georgwav.cosmetics.ui.ScreenOpener.open(TextureEditor.current(menu, pt)));
        if (ui.over(right - 58f, cy - 13f, 26f, 26f)) {
            panelTip = SolMenu.tr("sol.styles.edit");
            panelTipX = right - 150f;
            panelTipY = cy + 16f;
        }
        cy += 34f;
        {
            // How its colours change.
            String[] paints = {SolMenu.tr("sol.styles.paint.none"), SolMenu.tr("sol.styles.paint.recolour"), SolMenu.tr("sol.styles.paint.rainbow"),
                    SolMenu.tr("sol.styles.paint.theme")};
            for (int i = 0; i < paints.length; i++) {
                paints[i] = paints[i].toUpperCase(Locale.ROOT);
            }
            int sel = switch (c.paint()) {
                case NONE -> 0;
                case RECOLOUR -> 1;
                case RAINBOW -> 2;
                case THEME -> 3;
                case GLINT -> -1;
            };
            ui.segmented(tk, right, cy, paints, sel, i -> {
                StyleSetup.Paint np = StyleSetup.Paint.values()[i];
                StyleSetup.Choice now = Styles.setup().choice(pt.id());
                int tintNow = np == StyleSetup.Paint.RECOLOUR && now.tint() < 0 ? Pixels.PRESET_RGB[0] : now.tint();
                Styles.set(Styles.setup().with(pt.id(), now.withColour(np, now.hue(), now.saturation(), now.brightness(), tintNow)));
            });
            cy += 36f;
            if (pt.glint()) {
                g.textMid(lf, SolMenu.tr("sol.styles.follow_glint"), ix, cy, PixelTokens.INK2);
                boolean gl = c.paint() == StyleSetup.Paint.GLINT;
                ui.smallSwitch(tk, right - 30f, cy - 9f, gl, () -> {
                    StyleSetup.Choice now = Styles.setup().choice(pt.id());
                    Styles.set(Styles.setup().with(pt.id(), now.withColour(gl ? StyleSetup.Paint.NONE : StyleSetup.Paint.GLINT,
                            now.hue(), now.saturation(), now.brightness(), now.tint())));
                });
                cy += 32f;
            }
            cy = slider(g, ui, tk, ix, right, cy, SolMenu.tr("sol.styles.hue"), (c.hue() + 180f) / 360f, c.hue() + "°", v -> {
                StyleSetup.Choice now = Styles.setup().choice(pt.id());
                Styles.set(Styles.setup().with(pt.id(), now.withColour(now.paint(), Math.round(v * 360f - 180f), now.saturation(), now.brightness(), now.tint())));
            });
            cy = slider(g, ui, tk, ix, right, cy, SolMenu.tr("sol.styles.saturation"), c.saturation() / 2f, Math.round(c.saturation() * 100f) + "%", v -> {
                StyleSetup.Choice now = Styles.setup().choice(pt.id());
                Styles.set(Styles.setup().with(pt.id(), now.withColour(now.paint(), now.hue(), Math.round(v * 40f) / 20f, now.brightness(), now.tint())));
            });
            cy = slider(g, ui, tk, ix, right, cy, SolMenu.tr("sol.styles.brightness"), c.brightness() / 2f, Math.round(c.brightness() * 100f) + "%", v -> {
                StyleSetup.Choice now = Styles.setup().choice(pt.id());
                Styles.set(Styles.setup().with(pt.id(), now.withColour(now.paint(), now.hue(), now.saturation(), Math.round(v * 40f) / 20f, now.tint())));
            });
            if (c.paint() == StyleSetup.Paint.RECOLOUR) {
                g.textMid(lf, SolMenu.tr("sol.styles.tint"), ix, cy, PixelTokens.INK2);
                cy += 22f;
                cy = colours(g, ui, tk, ix, right, cy, c.tint(), false, "tint", rgb -> {
                    StyleSetup.Choice now = Styles.setup().choice(pt.id());
                    Styles.set(Styles.setup().with(pt.id(), now.withColour(StyleSetup.Paint.RECOLOUR, now.hue(), now.saturation(), now.brightness(), rgb)));
                });
            }
        }
        // Flat, 3D or one of Sol's models (items).
        if (pt.kind() == PartTable.Kind.ITEM || io.github.georgwav.cosmetics.sol.styles.StyleModels.has(pt.id())) {
            g.textMid(lf, SolMenu.tr("sol.styles.shape"), ix, cy, PixelTokens.INK2);
            String[] shapes = {SolMenu.tr("sol.styles.shape.flat").toUpperCase(Locale.ROOT), SolMenu.tr("sol.styles.shape.extruded").toUpperCase(Locale.ROOT),
                    SolMenu.tr("sol.styles.shape.model").toUpperCase(Locale.ROOT)};
            ui.segmented(tk, right, cy, shapes, c.shape().ordinal(), i -> {
                StyleSetup.Choice now = Styles.setup().choice(pt.id());
                StyleSetup.Shape sh = StyleSetup.Shape.values()[i];
                if (sh == StyleSetup.Shape.MODEL && !io.github.georgwav.cosmetics.sol.styles.StyleModels.has(pt.id())) {
                    menu.toast(SolMenu.tr("sol.styles.model_none"));
                    return;
                }
                Styles.set(Styles.setup().with(pt.id(), now.withShape(sh, now.thickness(), sh == StyleSetup.Shape.MODEL
                        ? io.github.georgwav.cosmetics.sol.styles.StyleModels.first(pt.id()) : now.model())));
            });
            cy += 36f;
            if (c.shape() == StyleSetup.Shape.EXTRUDED) {
                cy = slider(g, ui, tk, ix, right, cy, SolMenu.tr("sol.styles.thickness"), (c.thickness() - 0.5f) / 3.5f,
                        String.format(Locale.ROOT, "%.1f px", c.thickness()), v -> {
                            StyleSetup.Choice now = Styles.setup().choice(pt.id());
                            Styles.set(Styles.setup().with(pt.id(), now.withShape(now.shape(), Math.round((0.5f + v * 3.5f) * 2f) / 2f, now.model())));
                        });
            }
        }
        // The part's own settings.
        if (pt.id().equals("crystal")) {
            float spin = Styles.crystalSpin();
            cy = slider(g, ui, tk, ix, right, cy, SolMenu.tr("sol.styles.spin"), spin / 3f, Math.round(spin * 100f) + "%",
                    v -> extra(pt, "spin", Float.toString(Math.round(v * 30f) / 10f)));
            g.textMid(lf, SolMenu.tr("sol.styles.bounce"), ix, cy, PixelTokens.INK2);
            boolean bounce = Styles.crystalBounce();
            ui.smallSwitch(tk, right - 30f, cy - 9f, bounce, () -> extra(pt, "bounce", bounce ? "false" : null));
            cy += 34f;
            for (String which : new String[]{"core", "frame"}) {
                g.textMid(lf, SolMenu.tr("sol.styles." + which), ix, cy, PixelTokens.INK2);
                cy += 22f;
                cy = colours(g, ui, tk, ix, right, cy, colourOf(c.extra(which, null)), true, which, rgb -> extra(pt, which, rgb == -2 ? "theme" : rgb < 0 ? null : hex(rgb)));
            }
        } else if (pt.id().equals("anchor")) {
            g.textMid(lf, SolMenu.tr("sol.styles.glow"), ix, cy, PixelTokens.INK2);
            cy += 22f;
            cy = colours(g, ui, tk, ix, right, cy, colourOf(c.extra("glow", null)), true, "glow",
                    rgb -> extra(pt, "glow", rgb == -2 ? "theme" : rgb < 0 ? null : hex(rgb)));
            for (String which : new String[]{"mark", "pixel_glow"}) {
                g.textMid(lf, SolMenu.tr("sol.styles." + which), ix, cy, PixelTokens.INK2);
                boolean on = "true".equals(c.extra(which, "false"));
                ui.smallSwitch(tk, right - 30f, cy - 9f, on, () -> extra(pt, which, on ? null : "true"));
                cy += 32f;
            }
        }
        g.endClip();
        float content = cy + panelScroll.value() - (y + Pix.E + 20f) + 10f;
        panelScroll.setMax(content - (h - 2f * Pix.E - 20f));
        panelScroll.step(0.016f);
        panelScroll.bar(g, x + w - Pix.E - 4f, y + Pix.E + 4f, h - 2f * Pix.E - 8f);
        panelArea = new float[]{x, y, w, h};
        if (panelTip != null) {
            Pix.tooltip(g, PixelTokens.get(), panelTipX, panelTipY, panelTip);
            panelTip = null;
        }
    }

    private String panelTip;
    private float panelTipX, panelTipY;

    private float[] panelArea = new float[4];

    private static void extra(PartTable.Part pt, String key, String value) {
        StyleSetup s = Styles.setup();
        Styles.set(s.with(pt.id(), s.choice(pt.id()).withExtra(key, value)));
    }

    /** A labelled slider row; returns the next row's middle. */
    private static float slider(Gfx g, Ui ui, Tokens tk, float x, float right, float cy, String label, float value, String text,
                                java.util.function.Consumer<Float> set) {
        SolFont lf = g.font(SolFont.Family.MEDIUM, 12f);
        g.textMid(lf, label, x, cy, PixelTokens.INK2);
        ui.slider(tk, right, cy, value, text, set);
        return cy + 30f;
    }

    /** -1 none, -2 the theme's colour, else RGB. */
    private static int colourOf(String v) {
        if (v == null) {
            return -1;
        }
        if (v.equals("theme")) {
            return -2;
        }
        try {
            return Integer.parseInt(v, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06x", rgb & 0xFFFFFF);
    }

    /**
     * A row of colours: (none and the theme's, when {@code withNone}) the seven presets, then a custom one that opens
     * the picker. {@code set} gets RGB, -1 for none or -2 for the theme's. Returns the next row's middle.
     */
    private float colours(Gfx g, Ui ui, Tokens tk, float x, float right, float cy, int current, boolean withNone, String which, IntConsumer set) {
        PixelTokens p = PixelTokens.get();
        List<Integer> values = new ArrayList<>();
        if (withNone) {
            values.add(-1);
            values.add(-2);
        }
        for (int rgb : Pixels.PRESET_RGB) {
            values.add(rgb);
        }
        float sw = 22f, gap = 6f;
        int perRow = Math.max(1, (int) ((right - x + gap) / (sw + gap)));
        int n = values.size() + 1;
        boolean known = false;
        for (int i = 0; i < n; i++) {
            float sx = x + (i % perRow) * (sw + gap), sy = cy - sw / 2f + (i / perRow) * (sw + gap);
            if (i == n - 1) {
                // A colour of your own: the picker.
                boolean custom = !known && current >= 0;
                Pix.swatch(g, p, sx, sy, sw, sw, custom ? 0xFF000000 | current : 0xFF2A2A30, custom);
                Icons.draw(g, "plus", sx + 3f, sy + 3f, 16f, custom ? Colors.onColour(0xFF000000 | current) : PixelTokens.INK2, 0f, 0);
                ui.click(sx, sy, sw, sw, () -> openPicker(which, current >= 0 ? current : Pixels.PRESET_RGB[0], set));
                continue;
            }
            int v = values.get(i);
            boolean chosen = v == current;
            known |= chosen;
            int shown = v == -1 ? 0xFF1A1A1E : v == -2 ? 0xFF000000 | ThemeState.tokens().accent : 0xFF000000 | v;
            Pix.swatch(g, p, sx, sy, sw, sw, shown, chosen);
            if (v == -1) {
                Icons.draw(g, "x", sx + 3f, sy + 3f, 16f, PixelTokens.INK3, 0f, 0);
            } else if (v == -2) {
                Icons.draw(g, "sun", sx + 3f, sy + 3f, 16f, Colors.onColour(shown), 0f, 0);
            }
            ui.click(sx, sy, sw, sw, () -> set.accept(v));
        }
        return cy + ((n + perRow - 1) / perRow) * (sw + gap) + 14f;
    }

    private IntConsumer pickerSet;

    private void openPicker(String which, int start, IntConsumer set) {
        picking = which;
        pickerSet = set;
        int[] value = {start};
        picker = new ColorPicker(() -> value[0], rgb -> {
            value[0] = rgb;
            set.accept(rgb & 0xFFFFFF);
        });
    }

    private void pickerPopover(Gfx g, Ui ui, Tokens tk, float x, float y, float w, StyleSetup setup, SolMenu menu) {
        PixelTokens p = PixelTokens.get();
        ui.area(-10000f, -10000f, 30000f, 30000f, () -> picking = null);
        float h = ColorPicker.HEIGHT + 56f;
        Pix.card(g, p, x, y, w, h);
        ui.area(x, y, w, h, () -> {
        });
        SolFont f = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        g.textMid(f, SolMenu.tr("sol.styles.pick").toUpperCase(Locale.ROOT), x + 14f, y + 18f, PixelTokens.INK2);
        ui.tool(tk, x + w - 34f, y + 6f, "x", false, () -> picking = null);
        List<Integer> presets = new ArrayList<>();
        for (int rgb : Pixels.PRESET_RGB) {
            presets.add(rgb);
        }
        picker.draw(g, ui, tk, x + 14f, y + 36f, w - 28f, presets, null, null, menu.dt());
    }

    // ------------------------------------------------------------------ Your look, Extras, Cosmetics

    /** Every part that isn't the base's, where it comes from, an × to drop it; the base; the single textures. */
    private void look(Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, PartTable table, StylesIndex.Snapshot idx, StyleSetup setup) {
        PixelTokens p = PixelTokens.get();
        Pix.recess(g, p, x, y, w, h);
        float ix = x + Pix.E + 12f, iw = w - 2f * (Pix.E + 12f) - 8f;
        g.clip(x + Pix.E, y + Pix.E, x + w - Pix.E, y + h - Pix.E);
        float ry = y + Pix.E + 10f - scroll.value();
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 12f), sf = g.font(SolFont.Family.MEDIUM, 12.5f);
        if (setup.isEmpty()) {
            g.textCentre(g.font(SolFont.Family.MEDIUM, 13f), SolMenu.tr("sol.styles.look.empty"), x + w / 2f, y + 60f, PixelTokens.INK2);
        }
        if (setup.base() != null) {
            PackInfo b = idx.get(setup.base());
            ry = lookRow(g, ui, tk, ix, ry, iw, SolMenu.tr("sol.styles.base").toUpperCase(Locale.ROOT),
                    b == null ? SolMenu.tr("sol.styles.missing") : clean(b.name()), null, () -> Styles.set(Styles.setup().withBase(null)));
        }
        for (PartTable.Part pt : table.parts()) {
            StyleSetup.Choice c = setup.choice(pt.id());
            if (c.isDefault()) {
                continue;
            }
            String tabOf = pt.tab() != null ? pt.tab() : "more";
            ry = lookRow(g, ui, tk, ix, ry, iw, SolMenu.tr("sol.styles.part." + pt.id()).toUpperCase(Locale.ROOT), sourceName(c, idx, setup),
                    () -> {
                        tab = tabOf;
                        part = pt.id();
                        scroll.reset();
                    }, () -> Styles.set(Styles.setup().with(pt.id(), StyleSetup.Choice.DEFAULT)));
        }
        // The single textures, by pack.
        Map<String, Integer> byPack = new LinkedHashMap<>();
        for (String src : setup.leaves().values()) {
            byPack.merge(src, 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : byPack.entrySet()) {
            String src = e.getKey();
            PackInfo pk = src.startsWith("pack:") ? idx.get(src.substring(5)) : null;
            String name = pk == null ? SolMenu.tr("sol.styles.vanilla") : clean(pk.name());
            ry = lookRow(g, ui, tk, ix, ry, iw, SolMenu.tr("sol.styles.look.leaves_head").toUpperCase(Locale.ROOT),
                    SolMenu.tr("sol.styles.look.leaves", e.getValue(), name), null, () -> {
                        StyleSetup s = Styles.setup();
                        for (Map.Entry<String, String> l : s.leaves().entrySet()) {
                            if (l.getValue().equals(src)) {
                                s = s.withLeaf(l.getKey(), null);
                            }
                        }
                        Styles.set(s);
                    });
        }
        g.endClip();
        float content = ry + scroll.value() - (y + Pix.E + 10f);
        scroll.setMax(content - (h - 2f * Pix.E - 10f));
        scroll.step(0.016f);
        scroll.bar(g, x + w - Pix.E - 4f, y + Pix.E + 4f, h - 2f * Pix.E - 8f);
    }

    private float lookRow(Gfx g, Ui ui, Tokens tk, float x, float y, float w, String name, String from, Runnable open, Runnable drop) {
        PixelTokens p = PixelTokens.get();
        float rh = 46f;
        boolean over = ui.over(x, y, w, rh);
        Pix.slab(g, p, x, y, w, rh, over && open != null ? Pix.HOVER : Pix.NORMAL);
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 12f), sf = g.font(SolFont.Family.MEDIUM, 12.5f);
        float nameW = Math.min(220f, g.width(nf, name));
        g.textMid(nf, g.fitLabel(nf, name, 220f), x + 14f, y + rh / 2f, PixelTokens.INK);
        g.textMid(sf, g.fit(sf, from, w - nameW - 90f), x + 14f + Math.max(nameW, 160f) + 16f, y + rh / 2f, PixelTokens.INK2);
        if (open != null) {
            ui.click(x, y, w - 44f, rh, open);
        }
        ui.tool(tk, x + w - 36f, y + rh / 2f - 13f, "x", false, drop);
        return y + rh + 6f;
    }

    /** One card: icon, name, line, a button. */
    private float card(Gfx g, Ui ui, Tokens tk, float x, float y, float w, String icon, String name, String line, String button, Runnable action) {
        PixelTokens p = PixelTokens.get();
        float ch = 64f;
        Pix.card(g, p, x, y, w, ch);
        Pix.fill(g, x + 14f, y + 14f, 36f, 36f, 0x14FFFFFF);
        Icons.draw(g, icon, x + 20f, y + 20f, 24f, p.acc, Icons.FILL, 0);
        SolFont nf = g.font(SolFont.Family.PIXEL_BOLD, 12f), lf = g.font(SolFont.Family.MEDIUM, 12.5f);
        float bw = ui.buttonWidth(button);
        g.textMid(nf, g.fitLabel(nf, name, w - 64f - bw - 30f), x + 64f, y + 22f, PixelTokens.INK);
        g.textMid(lf, g.fit(lf, line, w - 64f - bw - 30f), x + 64f, y + 43f, PixelTokens.INK2);
        ui.button(tk, x + w - 14f, y + ch / 2f, button, false, action);
        return y + ch + 8f;
    }

    /** Bauloo's extras that Sol has as modules: each opens its module's page. */
    private void extras(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        String[][] items = {{"outline", "cube"}, {"crosshair", "cross"}, {"glint", "sparkle"}, {"hitboxes", "hitbox"}, {"held_items", "shield"},
                {"pvp_effects", "bolt"}, {"crystal_pvp", "crystal"}, {"music", "note"}};
        float cy = y - scroll.value();
        g.clip(x, y, x + w, y + h);
        for (String[] it : items) {
            Module m = Modules.byId(it[0]);
            if (m == null) {
                continue;
            }
            cy = card(g, ui, tk, x, cy, w - 10f, it[1], SolMenu.tr("sol.styles.extra." + it[0]), SolMenu.tr("sol.styles.extra." + it[0] + ".line"),
                    SolMenu.tr("sol.styles.open"), () -> menu.openDrawer(m));
        }
        g.endClip();
        scroll.setMax(cy + scroll.value() - y - h);
        scroll.step(0.016f);
    }

    /** Bauloo's cosmetics: Sol's Wardrobe, opened on the view. */
    private void cosmetics(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        String[][] items = {{"capes", "cape"}, {"hats", "crown"}, {"auras", "sparkle"}, {"wings", "wing"}, {"all", "shirt"}};
        float cy = y - scroll.value();
        g.clip(x, y, x + w, y + h);
        for (String[] it : items) {
            cy = card(g, ui, tk, x, cy, w - 10f, it[1], SolMenu.tr("sol.styles.cos." + it[0]), SolMenu.tr("sol.styles.cos." + it[0] + ".line"),
                    SolMenu.tr("sol.styles.open"), () -> io.github.georgwav.cosmetics.ui.ScreenOpener.open(
                            io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen.inMenu(menu, it[0], false)));
        }
        g.endClip();
        scroll.setMax(cy + scroll.value() - y - h);
        scroll.step(0.016f);
    }

    // ------------------------------------------------------------------ the lists under the top bar, and the pack card

    private void dropList(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, StylesIndex.Snapshot idx, StyleSetup setup) {
        PixelTokens p = PixelTokens.get();
        ui.area(-10000f, -10000f, 30000f, 30000f, () -> drop = null);
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        List<Runnable> removes = new ArrayList<>();
        List<Boolean> on = new ArrayList<>();
        switch (drop) {
            case "presets" -> {
                labels.add("+ " + SolMenu.tr("sol.styles.preset.save"));
                on.add(false);
                removes.add(null);
                actions.add(() -> {
                    int n = StylesPrefs.presets().size() + 1;
                    String name = SolMenu.tr("sol.styles.preset.name", n);
                    while (StylesPrefs.presets().containsKey(name)) {
                        name = SolMenu.tr("sol.styles.preset.name", ++n);
                    }
                    StylesPrefs.savePreset(name, Styles.setup());
                    menu.toast(SolMenu.tr("sol.notify.styles.preset_saved", name));
                });
                for (Map.Entry<String, StyleSetup> e : StylesPrefs.presets().entrySet()) {
                    labels.add(e.getKey());
                    on.add(e.getValue().equals(setup));
                    StyleSetup s = e.getValue();
                    String name = e.getKey();
                    actions.add(() -> {
                        Styles.set(s);
                        drop = null;
                    });
                    removes.add(() -> StylesPrefs.removePreset(name));
                }
            }
            case "filter" -> {
                labels.add(SolMenu.tr("sol.styles.filter_all"));
                on.add(filter == null);
                removes.add(null);
                actions.add(() -> {
                    filter = null;
                    drop = null;
                });
                for (PackInfo pk : idx.packs()) {
                    if (pk.kind() == PackInfo.Kind.EMPTY) {
                        continue;
                    }
                    labels.add(clean(pk.name()));
                    on.add(pk.key().equals(filter));
                    removes.add(null);
                    actions.add(() -> {
                        filter = pk.key();
                        drop = null;
                    });
                }
                // The open filter's card: what the pack changes.
                if (filter != null) {
                    labels.add(SolMenu.tr("sol.styles.card.open"));
                    on.add(false);
                    removes.add(null);
                    String f = filter;
                    actions.add(() -> {
                        openCard(f, idx);
                        drop = null;
                    });
                }
            }
            case "sort" -> {
                for (String s : SORTS) {
                    labels.add(SolMenu.tr("sol.styles.sort." + s));
                    on.add(s.equals(setup.sort() == null ? "pack" : setup.sort()));
                    removes.add(null);
                    actions.add(() -> {
                        Styles.set(Styles.setup().withSort(s));
                        drop = null;
                    });
                }
            }
            case "base" -> {
                List<PackInfo> bases = bases(idx);
                for (int i = Math.max(0, baseMoreFrom); i < bases.size(); i++) {
                    PackInfo pk = bases.get(i);
                    labels.add(clean(pk.name()));
                    on.add(pk.key().equals(setup.base()));
                    removes.add(null);
                    actions.add(() -> {
                        Styles.set(Styles.setup().withBase(pk.key()));
                        drop = null;
                    });
                }
            }
            default -> {
            }
        }
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        float lw = 220f;
        for (String l : labels) {
            lw = Math.max(lw, g.width(f, l) + 60f);
        }
        lw = Math.min(lw, 360f);
        float rowH = 32f, maxRows = 9f;
        float lh = Math.min(labels.size(), maxRows) * rowH + 12f;
        float lx = Math.min(dropX, x + w - lw), ly = dropY;
        Pix.card(g, p, lx, ly, lw, lh);
        ui.area(lx, ly, lw, lh, () -> {
        });
        g.clip(lx + Pix.E, ly + Pix.E, lx + lw - Pix.E, ly + lh - Pix.E);
        float ry = ly + 6f - dropScroll.value();
        for (int i = 0; i < labels.size(); i++) {
            boolean over = ui.over(lx + 4f, ry, lw - 8f, rowH);
            if (over || on.get(i)) {
                Pix.fill(g, lx + 4f, ry, lw - 8f, rowH, over ? 0x26FFFFFF : 0x14FFFFFF);
            }
            if (on.get(i)) {
                Pix.fill(g, lx + 4f, ry + 6f, 3f, rowH - 12f, p.acc);
            }
            float room = lw - 28f - (removes.get(i) != null ? 30f : 0f);
            g.textMid(f, g.fit(f, labels.get(i), room), lx + 16f, ry + rowH / 2f, on.get(i) ? p.acc : PixelTokens.INK);
            ui.click(lx + 4f, ry, lw - 8f, rowH, actions.get(i));
            if (removes.get(i) != null) {
                Runnable r = removes.get(i);
                float bx = lx + lw - 32f;
                Icons.draw(g, "x", bx + 2f, ry + 6f, 20f, ui.over(bx, ry + 4f, 24f, 24f) ? PixelTokens.RED : PixelTokens.INK3, 0f, 0);
                ui.area(bx, ry + 4f, 24f, 24f, r);
            }
            ry += rowH;
        }
        g.endClip();
        dropScroll.setMax(labels.size() * rowH + 12f - lh);
        dropScroll.step(0.016f);
    }

    private void openCard(String key, StylesIndex.Snapshot idx) {
        card = key;
        cardBranches.clear();
        PackInfo pk = idx.get(key);
        if (pk == null) {
            return;
        }
        PartTable table = PartTable.get();
        Map<String, Integer> counts = new java.util.TreeMap<>();
        for (String path : pk.files().keySet()) {
            if (path.endsWith(".png") && !path.contains(":")) {
                counts.merge(TextureTree.branch(path, table).split("/")[0], 1, Integer::sum);
            }
        }
        for (String trunk : TextureTree.TRUNKS) {
            Integer n = counts.get(trunk);
            if (n != null) {
                cardBranches.put(trunk, n);
            }
        }
    }

    /**
     * The pack card: what the pack changes ("Bare Bones: 2,341 of 3,812 textures; most of the blocks, items and
     * GUI"), its branches with their counts, Use as base, and Use its ... per branch.
     */
    private void packCard(Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h, PartTable table, StylesIndex.Snapshot idx, StyleSetup setup) {
        PixelTokens p = PixelTokens.get();
        PackInfo pk = idx.get(card);
        if (pk == null) {
            card = null;
            return;
        }
        ui.area(-10000f, -10000f, 30000f, 30000f, () -> card = null);
        float cw = 560f, chh = Math.min(h, 150f + ((cardBranches.size() + 1) / 2) * 44f);
        float cx = x + (w - cw) / 2f, cy = y + 10f;
        Pix.card(g, p, cx, cy, cw, chh);
        ui.area(cx, cy, cw, chh, () -> {
        });
        SolFont hf = g.font(SolFont.Family.PIXEL_BOLD, 14f), lf = g.font(SolFont.Family.MEDIUM, 13f);
        g.textMid(hf, g.fitLabel(hf, clean(pk.name()).toUpperCase(Locale.ROOT), cw - 80f), cx + 18f, cy + 24f, PixelTokens.INK);
        ui.tool(tk, cx + cw - 40f, cy + 11f, "x", false, () -> card = null);
        int vt = idx.vanilla().textures();
        List<String> most = new ArrayList<>();
        for (Map.Entry<String, Integer> e : cardBranches.entrySet()) {
            if (e.getValue() >= 20) {
                most.add(SolMenu.tr("sol.styles.trunk." + e.getKey()).toLowerCase(Locale.ROOT));
            }
        }
        String what = most.isEmpty() ? "" : SolMenu.tr("sol.styles.card.most", join(most));
        String line = SolMenu.tr("sol.styles.card.changes", pk.coverage(vt), what);
        g.textMid(lf, g.fit(lf, line, cw - 36f), cx + 18f, cy + 50f, PixelTokens.INK2);
        float by = cy + 70f;
        ui.button(tk, cx + 18f + ui.buttonWidth(SolMenu.tr("sol.styles.card.use_base")), by + 15f, SolMenu.tr("sol.styles.card.use_base"),
                pk.key().equals(setup.base()), () -> {
                    Styles.set(Styles.setup().withBase(pk.key()));
                    card = null;
                });
        by += 44f;
        int i = 0;
        float colW = (cw - 36f - 12f) / 2f;
        for (Map.Entry<String, Integer> e : cardBranches.entrySet()) {
            float bx = cx + 18f + (i % 2) * (colW + 12f), byy = by + (i / 2) * 44f;
            String trunk = e.getKey();
            String label = SolMenu.tr("sol.styles.card.use", SolMenu.tr("sol.styles.trunk." + trunk));
            String count = String.format(Locale.ROOT, "%,d", e.getValue());
            SolFont nf = g.font(SolFont.Family.PIXEL, 10f);
            g.textMid(nf, count, bx, byy + 15f, PixelTokens.INK3);
            float bw = ui.buttonWidth(label);
            ui.button(tk, bx + Math.min(colW, 54f + bw), byy + 15f, label, false, () -> {
                StyleSetup s = Styles.setup();
                for (String path : pk.files().keySet()) {
                    if (path.endsWith(".png") && !path.contains(":") && TextureTree.branch(path, table).startsWith(trunk)) {
                        s = s.withLeaf(path, "pack:" + pk.key());
                    }
                }
                Styles.set(s);
                card = null;
            });
            i++;
        }
    }

    private static String join(List<String> words) {
        if (words.size() <= 1) {
            return words.isEmpty() ? "" : words.get(0);
        }
        return String.join(", ", words.subList(0, words.size() - 1)) + " " + SolMenu.tr("sol.styles.and") + " " + words.get(words.size() - 1);
    }

    // ------------------------------------------------------------------ share codes

    private static void share(SolMenu menu, StylesIndex.Snapshot idx, StyleSetup setup) {
        if (idx == null) {
            return;
        }
        Map<String, ShareCode.PackRef> refs = new HashMap<>();
        for (String key : setup.packsUsed()) {
            PackInfo p = idx.get(key);
            if (p != null) {
                refs.put(key, new ShareCode.PackRef(p.name(), StylesIndex.sizeOf(p)));
            }
        }
        String code = ShareCode.encode(setup, refs);
        Minecraft.getInstance().keyboardHandler.setClipboard(code);
        menu.toast(SolMenu.tr("sol.notify.styles.copied"));
    }

    private static void importCode(SolMenu menu, StylesIndex.Snapshot idx) {
        if (idx == null) {
            return;
        }
        String text = Minecraft.getInstance().keyboardHandler.getClipboard();
        ShareCode.Imported in = text == null ? null : ShareCode.decode(text.strip(), idx::find);
        if (in == null) {
            menu.toast(SolMenu.tr("sol.notify.styles.bad_code"));
            return;
        }
        Styles.set(in.setup());
        menu.toast(in.missing().isEmpty() ? SolMenu.tr("sol.notify.styles.imported")
                : SolMenu.tr("sol.notify.styles.missing", String.join(", ", in.missing())));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean scroll(float amount) {
        if (drop != null) {
            return dropScroll.wheel(amount);
        }
        if (tab.equals("more") && treeMode && search.text().isBlank() && menu != null) {
            return tree.scroll(amount, menu.mouseX());
        }
        if (panelArea[2] > 0f && overPanel()) {
            return panelScroll.wheel(amount);
        }
        return scroll.wheel(amount);
    }

    private boolean overPanel() {
        SolMenu m = menu;
        if (m == null) {
            return false;
        }
        float mx = m.mouseX(), my = m.mouseY();
        return mx >= panelArea[0] && mx <= panelArea[0] + panelArea[2] && my >= panelArea[1] && my <= panelArea[1] + panelArea[3];
    }

    @Override
    public boolean typed(String text) {
        if (picking != null && picker != null) {
            return picker.typed(text);
        }
        if (search.focused()) {
            return search.typed(text);
        }
        if (!text.isBlank() && drop == null && card == null) {
            // Typing anywhere searches the parts and packs.
            search.focus(true);
            return search.typed(text);
        }
        return false;
    }

    @Override
    public boolean key(int key, boolean ctrl) {
        if (picking != null && picker != null && picker.key(key, ctrl)) {
            return true;
        }
        if (search.focused()) {
            if (key == InputConstants.KEY_ESCAPE) {
                search.set("");
                search.focus(false);
                return true;
            }
            return search.key(key, ctrl);
        }
        return false;
    }

    /** Esc: what's open over the tab closes first. */
    @Override
    public boolean back() {
        if (picking != null) {
            picking = null;
            return true;
        }
        if (drop != null) {
            drop = null;
            return true;
        }
        if (card != null) {
            card = null;
            return true;
        }
        if (search.focused() || !search.text().isEmpty()) {
            search.set("");
            search.focus(false);
            return true;
        }
        return false;
    }
}
