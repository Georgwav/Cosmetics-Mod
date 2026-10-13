package io.github.georgwav.cosmetics.ui;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.model.CosmeticLoader;
import io.github.georgwav.cosmetics.model.CosmeticOption;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.widget.FlatWidget;
import io.github.georgwav.cosmetics.ui.widget.Rows;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings screen: a flat panel with a live preview of your player on the left and four tabs on
 * the right (Wardrobe, Display, Performance, Compatibility). The arrow on a wardrobe row opens that
 * cosmetic's own page (colour, size, position, glow, animation). Everything is drawn here with plain fills
 * and text, so it looks the same on every Minecraft version and doesn't depend on any GUI library.
 *
 * <p>Changes apply immediately (the preview updates live) and are saved when the screen closes.
 */
public final class CosmeticsScreen extends Screen {
    private enum Tab { WARDROBE, DISPLAY, PERFORMANCE, COMPATIBILITY }

    /** The wardrobe's categories, in the side bar's order. */
    private enum View {
        WEARING, ALL, CAPES, WINGS, HATS, FACE, BODY, BACK, SHIELDS, AURAS, PETS, LIMBS, EMOTES, OUTFITS;

        boolean includes(BakedCosmetic cosmetic, List<String> wearing) {
            return switch (this) {
                case WEARING -> wearing.contains(cosmetic.id());
                case ALL -> true;
                case CAPES -> cosmetic.slot() == CosmeticSlot.CAPE;
                case WINGS -> cosmetic.wings() != null;
                case HATS -> cosmetic.slot() == CosmeticSlot.HEAD;
                case FACE -> cosmetic.slot() == CosmeticSlot.FACE;
                case BODY -> cosmetic.slot() == CosmeticSlot.BODY;
                case BACK -> cosmetic.slot() == CosmeticSlot.BACK && cosmetic.wings() == null;
                case SHIELDS -> cosmetic.slot() == CosmeticSlot.SHIELD;
                case AURAS -> cosmetic.slot() == CosmeticSlot.AURA;
                case PETS -> cosmetic.slot() == CosmeticSlot.PET;
                case LIMBS -> cosmetic.slot() == CosmeticSlot.ARM || cosmetic.slot() == CosmeticSlot.FEET;
                case EMOTES, OUTFITS -> false;
            };
        }

        /** Lists cosmetics, so it has the search field. */
        boolean searchable() {
            return this != EMOTES && this != OUTFITS;
        }
    }

    private static Tab lastTab = Tab.WARDROBE;
    private static View lastView = View.ALL;
    /** Most rows one list shows (only so a broken pack can't build a million widgets). */
    private static final int ROWS_PER_VIEW = 10_000;
    private static final int SIDEBAR_WIDTH = 104;
    /** How far you turned the preview round and tipped it (degrees), kept while the game runs. */
    private static float previewSpin;
    private static float previewPitch;
    private static final float MAX_PREVIEW_PITCH = 80f;
    /** The wardrobe search, kept while the game runs. */
    private static String lastQuery = "";
    private static final int SEARCH_HEIGHT = 16;
    /** Frames drawn since start; read by the in-game test to prove the screen renders on every version. */
    public static int framesRendered;
    public static final int TAB_COUNT = Tab.values().length;

    private final Screen parent;
    private final List<FlatWidget> chrome = new ArrayList<>();
    private final List<FlatWidget> content = new ArrayList<>();
    private final List<Integer> contentOffsets = new ArrayList<>();
    private Tab tab = lastTab;
    /** Cosmetic whose page is open in the Wardrobe tab, or null for the list. */
    private String detailId;
    private int scroll;
    private int contentHeight;
    private int px, py, pw, ph;
    private int listX, listY, listW, listH;
    private boolean showPreview;
    private int previewX, previewW;
    private int sidebarX;
    private View view = lastView;
    /** The wardrobe's cosmetic rows and the cosmetic each shows, for trying on whatever the mouse points at. */
    private final Map<FlatWidget, String> rowCosmetics = new java.util.IdentityHashMap<>();
    /** The cosmetic the preview shows you in right now without you wearing it, or null. */
    private String tryingOn;
    /** The preview's turn as drawn, easing towards {@link #previewSpin} (plus a half turn for back items). */
    private float shownSpin = previewSpin;
    private float shownPitch = previewPitch;
    /** Aura particles around the preview. */
    private final PreviewParticles previewParticles = new PreviewParticles();
    private String query = lastQuery;
    /** The wardrobe's search field, when the wardrobe list is showing. */
    private EditBox searchBox;

    public CosmeticsScreen(Screen parent) {
        super(Component.translatable("cosmeticsmod.screen.title"));
        this.parent = parent;
    }

    /** Opens on a given tab (0 = Wardrobe ... {@link #TAB_COUNT} - 1). */
    public CosmeticsScreen(Screen parent, int tabIndex) {
        this(parent);
        this.tab = Tab.values()[Math.floorMod(tabIndex, TAB_COUNT)];
    }

    /** Opens straight on one cosmetic's page. */
    public static CosmeticsScreen forCosmetic(Screen parent, String cosmeticId) {
        CosmeticsScreen screen = new CosmeticsScreen(parent, Tab.WARDROBE.ordinal());
        screen.detailId = cosmeticId;
        return screen;
    }

