package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.model.CosmeticLoader;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * The client's settings in the hub's design: the cosmetics' display, keys, which slots show, details, particles,
 * emotes, performance, compatibility and maintenance, each a section down the left, its settings as rows on the
 * right. Changes apply at once and are saved when the hub closes.
 */
final class SettingsPage implements HubPage {
    private static final String[] SECTIONS = {"general", "keys", "slots", "details", "particles", "emotes", "performance",
            "compatibility", "maintenance"};
    private static final float LEFT = 148f;
    private static final float RIGHT = 1292f;
    private static final float NAV_W = 212f;
    private static final float LIST_X = 384f;
    private static final float LIST_W = RIGHT - LIST_X;
    private static final float LIST_TOP = 204f;
    private static final float LIST_BOTTOM = 692f;
    private static final float ROW = 56f;

    /** One setting's row: its name (and a hint) on the left, its control on the right. */
    private interface Row {
        void draw(Ui ui, float y, float textX, float right);
    }

    private final HubScreen hub;
    private String section = "general";
    private float scroll;
    private float contentHeight;

    SettingsPage(HubScreen hub) {
        this.hub = hub;
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.screen." + key, args).getString();
    }

    @Override
    public String title() {
        return Component.translatable("cosmeticsmod.hub.settings.title").getString();
    }

    @Override
    public String line() {
        return hub.listeningFor() != null ? Component.translatable("cosmeticsmod.hub.mods.press_key").getString()
                : Component.translatable("cosmeticsmod.hub.settings.line").getString();
    }

    @Override
    public String icon() {
        return "sliders";
    }

    @Override
    public String searchHint() {
        return Component.translatable("cosmeticsmod.hub.settings.search").getString();
    }

    @Override
    public boolean overList(float x, float y) {
        return x >= LIST_X && x < RIGHT && y >= LIST_TOP && y < LIST_BOTTOM;
    }

    @Override
    public void scrollBy(double amount) {
        float max = Math.max(0f, contentHeight - (LIST_BOTTOM - LIST_TOP));
        scroll = Math.max(0f, Math.min(max, scroll - (float) amount * 48f));
    }

    // ------------------------------------------------------------------ rows

    private static void changed() {
        EquippedCosmetics.invalidate();
    }

    private static Row toggle(String key, BooleanSupplier get, Consumer<Boolean> set) {
        return (ui, y, textX, right) -> {
            ui.toggle("settings." + key, right - 52f, y + ROW / 2f - 16f, get.getAsBoolean(), () -> {
                set.accept(!get.getAsBoolean());
                changed();
            });
            name(ui, t(key), y, textX, right - 52f - 16f);
        };
    }

    private static Row slider(String key, double min, double max, double step, DoubleSupplier get, DoubleConsumer set,
                              DoubleFunction<String> label) {
        return (ui, y, textX, right) -> {
            float w = 220f;
            ui.slider("settings." + key, right - w, y + ROW / 2f - 16f, w, min, max, get.getAsDouble(), v -> {
                set.accept(step > 0 ? Math.round(v / step) * step : v);
                changed();
            });
            String value = label.apply(get.getAsDouble());
            ui.textMidRight(value, right - w - 18f, y + ROW / 2f, 0xCCFFFFFF, Ui.SMALL, true);
            name(ui, t(key), y, textX, right - w - 18f - ui.textWidth(value, Ui.SMALL, true) - 16f);
        };
    }

    private static <T> Row choice(String name, List<T> options, Supplier<T> get, Consumer<T> set, java.util.function.Function<T, String> label) {
        return (ui, y, textX, right) -> {
            List<String> labels = options.stream().map(label).toList();
            float w = ui.choiceWidth(labels);
            ui.choice("settings." + name, right - w, y + ROW / 2f - 18f, labels, options.indexOf(get.get()), i -> {
                set.accept(options.get(i));
                changed();
            });
            name(ui, name, y, textX, right - w - 16f);
        };
    }

    private static Row info(String name, Supplier<String> value, int color) {
        return (ui, y, textX, right) -> {
            String v = value.get();
            ui.textMidRight(v, right, y + ROW / 2f, color, Ui.SMALL, true);
            name(ui, name, y, textX, right - ui.textWidth(v, Ui.SMALL, true) - 16f);
        };
    }

    private static Row button(String name, String label, Runnable action) {
        return (ui, y, textX, right) -> {
            float left = ui.buttonRight(label, right, y + ROW / 2f - 18f, 36f, false, action);
            name(ui, name, y, textX, left - 16f);
        };
    }

