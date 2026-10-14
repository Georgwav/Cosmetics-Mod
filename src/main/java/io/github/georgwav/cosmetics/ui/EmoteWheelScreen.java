package io.github.georgwav.cosmetics.ui;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.ui.widget.FlatWidget;
import io.github.georgwav.cosmetics.ui.widget.Rows;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
//?}

import java.util.ArrayList;
import java.util.List;

/**
 * The emote wheel (B by default): eight square slots in a ring around the middle of the screen, each showing its
 * emote's most telling pose. Like Lunar's, hold the key, point at an emote and let go to play it; or tap the key
 * and click one. Slots start empty: click a "+" to pick an emote for it, the gear on a slot to change it, and the
 * cross under the gear to empty it. The scroll wheel turns the page, and pages can be added. Opened while gliding with an elytra it is the elytra wheel,
 * with its own slots, whose emotes play until you land. Drawn with plain fills and text like the settings screen.
 */
public final class EmoteWheelScreen extends Screen {
    private static final int PER_PAGE = CosmeticsConfig.WHEEL_SLOTS;
    /** The picker's tiles. */
    private static final int TILE = 58;
    private static final int TILE_GAP = 6;
    /** Remembered between openings. */
    private static int lastPage;
    /** Frames drawn since the game started (read by the in-game test). */
    public static int framesRendered;
    /** The key that opens the wheel (set when it is registered). */
    public static KeyMapping key;
    /** Letting go of the key this soon after it opened the wheel was a tap: the wheel stays open for clicking. */
    private static final long TAP_NANOS = 300_000_000L;

    private final long openedNanos = System.nanoTime();
    /** The elytra wheel (opened while gliding). */
    private final boolean elytra;
    private final EmotePictures pictures = new EmotePictures();
    private Slot pointed;

    private final List<FlatWidget> widgets = new ArrayList<>();
    private int page;
    private String hovered;
    /** The slot an emote is being picked for, or -1 while the wheel shows. */
    private int picking = -1;
    /** Rows of the picker scrolled past. */
    private int pickerScroll;
    private int pickerRows;
    private int pickerVisibleRows;
    private int panelX, panelY, panelW, panelH;

    public EmoteWheelScreen(boolean elytra) {
        super(Component.translatable(elytra ? "cosmeticsmod.emotes.elytra_title" : "cosmeticsmod.emotes.title"));
        this.elytra = elytra;
    }

    /** Emote pictures made since the game started (for the in-game test). */
    public static int picturesMade() {
        return EmotePictures.made;
    }

    /** Whether this is the elytra wheel (for the in-game test). */
    public boolean isElytraWheel() {
        return elytra;
    }

    /** Opens the wheel (the elytra wheel while gliding), unless another screen is open or emotes are off. */
    public static void openIfAllowed(Minecraft client) {
        if (ScreenOpener.current() == null && client.player != null && CosmeticsConfig.get().emotes) {
            ScreenOpener.open(new EmoteWheelScreen(client.player.isFallFlying()));
        }
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.emotes." + key, args).getString();
    }

    private int pages() {
        return Emotes.pages(elytra);
    }

    /** The slot squares' side, in GUI pixels. */
    private int slotSize() {
        return Math.max(44, Math.min(76, Math.min(width, height) / 6));
    }