    /** Pages the in-game test opens: every tab, a cosmetic's own page, then the wardrobe's outfits. */
    public static final int TEST_PAGE_COUNT = TAB_COUNT + 2;

    public static String testPageName(int page) {
        return page < TAB_COUNT ? tabName(page) : page == TAB_COUNT ? "cosmetic" : "outfits";
    }

    public static CosmeticsScreen testPage(int page) {
        if (page < TAB_COUNT) {
            return new CosmeticsScreen(null, page);
        }
        if (page > TAB_COUNT) {
            CosmeticsScreen outfits = new CosmeticsScreen(null, Tab.WARDROBE.ordinal());
            outfits.view = View.OUTFITS;
            return outfits;
        }
        // Prefer a cosmetic with every option (glow and animation) so the whole page gets drawn.
        BakedCosmetic pick = null;
        for (BakedCosmetic cosmetic : wardrobe()) {
            if (pick == null || (cosmetic.hasAnimation() && cosmetic.hasEmissive())) {
                pick = cosmetic;
            }
        }
        return forCosmetic(null, pick != null ? pick.id() : "");
    }

    public static String tabName(int tabIndex) {
        return Tab.values()[Math.floorMod(tabIndex, TAB_COUNT)].name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.screen." + key, args).getString();
    }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        chrome.clear();
        content.clear();
        contentOffsets.clear();
        contentHeight = 0;
        searchBox = null;
        rowCosmetics.clear();

        // As big as the window allows, within reason.
        pw = Math.min(width - 16, 880);
        ph = Math.min(height - 16, 540);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        BakedCosmetic detail = detailId != null && tab == Tab.WARDROBE ? CosmeticRegistry.get(detailId) : null;
        boolean wardrobeList = tab == Tab.WARDROBE && detail == null;
        int left = px + Theme.PAD;
        if (wardrobeList) {
            sidebarX = left;
            left += SIDEBAR_WIDTH + 8;
        }
        previewW = Math.max(Theme.PREVIEW_WIDTH, Math.min(230, pw / 4));
        showPreview = minecraft != null && minecraft.player != null && px + pw - left - previewW >= 260;
        if (showPreview) {
            previewX = left;
            left += previewW + Theme.PAD;
            int top = py + Theme.HEADER_HEIGHT + 6;
            addChrome(new Rows.DragArea(previewX, top, previewW, py + ph - Theme.FOOTER_HEIGHT - 6 - top,
                    (dx, dy) -> {
                        previewSpin += (float) dx * 2f;
                        previewPitch = Math.max(-MAX_PREVIEW_PITCH, Math.min(MAX_PREVIEW_PITCH, previewPitch - (float) dy * 1.5f));
                    }));
        }

        // Back arrow at the left of the header: from a cosmetic's page back to the list, else out of the screen.
        addChrome(new Rows.Button(px + Theme.PAD - 4, py + 6, 20, Theme.HEADER_HEIGHT - 12, "<", false, () -> {
            if (detailId != null) {
                openDetail(null);
            } else {
                onClose();
            }
        }));

        // Tabs, right-aligned in the header.
        int tabRight = px + pw - Theme.PAD + 6;
        Tab[] tabs = Tab.values();
        for (int i = tabs.length - 1; i >= 0; i--) {
            Tab each = tabs[i];
            String label = t("tab." + each.name().toLowerCase(java.util.Locale.ROOT));
            int w = font.width(label) + 14;
            tabRight -= w;
            addChrome(new Rows.Tab(tabRight, py + 6, w, Theme.HEADER_HEIGHT - 8, label, () -> tab == each,
                    () -> switchTab(each)));
        }

        // Footer buttons.
        int by = py + ph - Theme.FOOTER_HEIGHT + 6;
        int bh = Theme.FOOTER_HEIGHT - 12;
        addChrome(new Rows.Button(px + pw - Theme.PAD - 80, by, 80, bh, t("done"), true, this::onClose));
        addChrome(new Rows.Button(px + pw - Theme.PAD - 80 - 6 - 96, by, 96, bh, t("reset"), false, () -> {
            CosmeticsConfig.get().resetSettings();
            EquippedCosmetics.invalidate();
            rebuildWidgets();
        }));

        listX = left;
        listY = py + Theme.HEADER_HEIGHT + 6;
        listW = px + pw - Theme.PAD - listX - 4; // room for the scrollbar
        listH = py + ph - Theme.FOOTER_HEIGHT - 6 - listY;

        if (wardrobeList) {
            buildSidebar();
        }
        if (wardrobeList && view.searchable() && !wardrobe().isEmpty()) {
            Component hint = Component.translatable("cosmeticsmod.screen.wardrobe.search").withStyle(ChatFormatting.DARK_GRAY);
            searchBox = new EditBox(font, listX, listY, listW, SEARCH_HEIGHT, hint);
            searchBox.setMaxLength(64);
            searchBox.setValue(query);
            searchBox.setHint(hint);
            searchBox.setResponder(this::search);
            addWidget(searchBox);
            listY += SEARCH_HEIGHT + 4;
            listH -= SEARCH_HEIGHT + 4;
        }
        switch (tab) {
            case WARDROBE -> {
                if (detail != null) {
                    buildDetail(detail);
                } else {
                    buildWardrobe();
                }
            }
            case DISPLAY -> buildDisplay();
            case PERFORMANCE -> buildPerformance();
            case COMPATIBILITY -> buildCompatibility();
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        layoutContent();
    }

