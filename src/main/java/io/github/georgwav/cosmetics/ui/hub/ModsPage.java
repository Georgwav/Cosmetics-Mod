package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Profiles;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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
final class ModsPage implements HubPage {
    private static final Set<String> NOT_LISTED = Set.of("minecraft", "java", "fabricloader", CosmeticsMod.MOD_ID);
    private static final String[] SECTIONS = {"all", "on", "view", "world", "hud", "profiles", "installed", "look"};
    // Everything stays inside the open panel (x 80 to 1360, y 92 to 732, rounded corners): the header across the
    // top, the sections down the left, the list to their right.
    private static final float LEFT = 148f;
    private static final float RIGHT = 1292f;
    private static final float NAV_W = 212f;
    private static final float LIST_X = 384f;
    private static final float LIST_W = RIGHT - LIST_X;
    private static final float LIST_TOP = 204f;
    private static final float LIST_BOTTOM = 692f;

    private final HubScreen hub;
    private String section = "all";
    private String expanded;
    private float scroll;
    private float contentHeight;
    private List<ModContainer> installed;
    /** The saved profiles' names, read again now and then (they are files). */
    private List<String> profiles;
    private long profilesRead;
    /** The profile whose Delete was clicked once (a second click deletes it). */
    private String deleting;

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
            case "hud" -> m.category == Module.Category.HUD;
            default -> false;
        };
    }

    private List<String> profiles() {
        if (profiles == null || System.currentTimeMillis() - profilesRead > 2000) {
            profiles = Profiles.names();
            profilesRead = System.currentTimeMillis();
        }
        return profiles;
    }

    private int count(String id) {
        return switch (id) {
            case "installed" -> installed().size();
            case "profiles" -> profiles().size();
            case "look" -> 1;
            default -> (int) Modules.all().stream().filter(m -> shows(m, id)).count();
        };
    }

    @Override
    public String title() {
        return t("title");
    }

    @Override
    public String line() {
        long on = Modules.all().stream().filter(Module::enabled).count();
        return hub.listeningFor() != null ? t("press_key") : t("island", on, Modules.all().size());
    }

    @Override
    public String icon() {
        return "list";
    }

    @Override
    public String searchHint() {
        return t("search");
    }

    @Override
    public void scrollBy(double amount) {
        float max = Math.max(0f, contentHeight - (LIST_BOTTOM - LIST_TOP));
        scroll = Math.max(0f, Math.min(max, scroll - (float) amount * 48f));
    }

    @Override
    public boolean overList(float x, float y) {
        return x >= LIST_X && x < LIST_X + LIST_W && y >= LIST_TOP && y < LIST_BOTTOM;
    }

    /** The page's contents inside the open panel. */
    @Override
    public void draw(Ui ui, String query) {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        float headerRight = RIGHT;
        if (io.github.georgwav.cosmetics.compat.Compat.MOD_MENU) {
            headerRight = ui.buttonRight(t("mod_menu"), RIGHT, 140f, 36f, false, hub::openModMenu) - 16f;
        }
        ui.text(ui.fit(t("title"), headerRight - LEFT, Ui.TITLE, true), LEFT, 136f, Ui.TEXT, Ui.TITLE, true);
        long on = Modules.all().stream().filter(Module::enabled).count();
        String summary = t("summary", on, Modules.all().size(), installed().size());
        ui.text(ui.fit(summary, headerRight - LEFT, Ui.SMALL, false), LEFT, 172f, Ui.TEXT_DIM, Ui.SMALL, false);

        // Sections down the left, with how many each holds.
        float ny = LIST_TOP;
        for (String id : SECTIONS) {
            boolean current = needle.isEmpty() && id.equals(section);
            boolean hot = ui.hover(LEFT, ny, NAV_W, 40f);
            if (current || hot) {
                ui.roundRect(LEFT, ny, NAV_W, 40f, 14f, current ? Ui.WELL : 0xFF151518);
            }
            String badge = String.valueOf(count(id));
            float bw = Math.max(24f, ui.textWidth(badge, Ui.SMALL, true) + 14f);
            float bx = LEFT + NAV_W - 10f - bw;
            ui.roundRect(bx, ny + 10f, bw, 20f, 10f, current ? ui.accent : 0x14FFFFFF);
            ui.textMid(badge, bx + (bw - ui.textWidth(badge, Ui.SMALL, true)) / 2f, ny + 20f,
                    current ? ui.ink : 0xB0FFFFFF, Ui.SMALL, true);
            String label = ui.fit(t("section." + id), bx - 8f - (LEFT + 16f), 14f, true);
            ui.textMid(label, LEFT + 16f, ny + 20f, current ? Ui.TEXT : 0xBBFFFFFF, 14f, true);
            ui.click(LEFT, ny, NAV_W, 40f, () -> {
                section = id;
                scroll = 0f;
            });
            ny += 44f;
        }

        // The list, clipped to its area and scrolled.
        ui.clip(LIST_X, LIST_TOP, RIGHT, LIST_BOTTOM);
        float y = LIST_TOP - scroll;
        float start = y;
        boolean any = false;
        if (!section.equals("installed") && !section.equals("look") && !section.equals("profiles") || !needle.isEmpty()) {
            Module.Category last = null;
            for (Module m : Modules.all()) {
                if (needle.isEmpty() ? !shows(m, section) : !matches(m, needle)) {
                    continue;
                }
                if (section.equals("all") && needle.isEmpty() && m.category != last) {
                    last = m.category;
                    if (y > start) {
                        y += 8f;
                    }
                    ui.text(t("category." + m.category.name().toLowerCase(Locale.ROOT)), LIST_X + 6f, y + 4f,
                            Ui.TEXT_FAINT, Ui.SMALL, true);
                    y += 26f;
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
        if (section.equals("profiles") && needle.isEmpty()) {
            y = saveProfileCard(ui, y);
            for (String name : profiles()) {
                y = profileCard(ui, name, y);
            }
            any = true;
        }
        if (!any) {
            String empty = needle.isEmpty() ? t("empty") : t("no_match", query.trim());
            ui.text(ui.fit(empty, LIST_W - 12f, Ui.SMALL, false), LIST_X + 6f, y + 10f, Ui.TEXT_DIM, Ui.SMALL, false);
        }
        contentHeight = y + scroll - start;
        ui.endClip();
        scrollBy(0);
    }

    private static boolean matches(Module m, String needle) {
        return m.name().toLowerCase(Locale.ROOT).contains(needle) || m.note().toLowerCase(Locale.ROOT).contains(needle);
    }

    /** One module: icon, name and note, its key, its switch and its gear; open, its settings under it. */
    private float moduleCard(Ui ui, Module m, float y) {
        boolean open = m.id.equals(expanded);
        List<Setting<?>> settings = m.settings();
        float preview = open ? m.previewHeight() : 0f;
        float h = 64f + (open ? (preview > 0f ? preview + 12f : 0f) + settings.size() * 56f + 8f : 0f);
        float right = LIST_X + LIST_W - 20f;
        if (y + h >= LIST_TOP && y <= LIST_BOTTOM) {
            ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
            ui.circle(LIST_X + 34f, y + 32f, 20f, m.enabled() ? ui.accent : Ui.WELL);
            ui.icon(m.icon, LIST_X + 34f, y + 32f, 20f, m.enabled() && ui.isInkDark());
            ui.toggle(m.id, right - 52f, y + 16f, m.enabled(), () -> m.setEnabled(!m.enabled()));
            float gearX = right - 52f - 16f - 18f;
            ui.iconButton("gear", gearX, y + 32f, 18f, open, () -> expanded = open ? null : m.id);
            float keyLeft = keyButton(ui, m.toggleKey(), gearX - 18f - 12f, y + 14f);
            float textX = LIST_X + 66f;
            float room = keyLeft - 16f - textX;
            ui.text(ui.fit(m.name(), room, Ui.NAME, true), textX, y + 18f, Ui.TEXT, Ui.NAME, true);
            ui.text(ui.fit(m.note(), room, Ui.SMALL, false), textX, y + 37f, Ui.TEXT_DIM, Ui.SMALL, false);
            if (open) {
                float sy = y + 64f;
                if (preview > 0f) {
                    // A live preview on a mid-tone well, where light and dark both show.
                    ui.roundRect(textX, sy + 4f, right - textX, preview, 16f, 0xFF46586B);
                    m.drawPreview(ui.g, Math.round(ui.sx(textX)), Math.round(ui.sy(sy + 4f)), Math.round(ui.sx(right)),
                            Math.round(ui.sy(sy + 4f + preview)));
                    sy += preview + 12f;
                }
                for (Setting<?> s : settings) {
                    ui.roundRect(textX, sy, right - textX, 1f, 0f, Ui.LINE);
                    settingRow(ui, m, s, sy, textX, right);
                    sy += 56f;
                }
            }
        }
        return y + h + 10f;
    }

    /** A module's setting: its name (a dot when changed) and its control on the right. */
    private void settingRow(Ui ui, Module m, Setting<?> s, float y, float textX, float right) {
        float cy = y + 28f;
        float controlLeft = right;
        if (s instanceof Setting.Toggle toggle) {
            ui.toggle(m.id + "." + s.id, right - 52f, cy - 16f, toggle.get(), () -> toggle.set(!toggle.get()));
            controlLeft = right - 52f;
        } else if (s instanceof Setting.Slider slider) {
            float w = 220f;
            ui.slider(m.id + "." + s.id, right - w, cy - 16f, w, slider.min, slider.max, slider.get(), slider::set);
            String value = slider.text();
            ui.textMidRight(value, right - w - 18f, cy, 0xCCFFFFFF, Ui.SMALL, true);
            controlLeft = right - w - 18f - ui.textWidth(value, Ui.SMALL, true);
        } else if (s instanceof Setting.Choice choice) {
            List<String> labels = new ArrayList<>();
            for (String option : choice.options) {
                labels.add(m.optionName(s, option));
            }
            float w = ui.choiceWidth(labels);
            ui.choice(m.id + "." + s.id, right - w, cy - 18f, labels, choice.options.indexOf(choice.get()),
                    i -> choice.set(choice.options.get(i)));
            controlLeft = right - w;
        }
        if (s.changed()) {
            ui.circle(textX - 9f, cy, 3.5f, ui.accent);
        }
        String name = m.settingName(s);
        ui.textMid(ui.fit(name, controlLeft - 16f - textX, Ui.NAME, false), textX, cy, Ui.TEXT, Ui.NAME, false);
    }

    /** The key that switches a module: its name, or "Press a key" while it waits for one. Returns its left edge. */
    private float keyButton(Ui ui, KeyMapping key, float right, float y) {
        if (key == null) {
            return right;
        }
        boolean waiting = hub.listeningFor() == key;
        String label = waiting ? t("press_key") : key.isUnbound() ? t("no_key") : KeyBinds.name(key);
        label = ui.fit(label, 160f, Ui.SMALL, true);
        float w = ui.textWidth(label, Ui.SMALL, true) + 28f;
        boolean hot = ui.hover(right - w, y, w, 36f);
        ui.roundRect(right - w, y, w, 36f, 18f, waiting ? ui.accent : hot ? 0xFF26262A : Ui.WELL);
        ui.textMid(label, right - w + 14f, y + 18f, waiting ? ui.ink : key.isUnbound() ? Ui.TEXT_DIM : Ui.TEXT,
                Ui.SMALL, true);
        ui.click(right - w, y, w, 36f, () -> hub.listenFor(waiting ? null : key));
        return right - w;
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
            float textRight = right;
            if (io.github.georgwav.cosmetics.compat.Compat.MOD_MENU) {
                String id = mod.getMetadata().getId();
                textRight = ui.buttonRight(t("settings"), right, y + 12f, 36f, false, () -> hub.openModSettings(id));
            }
            float textX = LIST_X + 66f;
            float room = textRight - 16f - textX;
            ui.text(ui.fit(name, room, Ui.NAME, true), textX, y + 16f, Ui.TEXT, Ui.NAME, true);
            ui.text(ui.fit(version, room, Ui.SMALL, false), textX, y + 35f, Ui.TEXT_DIM, Ui.SMALL, false);
        }
        return y + h + 10f;
    }

    /** Saving every module's settings as a new profile, "Profile N". */
    private float saveProfileCard(Ui ui, float y) {
        float h = 64f;
        float right = LIST_X + LIST_W - 20f;
        ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
        String typed = Profiles.nextName(t("profile"), profiles());
        String label = t("save_profile");
        float left = ui.buttonRight(label, right, y + 14f, 36f, true, () -> {
            String saved = Profiles.save(typed);
            profiles = null;
            hub.notify(saved != null ? t("profile_saved", saved) : t("profile_not_saved"));
        });
        float textX = LIST_X + 24f;
        float room = left - 16f - textX;
        ui.text(ui.fit(t("profiles"), room, Ui.NAME, true), textX, y + 18f, Ui.TEXT, Ui.NAME, true);
        ui.text(ui.fit(t("profiles.hint", typed), room, Ui.SMALL, false), textX, y + 37f, Ui.TEXT_DIM, Ui.SMALL, false);
        return y + h + 10f;
    }

    /** A saved profile: Load puts its settings back; Delete (clicked twice) removes it. */
    private float profileCard(Ui ui, String name, float y) {
        float h = 60f;
        if (y + h >= LIST_TOP && y <= LIST_BOTTOM) {
            float right = LIST_X + LIST_W - 20f;
            ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
            ui.circle(LIST_X + 34f, y + 30f, 18f, Ui.WELL);
            ui.icon("sliders", LIST_X + 34f, y + 30f, 18f, false);
            boolean sure = name.equals(deleting);
            float x = ui.buttonRight(t(sure ? "delete_sure" : "delete"), right, y + 12f, 36f, false, () -> {
                if (sure) {
                    Profiles.delete(name);
                    profiles = null;
                    deleting = null;
                } else {
                    deleting = name;
                }
            });
            x = ui.buttonRight(t("load"), x - 8f, y + 12f, 36f, true, () -> {
                deleting = null;
                boolean ok = Profiles.load(name);
                hub.notify(ok ? t("profile_loaded", name) : t("profile_unreadable"));
            });
            float textX = LIST_X + 66f;
            ui.textMid(ui.fit(name, x - 16f - textX, Ui.NAME, true), textX, y + 30f, Ui.TEXT, Ui.NAME, true);
        }
        return y + h + 10f;
    }

    /** The client's own look: how blurred the world is behind menus (the game's setting, also behind the hub). */
    private float lookCard(Ui ui, float y) {
        Minecraft mc = Minecraft.getInstance();
        float h = 64f;
        float right = LIST_X + LIST_W - 20f;
        ui.roundRect(LIST_X, y, LIST_W, h, 22f, Ui.CARD);
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
        ui.textMidRight(value, right - w - 18f, y + 32f, 0xCCFFFFFF, Ui.SMALL, true);
        float textX = LIST_X + 24f;
        float room = right - w - 18f - ui.textWidth(value, Ui.SMALL, true) - 16f - textX;
        ui.text(ui.fit(t("blur"), room, Ui.NAME, true), textX, y + 18f, Ui.TEXT, Ui.NAME, true);
        ui.text(ui.fit(t("blur.hint"), room, Ui.SMALL, false), textX, y + 37f, Ui.TEXT_DIM, Ui.SMALL, false);
        return y + h + 10f;
    }
}