    private Row key(String name, KeyMapping mapping) {
        return (ui, y, textX, right) -> {
            if (mapping == null) {
                return;
            }
            boolean waiting = hub.listeningFor() == mapping;
            String label = waiting ? Component.translatable("cosmeticsmod.hub.mods.press_key").getString()
                    : mapping.isUnbound() ? Component.translatable("cosmeticsmod.hub.mods.no_key").getString() : KeyBinds.name(mapping);
            float w = ui.textWidth(label, Ui.SMALL, true) + 28f;
            boolean hot = ui.hover(right - w, y + 10f, w, 36f);
            ui.roundRect(right - w, y + 10f, w, 36f, 18f, waiting ? ui.accent : hot ? 0xFF26262A : Ui.WELL);
            ui.textMid(label, right - w + 14f, y + 28f, waiting ? ui.ink : Ui.TEXT, Ui.SMALL, true);
            ui.click(right - w, y + 10f, w, 36f, () -> hub.listenFor(waiting ? null : mapping));
            name(ui, name, y, textX, right - w - 16f);
        };
    }

    private static void name(Ui ui, String name, float y, float textX, float room) {
        ui.textMid(ui.fit(name, room - textX, Ui.NAME, false), textX, y + ROW / 2f, Ui.TEXT, Ui.NAME, false);
    }

    private static String percent(double v) {
        return Math.round(v * 100) + "%";
    }

    private static String blocks(double v) {
        int b = (int) v;
        return b <= 0 ? t("unlimited") : t("blocks", b);
    }