    private void switchTab(Tab next) {
        if (tab != next || detailId != null) {
            tab = next;
            lastTab = next;
            detailId = null;
            scroll = 0;
            rebuildWidgets();
        }
    }

    private void search(String text) {
        if (text.equals(query)) {
            return;
        }
        query = text;
        lastQuery = text;
        refreshList();
    }

    /** Rebuilds only the wardrobe list; rebuilding everything would take the focus away from the search field. */
    private void refreshList() {
        for (FlatWidget widget : content) {
            removeWidget(widget);
        }
        content.clear();
        contentOffsets.clear();
        rowCosmetics.clear();
        contentHeight = 0;
        scroll = 0;
        buildWardrobe();
        layoutContent();
    }

    private void switchView(View next) {
        if (view != next) {
            boolean searchChanges = view.searchable() != next.searchable();
            view = next;
            lastView = next;
            scroll = 0;
            if (searchChanges) {
                rebuildWidgets(); // the search field comes or goes
            } else {
                refreshList();
            }
        }
    }

    /** The wardrobe's categories down the left, each with how many cosmetics (or outfits) it holds. */
    private void buildSidebar() {
        List<String> wearing = CosmeticsConfig.get().localEquipped();
        int y = listY;
        for (View each : View.values()) {
            int count;
            if (each == View.OUTFITS) {
                count = CosmeticsConfig.get().outfits.size();
            } else if (each == View.EMOTES) {
                count = Emotes.all().size();
            } else {
                count = (int) wardrobe().stream().filter(c -> each.includes(c, wearing)).count();
            }
            String label = t("view." + each.name().toLowerCase(Locale.ROOT));
            View target = each;
            if (each == View.EMOTES) {
                y += 6;
            }
            addChrome(new Rows.SideTab(sidebarX, y, SIDEBAR_WIDTH, 18, label,
                    each == View.WEARING ? () -> CosmeticsConfig.get().localEquipped().size() : () -> count, () -> view == target,
                    () -> switchView(target)));
            y += 20;
        }
    }

    /**
     * For the in-game test: rebuilds the widgets as switching tabs or resizing the window does, and says
     * whether the list still starts at the top.
     */
    public boolean rebuildKeepsListAtTop() {
        rebuildWidgets();
        return contentOffsets.isEmpty() || contentOffsets.get(0) == 0;
    }

    private void openDetail(String cosmeticId) {
        detailId = cosmeticId;
        scroll = 0;
        rebuildWidgets();
    }

    private void addChrome(FlatWidget widget) {
        chrome.add(widget);
        addWidget(widget);
    }