    @Override
    protected void init() {
        widgets.clear();
        page = Math.max(0, Math.min(lastPage, pages() - 1));
        if (picking >= 0) {
            initPicker();
            return;
        }
        int cx = width / 2;
        int cy = height / 2;
        int size = slotSize();
        // Far enough out that neighbours (and their names) don't touch, close enough to stay on screen.
        int radius = Math.max((int) (size * 1.55f), Math.min(150, Math.min(width / 2 - size, height / 2 - size - 14)));
        for (int i = 0; i < PER_PAGE; i++) {
            // Clockwise from the top.
            double angle = -Math.PI / 2 + i * 2 * Math.PI / PER_PAGE;
            int x = cx + (int) Math.round(Math.cos(angle) * radius) - size / 2;
            int y = cy + (int) Math.round(Math.sin(angle) * radius) - size / 2;
            add(new Slot(x, y, size, page * PER_PAGE + i, i + 1));
        }
        if (EmotePlayer.playing() != null) {
            add(new Rows.Button(cx - 30, cy - 10, 60, 20, t("stop"), true, () -> {
                EmotePlayer.stop();
                close();
            }));
        }
        // Pages: turn, add one, remove an empty one.
        int by = Math.min(height - 40, cy + radius + size / 2 + 18);
        if (pages() > 1) {
            add(new Rows.Button(cx - 60, by, 20, 16, "<", false, () -> turn(-1)));
            add(new Rows.Button(cx + 40, by, 20, 16, ">", false, () -> turn(1)));
        }
        add(new Rows.Button(cx + 66, by, 64, 16, t("add_page"), false, () -> {
            if (Emotes.addPage(elytra)) {
                lastPage = pages() - 1;
                CosmeticsConfig.save();
                rebuildWidgets();
            }
        }));
        if (pages() > 1 && pageEmpty()) {
            add(new Rows.Button(cx - 130, by, 64, 16, t("remove_page"), false, () -> {
                Emotes.removePage(elytra, page);
                lastPage = Math.max(0, page - 1);
                CosmeticsConfig.save();
                rebuildWidgets();
            }));
        }
    }

    private boolean pageEmpty() {
        for (int i = 0; i < PER_PAGE; i++) {
            if (Emotes.at(elytra, page * PER_PAGE + i) != null) {
                return false;
            }
        }
        return true;
    }

    /** Every emote as a tile to pick from, made for this wheel first, scrolled with the mouse wheel. */
    private void initPicker() {
        panelW = Math.min(width - 32, 560);
        panelH = Math.min(height - 32, 360);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        List<Emote> all = new ArrayList<>(Emotes.all());
        all.sort(java.util.Comparator.comparing((Emote e) -> e.elytra() != elytra));
        int gridX = panelX + 10;
        int gridY = panelY + 28;
        int gridH = panelH - 28 - 34;
        int columns = Math.max(1, (panelW - 20 + TILE_GAP) / (TILE + TILE_GAP));
        int rowH = TILE + 12 + TILE_GAP;
        pickerRows = (all.size() + columns - 1) / columns;
        pickerVisibleRows = Math.max(1, gridH / rowH);
        pickerScroll = Math.max(0, Math.min(pickerScroll, pickerRows - pickerVisibleRows));
        int left = gridX + (panelW - 20 - (columns * (TILE + TILE_GAP) - TILE_GAP)) / 2;
        for (int i = 0; i < all.size(); i++) {
            int row = i / columns - pickerScroll;
            if (row < 0 || row >= pickerVisibleRows) {
                continue;
            }
            add(new Pick(left + (i % columns) * (TILE + TILE_GAP), gridY + row * rowH, all.get(i)));
        }
        int by = panelY + panelH - 26;
        add(new Rows.Button(panelX + panelW - 10 - 80, by, 80, 18, t("back"), false, this::endPicking));
        if (Emotes.at(elytra, picking) != null) {
            add(new Rows.Button(panelX + 10, by, 110, 18, t("empty_slot"), false, () -> {
                Emotes.assign(elytra, picking, "");
                CosmeticsConfig.save();
                endPicking();
            }));
        }
    }

    private void startPicking(int slot) {
        picking = slot;
        pickerScroll = 0;
        rebuildWidgets();
    }

    private void endPicking() {
        picking = -1;
        rebuildWidgets();
    }

    private void add(FlatWidget widget) {
        widgets.add(widget);
        addWidget(widget);
    }

