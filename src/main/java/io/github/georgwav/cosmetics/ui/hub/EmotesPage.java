package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.emote.Emote;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.emote.Emotes;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/**
 * The Emotes page, as the design has it: every emote as a row; Play shows it on you, and each can go on the emote
 * wheel and on the elytra wheel (the one that opens while gliding). Search narrows the list.
 */
final class EmotesPage implements HubPage {
    private static final float LEFT = 148f;
    private static final float RIGHT = 1292f;
    private static final float LIST_TOP = 204f;
    private static final float LIST_BOTTOM = 692f;
    private static final float ROW = 60f;

    private float scroll;
    private float contentHeight;

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.screen." + key, args).getString();
    }

    @Override
    public String title() {
        return t("view.emotes");
    }

    @Override
    public String line() {
        Emote playing = EmotePlayer.playing();
        return playing != null ? Emotes.name(playing) + " · " + t("emotes.playing")
                : t("emotes.hint", KeyBinds.name(KeyBinds.emotes));
    }

    @Override
    public String icon() {
        return "smile";
    }

    @Override
    public String searchHint() {
        return Component.translatable("cosmeticsmod.hub.emotes.search").getString();
    }

    @Override
    public boolean overList(float x, float y) {
        return x >= LEFT && x < RIGHT && y >= LIST_TOP && y < LIST_BOTTOM;
    }

    @Override
    public void scrollBy(double amount) {
        float max = Math.max(0f, contentHeight - (LIST_BOTTOM - LIST_TOP));
        scroll = Math.max(0f, Math.min(max, scroll - (float) amount * 48f));
    }

    private static String wheels(boolean onWheel, boolean onElytraWheel) {
        return onWheel && onElytraWheel ? t("emotes.on_both") : onWheel ? t("emotes.on_wheel")
                : onElytraWheel ? t("emotes.on_elytra") : "";
    }

    @Override
    public void draw(Ui ui, String query) {
        ui.text(ui.fit(title(), RIGHT - LEFT, Ui.TITLE, true), LEFT, 136f, Ui.TEXT, Ui.TITLE, true);
        String hint = !CosmeticsConfig.get().emotes ? t("emotes.off") : t("emotes.hint", KeyBinds.name(KeyBinds.emotes));
        ui.text(ui.fit(hint, RIGHT - LEFT, Ui.SMALL, false), LEFT, 172f, CosmeticsConfig.get().emotes ? Ui.TEXT_DIM : 0xFFF0C070,
                Ui.SMALL, false);

        ui.clip(LEFT, LIST_TOP, RIGHT, LIST_BOTTOM);
        float y = LIST_TOP - scroll;
        float start = y;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        boolean any = false;
        for (Emote emote : Emotes.all()) {
            String name = Emotes.name(emote);
            if (!needle.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(needle)) {
                continue;
            }
            any = true;
            if (y + ROW >= LIST_TOP && y <= LIST_BOTTOM) {
                row(ui, emote, name, y);
            }
            y += ROW + 6f;
        }
        if (!any) {
            String empty = Emotes.all().isEmpty() ? t("emotes.empty")
                    : Component.translatable("cosmeticsmod.hub.mods.no_match", query.trim()).getString();
            ui.text(ui.fit(empty, RIGHT - LEFT, Ui.SMALL, false), LEFT + 6f, y + 10f, Ui.TEXT_DIM, Ui.SMALL, false);
        }
        contentHeight = y - start;
        ui.endClip();
        scrollBy(0);
    }

    private void row(Ui ui, Emote emote, String name, float y) {
        String id = emote.id();
        boolean onWheel = Emotes.onWheel(id, false);
        boolean onElytra = Emotes.onWheel(id, true);
        boolean playing = EmotePlayer.playing() == emote;
        ui.roundRect(LEFT, y, RIGHT - LEFT, ROW, 18f, Ui.CARD);
        float right = RIGHT - 12f;
        float bt = y + (ROW - 36f) / 2f;
        right = ui.buttonRight(t(onElytra ? "emotes.elytra_remove" : "emotes.elytra_add"), right, bt, 36f, false,
                () -> Emotes.setOnWheel(id, true, !onElytra)) - 8f;
        right = ui.buttonRight(t(onWheel ? "emotes.wheel_remove" : "emotes.wheel_add"), right, bt, 36f, false,
                () -> Emotes.setOnWheel(id, false, !onWheel)) - 8f;
        right = ui.buttonRight(t("emotes.play"), right, bt, 36f, !playing, () -> EmotePlayer.play(emote)) - 16f;
        float textX = LEFT + 20f;
        String status = playing ? t("emotes.playing") : wheels(onWheel, onElytra);
        if (status.isEmpty()) {
            ui.textMid(ui.fit(name, right - textX, Ui.NAME, true), textX, y + ROW / 2f, Ui.TEXT, Ui.NAME, true);
        } else {
            ui.text(ui.fit(name, right - textX, Ui.NAME, true), textX, y + 16f, Ui.TEXT, Ui.NAME, true);
            ui.text(ui.fit(status, right - textX, Ui.SMALL, false), textX, y + 35f, Ui.TEXT_DIM, Ui.SMALL, false);
        }
    }
}
