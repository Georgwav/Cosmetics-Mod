package io.github.georgwav.cosmetics.ui;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
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

    private static Tab lastTab = Tab.WARDROBE;
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

    /** Pages the in-game test opens: every tab, then a cosmetic's own page. */
    public static final int TEST_PAGE_COUNT = TAB_COUNT + 1;

    public static String testPageName(int page) {
        return page < TAB_COUNT ? tabName(page) : "cosmetic";
    }

    public static CosmeticsScreen testPage(int page) {
        if (page < TAB_COUNT) {
            return new CosmeticsScreen(null, page);
        }
        // Prefer a cosmetic with every option (glow and animation) so the whole page gets drawn.
        BakedCosmetic pick = null;
        for (BakedCosmetic cosmetic : CosmeticRegistry.all()) {
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

        pw = Math.min(width - 16, 500);
        ph = Math.min(height - 16, 340);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        showPreview = minecraft != null && minecraft.player != null && pw >= 380;

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

        listX = px + Theme.PAD + (showPreview ? Theme.PREVIEW_WIDTH + Theme.PAD : 0);
        listY = py + Theme.HEADER_HEIGHT + 6;
        listW = px + pw - Theme.PAD - listX - 4; // room for the scrollbar
        listH = py + ph - Theme.FOOTER_HEIGHT - 6 - listY;

        BakedCosmetic detail = detailId != null && tab == Tab.WARDROBE ? CosmeticRegistry.get(detailId) : null;
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
        List<BakedCosmetic> all = new ArrayList<>(CosmeticRegistry.all());
        if (all.isEmpty()) {
            add(new Rows.Info(listX, 0, listW, t("wardrobe.empty"), () -> "", Theme.TEXT_DIM));
            return;
        }
        all.sort(Comparator.comparing(BakedCosmetic::name, String.CASE_INSENSITIVE_ORDER));
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            List<BakedCosmetic> inSlot = all.stream().filter(c -> c.slot() == slot).toList();
            if (inSlot.isEmpty()) {
                continue;
            }
            section("slot." + slot.id());
            for (BakedCosmetic cosmetic : inSlot) {
                String id = cosmetic.id();
                String source = id.startsWith("cosmeticsmod:") ? null : id.substring(0, id.indexOf(':'));
                add(new Rows.Toggle(listX, 0, listW, cosmetic.name(), source,
                        () -> config.localEquipped().contains(id), on -> setEquipped(id, on))
                        .onEdit(() -> openDetail(id)));
            }
        }
        if (!config.showOwnCosmetics || !config.enabled) {
            section("wardrobe.note");
            add(new Rows.Info(listX, 0, listW, t("wardrobe.hidden"), () -> "", Theme.WARN));
        }
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
        if (glow || animation) {
            section("section.effects");
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

    private static void drawWidget(FlatWidget widget, Canvas g, int mouseX, int mouseY, float partialTick) {
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

    private static void drawWidget(FlatWidget widget, Canvas g, int mouseX, int mouseY, float partialTick) {
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
        g.text(font, title.getString(), px + Theme.PAD, py + (Theme.HEADER_HEIGHT - font.lineHeight) / 2 + 1, Theme.TEXT);
        g.fill(px, py + Theme.HEADER_HEIGHT, px + pw, py + Theme.HEADER_HEIGHT + 1, Theme.DIVIDER);
        g.fill(px, py + ph - Theme.FOOTER_HEIGHT, px + pw, py + ph - Theme.FOOTER_HEIGHT + 1, Theme.DIVIDER);
        g.text(font, t("hint"), px + Theme.PAD, py + ph - Theme.FOOTER_HEIGHT + (Theme.FOOTER_HEIGHT - font.lineHeight) / 2 + 1,
                Theme.TEXT_FAINT);

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
    }

    private void drawPreview(Canvas g, int mouseX, int mouseY) {
        int x1 = px + Theme.PAD;
        int y1 = listY;
        int x2 = x1 + Theme.PREVIEW_WIDTH;
        int y2 = listY + listH;
        g.fill(x1, y1, x2, y2, Theme.PREVIEW_BG);
        int scale = Math.max(20, (int) ((y2 - y1) / 2.9f));
        g.entityFollowingMouse(x1, y1 + 6, x2, y2 - 6, scale, 0.0625f, mouseX, mouseY, minecraft.player);
        g.centeredText(font, t("preview"), (x1 + x2) / 2, y2 - font.lineHeight - 4, Theme.TEXT_FAINT);
    }
}