    private void turn(int by) {
        lastPage = Math.floorMod(page + by, pages());
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        if (picking >= 0) {
            int next = Math.max(0, Math.min(pickerScroll + (scrollY > 0 ? -1 : 1), pickerRows - pickerVisibleRows));
            if (next != pickerScroll) {
                pickerScroll = next;
                rebuildWidgets();
            }
            return true;
        }
        if (pages() > 1) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Escape in the picker goes back to the wheel; otherwise it closes the wheel. */
    @Override
    public void onClose() {
        if (picking >= 0) {
            endPicking();
            return;
        }
        close();
    }

    private void close() {
        super.onClose();
    }

    @Override
    public void removed() {
        pictures.close();
        super.removed();
    }

    private void play(Emote emote) {
        EmotePlayer.play(emote, elytra);
        close();
    }

    // Letting go of the wheel key: plays the emote pointed at; held a while and pointing at nothing closes the wheel.
    // While picking, the key does nothing.
    private boolean wheelKeyReleased() {
        if (picking >= 0) {
            return true;
        }
        if (pointed != null && pointed.emote() != null) {
            play(pointed.emote());
            return true;
        }
        if (System.nanoTime() - openedNanos > TAP_NANOS) {
            close();
            return true;
        }
        return false;
    }

    //? if >=1.21.9 {
    @Override
    public boolean keyReleased(KeyEvent event) {
        if (key != null && key.matches(event) && wheelKeyReleased()) {
            return true;
        }
        return super.keyReleased(event);
    }
    //?} else {
    /*@Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (key != null && key.matches(keyCode, scanCode) && wheelKeyReleased()) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }
    *///?}

    // ------------------------------------------------------------------ drawing

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

    private void draw(Canvas g, int mouseX, int mouseY, float partialTick) {
        framesRendered++;
        // A light dim, so you still see yourself and the world.
        g.fill(0, 0, width, height, 0x700A0B0E);
        int cx = width / 2;
        int cy = height / 2;
        if (picking >= 0) {
            g.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, Theme.PANEL_BORDER);
            g.fill(panelX, panelY, panelX + panelW, panelY + panelH, Theme.PANEL);
            g.text(font, t("pick_title", picking % PER_PAGE + 1), panelX + 10, panelY + 10, Theme.TEXT);
            if (pickerRows > pickerVisibleRows) {
                String rows = (pickerScroll + 1) + "-" + Math.min(pickerRows, pickerScroll + pickerVisibleRows) + " / "
                        + pickerRows;
                g.text(font, rows, panelX + panelW - 10 - font.width(rows), panelY + 10, Theme.TEXT_FAINT);
            }
            for (FlatWidget widget : widgets) {
                drawWidget(widget, g, mouseX, mouseY, partialTick);
            }
            if (Emotes.all().isEmpty()) {
                g.centeredText(font, t("none"), cx, cy - font.lineHeight / 2, Theme.TEXT_DIM);
            }
            return;
        }
        hovered = null;
        pointed = null;
        for (FlatWidget widget : widgets) {
            drawWidget(widget, g, mouseX, mouseY, partialTick);
            if (widget instanceof Slot slot && slot.isMouseOver(mouseX, mouseY)) {
                pointed = slot;
                hovered = slot.emote() != null ? Emotes.name(slot.emote()) : t("add");
            }
        }
        if (EmotePlayer.playing() == null) {
            String middle = hovered != null ? hovered : title.getString();
            g.centeredText(font, middle, cx, cy - font.lineHeight / 2, hovered != null ? Theme.TEXT : Theme.TEXT_DIM);
        }
        if (pages() > 1) {
            int size = slotSize();
            int radius = Math.max((int) (size * 1.55f), Math.min(150, Math.min(width / 2 - size, height / 2 - size - 14)));
            int by = Math.min(height - 40, cy + radius + size / 2 + 18);
            g.centeredText(font, (page + 1) + " / " + pages(), cx, by + 4, Theme.TEXT_DIM);
        }
        g.centeredText(font, t("hint"), cx, height - font.lineHeight - 8, Theme.TEXT_FAINT);
    }

    /** A "+" made of two bars, for an empty slot. */
    private static void plus(Canvas g, int cx, int cy, int arm, int color) {
        g.fill(cx - arm, cy - 1, cx + arm + 1, cy + 2, color);
        g.fill(cx - 1, cy - arm, cx + 2, cy + arm + 1, color);
    }

    /** One slot of the wheel: an emote's picture and name, or a "+" to fill it. */
    private final class Slot extends FlatWidget {
        private static final int GEAR = 12;
        private final int index;
        private final String number;

        Slot(int x, int y, int size, int index, int number) {
            super(x, y, size, size, Component.literal(""));
            this.index = index;
            this.number = Integer.toString(number);
        }

        Emote emote() {
            return Emotes.at(elytra, index);
        }

