package io.github.georgwav.cosmetics.sol.ui.wardrobe;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticLoader;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.render.CapeElytra;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.PixelTokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.menu.SolMenu;
import io.github.georgwav.cosmetics.sol.ui.menu.WindowBar;
import io.github.georgwav.cosmetics.sol.ui.quick.PresetThumbs;
import io.github.georgwav.cosmetics.sol.ui.quick.QuickMenu;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.ui.PreviewParticles;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import io.github.georgwav.cosmetics.ui.TilePictures;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * The Wardrobe: every cosmetic, outfit, emote and skin, and every cosmetics setting the Cosmetics screen had, in the
 * Sol style, as the menu's Wardrobe tab in the pixel window (A.7.3). The views down a 200 px rail (with how many
 * each holds, and the source above them), the tiles in a recess in the middle (click to wear, the gear for the
 * cosmetic's page; pointing at one tries it on; worn ones glow in the on colour), and you on the right in the preview
 * pane, turning as you drag. A cosmetic's page, an outfit's page and the settings open in the middle; Esc steps back.
 * Changes apply at once and are saved when the Wardrobe closes.
 */
public final class WardrobeScreen extends SolScreen {
    /** The rail's views, in the Cosmetics screen's order, then outfits, emotes, skins and the settings. */
    public enum View {
        WEARING, ALL, CAPES, WINGS, HATS, FACE, BODY, BACK, SHIELDS, KNIVES, AURAS, PETS, LIMBS, OUTFITS, EMOTES, SKINS, SETTINGS;

        public boolean includes(BakedCosmetic c, List<String> wearing) {
            return switch (this) {
                case WEARING -> wearing.contains(c.id());
                case ALL -> true;
                case CAPES -> c.slot() == CosmeticSlot.CAPE;
                case WINGS -> isWings(c);
                case HATS -> c.slot() == CosmeticSlot.HEAD;
                case FACE -> c.slot() == CosmeticSlot.FACE;
                case BODY -> c.slot() == CosmeticSlot.BODY;
                case BACK -> c.slot() == CosmeticSlot.BACK && !isWings(c);
                case SHIELDS -> c.slot() == CosmeticSlot.SHIELD;
                case KNIVES -> c.slot() == CosmeticSlot.MELEE;
                case AURAS -> c.slot() == CosmeticSlot.AURA;
                case PETS -> c.slot() == CosmeticSlot.PET;
                case LIMBS -> c.slot() == CosmeticSlot.ARM || c.slot() == CosmeticSlot.FEET;
                default -> false;
            };
        }

        public boolean lists() {
            return ordinal() <= LIMBS.ordinal();
        }

        public String icon() {
            return switch (this) {
                case WEARING -> "check";
                case ALL -> "grid4";
                case CAPES -> "cape";
                case WINGS -> "wings";
                case HATS -> "crown";
                case FACE -> "eye";
                case BODY -> "shirt";
                case BACK -> "box";
                case SHIELDS -> "shield";
                case KNIVES -> "sword";
                case AURAS -> "flame";
                case PETS -> "heart";
                case LIMBS -> "run";
                case OUTFITS -> "ring";
                case EMOTES -> "people";
                case SKINS -> "shirt";
                case SETTINGS -> "gear";
            };
        }

        public String label() {
            return switch (this) {
                case SKINS -> Component.translatable("sol.wardrobe.view.skins").getString();
                case SETTINGS -> Component.translatable("sol.wardrobe.view.settings").getString();
                default -> t("view." + name().toLowerCase(Locale.ROOT));
            };
        }

        static boolean isWings(BakedCosmetic c) {
            return c.wings() != null || "wings".equals(c.kind());
        }
    }

    /** Where cosmetics came from, by their pack's namespace: the Wardrobe shows one source at a time, or all. */
    enum Source {
        ALL(null), BADLION("blc_import"), LUNAR("lunar_import"), NORISK("norisk_import"), BUILT_IN("cosmeticsmod"), OTHER(null);

        private final String namespace;

        Source(String namespace) {
            this.namespace = namespace;
        }

        static Source of(BakedCosmetic c) {
            String ns = c.id().substring(0, c.id().indexOf(':'));
            for (Source s : values()) {
                if (ns.equals(s.namespace)) {
                    return s;
                }
            }
            return OTHER;
        }

        boolean includes(BakedCosmetic c) {
            return this == ALL || of(c) == this;
        }
    }

    // Kept while the game runs, as the Cosmetics screen kept them.
    private static View lastView = View.ALL;
    private static Source source = Source.ALL;
    private static String lastQuery = "";
    static float previewSpin, previewPitch;
    static boolean previewElytra;
    private static final float MAX_PITCH = 80f;
    /** Most tiles one view shows at once (only so a broken pack can't build thousands); the rest are a search away. */
    private static final int TILE_CAP = 300;
    /** Frames drawn, for the in-game test. */
    public static int framesRendered;

    // The layout in the window, in design pixels: the rail, the tiles' recess and you, under the head row.
    static final float RAIL_X = SolMenu.BODY_X, RAIL_W = 200f, TOP = SolMenu.BODY_Y + 42f + 16f, BOTTOM = SolMenu.BODY_BOTTOM;
    static final float PREVIEW_W = 300f, PREVIEW_X = SolMenu.BODY_X + SolMenu.BODY_W - PREVIEW_W;
    /** The tiles' recess, and the column inside it every view draws in. */
    static final float BOX_X = RAIL_X + RAIL_W + 18f, BOX_W = PREVIEW_X - 18f - BOX_X;
    static final float MAIN_X = BOX_X + Pix.E + 14f, MAIN_W = BOX_W - 2f * (Pix.E + 14f) - 8f;
    static final float MAIN_TOP = TOP + Pix.E + 14f, MAIN_BOTTOM = BOTTOM - Pix.E - 8f;
    /** A rail row's height, and a tile's. */
    private static final float ROW_H = 34f, TILE_H = 150f, TILE_MIN = 148f, TILE_GAP = 12f;

    View view = lastView;
    final Ui ui = new Ui();
    final TilePictures pictures = new TilePictures();
    private final PreviewParticles particles = new PreviewParticles();
    private final Scroll scroll = new Scroll();
    private final TextField search = new TextField(64, q -> {
        lastQuery = q;
        scroll.reset();
    });
    /** A cosmetic's page (its id), an outfit's page, or neither (the view's list). */
    String detailId;
    CosmeticsConfig.Outfit outfitPage;
    CosmeticPage cosmeticPage;
    private OutfitPage outfitPageView;
    private final SettingsView settingsView = new SettingsView(this);
    private final SkinsView skinsView = new SkinsView(this);
    /** The key bind waiting for a key, or null. */
    KeyMapping listeningFor;
    private int copiedFailures = -1;
    private String tryingOn;
    /** The in-game test's tile being tried on (as if pointed at), or null. */
    private String stagedTry;
    private float shownSpin = previewSpin, shownPitch = previewPitch;
    private float dragX = Float.NaN, dragY;
    private final Scroll railScroll = new Scroll();
    /** Opened from the quick menu (Esc goes back to your ring there). */
    private boolean fromQuick;
    private final WindowBar bar = new WindowBar();
    private final PresetThumbs sky = new PresetThumbs(147, 236, false);
    private final WindowBar.Host host = new WindowBar.Host() {
        @Override
        public String current() {
            return "wardrobe";
        }

        @Override
        public void go(String tab) {
            if (tab.equals("wardrobe")) {
                return;
            }
            leave();
            if (parent instanceof SolMenu m) {
                ScreenOpener.open(m);
                m.go(tab);
            } else {
                SolMenu.open(parent, tab, fromQuick);
            }
        }

        @Override
        public void quickMenu() {
            leave();
            ScreenOpener.open(new QuickMenu(root()));
        }

        @Override
        public void back() {
            if (!WardrobeScreen.this.back()) {
                onClose();
            }
        }

        @Override
        public void close() {
            leave();
            ScreenOpener.open(root());
        }

        @Override
        public void profiles() {
            leave();
            SolMenu m = SolMenu.create(root(), "modules", fromQuick);
            m.stage("profiles");
            ScreenOpener.open(m);
        }

        @Override
        public boolean profilesOpen() {
            return false;
        }

        @Override
        public String backTip() {
            return tr(parent instanceof SolMenu ? "sol.menu.back" : fromQuick ? "sol.menu.back_quick" : "sol.menu.back");
        }
    };

    public WardrobeScreen(Screen parent) {
        super(Component.translatable("sol.wardrobe.title"), parent, Feature.MENU);
        search.set(lastQuery);
    }

    /** The Wardrobe as the menu's tab: from the quick menu, Esc goes back to your ring there. */
    public static WardrobeScreen inMenu(Screen parent, String what, boolean fromQuick) {
        WardrobeScreen s = on(parent, what);
        s.fromQuick = fromQuick;
        return s;
    }

    /** What the window closes to: the menu's own parent when the menu opened the Wardrobe. */
    private Screen root() {
        return parent instanceof SolMenu m ? m.parentScreen() : parent;
    }

    /** Before the Wardrobe gives way to another screen: what you tried on comes off, and it's saved. */
    private void leave() {
        EquippedCosmetics.preview(null);
        CosmeticsConfig.save();
        EquippedCosmetics.invalidate();
    }

    /** Opens on a view ("outfits", "emotes", "skins", "settings", or a cosmetic's page with "cosmetic:<id>"). */
    public static WardrobeScreen on(Screen parent, String what) {
        WardrobeScreen s = new WardrobeScreen(parent);
        if (what != null) {
            if (what.startsWith("cosmetic:")) {
                s.openCosmetic(what.substring("cosmetic:".length()));
            } else {
                for (View v : View.values()) {
                    if (v.name().equalsIgnoreCase(what)) {
                        s.view = v;
                    }
                }
            }
        }
        return s;
    }

    static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.screen." + key, args).getString();
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    float dt() {
        return dt;
    }

    float time() {
        return time;
    }

    void toast(String text) {
        Notifications.post(text);
    }

    // The game's own draws inside a frame (pictures, skins) take screen pixels: the kit's canvas is scaled to them.
    static int screenX(Gfx g, float x) {
        return Math.round(g.d.sx(x));
    }

    static int screenY(Gfx g, float y) {
        return Math.round(g.d.sy(y));
    }

    static int screenSize(Gfx g, float size) {
        return Math.max(1, Math.round(size * g.d.u));
    }

    // ------------------------------------------------------------------ what the Wardrobe lists

    // With thousands of cosmetics loaded, the lists are worked out once per change, not every frame.
    private static List<BakedCosmetic> wardrobeList = List.of();
    private static int wardrobeGeneration = -1;
    private static Source wardrobeSource;
    private static List<Source> sourceList = List.of();
    private static int sourceGeneration = -1;

    /** The cosmetics the Wardrobe lists: all but emote props (they come with their emotes), from the source shown. */
    public static List<BakedCosmetic> wardrobe() {
        if (source != Source.ALL && !sources().contains(source)) {
            source = Source.ALL; // that pack is gone
        }
        int gen = CosmeticRegistry.generation();
        if (gen != wardrobeGeneration || source != wardrobeSource) {
            wardrobeList = CosmeticRegistry.all().stream().filter(c -> !c.definition().prop() && source.includes(c)).toList();
            wardrobeGeneration = gen;
            wardrobeSource = source;
        }
        return wardrobeList;
    }

    /** ALL, then each source some loaded cosmetic comes from. */
    static List<Source> sources() {
        int gen = CosmeticRegistry.generation();
        if (gen == sourceGeneration) {
            return sourceList;
        }
        EnumSet<Source> present = EnumSet.noneOf(Source.class);
        for (BakedCosmetic c : CosmeticRegistry.all()) {
            if (!c.definition().prop()) {
                present.add(Source.of(c));
            }
        }
        List<Source> out = new ArrayList<>();
        out.add(Source.ALL);
        out.addAll(present);
        sourceList = List.copyOf(out);
        sourceGeneration = gen;
        return sourceList;
    }

    public static void setEquipped(String id, boolean on) {
        CosmeticsConfig config = CosmeticsConfig.get();
        CosmeticsConfig.wear(config.localEquipped(), id, on, other -> {
            BakedCosmetic c = CosmeticRegistry.get(other);
            return c != null ? c.kind() : null;
        }, config.severalOfAKind);
        EquippedCosmetics.invalidate();
    }

    void openCosmetic(String id) {
        detailId = id;
        outfitPage = null;
        BakedCosmetic c = id == null ? null : CosmeticRegistry.get(id);
        cosmeticPage = c == null ? null : new CosmeticPage(this, c);
        if (c == null) {
            detailId = null;
        }
    }

    void openOutfit(CosmeticsConfig.Outfit outfit) {
        outfitPage = outfit;
        detailId = null;
        cosmeticPage = null;
        outfitPageView = outfit == null ? null : new OutfitPage(this, outfit);
    }

    void go(View v) {
        view = v;
        lastView = v;
        scroll.reset();
        openCosmetic(null);
        openOutfit(null);
        search.focus(false);
    }

    // ------------------------------------------------------------------ the frame

    @Override
    protected void frame(Gfx g) {
        framesRendered++;
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        pictures.beginFrame();
        if (outfitPage != null && !CosmeticsConfig.get().savedOutfits.contains(outfitPage)) {
            openOutfit(null); // deleted, or the settings were reset
        }
        PixelTokens p = PixelTokens.get();
        Pix.window(g, p);
        bar.draw(g, ui, tk, time, dt, host);
        header(g, tk);
        rail(g, tk);
        Pix.recess(g, p, BOX_X, TOP, BOX_W, BOTTOM - TOP);
        String pointed = null;
        CosmeticsConfig.Outfit pointedOutfit = null;
        float mainTop = MAIN_TOP, mainH = MAIN_BOTTOM - MAIN_TOP;
        if (cosmeticPage != null) {
            cosmeticPage.draw(g, tk, MAIN_X, mainTop, MAIN_W, mainH);
            pointed = detailId;
        } else if (outfitPageView != null) {
            outfitPageView.draw(g, tk, MAIN_X, mainTop, MAIN_W, mainH);
            pointedOutfit = outfitPage;
        } else {
            switch (view) {
                case OUTFITS -> pointedOutfit = outfits(g, tk, mainTop, mainH);
                case EMOTES -> emotes(g, tk, mainTop, mainH);
                case SKINS -> skinsView.draw(g, tk, MAIN_X, mainTop, MAIN_W, mainH);
                case SETTINGS -> settingsView.draw(g, tk, MAIN_X, mainTop, MAIN_W, mainH);
                default -> {
                    pointed = grid(g, tk, mainTop, mainH);
                    if (pointed == null) {
                        pointed = stagedTry;
                    }
                }
            }
        }
        // Trying on: the outfit or cosmetic pointed at (or whose page is open) shows on you, not worn.
        if (pointedOutfit != null) {
            tryingOn = null;
            EquippedCosmetics.previewOutfit(pointedOutfit);
        } else {
            tryingOn = pointed != null && !CosmeticsConfig.get().localEquipped().contains(pointed) ? pointed : null;
            EquippedCosmetics.preview(tryingOn);
        }
        previewPanel(g, tk);
        footer(g, tk);
    }

    /** The head row: the Wardrobe and how much you wear, and the search on the right. */
    private void header(Gfx g, Tokens tk) {
        String count = tr("sol.wardrobe.count", CosmeticsConfig.get().localEquipped().size(), wardrobe().size());
        SolMenu.head(g, tr("sol.wardrobe.title"), count, RAIL_X, SolMenu.BODY_Y + 21f);
        if (view.lists() && cosmeticPage == null && outfitPageView == null && !wardrobe().isEmpty()) {
            float sw = 280f;
            search.draw(g, ui, tk, SolMenu.BODY_X + SolMenu.BODY_W - sw, SolMenu.BODY_Y, sw, 42f, "search", t("wardrobe.search"), dt);
        }
    }

    /** The window's footer: what to do, and the build. */
    private void footer(Gfx g, Tokens tk) {
        SolFont f = g.font(SolFont.Family.PIXEL, 10f);
        float y = Pix.WIN_Y + Pix.WIN_H - Pix.E - 14f;
        g.textMid(f, tr("sol.wardrobe.hint"), RAIL_X, y, 0x73FFFFFF);
        g.textRight(f, SettingsView.buildLabel(), SolMenu.BODY_X + SolMenu.BODY_W, y, 0x59FFFFFF);
    }

    // ------------------------------------------------------------------ the rail

    private int[] countCache = new int[0];
    private String countKey = "";

    /** How many of the listed cosmetics each view has (worked out again when the list or what you wear changes). */
    private int[] counts(List<String> wearing) {
        List<BakedCosmetic> all = wardrobe();
        String key = CosmeticRegistry.generation() + "|" + source + "|" + wearing.hashCode() + "|" + wearing.size();
        if (!key.equals(countKey)) {
            int[] out = new int[View.values().length];
            for (View v : View.values()) {
                int n = 0;
                for (BakedCosmetic c : all) {
                    if (v.includes(c, wearing)) {
                        n++;
                    }
                }
                out[v.ordinal()] = n;
            }
            countCache = out;
            countKey = key;
        }
        return countCache;
    }

    /**
     * The rail (a recess 200 px wide, scrolling when it's taller than the window): the source, then a row for each
     * view with its icon, name and count; the one open outlined in the accent; a line before the outfits and before
     * the settings.
     */
    private void rail(Gfx g, Tokens tk) {
        PixelTokens p = PixelTokens.get();
        Pix.recess(g, p, RAIL_X, TOP, RAIL_W, BOTTOM - TOP);
        // Room for the scrollbar on the right, so it never covers a row.
        float x = RAIL_X + Pix.E + 6f, w = RAIL_W - 2f * (Pix.E + 6f) - 10f, top = TOP + Pix.E + 6f, bottom = BOTTOM - Pix.E - 6f;
        float y = top - railScroll.value();
        g.clip(RAIL_X + Pix.E, TOP + Pix.E, RAIL_X + RAIL_W - Pix.E, BOTTOM - Pix.E);
        List<Source> sources = sources();
        SolFont f = g.font(SolFont.Family.SEMIBOLD, 13.5f), cf = g.font(SolFont.Family.PIXEL, 10f), sf = g.font(SolFont.Family.PIXEL_BOLD, 11f);
        if (sources.size() > 2) {
            String label = t("source." + source.name().toLowerCase(Locale.ROOT));
            boolean over = ui.over(x, y, w, 36f);
            int st = source != Source.ALL ? Pix.ON : over ? Pix.HOVER : Pix.NORMAL;
            Pix.slab(g, p, x, y, w, 36f, st);
            SolFont lf = g.fitting(label, w - 20f, 11f, 10f, 8f);
            g.textMid(lf, g.fitLabel(lf, label, w - 20f), x + 10f, y + 18f, Pix.text(p, st));
            int fs = ui.click(x, y, w, 36f, () -> {
                source = sources.get((sources.indexOf(source) + 1) % sources.size());
                scroll.reset();
            });
            if (ui.focused(fs)) {
                Pix.focus(g, x, y, w, 36f);
            }
            y += 36f + 6f;
        }
        List<String> wearing = CosmeticsConfig.get().localEquipped();
        int[] counts = counts(wearing);
        for (View v : View.values()) {
            if (v == View.OUTFITS || v == View.SETTINGS) {
                Pix.fill(g, x + 4f, y + 4f, w - 8f, Pix.E, 0xFF2A2A2E);
                y += 4f + Pix.E + 6f;
            }
            int count = switch (v) {
                case OUTFITS -> CosmeticsConfig.get().savedOutfits.size();
                case EMOTES -> Emotes.all().size();
                case SKINS, SETTINGS -> -1;
                case WEARING -> wearing.size();
                default -> counts[v.ordinal()];
            };
            boolean on = v == view, over = ui.over(x, y, w, ROW_H) && ui.over(RAIL_X, TOP, RAIL_W, BOTTOM - TOP);
            int fi = ui.click(x, y, w, ROW_H, () -> go(v));
            if (on) {
                Pix.slab(g, p, x, y, w, ROW_H, Pix.ON);
            } else if (over) {
                Pix.fill(g, x, y, w, ROW_H, 0x0DFFFFFF);
            }
            Icons.draw(g, v.icon(), x + 8f, y + ROW_H / 2f - 12f, 24f, on ? p.acc : 0xFF8D8892, 0f, 0);
            g.textMid(f, g.fitLabel(f, v.label(), w - 76f), x + 40f, y + ROW_H / 2f, on || over ? 0xFFFFFFFF : 0xFFCFC9D3);
            if (count >= 0) {
                g.textRight(cf, String.valueOf(count), x + w - 10f, y + ROW_H / 2f, PixelTokens.INK3);
            }
            if (ui.focused(fi)) {
                Pix.focus(g, x, y, w, ROW_H);
            }
            y += ROW_H + 2f;
        }
        g.endClip();
        railScroll.setMax(y + railScroll.value() - bottom);
        railScroll.step(dt);
        railScroll.bar(g, RAIL_X + RAIL_W - Pix.E - 2f, TOP + Pix.E + 4f, BOTTOM - TOP - 2f * Pix.E - 8f);
    }

    // ------------------------------------------------------------------ the grid

    /** The view and search the order was taken for, and what you wore then (it sorts first). */
    private String orderKey;
    private java.util.Set<String> orderWorn = java.util.Set.of();
    /** The view's cosmetics in order, and what they were worked out for. */
    private List<BakedCosmetic> shownList = List.of();
    private String shownKey = "";

    /** The view's cosmetics as tiles with the notices above them; returns the cosmetic pointed at. */
    private String grid(Gfx g, Tokens tk, float top, float h) {
        drawnOrder.clear();
        tileShown = false;
        gearShown = false;
        CosmeticsConfig config = CosmeticsConfig.get();
        List<BakedCosmetic> all = wardrobe();
        List<String> wearing = config.localEquipped();
        g.clip(MAIN_X - 4f, top - 4f, MAIN_X + MAIN_W + 6f, top + h);
        float y = top - scroll.value();
        y = notices(g, tk, y);
        String pointed = null;
        if (all.isEmpty()) {
            g.text(g.font(SolFont.Family.MEDIUM, 13f), t("wardrobe.empty"), MAIN_X, y + 18f, 0x99FFFFFF);
            y += 30f;
        } else {
            // What you wore when the view (or search) was opened first, then by name, so a tile stays where it is
            // as you put it on or take it off; a search matches names and ids.
            String needle = search.text().trim().toLowerCase(Locale.ROOT);
            String key = view + "|" + needle;
            if (!key.equals(orderKey)) {
                orderKey = key;
                orderWorn = java.util.Set.copyOf(wearing);
            }
            java.util.Set<String> worn = orderWorn;
            String shownFor = key + "|" + CosmeticRegistry.generation() + "|" + source + "|" + worn.hashCode() + "|"
                    + wearing.hashCode() + "|" + wearing.size();
            if (!shownFor.equals(shownKey)) {
                List<BakedCosmetic> sorted = new ArrayList<>(all);
                sorted.sort(Comparator.comparing((BakedCosmetic c) -> !worn.contains(c.id()))
                        .thenComparing(BakedCosmetic::name, String.CASE_INSENSITIVE_ORDER));
                shownList = sorted.stream().filter(c -> view.includes(c, wearing) && matches(c, needle)).toList();
                shownKey = shownFor;
            }
            List<BakedCosmetic> shown = shownList;
            if (shown.isEmpty()) {
                g.text(g.font(SolFont.Family.MEDIUM, 13f), needle.isEmpty() ? t("wardrobe.empty_view")
                        : t("wardrobe.no_match", search.text().trim()), MAIN_X, y + 18f, 0x99FFFFFF);
                y += 30f;
            } else {
                int capped = Math.min(shown.size(), TILE_CAP);
                List<BakedCosmetic> list = shown.subList(0, capped);
                if (view == View.ALL || view == View.WEARING) {
                    // Everything: grouped by slot, as on the Cosmetics screen.
                    for (CosmeticSlot slot : CosmeticSlot.values()) {
                        List<BakedCosmetic> inSlot = list.stream().filter(c -> c.slot() == slot).toList();
                        if (inSlot.isEmpty()) {
                            continue;
                        }
                        SolFont head = g.font(SolFont.Family.PIXEL, 11f);
                        g.textSpaced(head, t("slot." + slot.id()).toUpperCase(Locale.ROOT), MAIN_X, y + 12f, 1.1f, PixelTokens.INK3);
                        y += 22f;
                        float[] out = tiles(g, tk, inSlot, y, top, h);
                        y = out[0];
                        if (out[1] >= 0) {
                            pointed = inSlot.get((int) out[1]).id();
                        }
                    }
                } else if (view == View.KNIVES) {
                    // A knife's finishes together, under its name.
                    java.util.Map<String, List<BakedCosmetic>> groups = new java.util.LinkedHashMap<>();
                    for (BakedCosmetic c : list) {
                        String group = c.definition().group() != null ? c.definition().group() : c.name();
                        groups.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(c);
                    }
                    for (var e : groups.entrySet()) {
                        SolFont head = g.font(SolFont.Family.PIXEL, 11f);
                        g.textSpaced(head, e.getKey().toUpperCase(Locale.ROOT), MAIN_X, y + 12f, 1.1f, PixelTokens.INK3);
                        y += 22f;
                        float[] out = tiles(g, tk, e.getValue(), y, top, h);
                        y = out[0];
                        if (out[1] >= 0) {
                            pointed = e.getValue().get((int) out[1]).id();
                        }
                    }
                } else {
                    float[] out = tiles(g, tk, list, y, top, h);
                    y = out[0];
                    if (out[1] >= 0) {
                        pointed = list.get((int) out[1]).id();
                    }
                }
                if (shown.size() > capped) {
                    g.text(g.font(SolFont.Family.MEDIUM, 12f), t("wardrobe.more", shown.size() - capped), MAIN_X, y + 14f, 0x99FFFFFF);
                    y += 24f;
                }
            }
        }
        g.endClip();
        scroll.setMax(y + scroll.value() - top - h + 12f);
        scroll.step(dt);
        scroll.bar(g, MAIN_X + MAIN_W + 4f, top, h);
        return pointed;
    }

    private static boolean matches(BakedCosmetic c, String needle) {
        return needle.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(needle) || c.id().contains(needle);
    }

    /**
     * Above the tiles: an imported pack from an older importer, cosmetics that couldn't load (Copy all, and the first
     * three reasons), and a warning while your cosmetics are hidden.
     */
    private float notices(Gfx g, Tokens tk, float y) {
        CosmeticsConfig config = CosmeticsConfig.get();
        List<String> lines = new ArrayList<>();
        for (String ns : CosmeticLoader.outdatedImports()) {
            lines.add(t("wardrobe.reimport", importName(ns)));
        }
        if (!config.showOwnCosmetics || !config.enabled) {
            lines.add(t("wardrobe.hidden"));
        }
        SolFont f = g.font(SolFont.Family.MEDIUM, 12.5f);
        for (String l : lines) {
            y = notice(g, tk, f, l, y, null, null);
        }
        List<String> failed = CosmeticLoader.failures();
        if (!failed.isEmpty()) {
            String head = t("wardrobe.failed", failed.size());
            String button = copiedFailures == failed.size() ? t("wardrobe.failed_copied") : t("wardrobe.failed_copy");
            y = notice(g, tk, f, head, y, button, () -> {
                minecraft.keyboardHandler.setClipboard(String.join("\n", failed));
                copiedFailures = failed.size();
            });
            SolFont s = g.font(SolFont.Family.MONO, 11f);
            for (String why : failed.subList(0, Math.min(3, failed.size()))) {
                g.text(s, g.fit(s, why, MAIN_W - 16f), MAIN_X + 12f, y + 2f, 0x99FFFFFF);
                y += 16f;
            }
            y += 8f;
        }
        return y;
    }

    private float notice(Gfx g, Tokens tk, SolFont f, String text, float y, String button, Runnable action) {
        float h = 40f;
        Pix.fill(g, MAIN_X, y, MAIN_W, h, Colors.mixRgb(PixelTokens.get().c1, Tokens.AMBER, 0.12f));
        g.stroke(MAIN_X, y, MAIN_W, h, 0f, g.px(Pix.E), Tokens.AMBER);
        Icons.draw(g, "bolt", MAIN_X + 10f, y + h / 2f - 12f, 24f, Tokens.AMBER, 0f, 0);
        float room = MAIN_W - 46f - (button != null ? g.width(f, button) + 40f : 0f);
        g.textMid(f, g.fit(f, text, room), MAIN_X + 42f, y + h / 2f, 0xFFFFFFFF);
        if (button != null) {
            ui.button(tk, MAIN_X + MAIN_W - 6f, y + h / 2f, button, false, action);
        }
        return y + h + 8f;
    }

    private static String importName(String namespace) {
        return switch (namespace) {
            case "blc_import" -> "Badlion";
            case "lunar_import" -> "Lunar";
            case "norisk_import" -> "NoRisk";
            default -> namespace;
        };
    }

    /** Tiles as many to a row as fit; returns {the y below them, the index pointed at or -1}. */
    private float[] tiles(Gfx g, Tokens tk, List<BakedCosmetic> list, float y, float top, float h) {
        PixelTokens p = PixelTokens.get();
        int cols = Math.max(1, (int) ((MAIN_W + TILE_GAP) / (TILE_MIN + TILE_GAP)));
        float tw = (MAIN_W - TILE_GAP * (cols - 1)) / cols, th = TILE_H;
        List<String> wearing = CosmeticsConfig.get().localEquipped();
        int pointed = -1;
        SolFont nf = g.font(SolFont.Family.SEMIBOLD, 13f), tagf = g.font(SolFont.Family.PIXEL_BOLD, 9f);
        for (int i = 0; i < list.size(); i++) {
            BakedCosmetic c = list.get(i);
            float tx = MAIN_X + (i % cols) * (tw + TILE_GAP), ty = y + (i / cols) * (th + TILE_GAP);
            if (ty + th < top - 8f || ty > top + h + 8f) {
                continue;
            }
            boolean worn = wearing.contains(c.id());
            boolean hover = ui.over(tx, ty, tw, th) && ui.over(BOX_X, TOP, BOX_W, BOTTOM - TOP);
            if (hover) {
                pointed = i;
            }
            drawnOrder.add(c.id());
            if (!tileShown) {
                tileShown = true;
                tileId = c.id();
                tileX = tx;
                tileY = ty;
            }
            int fi = ui.click(tx, ty, tw, th, () -> setEquipped(c.id(), !worn));
            // The gear (shown on hover, focus or worn) after the tile, so its hit is on top: a click on it opens the
            // cosmetic's page instead of wearing it.
            boolean gear = hover || worn || ui.focused(fi);
            int gearFocus = gear ? ui.click(tx + tw - 38f, ty + 6f, 32f, 32f, () -> openCosmetic(c.id())) : -1;
            if (gear && !gearShown) {
                gearShown = true;
                gearX = tx + tw - 38f;
                gearY = ty + 6f;
            }
            if (worn) {
                Pix.worn(g, p, tx, ty, tw, th);
            } else {
                Pix.slab(g, p, tx, ty, tw, th, hover ? Pix.HOVER : Pix.NORMAL);
            }
            float ax = tx + 10f, ay = ty + 10f, aw = tw - 20f, ah = 96f;
            float size = Math.min(aw, ah);
            int sz = screenSize(g, size);
            int px = screenX(g, ax + aw / 2f) - sz / 2, py = screenY(g, ay + ah / 2f) - sz / 2;
            if (!pictures.draw(g.game(), c, px, py, sz, hover)) {
                // Nothing to picture (only particles) or not made yet: a few pixel sparkles.
                for (int k = 0; k < 6; k++) {
                    double a = k * Math.PI / 3 + time * (hover && !calm() ? 1.5 : 0.0);
                    Pix.fill(g, ax + aw / 2f + (float) Math.cos(a) * size * 0.28f - 2f, ay + ah / 2f + (float) Math.sin(a) * size * 0.28f - 2f,
                            4f, 4f, p.acc);
                }
            }
            g.layer();
            String tag = worn ? t("wardrobe.worn_tag") : null;
            float tagW = tag != null ? g.width(tagf, tag) + 6f : 0f;
            g.textMid(nf, g.fitLabel(nf, c.name(), tw - 20f - tagW), tx + 10f, ty + th - 18f, worn ? 0xFFFFFFFF : 0xFFD9D4DC);
            if (tag != null) {
                g.textRight(tagf, tag, tx + tw - 10f, ty + th - 18f, p.enText);
            }
            if (gear) {
                gearButton(g, tk, tx + tw - 38f, ty + 6f, gearFocus);
            }
            if (ui.focused(fi)) {
                Pix.focus(g, tx, ty, tw, th);
            }
        }
        int rows = (list.size() + cols - 1) / cols;
        return new float[]{y + rows * (th + TILE_GAP), pointed};
    }

    /** A tile's gear: a 32 px slab over its picture. */
    private void gearButton(Gfx g, Tokens tk, float x, float y, int focus) {
        PixelTokens p = PixelTokens.get();
        boolean over = ui.over(x, y, 32f, 32f);
        Pix.slab(g, p, x, y, 32f, 32f, over ? Pix.HOVER : Pix.NORMAL);
        Icons.draw(g, "gear", x + 4f, y + 4f, 24f, Pix.text(p, over ? Pix.HOVER : Pix.NORMAL), 0f, 0);
        if (ui.focused(focus)) {
            Pix.focus(g, x, y, 32f, 32f);
        }
    }

    // ------------------------------------------------------------------ outfits

    /** Saved outfits as tiles (point to try on, click to wear, the gear for its page); returns the one pointed at. */
    private CosmeticsConfig.Outfit outfits(Gfx g, Tokens tk, float top, float h) {
        CosmeticsConfig config = CosmeticsConfig.get();
        g.clip(MAIN_X - 4f, top - 4f, MAIN_X + MAIN_W + 6f, top + h);
        float y = top - scroll.value();
        // What outfits are, then the button that saves one.
        SolFont s = g.font(SolFont.Family.MEDIUM, 13f);
        g.textMid(s, g.fit(s, config.savedOutfits.isEmpty() ? t("outfits.empty") : t("outfits.hint"), MAIN_W), MAIN_X, y + 8f,
                PixelTokens.INK2);
        y += 24f;
        ui.bigButton(MAIN_X, y, "plus", t("outfits.save"), true, false, () -> {
            CosmeticsConfig.Outfit o = config.saveOutfit(t("outfits.name"));
            CosmeticsConfig.save();
            openOutfit(o);
        });
        y += 42f + 14f;
        CosmeticsConfig.Outfit pointed = null;
        PixelTokens px = PixelTokens.get();
        int cols = Math.max(1, (int) ((MAIN_W + TILE_GAP) / (TILE_MIN + TILE_GAP)));
        float gap = TILE_GAP, tw = (MAIN_W - gap * (cols - 1)) / cols, th = 156f;
        SolFont nf = g.font(SolFont.Family.SEMIBOLD, 12.5f), cf = g.font(SolFont.Family.MEDIUM, 11f);
        List<CosmeticsConfig.Outfit> list = List.copyOf(config.savedOutfits);
        for (int i = 0; i < list.size(); i++) {
            CosmeticsConfig.Outfit o = list.get(i);
            float tx = MAIN_X + (i % cols) * (tw + gap), ty = y + (i / cols) * (th + gap);
            boolean worn = config.wearsOutfit(o);
            boolean hover = ui.over(tx, ty, tw, th);
            if (hover) {
                pointed = o;
            }
            ui.click(tx, ty, tw, th, () -> {
                config.wearOutfit(o);
                EquippedCosmetics.invalidate();
            });
            // The gear's hit after the tile's, so it's the one on top.
            int gearFocus = hover || worn ? ui.click(tx + tw - 38f, ty + 6f, 32f, 32f, () -> openOutfit(o)) : -1;
            if (worn) {
                Pix.worn(g, px, tx, ty, tw, th);
            } else {
                Pix.slab(g, px, tx, ty, tw, th, hover ? Pix.HOVER : Pix.NORMAL);
            }
            // Its first four cosmetics, two by two.
            float cell = Math.min((tw - 20f) / 2f, 48f), ax = tx + (tw - 2f * cell) / 2f, ay = ty + 10f;
            int shown = 0;
            for (String id : o.cosmetics) {
                BakedCosmetic c = CosmeticRegistry.get(id);
                if (c == null) {
                    continue;
                }
                float cx = ax + (shown % 2) * cell, cy = ay + (shown / 2) * cell;
                int sz = screenSize(g, cell - 4f);
                if (!pictures.draw(g.game(), c, screenX(g, cx + 2f), screenY(g, cy + 2f), sz, false)) {
                    g.rect(cx + cell / 2f - 1f, cy + cell / 2f - 1f, 2f, 2f, 0x80FFFFFF);
                }
                if (++shown == 4) {
                    break;
                }
            }
            g.layer();
            int more = o.cosmetics.size() - shown;
            if (shown == 4 && more > 0) {
                g.textRight(cf, "+" + more, tx + tw - 10f, ay + 2f * cell - 6f, 0x99FFFFFF);
            }
            // Its name, and how many things it has as a pixel tag.
            SolFont tagf = g.font(SolFont.Family.PIXEL_BOLD, 9f);
            String items = t("outfits.items", o.cosmetics.size());
            float tagW = g.width(tagf, items);
            g.textMid(nf, g.fitLabel(nf, o.name, tw - 26f - tagW), tx + 10f, ty + th - 18f, worn ? 0xFFFFFFFF : 0xFFD9D4DC);
            g.textRight(tagf, items, tx + tw - 10f, ty + th - 18f, worn ? px.enText : PixelTokens.INK3);
            if (hover || worn) {
                gearButton(g, tk, tx + tw - 38f, ty + 6f, gearFocus);
            }
        }
        int rows = (list.size() + cols - 1) / cols;
        y += rows * (th + gap);
        g.endClip();
        scroll.setMax(y + scroll.value() - top - h + 12f);
        scroll.step(dt);
        scroll.bar(g, MAIN_X + MAIN_W + 4f, top, h);
        return pointed;
    }

    // ------------------------------------------------------------------ emotes

    /** Every emote: play it (it shows on you), put it on the emote wheel or the elytra wheel, or take it off. */
    private void emotes(Gfx g, Tokens tk, float top, float h) {
        g.clip(MAIN_X - 4f, top - 4f, MAIN_X + MAIN_W + 6f, top + h);
        PageRows rows = new PageRows(g, ui, tk, MAIN_X, top - scroll.value(), MAIN_W, null);
        rows.text(t("emotes.hint", KeyBinds.name(KeyBinds.emotes)), 0x99FFFFFF);
        if (Emotes.all().isEmpty()) {
            rows.text(t("emotes.empty"), 0x99FFFFFF);
        } else {
            if (!CosmeticsConfig.get().emotes) {
                rows.text(t("emotes.off"), Colors.withAlpha(Tokens.AMBER, 0.95f));
            }
            for (Emote e : Emotes.all()) {
                String id = e.id();
                boolean onWheel = Emotes.onWheel(id, false), onElytra = Emotes.onWheel(id, true);
                String status = EmotePlayer.playing() == e ? t("emotes.playing")
                        : onWheel && onElytra ? t("emotes.on_both") : onWheel ? t("emotes.on_wheel")
                        : onElytra ? t("emotes.on_elytra") : "";
                float cy = rows.row(Emotes.name(e), status);
                if (Float.isNaN(cy)) {
                    continue;
                }
                float r = rows.right();
                r -= ui.button(tk, r, cy, t(onElytra ? "emotes.elytra_remove" : "emotes.elytra_add"), false,
                        () -> Emotes.setOnWheel(id, true, !onElytra)) + 6f;
                r -= ui.button(tk, r, cy, t(onWheel ? "emotes.wheel_remove" : "emotes.wheel_add"), false,
                        () -> Emotes.setOnWheel(id, false, !onWheel)) + 6f;
                ui.button(tk, r, cy, t("emotes.play"), true, () -> EmotePlayer.play(e));
            }
        }
        float end = rows.y;
        g.endClip();
        scroll.setMax(end + scroll.value() - top - h + 12f);
        scroll.step(dt);
        scroll.bar(g, MAIN_X + MAIN_W + 4f, top, h);
    }

    // ------------------------------------------------------------------ you, on the right

    /** You, in the preview pane: the preset's sky in pixels behind you, the Elytra chip, and the caption. */
    private void previewPanel(Gfx g, Tokens tk) {
        PixelTokens p = PixelTokens.get();
        float x = PREVIEW_X, y = TOP, w = PREVIEW_W, h = BOTTOM - TOP;
        Pix.pane(g, p, x, y, w, h);
        io.github.georgwav.cosmetics.sol.render.SolTexture t = sky.get(ThemeState.baseEntry());
        if (t != null) {
            Pix.pixels(g, t, sky.w, sky.h, x + Pix.E, y + Pix.E, w - 2f * Pix.E, h - 2f * Pix.E);
        }
        Pix.paneEdge(g, p, x, y, w, h);
        // Drag to turn and tilt (the Elytra chip first, so a click on it isn't a drag).
        // (Not on the mannequin: it has no elytra to show.)
        if (!mannequinShown()) {
            String elytra = t("preview.elytra");
            SolFont cf = g.font(SolFont.Family.PIXEL_BOLD, 11f);
            float chipW = g.width(cf, elytra) + 24f;
            ui.chip(tk, x + w - 12f - chipW, y + 28f, elytra, previewElytra, () -> previewElytra = !previewElytra);
        }
        ui.drag(x, y, w, h - 80f, (px, py) -> {
            if (Float.isNaN(dragX)) {
                dragX = px;
                dragY = py;
                return;
            }
            previewSpin += (px - dragX) * 1.6f;
            previewPitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, previewPitch - (py - dragY) * 1.2f));
            dragX = px;
            dragY = py;
        });
        BakedCosmetic shown = tryingOn != null ? CosmeticRegistry.get(tryingOn) : null;
        Pix.caption(g, p, x, y, w, h, shown != null ? t("preview.trying_title") : t("preview.you"),
                shown != null ? shown.name() : t("preview.drag"));
    }

    /** You without a world (the Wardrobe opened from the title screen), with what you wear. */
    private io.github.georgwav.cosmetics.ui.Mannequin mannequin;
    /** For the in-game test: the mannequin even in a world. */
    public static boolean forceMannequin;

    /** Whether the preview is the mannequin: no world yet (or the in-game test asks for it). */
    private boolean mannequinShown() {
        return minecraft.player == null || forceMannequin;
    }

    @Override
    protected void game(Canvas c, int mouseX, int mouseY) {
        double gs = minecraft.getWindow().getGuiScale();
        int x1 = (int) Math.round(d.sx(PREVIEW_X) / gs), x2 = (int) Math.round(d.sx(PREVIEW_X + PREVIEW_W) / gs);
        int y1 = (int) Math.round(d.sy(TOP + 48f) / gs), y2 = (int) Math.round(d.sy(BOTTOM - 84f) / gs);
        boolean onMannequin = mannequinShown();
        BakedCosmetic shown = tryingOn != null ? CosmeticRegistry.get(tryingOn) : null;
        // Capes, wings and backpacks are worn on the back: turn round to show them, and the elytra when it shows.
        boolean back = shown != null ? shown.slot() == CosmeticSlot.CAPE || shown.slot() == CosmeticSlot.BACK || shown.wings() != null
                : previewElytra && !onMannequin;
        float target = previewSpin + (back ? 180f : 0f);
        shownSpin += (target - shownSpin) * 0.2f;
        if (Math.abs(target - shownSpin) < 0.5f) {
            shownSpin = target;
        }
        shownPitch += (previewPitch - shownPitch) * 0.3f;
        if (onMannequin) {
            if (mannequin == null) {
                mannequin = new io.github.georgwav.cosmetics.ui.Mannequin("wardrobe", 256);
            }
            int side = Math.min(x2 - x1, y2 - y1);
            mannequin.draw(c, (x1 + x2 - side) / 2, (y1 + y2 - side) / 2, side, -shownSpin, 8f + shownPitch, false);
            return;
        }
        // The player about two thirds of the panel's height, with room above for halos and below for the feet.
        int scale = Math.max(20, (int) ((y2 - y1) / 3.4f));
        if (previewElytra) {
            CapeElytra.wearingElytra(minecraft.player, () -> c.entityTurned(x1, y1, x2, y2, scale, 0.0625f, mouseX, mouseY,
                    minecraft.player, shownSpin, shownPitch));
        } else {
            c.entityTurned(x1, y1, x2, y2, scale, 0.0625f, mouseX, mouseY, minecraft.player, shownSpin, shownPitch);
        }
        // The auras' particles, turned the way the preview turns you.
        particles.update(EquippedCosmetics.forPlayer(minecraft.player.getUUID(), true), 180f + shownSpin);
        float entityScale = minecraft.player.getScale();
        float feetY = minecraft.player.getBbHeight() / entityScale / 2f + 0.0625f;
        org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(shownPitch));
        c.clip(x1, y1, x2, y2);
        particles.draw(c, (x1 + x2) / 2f, (y1 + y2) / 2f, scale, feetY, pose);
        c.endClip();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected boolean press(float x, float y, int button) {
        if (listeningFor != null) {
            if (button != InputConstants.MOUSE_BUTTON_LEFT) {
                KeyBinds.bind(listeningFor, KeyBinds.mouse(button));
            }
            listeningFor = null;
            return true;
        }
        dragX = Float.NaN;
        boolean onField = search.focused();
        search.focus(false);
        if (cosmeticPage != null) {
            cosmeticPage.unfocus();
        }
        if (outfitPageView != null) {
            outfitPageView.unfocus();
        }
        settingsView.unfocus();
        boolean took = ui.press(x, y);
        return took || onField;
    }

    @Override
    protected boolean drag(float x, float y, int button) {
        return ui.dragTo(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        dragX = Float.NaN;
        return ui.release();
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        if (x >= RAIL_X && x <= RAIL_X + RAIL_W && y >= TOP && y <= BOTTOM) {
            return railScroll.wheel(amount);
        }
        if (x >= BOX_X && x <= BOX_X + BOX_W && y >= TOP && y <= BOTTOM) {
            if (cosmeticPage != null) {
                return cosmeticPage.scroll(amount);
            }
            if (outfitPageView != null) {
                return outfitPageView.scroll(amount);
            }
            if (view == View.SETTINGS) {
                return settingsView.scroll(amount);
            }
            if (view == View.SKINS) {
                return skinsView.scroll(amount);
            }
            return scroll.wheel(amount);
        }
        return false;
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        if (listeningFor != null) {
            KeyBinds.bind(listeningFor, key == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : KeyBinds.keyboard(key, 0));
            listeningFor = null;
            minecraft.options.save();
            return true;
        }
        if (search.key(key, ctrl) || (cosmeticPage != null && cosmeticPage.key(key, ctrl))
                || (outfitPageView != null && outfitPageView.key(key, ctrl)) || (view == View.SETTINGS && settingsView.key(key, ctrl))) {
            return true;
        }
        switch (key) {
            case InputConstants.KEY_TAB -> {
                ui.tab(shift);
                return true;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> {
                return ui.activate();
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    protected boolean wantsText() {
        // Typing in a cosmetics view searches it.
        return view.lists() && cosmeticPage == null && outfitPageView == null;
    }

    @Override
    protected boolean typed(String text) {
        if (search.typed(text) || (cosmeticPage != null && cosmeticPage.typed(text))
                || (outfitPageView != null && outfitPageView.typed(text)) || (view == View.SETTINGS && settingsView.typed(text))) {
            return true;
        }
        // Typing in a cosmetics view searches it.
        if (view.lists() && cosmeticPage == null && outfitPageView == null && !text.isBlank()) {
            search.focus(true);
            return search.typed(text);
        }
        return false;
    }

    /** Esc: out of a page back to its list, else out of the Wardrobe. */
    @Override
    protected boolean back() {
        if (cosmeticPage != null) {
            openCosmetic(null);
            return true;
        }
        if (outfitPageView != null) {
            openOutfit(null);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        leave();
        if (fromQuick && !(parent instanceof SolMenu)) {
            ScreenOpener.open(new QuickMenu(parent, true, "wardrobe"));
        } else {
            ScreenOpener.open(parent);
        }
    }

    @Override
    protected void closed() {
        bar.release();
        sky.release();
        pictures.close();
        skinsView.release();
        if (mannequin != null) {
            mannequin.release();
            mannequin = null;
        }
        EquippedCosmetics.preview(null);
    }

    /**
     * The pages the in-game smoke test opens, in order:
     * - the grid, a cosmetic's page, the outfits and the emotes;
     * - a cape's page with the preview wearing an elytra;
     * - every settings page.
     */
    public static final String[] TEST_PAGES = {"all", "drawer", "outfits", "emotes", "drawer_cape", "settings_display",
            "settings_performance", "settings_compatibility", "settings_maintenance"};

    public static WardrobeScreen testPage(int page) {
        WardrobeScreen s = new WardrobeScreen(null);
        String state = TEST_PAGES[page];
        if (state.startsWith("settings_")) {
            s.stage("settings");
        }
        return s.stage(state);
    }

    /** How many launcher skins the last Skins view shows, and whether their read finished (the in-game test). */
    public static int skinsShown() {
        return SkinsView.shown;
    }

    public static boolean skinsRead() {
        return SkinsView.read;
    }

    /** Tile pictures made since the game started (for the in-game test). */
    public static int tilePicturesMade() {
        return TilePictures.made;
    }

    /** Whether the preview of you is drawn (with a player to show; for the in-game test). */
    public boolean showsPreview() {
        return minecraft != null && minecraft.player != null;
    }

    /** Whether the page shown starts at the top of its list (for the in-game test: nothing scrolled it). */
    public boolean listAtTop() {
        return scroll.value() == 0f && (cosmeticPage == null || cosmeticPage.atTop())
                && (outfitPageView == null || outfitPageView.atTop()) && settingsView.atTop();
    }

    // For the in-game test: the tiles drawn last frame in order, the first tile and the first gear shown.
    private final List<String> drawnOrder = new ArrayList<>();
    private boolean tileShown, gearShown;
    private String tileId;
    private float tileX, tileY, gearX, gearY;
    private List<String> orderBefore;
    private String clicked;
    private boolean clickedWasWorn;

    /** For the in-game test: clicks the first tile (as a click on it would), remembering where every tile was. */
    public boolean testClickTile() {
        if (!tileShown) {
            return false;
        }
        orderBefore = List.copyOf(drawnOrder);
        clicked = tileId;
        clickedWasWorn = CosmeticsConfig.get().localEquipped().contains(tileId);
        return ui.press(tileX + 20f, tileY + 60f);
    }

    /**
     * Frames later: what's wrong (the tile didn't change, or tiles moved); the tile is put back as it was, then a
     * gear is clicked, which must open its cosmetic's page.
     */
    public List<String> testCheckTileAndGear() {
        List<String> problems = new ArrayList<>();
        if (clicked == null) {
            problems.add("the Wardrobe showed no tile to click");
            return problems;
        }
        if (CosmeticsConfig.get().localEquipped().contains(clicked) == clickedWasWorn) {
            problems.add("clicking a Wardrobe tile didn't " + (clickedWasWorn ? "take it off" : "put it on"));
        }
        if (!drawnOrder.equals(orderBefore)) {
            problems.add("the Wardrobe's tiles moved when one was clicked (" + orderBefore + " -> " + drawnOrder + ")");
        }
        setEquipped(clicked, clickedWasWorn);
        if (!gearShown) {
            problems.add("no Wardrobe tile showed its gear");
        } else if (!ui.press(gearX + 13f, gearY + 13f) || cosmeticPage == null) {
            problems.add("clicking a Wardrobe tile's gear didn't open the cosmetic's page");
        }
        return problems;
    }

    /** For the scenario: a state the reference shots show. */
    public WardrobeScreen stage(String state) {
        previewElytra = false;
        stagedTry = null;
        switch (state) {
            case "try_on" -> {
                // All, with a hat you don't wear being tried on.
                go(View.ALL);
                java.util.Set<String> worn = new java.util.HashSet<>(CosmeticsConfig.get().localEquipped());
                for (BakedCosmetic c : wardrobe()) {
                    if (!worn.contains(c.id()) && (stagedTry == null || c.slot() == CosmeticSlot.HEAD)) {
                        stagedTry = c.id();
                        if (c.slot() == CosmeticSlot.HEAD) {
                            break;
                        }
                    }
                }
            }
            case "drawer" -> {
                BakedCosmetic pick = null;
                for (BakedCosmetic c : wardrobe()) {
                    if (pick == null || (c.hasAnimation() && c.hasEmissive())) {
                        pick = c;
                    }
                }
                if (pick != null) {
                    openCosmetic(pick.id());
                }
            }
            case "drawer_cape", "drawer_pet", "drawer_knife" -> {
                CosmeticSlot slot = state.endsWith("cape") ? CosmeticSlot.CAPE : state.endsWith("pet") ? CosmeticSlot.PET
                        : CosmeticSlot.MELEE;
                BakedCosmetic pick = null;
                for (BakedCosmetic c : wardrobe()) {
                    if (c.slot() == slot && (pick == null || c.hasEmissive())) {
                        pick = c;
                    }
                }
                if (pick != null) {
                    openCosmetic(pick.id());
                    if (slot == CosmeticSlot.CAPE) {
                        previewElytra = true;
                    }
                }
            }
            case "outfit_page" -> {
                CosmeticsConfig config = CosmeticsConfig.get();
                if (config.savedOutfits.isEmpty()) {
                    config.saveOutfit(t("outfits.name"));
                }
                openOutfit(config.savedOutfits.get(0));
            }
            default -> {
                for (View v : View.values()) {
                    if (v.name().equalsIgnoreCase(state)) {
                        go(v);
                    }
                }
                settingsView.stage(state);
            }
        }
        return this;
    }
}
