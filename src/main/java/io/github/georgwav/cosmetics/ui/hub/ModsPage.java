package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Mods page: every module of the client (switch, key, its own settings under the gear) and every installed mod
 * (its settings through Mod Menu), sorted into sections down the left, searched from the bar at the bottom.
 */
final class ModsPage {
    private static final Set<String> NOT_LISTED = Set.of("minecraft", "java", "fabricloader", CosmeticsMod.MOD_ID);
    private static final String[] SECTIONS = {"all", "on", "view", "world", "installed", "look"};
    private static final float LIST_X = 372f;
    private static final float LIST_W = 940f;
    private static final float LIST_TOP = 176f;
    private static final float LIST_BOTTOM = 712f;

    private final HubScreen hub;
    private String section = "all";
    private String expanded;
    private float scroll;
    private float contentHeight;
    private List<ModContainer> installed;

    ModsPage(HubScreen hub) {
        this.hub = hub;
    }

    /** Opens on one module's settings (its gear in the island). */
    void show(String moduleId) {
        section = "all";
        expanded = moduleId;
        scroll = 0f;
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.hub.mods." + key, args).getString();
    }

    private List<ModContainer> installed() {
        if (installed == null) {
            // The mods you installed yourself: not libraries packed inside other mods, not the game or the loader.
            installed = new ArrayList<>();
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                String id = mod.getMetadata().getId();
                if (!NOT_LISTED.contains(id) && mod.getContainingMod().isEmpty() && !id.startsWith("fabric-")
                        || id.equals("fabric-api")) {
                    installed.add(mod);
                }
            }
            installed.sort(Comparator.comparing(m -> m.getMetadata().getName().toLowerCase(Locale.ROOT)));
        }
        return installed;
    }

    private static boolean shows(Module m, String section) {
        return switch (section) {
            case "all" -> true;
            case "on" -> m.enabled();
            case "view" -> m.category == Module.Category.VIEW;
            case "world" -> m.category == Module.Category.WORLD;
            default -> false;
        };
    }

    private int count(String id) {
        return switch (id) {
            case "installed" -> installed().size();
            case "look" -> 1;
            default -> (int) Modules.all().stream().filter(m -> shows(m, id)).count();
        };
    }

    void scrollBy(double amount) {
        float max = Math.max(0f, contentHeight - (LIST_BOTTOM - LIST_TOP));
        scroll = Math.max(0f, Math.min(max, scroll - (float) amount * 48f));
    }

    boolean overList(float x, float y) {
        return x >= LIST_X && x < LIST_X + LIST_W && y >= LIST_TOP && y < LIST_BOTTOM;
    }

    /** The page's contents inside the open panel. */
    void draw(Ui ui, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        ui.text(t("title"), 128f, 118f, Ui.TEXT, ui.textScale + 1);
        long on = Modules.all().stream().filter(Module::enabled).count();
        ui.text(t("summary", on, Modules.all().size(), installed().size()), 128f, 118f + 9f * (ui.textScale + 1) / ui.u + 6f, Ui.TEXT_DIM);
        if (io.github.georgwav.cosmetics.compat.Compat.MOD_MENU) {
            ui.buttonRight(t("mod_menu"), 1312f, 118f, 36f, false, hub::openModMenu);
        }

        // Sections down the left, with how many each holds.
        float ny = LIST_TOP;
        for (String id : SECTIONS) {
            boolean current = needle.isEmpty() && id.equals(section);
            boolean hot = ui.hover(120f, ny, 220f, 40f);
            if (current || hot) {
                ui.roundRect(120f, ny, 220f, 40f, 14f, current ? Ui.WELL : 0xFF151518);
            }
            ui.textMid(t("section." + id), 136f, ny + 20f, current ? Ui.TEXT : 0xBBFFFFFF);
            String badge = String.valueOf(count(id));
            float bw = ui.textWidth(badge) + 14f;
            ui.roundRect(328f - bw, ny + 10f, bw, 20f, 10f, current ? ui.accent : 0x14FFFFFF);
            ui.textMid(badge, 335f - bw, ny + 20f, current ? ui.ink : 0xB0FFFFFF);
            ui.click(120f, ny, 220f, 40f, () -> {
                section = id;
                scroll = 0f;
            });
            ny += 44f;
        }

        // The list, clipped to its area and scrolled.
        ui.g.clipScreen(ui.sx(LIST_X - 12f), ui.sy(LIST_TOP), ui.sx(LIST_X + LIST_W + 12f), ui.sy(LIST_BOTTOM), ui.gs);
        float y = LIST_TOP - scroll;
        float start = y;
        boolean any = false;
        if (!section.equals("installed") && !section.equals("look") || !needle.isEmpty()) {
            Module.Category last = null;
            for (Module m : Modules.all()) {
                if (needle.isEmpty() ? !shows(m, section) : !matches(m, needle)) {
                    continue;
                }
                if (section.equals("all") && needle.isEmpty() && m.category != last) {
                    last = m.category;
                    ui.text(t("category." + m.category.name().toLowerCase(Locale.ROOT)), LIST_X + 4f, y + 6f, Ui.TEXT_FAINT);
                    y += 30f;
                }
                y = moduleCard(ui, m, y);
                any = true;
            }
        }
        if (section.equals("installed") || !needle.isEmpty()) {
            for (ModContainer mod : installed()) {
                if (!needle.isEmpty() && !mod.getMetadata().getName().toLowerCase(Locale.ROOT).contains(needle)) {
                    continue;
                }
                y = modCard(ui, mod, y);
                any = true;
            }
        }
        if (section.equals("look") && needle.isEmpty()) {
            y = lookCard(ui, y);
            any = true;
        }
        if (!any) {
            ui.text(needle.isEmpty() ? t("empty") : t("no_match", query.trim()), LIST_X + 4f, y + 10f, Ui.TEXT_DIM);
        }
        contentHeight = y + scroll - start;
        ui.g.endClip();
        scrollBy(0);
    }

    private static boolean matches(Module m, String needle) {
        return m.name().toLowerCase(Locale.ROOT).contains(needle) || m.note().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** One module: icon, name and note, its key, its switch and its gear; open, its settings under it. */
    private float moduleCard(Ui ui, Module m, float y) {
        boolean open = m.id.equals(expanded);
        List<Setting<?>> settings = m.settings();
        float h = 64f + (open ? settings.size() * 56f + 8f : 0f);
        float right = LIST_X + LIST_W - 20f;
        if (y + h >= LIST_TOP && y <= LIST_BOTTOM) {
            ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
            ui.circle(LIST_X + 36f, y + 32f, 20f, m.enabled() ? ui.accent : Ui.WELL);
            ui.icon(m.icon, LIST_X + 36f, y + 32f, 20f, m.enabled() && ui.isInkDark());
            float textRight = right - 52f - 16f - 200f;
            ui.text(ui.fit(m.name(), textRight - LIST_X - 68f), LIST_X + 68f, y + 14f, Ui.TEXT);
            ui.text(ui.fit(m.note(), textRight - LIST_X - 68f), LIST_X + 68f, y + 34f, Ui.TEXT_DIM);
            ui.toggle(m.id, right - 52f, y + 16f, m.enabled(), () -> m.setEnabled(!m.enabled()));
            float gearX = right - 52f - 16f - 18f;
            ui.iconButton("gear", gearX, y + 32f, 18f, open, () -> expanded = open ? null : m.id);
            keyButton(ui, m.toggleKey(), gearX - 18f - 12f, y + 14f);
            if (open) {
                float sy = y + 64f;
                for (Setting<?> s : settings) {
                    ui.roundRect(LIST_X + 68f, sy, LIST_W - 88f, 1f, 0f, Ui.LINE);
                    settingRow(ui, m, s, sy, right);
                    sy += 56f;
                }
            }
        }
        return y + h + 10f;
    }

    /** A module's setting: its name (a dot when changed) and its control on the right. */
    private void settingRow(Ui ui, Module m, Setting<?> s, float y, float right) {
        String base = "cosmeticsmod.module." + m.id + "." + s.id;
        float cy = y + 28f;
        if (s.changed()) {
            ui.circle(LIST_X + 60f, cy, 3.5f, ui.accent);
        }
        ui.textMid(Component.translatable(base).getString(), LIST_X + 68f, cy, Ui.TEXT);
        if (s instanceof Setting.Toggle toggle) {
            ui.toggle(m.id + "." + s.id, right - 52f, cy - 16f, toggle.get(), () -> toggle.set(!toggle.get()));
        } else if (s instanceof Setting.Slider slider) {
            float w = 220f;
            ui.slider(m.id + "." + s.id, right - w, cy - 16f, w, slider.min, slider.max, slider.get(), slider::set);
            String value = slider.text();
            ui.textMid(value, right - w - 16f - ui.textWidth(value), cy, 0xCCFFFFFF);
        } else if (s instanceof Setting.Choice choice) {
            List<String> labels = new ArrayList<>();
            for (String option : choice.options) {
                labels.add(Component.translatable(base + "." + option).getString());
            }
            float w = 6f;
            for (String label : labels) {
                w += ui.textWidth(label) + 24f;
            }
            ui.choice(m.id + "." + s.id, right - w, cy - 18f, labels, choice.options.indexOf(choice.get()),
                    i -> choice.set(choice.options.get(i)));
        }
    }

    /** The key that switches a module: its name, or "Press a key" while it waits for one. */
    private void keyButton(Ui ui, KeyMapping key, float right, float y) {
        if (key == null) {
            return;
        }
        boolean waiting = hub.listeningFor() == key;
        String label = waiting ? t("press_key") : key.isUnbound() ? t("no_key") : KeyBinds.name(key);
        float w = ui.textWidth(label) + 28f;
        boolean hot = ui.hover(right - w, y, w, 36f);
        ui.roundRect(right - w, y, w, 36f, 12f, waiting ? ui.accent : hot ? 0xFF26262A : Ui.WELL);
        ui.textMid(label, right - w + 14f, y + 18f, waiting ? ui.ink : key.isUnbound() ? Ui.TEXT_DIM : Ui.TEXT);
        ui.click(right - w, y, w, 36f, () -> hub.listenFor(waiting ? null : key));
    }

    /** An installed mod: its name and version, and its settings through Mod Menu. */
    private float modCard(Ui ui, ModContainer mod, float y) {
        float h = 60f;
        if (y + h >= LIST_TOP && y <= LIST_BOTTOM) {
            ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
            ui.circle(LIST_X + 34f, y + 30f, 18f, Ui.WELL);
            ui.icon("grid", LIST_X + 34f, y + 30f, 18f, false);
            String name = mod.getMetadata().getName();
            String version = mod.getMetadata().getVersion().getFriendlyString();
            float right = LIST_X + LIST_W - 20f;
            float textRight = right - 140f;
            ui.text(ui.fit(name, textRight - LIST_X - 64f), LIST_X + 64f, y + 12f, Ui.TEXT);
            ui.text(ui.fit(version, textRight - LIST_X - 64f), LIST_X + 64f, y + 32f, Ui.TEXT_DIM);
            if (io.github.georgwav.cosmetics.compat.Compat.MOD_MENU) {
                String id = mod.getMetadata().getId();
                ui.buttonRight(t("settings"), right, y + 12f, 36f, false, () -> openSettings(id));
            }
        }
        return y + h + 10f;
    }

    /** The client's own look: how blurred the world is behind menus (the game's setting, also behind the hub). */
    private float lookCard(Ui ui, float y) {
        Minecraft mc = Minecraft.getInstance();
        float h = 64f;
        float right = LIST_X + LIST_W - 20f;
        ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
        ui.text(t("blur"), LIST_X + 24f, y + 14f, Ui.TEXT);
        ui.text(t("blur.hint"), LIST_X + 24f, y + 34f, Ui.TEXT_DIM);
        int blur = mc.options.menuBackgroundBlurriness().get();
        float w = 220f;
        ui.slider("blur", right - w, y + 16f, w, 0, 10, blur, v -> {
            int next = (int) Math.round(v);
            if (next != mc.options.menuBackgroundBlurriness().get()) {
                mc.options.menuBackgroundBlurriness().set(next);
                mc.options.save();
            }
        });
        String value = String.valueOf(blur);
        ui.textMid(value, right - w - 16f - ui.textWidth(value), y + 32f, 0xCCFFFFFF);
        return y + h + 10f;
    }

    /** A mod's own settings screen, as Mod Menu's "Configure" opens it; back from it comes back to the hub. */
    private void openSettings(String modId) {
        try {
            Object screen = Class.forName("com.terraformersmc.modmenu.ModMenu")
                    .getMethod("getConfigScreen", String.class, Screen.class).invoke(null, modId, hub);
            if (screen instanceof Screen s) {
                io.github.georgwav.cosmetics.ui.ScreenOpener.open(s);
            } else {
                hub.notify(t("no_settings"));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("Couldn't open the settings of {}", modId, e);
            hub.notify(t("no_settings"));
        }
    }
}