    private <W extends FlatWidget> W add(W widget) {
        contentOffsets.add(contentHeight);
        contentHeight += widget.getHeight() + Theme.ROW_GAP;
        content.add(widget);
        addWidget(widget);
        return widget;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight - listH);
    }

    private void layoutContent() {
        for (int i = 0; i < content.size(); i++) {
            FlatWidget widget = content.get(i);
            int y = listY + contentOffsets.get(i) - scroll;
            widget.setY(y);
            widget.setClip(listY, listY + listH);
            widget.visible = y + widget.getHeight() > listY && y < listY + listH;
        }
    }

    // ------------------------------------------------------------------ tabs

    private void section(String key) {
        if (contentHeight > 0) {
            contentHeight += 4;
        }
        add(new Rows.Section(listX, 0, listW, t(key)));
    }

    private Rows.Toggle toggle(String key, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        return add(new Rows.Toggle(listX, 0, listW, t(key), null, getter, setter));
    }

    private void buildWardrobe() {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (view == View.OUTFITS) {
            buildOutfits();
            return;
        }
        if (view == View.EMOTES) {
            buildEmotes();
            return;
        }
        List<BakedCosmetic> all = new ArrayList<>(wardrobe());
        if (all.isEmpty()) {
            add(new Rows.Info(listX, 0, listW, t("wardrobe.empty"), () -> "", Theme.TEXT_DIM));
            return;
        }
        // What you wear first, then by name; a search matches names and ids.
        List<String> wearing = config.localEquipped();
        all.sort(Comparator.comparing((BakedCosmetic c) -> !wearing.contains(c.id()))
                .thenComparing(BakedCosmetic::name, String.CASE_INSENSITIVE_ORDER));
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<BakedCosmetic> shown = all.stream().filter(c -> view.includes(c, wearing) && matches(c, needle)).toList();
        if (shown.isEmpty()) {
            add(new Rows.Info(listX, 0, listW, needle.isEmpty() ? t("wardrobe.empty_view") : t("wardrobe.no_match", query.trim()),
                    () -> "", Theme.TEXT_DIM));
        } else if (view == View.ALL || view == View.WEARING) {
            // Everything: grouped by slot.
            int limit = ROWS_PER_VIEW;
            for (CosmeticSlot slot : CosmeticSlot.values()) {
                List<BakedCosmetic> inSlot = shown.stream().filter(c -> c.slot() == slot).toList();
                if (inSlot.isEmpty()) {
                    continue;
                }
                section("slot." + slot.id());
                addCosmeticRows(inSlot, limit);
            }
        } else {
            addCosmeticRows(shown, ROWS_PER_VIEW);
        }
        if (!config.showOwnCosmetics || !config.enabled) {
            section("wardrobe.note");
            add(new Rows.Info(listX, 0, listW, t("wardrobe.hidden"), () -> "", Theme.WARN));
        }
    }

    /** One row per cosmetic (the switch wears it, the arrow opens its page), then "N more" past the limit. */
    private void addCosmeticRows(List<BakedCosmetic> cosmetics, int limit) {
        CosmeticsConfig config = CosmeticsConfig.get();
        for (BakedCosmetic cosmetic : cosmetics.subList(0, Math.min(limit, cosmetics.size()))) {
            String id = cosmetic.id();
            String source = id.startsWith("cosmeticsmod:") ? null : id.substring(0, id.indexOf(':'));
            Rows.Toggle row = add(new Rows.Toggle(listX, 0, listW, cosmetic.name(), source,
                    () -> config.localEquipped().contains(id), on -> setEquipped(id, on))
                    .onEdit(() -> openDetail(id)));
            rowCosmetics.put(row, id);
        }
        if (cosmetics.size() > limit) {
            add(new Rows.Info(listX, 0, listW, t("wardrobe.more", cosmetics.size() - limit), () -> "", Theme.TEXT_DIM));
        }
    }

    /** Every emote: play it (it shows in the preview) and put it on the emote wheel or take it off. */
    private void buildEmotes() {
        add(new Rows.Info(listX, 0, listW, t("emotes.hint"), () -> "", Theme.TEXT_DIM));
        if (Emotes.all().isEmpty()) {
            add(new Rows.Info(listX, 0, listW, t("emotes.empty"), () -> "", Theme.TEXT_DIM));
            return;
        }
        if (!CosmeticsConfig.get().emotes) {
            add(new Rows.Info(listX, 0, listW, t("emotes.off"), () -> "", Theme.WARN));
        }
        contentHeight += 4;
        for (Emote emote : Emotes.all()) {
            String id = emote.id();
            boolean onWheel = Emotes.onWheel(id, false);
            boolean onElytraWheel = Emotes.onWheel(id, true);
            add(new Rows.Actions(listX, 0, listW, Emotes.name(emote),
                    () -> EmotePlayer.playing() == emote ? t("emotes.playing") : wheels(onWheel, onElytraWheel),
                    List.of(t("emotes.play"), t(onWheel ? "emotes.wheel_remove" : "emotes.wheel_add"),
                            t(onElytraWheel ? "emotes.elytra_remove" : "emotes.elytra_add")),
                    List.of(() -> EmotePlayer.play(emote), () -> {
                        Emotes.setOnWheel(id, false, !onWheel);
                        rebuildWidgets();
                    }, () -> {
                        Emotes.setOnWheel(id, true, !onElytraWheel);
                        rebuildWidgets();
                    })));
        }
    }

    /** Which wheels an emote is on, for its row. */
    private static String wheels(boolean onWheel, boolean onElytraWheel) {
        return onWheel && onElytraWheel ? t("emotes.on_both") : onWheel ? t("emotes.on_wheel")
                : onElytraWheel ? t("emotes.on_elytra") : "";
    }

    /** Saved outfits: save what you wear, then wear, update or delete each one. */
    private void buildOutfits() {
        CosmeticsConfig config = CosmeticsConfig.get();
        add(new Rows.Button(listX, 0, listW, Theme.ROW_HEIGHT, t("outfits.save"), true, () -> {
            config.saveOutfit(t("outfits.name"));
            rebuildWidgets(); // the side bar's count changes too
        }));
        contentHeight += 4;
        if (config.outfits.isEmpty()) {
            add(new Rows.Info(listX, 0, listW, t("outfits.empty"), () -> "", Theme.TEXT_DIM));
            return;
        }
        for (Map.Entry<String, List<String>> outfit : config.outfits.entrySet()) {
            String name = outfit.getKey();
            List<String> ids = outfit.getValue();
            add(new Rows.Actions(listX, 0, listW, name,
                    () -> config.wearsOutfit(name) ? t("outfits.wearing", ids.size()) : t("outfits.count", ids.size()),
                    List.of(t("outfits.wear"), t("outfits.update"), t("outfits.delete")),
                    List.of(() -> {
                        config.wearOutfit(name);
                        EquippedCosmetics.invalidate();
                    }, () -> {
                        config.outfits.put(name, new ArrayList<>(config.localEquipped()));
                    }, () -> {
                        config.outfits.remove(name);
                        rebuildWidgets();
                    })));
        }
    }

    /** The cosmetics the Wardrobe lists: all but emote props, which only come with their emotes. */
    private static List<BakedCosmetic> wardrobe() {
        return CosmeticRegistry.all().stream().filter(c -> !c.definition().prop()).toList();
    }

    private static boolean matches(BakedCosmetic cosmetic, String needle) {
        return needle.isEmpty() || cosmetic.name().toLowerCase(Locale.ROOT).contains(needle)
                || cosmetic.id().contains(needle);
    }

    private static void setEquipped(String id, boolean on) {
        List<String> equipped = CosmeticsConfig.get().localEquipped();
        equipped.remove(id);
        if (on) {
            equipped.add(id);
        }
        EquippedCosmetics.invalidate();
    }

    /** Colours offered for tinting, as RGB. White keeps the texture's own colours. */
    private static final List<Integer> COLORS = List.of(
            0xFFFFFF, 0xFF5555, 0xFFAA33, 0xFFFF55, 0x88FF55, 0x33CC66, 0x55FFFF,
            0x5588FF, 0xAA66FF, 0xFF77CC, 0xAAAAAA, 0x444444);
    private static final List<String> COLOR_NAMES = List.of(
            "white", "red", "orange", "yellow", "lime", "green", "cyan", "blue", "purple", "pink", "gray", "black");

    private static String colorName(int rgb) {
        int index = COLORS.indexOf(rgb);
        return index >= 0 ? t("color." + COLOR_NAMES.get(index)) : String.format("#%06X", rgb);
    }

    /** One cosmetic's page: wear it, then whichever of colour, size and position, glow and animation it allows. */
    private void buildDetail(BakedCosmetic cosmetic) {
        CosmeticsConfig config = CosmeticsConfig.get();
        String id = cosmetic.id();
        add(new Rows.Button(listX, 0, listW, Theme.ROW_HEIGHT, t("detail.back"), false, () -> openDetail(null)));
        contentHeight += 4;
        add(new Rows.Section(listX, 0, listW, cosmetic.name()));
        add(new Rows.Toggle(listX, 0, listW, t("detail.wear"), null,
                () -> config.localEquipped().contains(id), on -> setEquipped(id, on)));

        // Only the options this cosmetic allows; the others keep the creator's setting and aren't shown.
        String[] styles = cosmetic.styles();
        if (styles.length > 1 && cosmetic.allows(CosmeticOption.STYLE)) {
            String defaultStyle = styles[cosmetic.defaultStyle()];
            section("section.style");
            add(new Rows.Cycle<>(listX, 0, listW, t("style"), List.of(styles),
                    () -> styles[cosmetic.styleIndex(config.options(id).style)],
                    v -> config.editOptions(id).style = v.equals(defaultStyle) ? null : v,
                    CosmeticsScreen::styleName));
        }
        boolean tint = cosmetic.allows(CosmeticOption.TINT);
        boolean rainbow = cosmetic.allows(CosmeticOption.RAINBOW);
        boolean saturation = cosmetic.allows(CosmeticOption.SATURATION);
        if (tint || rainbow || saturation) {
            section("section.color");
        }
        if (tint) {
            int defaultColor = cosmetic.defaultColor();
            List<Integer> colors = new ArrayList<>(COLORS);
            if (!colors.contains(defaultColor)) {
                colors.add(0, defaultColor);
            }
            add(new Rows.Cycle<>(listX, 0, listW, t("color"), colors,
                    () -> {
                        Integer color = config.options(id).color;
                        return color != null ? color : defaultColor;
                    },
                    // Picking the cosmetic's own colour goes back to following it.
                    v -> config.editOptions(id).color = v == defaultColor ? null : v,
                    v -> v == defaultColor && v != CosmeticDefinition.WHITE ? t("color.default") : colorName(v))
                    .withSwatch(rgb -> 0xFF000000 | rgb));
        }
        if (rainbow) {
            add(new Rows.Toggle(listX, 0, listW, t("rainbow"), null,
                    () -> config.options(id).rainbow, v -> config.editOptions(id).rainbow = v));
            add(new Rows.Slider(listX, 0, listW, t("rainbow_speed"), 0.1, 5, 0.1,
                    () -> config.options(id).rainbowSpeed, v -> config.editOptions(id).rainbowSpeed = (float) v,
                    CosmeticsScreen::times));
        }
        if (saturation) {
            add(new Rows.Slider(listX, 0, listW, t("saturation"), 0, 1, 0.05,
                    () -> config.options(id).saturation, v -> config.editOptions(id).saturation = (float) v,
                    v -> Math.round(v * 100) + "%"));
        }

        boolean size = cosmetic.allows(CosmeticOption.SIZE);
        boolean position = cosmetic.allows(CosmeticOption.POSITION);
        if (size || position) {
            section("section.shape");
        }
        if (size) {
            add(new Rows.Slider(listX, 0, listW, t("size"), 0.5, 1.5, 0.05,
                    () -> config.options(id).scale, v -> config.editOptions(id).scale = (float) v,
                    v -> Math.round(v * 100) + "%"));
        }
        if (position) {
            add(new Rows.Slider(listX, 0, listW, t("offset_x"), -8, 8, 0.5,
                    () -> config.options(id).offsetX, v -> config.editOptions(id).offsetX = (float) v, CosmeticsScreen::pixels));
            add(new Rows.Slider(listX, 0, listW, t("offset_y"), -8, 8, 0.5,
                    () -> -config.options(id).offsetY, v -> config.editOptions(id).offsetY = (float) -v, CosmeticsScreen::pixels));
            add(new Rows.Slider(listX, 0, listW, t("offset_z"), -8, 8, 0.5,
                    () -> -config.options(id).offsetZ, v -> config.editOptions(id).offsetZ = (float) -v, CosmeticsScreen::pixels));
        }

        boolean glow = cosmetic.hasEmissive() && cosmetic.allows(CosmeticOption.GLOW);
        boolean animation = cosmetic.hasAnimation() && cosmetic.allows(CosmeticOption.ANIMATION);
        // Cloth needs a flat cape: one with a layer that isn't skeletal (those wave by themselves).
        boolean cloth = cosmetic.slot() == CosmeticSlot.CAPE
                && java.util.Arrays.stream(cosmetic.layers()).anyMatch(l -> l.rig() == null);
        if (glow || animation || cloth) {
            section("section.effects");
        }
        if (cloth) {
            add(new Rows.Toggle(listX, 0, listW, t("cloth"), config.capePhysics ? null : t("cloth.physics_off"),
                    () -> config.options(id).cloth, v -> config.editOptions(id).cloth = v));
        }
        if (glow) {
            add(new Rows.Toggle(listX, 0, listW, t("glow"), config.emissiveParts ? null : t("glow.off_globally"),
                    () -> config.options(id).glow, v -> config.editOptions(id).glow = v));
        }
        if (animation) {
            add(new Rows.Toggle(listX, 0, listW, t("animation"), null,
                    () -> config.options(id).animate, v -> config.editOptions(id).animate = v));
            add(new Rows.Slider(listX, 0, listW, t("animation_speed"), 0.1, 3, 0.1,
                    () -> config.options(id).animationSpeed, v -> config.editOptions(id).animationSpeed = (float) v,
                    CosmeticsScreen::times));
        }

        if (!cosmetic.definition().particles().isEmpty()) {
            section("section.cosmetic_particles");
            add(new Rows.Toggle(listX, 0, listW, t("cosmetic_particles"), config.particles ? null : t("particles.off_globally"),
                    () -> config.options(id).particles, v -> config.editOptions(id).particles = v));
            add(new Rows.Slider(listX, 0, listW, t("particle_amount"), 0, 3, 0.1,
                    () -> config.options(id).particleAmount, v -> config.editOptions(id).particleAmount = (float) v,
                    v -> Math.round(v * 100) + "%"));
            add(new Rows.Slider(listX, 0, listW, t("particle_size"), 0.25, 3, 0.05,
                    () -> config.options(id).particleSize, v -> config.editOptions(id).particleSize = (float) v,
                    v -> Math.round(v * 100) + "%"));
        }

        contentHeight += 4;
        add(new Rows.Button(listX, 0, listW, Theme.ROW_HEIGHT, t("detail.reset"), false, () -> {
            config.resetOptions(id);
            rebuildWidgets();
        }));
    }

    /** {@code curved} becomes {@code Curved}, {@code soft_wave} becomes {@code Soft wave}. */
    private static String styleName(String style) {
        String words = style.replace('_', ' ').replace('-', ' ');
        return words.isEmpty() ? style : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static String times(double value) {
        return String.format(java.util.Locale.ROOT, "%.1fx", value);
    }

    private static String pixels(double value) {
        return (value > 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.1f px", value);
    }

    private void buildDisplay() {
        CosmeticsConfig c = CosmeticsConfig.get();
        section("section.general");
        toggle("enabled", () -> c.enabled, v -> c.enabled = v);
        toggle("show_own", () -> c.showOwnCosmetics, v -> c.showOwnCosmetics = v);
        toggle("show_others", () -> c.showOtherPlayersCosmetics, v -> c.showOtherPlayersCosmetics = v);

        section("section.slots");
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            add(new Rows.Toggle(listX, 0, listW, t("slot." + slot.id()), null,
                    () -> c.slotVisible(slot), v -> c.slotVisible.put(slot, v)));
        }

        section("section.details");
        toggle("emissive", () -> c.emissiveParts, v -> c.emissiveParts = v);
        toggle("translucent", () -> c.translucentParts, v -> c.translucentParts = v);
        toggle("hide_helmet", () -> c.hideHeadwearWithHelmet, v -> c.hideHeadwearWithHelmet = v);
        toggle("hide_elytra", () -> c.hideCapeWithElytra, v -> c.hideCapeWithElytra = v);
        toggle("cape_physics", () -> c.capePhysics, v -> c.capePhysics = v);
        add(new Rows.Slider(listX, 0, listW, t("cape_swing"), 0, 2, 0.1,
                () -> c.capeSwing, v -> c.capeSwing = (float) v, v -> Math.round(v * 100) + "%"));

        section("section.particles");
        toggle("particles", () -> c.particles, v -> c.particles = v);
        add(new Rows.Slider(listX, 0, listW, t("particle_density"), 0, 2, 0.1,
                () -> c.particleDensity, v -> c.particleDensity = (float) v, v -> Math.round(v * 100) + "%"));
        toggle("own_particles_first_person", () -> c.ownParticlesInFirstPerson, v -> c.ownParticlesInFirstPerson = v);

        section("section.emotes");
        toggle("emotes_enabled", () -> c.emotes, v -> {
            c.emotes = v;
            if (!v) {
                EmotePlayer.stop();
            }
        });
        add(new Rows.Cycle<>(listX, 0, listW, t("emote_camera"), List.of(CosmeticsConfig.EmoteCamera.values()),
                () -> c.emoteCamera, v -> c.emoteCamera = v,
                v -> t("emote_camera." + v.name().toLowerCase(Locale.ROOT))));
        toggle("emote_stop_on_move", () -> c.emoteStopOnMove, v -> c.emoteStopOnMove = v);
    }

    private void buildPerformance() {
        CosmeticsConfig c = CosmeticsConfig.get();
        section("section.distance");
        add(new Rows.Slider(listX, 0, listW, t("render_distance"), 0, 256, 8,
                () -> c.maxRenderDistance, v -> c.maxRenderDistance = (int) v, CosmeticsScreen::blocks));

        section("section.shaders");
        add(new Rows.Info(listX, 0, listW, "Iris", () -> Compat.IRIS
                ? (Compat.isShaderPackActive() ? t("iris.active") : t("iris.installed"))
                : t("not_installed"), Compat.IRIS ? Theme.GOOD : Theme.TEXT_FAINT));
        toggle("shadows", () -> c.renderInShadowPass, v -> c.renderInShadowPass = v);
        add(new Rows.Slider(listX, 0, listW, t("shadow_distance"), 0, 256, 8,
                () -> c.shadowDistance, v -> c.shadowDistance = (int) v, CosmeticsScreen::blocks));
    }

    private void buildCompatibility() {
        CosmeticsConfig c = CosmeticsConfig.get();
        section("section.priority");
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (slot.anchor() == CosmeticSlot.Anchor.HELD) {
                continue; // a held cosmetic replaces the item; no other mod's cosmetic competes for it
            }
            add(new Rows.Cycle<>(listX, 0, listW, t("slot." + slot.id()), List.of(SlotPriority.values()),
                    () -> c.priority(slot), v -> c.slotPriority.put(slot, v),
                    v -> t("priority." + v.name().toLowerCase(java.util.Locale.ROOT))));
        }
        add(new Rows.Toggle(listX, 0, listW, t("defer_figura"), Compat.FIGURA ? null : t("not_installed"),
                () -> c.deferToFigura, v -> c.deferToFigura = v));

        section("section.detected");
        Map<String, String> mods = Compat.detectedMods();
        if (mods.isEmpty()) {
            add(new Rows.Info(listX, 0, listW, t("detected.none"), () -> "", Theme.TEXT_DIM));
        }
        mods.forEach((name, version) -> add(new Rows.Info(listX, 0, listW, name, () -> version, Theme.GOOD)));

        section("section.maintenance");
        add(new Rows.Info(listX, 0, listW, t("loaded"), () -> String.valueOf(CosmeticRegistry.all().size()), Theme.TEXT_DIM));
        add(new Rows.Info(listX, 0, listW, t("errors"), () -> RenderGuard.failureCount() == 0
                ? t("errors.none") : String.valueOf(RenderGuard.failureCount()),
                RenderGuard.failureCount() == 0 ? Theme.GOOD : Theme.WARN));
        add(new Rows.Button(listX, 0, listW, Theme.ROW_HEIGHT, t("reload"), false, () -> {
            CosmeticLoader.reload(minecraft.getResourceManager());
            rebuildWidgets();
        }));
    }

    private static String blocks(double value) {
        int v = (int) value;
        return v <= 0 ? Component.translatable("cosmeticsmod.screen.unlimited").getString()
                : Component.translatable("cosmeticsmod.screen.blocks", v).getString();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listX && mouseX < listX + listW + 6 && mouseY >= listY && mouseY < listY + listH) {
            scroll = Math.max(0, Math.min(maxScroll(), scroll - (int) Math.round(scrollY * Theme.ROW_HEIGHT)));
            layoutContent();
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        EquippedCosmetics.preview(null);
        CosmeticsConfig.save();
        EquippedCosmetics.invalidate();
        ScreenOpener.open(parent);
    }

    // ------------------------------------------------------------------ drawing

    // Minecraft's draw entry points; 26.1 renamed them (render -> extractRenderState, and so on).
    //? if >=26.1 {
    /*@Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(g), mouseX, mouseY, partialTick);
    }

    private static void drawWidget(AbstractWidget widget, Canvas g, int mouseX, int mouseY, float partialTick) {
        widget.extractRenderState(g.raw, mouseX, mouseY, partialTick);
    }
    *///?} else {
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(g), mouseX, mouseY, partialTick);
    }

    private static void drawWidget(AbstractWidget widget, Canvas g, int mouseX, int mouseY, float partialTick) {
        widget.render(g.raw, mouseX, mouseY, partialTick);
    }
    //?}

    /** Paints the whole screen; the backdrop is drawn here so every version draws it exactly once, without blur. */
    private void draw(Canvas g, int mouseX, int mouseY, float partialTick) {
        framesRendered++;
        g.fill(0, 0, width, height, Theme.BACKDROP);
        g.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, Theme.PANEL_BORDER);
        g.fill(px, py, px + pw, py + ph, Theme.PANEL);

        // Header and footer.
        g.text(font, title.getString(), px + Theme.PAD + 22, py + (Theme.HEADER_HEIGHT - font.lineHeight) / 2 + 1, Theme.TEXT);
        g.fill(px, py + Theme.HEADER_HEIGHT, px + pw, py + Theme.HEADER_HEIGHT + 1, Theme.DIVIDER);
        g.fill(px, py + ph - Theme.FOOTER_HEIGHT, px + pw, py + ph - Theme.FOOTER_HEIGHT + 1, Theme.DIVIDER);
        g.text(font, t("hint"), px + Theme.PAD, py + ph - Theme.FOOTER_HEIGHT + (Theme.FOOTER_HEIGHT - font.lineHeight) / 2 + 1,
                Theme.TEXT_FAINT);

        updateTryOn(mouseX, mouseY);
        if (showPreview) {
            drawPreview(g, mouseX, mouseY);
        }

        g.clip(listX, listY, listX + listW, listY + listH);
        for (FlatWidget widget : content) {
            drawWidget(widget, g, mouseX, mouseY, partialTick);
        }
        g.endClip();

        if (maxScroll() > 0) {
            int trackX = listX + listW + 2;
            int barH = Math.max(16, listH * listH / contentHeight);
            int barY = listY + (listH - barH) * scroll / maxScroll();
            g.fill(trackX, listY, trackX + 2, listY + listH, Theme.TRACK);
            g.fill(trackX, barY, trackX + 2, barY + barH, Theme.TEXT_FAINT);
        }

        for (FlatWidget widget : chrome) {
            drawWidget(widget, g, mouseX, mouseY, partialTick);
        }
        if (searchBox != null) {
            drawWidget(searchBox, g, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Shows what the mouse points at in the wardrobe (or the cosmetic whose page is open) on you in the
     * preview, without wearing it. Called every frame; the preview lapses on its own once the screen is gone.
     */
    private void updateTryOn(int mouseX, int mouseY) {
        String pointed = null;
        if (tab == Tab.WARDROBE && detailId != null) {
            pointed = detailId;
        } else if (tab == Tab.WARDROBE && mouseY >= listY && mouseY < listY + listH) {
            for (Map.Entry<FlatWidget, String> row : rowCosmetics.entrySet()) {
                if (row.getKey().visible && row.getKey().isMouseOver(mouseX, mouseY)) {
                    pointed = row.getValue();
                    break;
                }
            }
        }
        tryingOn = pointed != null && !CosmeticsConfig.get().localEquipped().contains(pointed) ? pointed : null;
        EquippedCosmetics.preview(tryingOn);
    }

    private void drawPreview(Canvas g, int mouseX, int mouseY) {
        int x1 = previewX;
        int y1 = py + Theme.HEADER_HEIGHT + 6;
        int x2 = x1 + previewW;
        int y2 = py + ph - Theme.FOOTER_HEIGHT - 6;
        g.fill(x1, y1, x2, y2, Theme.PREVIEW_BG);
        int scale = Math.max(20, (int) ((y2 - y1) / 2.9f));
        BakedCosmetic shown = tryingOn != null ? CosmeticRegistry.get(tryingOn) : null;
        // Capes, wings and backpacks are worn on the back: turn round to show them.
        boolean back = shown != null && (shown.slot() == CosmeticSlot.CAPE || shown.slot() == CosmeticSlot.BACK
                || shown.wings() != null);
        float target = previewSpin + (back ? 180f : 0f);
        shownSpin += (target - shownSpin) * 0.2f;
        if (Math.abs(target - shownSpin) < 0.5f) {
            shownSpin = target;
        }
        shownPitch += (previewPitch - shownPitch) * 0.3f;
        g.entityTurned(x1, y1 + 6, x2, y2 - 18, scale, 0.0625f, mouseX, mouseY, minecraft.player, shownSpin, shownPitch);
        // The particles of auras, drawn the way the preview turns the player.
        float bodyYaw = 180f + shownSpin;
        previewParticles.update(EquippedCosmetics.forPlayer(minecraft.player.getUUID(), true), bodyYaw);
        float entityScale = minecraft.player.getScale();
        float feetY = minecraft.player.getBbHeight() / entityScale / 2f + 0.0625f;
        org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(shownPitch));
        g.clip(x1, y1, x2, y2);
        previewParticles.draw(g, (x1 + x2) / 2f, (y1 + 6 + y2 - 18) / 2f, scale, feetY, pose);
        g.endClip();
        String caption = shown != null ? t("preview.trying", shown.name()) : t("preview.drag");
        g.centeredText(font, fitText(caption, previewW - 8), (x1 + x2) / 2, y2 - font.lineHeight - 4,
                shown != null ? Theme.ACCENT : Theme.TEXT_FAINT);
    }

    private String fitText(String text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end)) + font.width(ellipsis) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + ellipsis;
    }
}