        private boolean onGear(double mouseX, double mouseY) {
            return mouseX >= getX() + getWidth() - GEAR - 2 && mouseY < getY() + GEAR + 2;
        }

        /** The cross in the bottom right corner: empties the slot at once. */
        private boolean onRemove(double mouseX, double mouseY) {
            return mouseX >= getX() + getWidth() - GEAR - 2 && mouseY >= getY() + getHeight() - GEAR - 2;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            Emote emote = emote();
            boolean playing = emote != null && EmotePlayer.playing() == emote;
            int border = hovered ? Theme.ACCENT_HOVER : playing ? Theme.GOOD : Theme.PANEL_BORDER;
            rect(g, getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, border);
            rect(g, getX(), getY(), getWidth(), getHeight(), hovered ? Theme.TRACK_HOVER : Theme.PANEL);
            int cx = getX() + getWidth() / 2;
            if (emote == null) {
                int cy = getY() + getHeight() / 2 - 4;
                plus(g, cx, cy, Math.max(5, getWidth() / 7), hovered ? Theme.ACCENT_HOVER : Theme.TEXT_FAINT);
                g.centeredText(font, t("add"), cx, getY() + getHeight() - font.lineHeight - 3,
                        hovered ? Theme.TEXT : Theme.TEXT_FAINT);
                return;
            }
            pictures.draw(g, emote, getX() + 1, getY() + 1, getWidth() - 2);
            text(g, font, number, getX() + 3, getY() + 3, Theme.TEXT_FAINT);
            if (hovered) {
                text(g, font, "\u2699", getX() + getWidth() - GEAR, getY() + 2,
                        onGear(mouseX, mouseY) ? Theme.ACCENT_HOVER : Theme.TEXT_DIM);
                boolean overRemove = onRemove(mouseX, mouseY);
                int rx = getX() + getWidth() - GEAR - 1;
                int ry = getY() + getHeight() - GEAR - 1;
                rect(g, rx, ry, GEAR, GEAR, overRemove ? Theme.WARN : Theme.TRACK);
                g.centeredText(font, "\u00d7", rx + GEAR / 2 + 1, ry + 2, overRemove ? Theme.KNOB : Theme.TEXT_DIM);
            }
            // The name under the square.
            g.centeredText(font, fit(font, Emotes.name(emote), getWidth() + 24), cx, getY() + getHeight() + 3,
                    hovered ? Theme.TEXT : Theme.TEXT_DIM);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            Emote emote = emote();
            if (emote != null && onRemove(mouseX, mouseY)) {
                Emotes.assign(elytra, index, "");
                CosmeticsConfig.save();
            } else if (emote == null || onGear(mouseX, mouseY)) {
                startPicking(index);
            } else {
                play(emote);
            }
        }
    }

    /** One emote in the picker: clicking puts it in the slot being filled. */
    private final class Pick extends FlatWidget {
        private final Emote emote;

        Pick(int x, int y, Emote emote) {
            super(x, y, TILE, TILE, Component.literal(Emotes.name(emote)));
            this.emote = emote;
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            boolean current = Emotes.at(elytra, picking) == emote;
            int border = hovered ? Theme.ACCENT_HOVER : current ? Theme.ACCENT : Theme.PANEL_BORDER;
            rect(g, getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, border);
            rect(g, getX(), getY(), getWidth(), getHeight(), hovered ? Theme.TRACK_HOVER : Theme.TILE);
            pictures.draw(g, emote, getX() + 1, getY() + 1, getWidth() - 2);
            g.centeredText(font, fit(font, Emotes.name(emote), TILE + TILE_GAP - 2), getX() + getWidth() / 2,
                    getY() + getHeight() + 2, hovered ? Theme.TEXT : Theme.TEXT_DIM);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            // An emote is on a wheel once: from its old slot to this one.
            for (int i = 0; i < Emotes.slots(elytra).size(); i++) {
                if (i != picking && Emotes.slots(elytra).get(i).equals(emote.id())) {
                    Emotes.assign(elytra, i, "");
                }
            }
            Emotes.assign(elytra, picking, emote.id());
            CosmeticsConfig.save();
            endPicking();
        }
    }
}