    /** A section's rows, as the old settings screen had them. */
    private List<Row> rows(String id) {
        CosmeticsConfig c = CosmeticsConfig.get();
        List<Row> rows = new ArrayList<>();
        switch (id) {
            case "general" -> {
                rows.add(toggle("enabled", () -> c.enabled, v -> c.enabled = v));
                rows.add(toggle("show_own", () -> c.showOwnCosmetics, v -> c.showOwnCosmetics = v));
                rows.add(toggle("show_others", () -> c.showOtherPlayersCosmetics, v -> c.showOtherPlayersCosmetics = v));
                rows.add(toggle("several_of_a_kind", () -> c.severalOfAKind, v -> c.severalOfAKind = v));
                rows.add(toggle("color_any_cosmetic", () -> c.colorAnyCosmetic, v -> c.colorAnyCosmetic = v));
                rows.add(toggle("shield_item_icons", () -> c.shieldItemIcons, v -> c.shieldItemIcons = v));
            }
            case "keys" -> {
                rows.add(key(t("key_open"), KeyBinds.open));
                rows.add(key(t("key_emotes"), KeyBinds.emotes));
            }
            case "slots" -> {
                for (CosmeticSlot slot : CosmeticSlot.values()) {
                    rows.add((ui, y, textX, right) -> {
                        ui.toggle("settings.slot." + slot.id(), right - 52f, y + ROW / 2f - 16f, c.slotVisible(slot), () -> {
                            c.slotVisible.put(slot, !c.slotVisible(slot));
                            changed();
                        });
                        name(ui, t("slot." + slot.id()), y, textX, right - 68f);
                    });
                }
            }
            case "details" -> {
                rows.add(toggle("emissive", () -> c.emissiveParts, v -> c.emissiveParts = v));
                rows.add(toggle("translucent", () -> c.translucentParts, v -> c.translucentParts = v));
                rows.add(toggle("hide_helmet", () -> c.hideHeadwearWithHelmet, v -> c.hideHeadwearWithHelmet = v));
                rows.add(toggle("hide_elytra", () -> c.hideCapeWithElytra, v -> c.hideCapeWithElytra = v));
                rows.add(toggle("elytra_close", () -> c.elytraCloseToBack, v -> c.elytraCloseToBack = v));
                rows.add(toggle("cape_physics", () -> c.capePhysics, v -> c.capePhysics = v));
                rows.add(toggle("wavy_capes", () -> c.wavyCapes, v -> c.wavyCapes = v));
                rows.add(slider("cape_swing", 0, 2, 0.1, () -> c.capeSwing, v -> c.capeSwing = (float) v, SettingsPage::percent));
            }
            case "particles" -> {
                rows.add(toggle("particles", () -> c.particles, v -> c.particles = v));
                rows.add(slider("particle_density", 0, 2, 0.1, () -> c.particleDensity, v -> c.particleDensity = (float) v,
                        SettingsPage::percent));
                rows.add(toggle("own_particles_first_person", () -> c.ownParticlesInFirstPerson,
                        v -> c.ownParticlesInFirstPerson = v));
            }
            case "emotes" -> {
                rows.add(toggle("emotes_enabled", () -> c.emotes, v -> {
                    c.emotes = v;
                    if (!v) {
                        EmotePlayer.stop();
                    }
                }));
                rows.add(choice(t("emote_camera"), List.of(CosmeticsConfig.EmoteCamera.values()), () -> c.emoteCamera,
                        v -> c.emoteCamera = v, v -> t("emote_camera." + v.name().toLowerCase(Locale.ROOT))));
                rows.add(toggle("emote_stop_on_move", () -> c.emoteStopOnMove, v -> c.emoteStopOnMove = v));
            }
            case "performance" -> {
                rows.add(slider("render_distance", 0, 256, 8, () -> c.maxRenderDistance, v -> c.maxRenderDistance = (int) v,
                        SettingsPage::blocks));
                rows.add(info("Iris", () -> Compat.IRIS ? (Compat.isShaderPackActive() ? t("iris.active") : t("iris.installed"))
                        : t("not_installed"), Compat.IRIS ? 0xFF8EE08E : Ui.TEXT_FAINT));
                rows.add(toggle("shadows", () -> c.renderInShadowPass, v -> c.renderInShadowPass = v));
                rows.add(slider("shadow_distance", 0, 256, 8, () -> c.shadowDistance, v -> c.shadowDistance = (int) v,
                        SettingsPage::blocks));
            }
            case "compatibility" -> {
                for (CosmeticSlot slot : CosmeticSlot.values()) {
                    if (slot.anchor() == CosmeticSlot.Anchor.HELD) {
                        continue;
                    }
                    rows.add(choice(t("slot." + slot.id()), List.of(SlotPriority.values()), () -> c.priority(slot),
                            v -> c.slotPriority.put(slot, v), v -> t("priority." + v.name().toLowerCase(Locale.ROOT))));
                }
                rows.add(toggle("defer_figura", () -> c.deferToFigura, v -> c.deferToFigura = v));
                Map<String, String> mods = Compat.detectedMods();
                mods.forEach((name, version) -> rows.add(info(name, () -> version, 0xFF8EE08E)));
            }
            case "maintenance" -> {
                rows.add(info(t("loaded"), () -> String.valueOf(CosmeticRegistry.all().size()), Ui.TEXT_DIM));
                rows.add(info(t("errors"), () -> RenderGuard.failureCount() == 0 ? t("errors.none")
                        : String.valueOf(RenderGuard.failureCount()), RenderGuard.failureCount() == 0 ? 0xFF8EE08E : 0xFFF0C070));
                rows.add(button(t("reload"), Component.translatable("cosmeticsmod.hub.settings.reload").getString(),
                        () -> CosmeticLoader.reload(Minecraft.getInstance().getResourceManager())));
            }
            default -> {
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Ui ui, String query) {
        ui.text(ui.fit(title(), RIGHT - LEFT, Ui.TITLE, true), LEFT, 136f, Ui.TEXT, Ui.TITLE, true);
        String summary = Component.translatable("cosmeticsmod.hub.settings.summary").getString();
        ui.text(ui.fit(summary, RIGHT - LEFT, Ui.SMALL, false), LEFT, 172f, Ui.TEXT_DIM, Ui.SMALL, false);

        float ny = LIST_TOP;
        for (String id : SECTIONS) {
            boolean current = id.equals(section);
            boolean hot = ui.hover(LEFT, ny, NAV_W, 40f);
            if (current || hot) {
                ui.roundRect(LEFT, ny, NAV_W, 40f, 14f, current ? Ui.WELL : 0xFF151518);
            }
            String label = ui.fit(Component.translatable("cosmeticsmod.hub.settings.section." + id).getString(),
                    NAV_W - 32f, 14f, true);
            ui.textMid(label, LEFT + 16f, ny + 20f, current ? Ui.TEXT : 0xBBFFFFFF, 14f, true);
            ui.click(LEFT, ny, NAV_W, 40f, () -> {
                section = id;
                scroll = 0f;
            });
            ny += 44f;
        }

        ui.clip(LIST_X, LIST_TOP, RIGHT, LIST_BOTTOM);
        float y = LIST_TOP - scroll;
        float start = y;
        List<Row> rows = rows(section);
        float h = rows.size() * ROW + 8f;
        ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
        float textX = LIST_X + 24f;
        float right = LIST_X + LIST_W - 20f;
        float ry = y + 4f;
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) {
                ui.roundRect(textX, ry, right - textX, 1f, 0f, Ui.LINE);
            }
            if (ry + ROW >= LIST_TOP && ry <= LIST_BOTTOM) {
                rows.get(i).draw(ui, ry, textX, right);
            }
            ry += ROW;
        }
        if (section.equals("keys")) {
            ui.text(ui.fit(t("keys.hint"), LIST_W - 12f, Ui.SMALL, false), LIST_X + 6f, y + h + 14f, Ui.TEXT_DIM, Ui.SMALL, false);
            h += 40f;
        }
        contentHeight = h + 10f;
        ui.endClip();
        scrollBy(0);
    }
}
