package io.github.georgwav.cosmetics.ui;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticLoader;
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
 * the right (Wardrobe, Display, Performance, Compatibility). Everything is drawn here with plain fills
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
            addChrome(new Rows.Tab(tabRight, py + 6, w, Theme.HEADER_HEIGHT - 8, label, () -> tab == each, () -> switchTab(each)));
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

        switch (tab) {
            case WARDROBE -> buildWardrobe();
            case DISPLAY -> buildDisplay();
            case PERFORMANCE -> buildPerformance();
            case COMPATIBILITY -> buildCompatibility();
        }
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
        layoutContent();
    }

    private void switchTab(Tab next) {
        if (tab != next) {
            tab = next;
            lastTab = next;
            scroll = 0;
            rebuildWidgets();
        }
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
                        () -> config.localEquipped().contains(id),
                        on -> {
                            List<String> equipped = config.localEquipped();
                            equipped.remove(id);
                            if (on) {
                                equipped.add(id);
                            }
                            EquippedCosmetics.invalidate();
                        }));
            }
        }
        if (!config.showOwnCosmetics || !config.enabled) {
            section("wardrobe.note");
            add(new Rows.Info(listX, 0, listW, t("wardrobe.hidden"), () -> "", Theme.WARN));
        }
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
