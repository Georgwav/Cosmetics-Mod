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
 * The emote wheel (B by default): up to eight emotes in a ring around the middle of the screen. Like Lunar's,
 * hold the key, point at an emote and let go to play it; or tap the key and click one. The scroll wheel turns
 * the page when there are more. Which emotes are on it is picked in the
 * Wardrobe's Emotes list. Drawn with plain fills and text like the settings screen.
 */
public final class EmoteWheelScreen extends Screen {
    private static final int PER_PAGE = 8;
    private static final int SLOT_WIDTH = 100;
    private static final int SLOT_HEIGHT = 26;
    /** Remembered between openings. */
    private static int lastPage;
    /** Frames drawn since the game started (read by the in-game test). */
    public static int framesRendered;
    /** The key that opens the wheel (set when it is registered). */
    public static KeyMapping key;
    /** Letting go of the key this soon after it opened the wheel was a tap: the wheel stays open for clicking. */
    private static final long TAP_NANOS = 300_000_000L;

    private final long openedNanos = System.nanoTime();
    private Slot pointed;

    private final List<FlatWidget> widgets = new ArrayList<>();
    private List<Emote> emotes = List.of();
    private int page;
    private String hovered;

    public EmoteWheelScreen() {
        super(Component.translatable("cosmeticsmod.emotes.title"));
    }

    /** Opens the wheel, unless another screen is open or emotes are off. */
    public static void openIfAllowed(Minecraft client) {
        if (ScreenOpener.current() == null && client.player != null && CosmeticsConfig.get().emotes) {
            ScreenOpener.open(new EmoteWheelScreen());
        }
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.emotes." + key, args).getString();
    }

    private int pages() {
        return Math.max(1, (emotes.size() + PER_PAGE - 1) / PER_PAGE);
    }

    @Override
    protected void init() {
        widgets.clear();
        emotes = Emotes.wheel();
        page = Math.max(0, Math.min(lastPage, pages() - 1));
        int cx = width / 2;
        int cy = height / 2;
        int radius = Math.max(60, Math.min(120, Math.min(width / 2 - SLOT_WIDTH / 2 - 8, height / 2 - SLOT_HEIGHT - 8)));
        int first = page * PER_PAGE;
        int count = Math.min(PER_PAGE, emotes.size() - first);
        for (int i = 0; i < count; i++) {
            // Clockwise from the top.
            double angle = -Math.PI / 2 + i * 2 * Math.PI / PER_PAGE;
            int x = cx + (int) Math.round(Math.cos(angle) * radius * 1.35) - SLOT_WIDTH / 2;
            int y = cy + (int) Math.round(Math.sin(angle) * radius) - SLOT_HEIGHT / 2;
            add(new Slot(x, y, emotes.get(first + i), i + 1));
        }
        if (EmotePlayer.playing() != null) {
            add(new Rows.Button(cx - 30, cy - 10, 60, 20, t("stop"), true, () -> {
                EmotePlayer.stop();
                onClose();
            }));
        }
        if (pages() > 1) {
            add(new Rows.Button(cx - 70, cy + 16, 20, 16, "<", false, () -> turn(-1)));
            add(new Rows.Button(cx + 50, cy + 16, 20, 16, ">", false, () -> turn(1)));
        }
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
        if (pages() > 1 && scrollY != 0) {
            turn(scrollY > 0 ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void play(Emote emote) {
        EmotePlayer.play(emote);
        onClose();
    }

    // Letting go of the wheel key: plays the emote pointed at; held a while and pointing at nothing closes the wheel.
    private boolean wheelKeyReleased() {
        if (pointed != null) {
            play(pointed.emote);
            return true;
        }
        if (System.nanoTime() - openedNanos > TAP_NANOS) {
            onClose();
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
        hovered = null;
        pointed = null;
        for (FlatWidget widget : widgets) {
            drawWidget(widget, g, mouseX, mouseY, partialTick);
            if (widget instanceof Slot slot && slot.isMouseOver(mouseX, mouseY)) {
                hovered = Emotes.name(slot.emote);
                pointed = slot;
            }
        }
        if (emotes.isEmpty()) {
            g.centeredText(font, t("none"), cx, cy - font.lineHeight / 2, Theme.TEXT_DIM);
        } else if (EmotePlayer.playing() == null) {
            String middle = hovered != null ? hovered : title.getString();
            g.centeredText(font, middle, cx, cy - font.lineHeight / 2, hovered != null ? Theme.TEXT : Theme.TEXT_DIM);
        }
        if (pages() > 1) {
            g.centeredText(font, (page + 1) + " / " + pages(), cx, cy + 20, Theme.TEXT_DIM);
        }
        g.centeredText(font, t("hint"), cx, height - font.lineHeight - 8, Theme.TEXT_FAINT);
    }

    /** One emote on the wheel. */
    private final class Slot extends FlatWidget {
        private final Emote emote;
        private final String number;

        Slot(int x, int y, Emote emote, int number) {
            super(x, y, SLOT_WIDTH, SLOT_HEIGHT, Component.literal(Emotes.name(emote)));
            this.emote = emote;
            this.number = Integer.toString(number);
        }

        @Override
        protected void draw(Canvas g, Font font, int mouseX, int mouseY, boolean hovered) {
            boolean playing = EmotePlayer.playing() == emote;
            int border = hovered ? Theme.ACCENT_HOVER : playing ? Theme.GOOD : Theme.PANEL_BORDER;
            rect(g, getX() - 1, getY() - 1, getWidth() + 2, getHeight() + 2, border);
            rect(g, getX(), getY(), getWidth(), getHeight(), hovered ? Theme.TRACK_HOVER : Theme.PANEL);
            int ty = getY() + (getHeight() - font.lineHeight) / 2 + 1;
            text(g, font, number, getX() + 5, ty, Theme.TEXT_FAINT);
            g.centeredText(font, fit(font, Emotes.name(emote), getWidth() - 24), getX() + getWidth() / 2 + 4, ty,
                    hovered ? Theme.TEXT : Theme.TEXT_DIM);
        }

        @Override
        protected void pressed(double mouseX, double mouseY) {
            play(emote);
        }
    }
}
