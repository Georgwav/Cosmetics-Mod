package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.sol.atmosphere.Atmosphere;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import io.github.georgwav.cosmetics.sol.util.Spring;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.ui.ScreenOpener;

import java.util.List;

/** Settings: the client's own (Sol UI, keys, motion, quality, blur, light style, main folder, updates). */
final class SettingsSection implements Section {
    private static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cosmeticsmod")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
    private final Scroll scroll = new Scroll();

    private interface Row {
        void control(Gfx g, Ui ui, Tokens tk, float right, float cy);
    }

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        SolConfig cfg = SolConfig.get();
        Atmosphere at = Atmosphere.INSTANCE;
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f), mono = g.font(SolFont.Family.MONO, 12f);
        String title = SolMenu.tr("sol.section.settings");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        g.textMid(mono, SolMenu.tr("sol.menu.settings.count"), x + g.width(h2, title) + 14f, headY, 0x8CFFFFFF);

        String[] labels = {"sol_ui", "menu_key", "search", "motion", "quality", "blur", "light", "cosmetics", "keys", "screenshots",
                "fast_quit", "pack_folders", "main_folder", "updates"};
        Row[] rows = {
                (gg, u, t, r, cy) -> u.smallSwitch(t, r - 30f, cy - 9f, cfg.solUi, () -> {
                    cfg.solUi = !cfg.solUi;
                    SolConfig.save();
                }),
                (gg, u, t, r, cy) -> Widgets.kbd(gg, KeyBinds.name(KeyBinds.quickMenu()), r, cy),
                (gg, u, t, r, cy) -> Widgets.kbd(gg, SolMenu.tr("sol.quick.ctrl_k"), r, cy),
                (gg, u, t, r, cy) -> u.segmented(t, r, cy, new String[]{SolMenu.tr("sol.menu.settings.full"), SolMenu.tr("sol.menu.settings.calm")},
                        cfg.calmMotion() ? 1 : 0, i -> {
                            cfg.motion = i == 1 ? "calm" : "full";
                            Spring.reducedMotion = i == 1;
                            SolConfig.save();
                        }),
                (gg, u, t, r, cy) -> {
                    if (at == null) {
                        return;
                    }
                    List<String> q = List.of("auto", "low", "medium", "high");
                    String[] names = q.stream().map(o -> at.optionName(at.quality, o)).toArray(String[]::new);
                    u.segmented(t, r, cy, names, Math.max(0, q.indexOf(at.quality.get())), i -> at.quality.set(q.get(i)));
                },
                (gg, u, t, r, cy) -> {
                    var opt = menu.mc().options.menuBackgroundBlurriness();
                    boolean on = opt.get() > 0;
                    u.segmented(t, r, cy, new String[]{SolMenu.tr("sol.menu.settings.off"), SolMenu.tr("sol.menu.settings.on")}, on ? 1 : 0, i -> {
                        opt.set(i == 1 ? 5 : 0);
                        cfg.menuBlur = i == 1;
                        menu.mc().options.save();
                        SolConfig.save();
                    });
                },
                (gg, u, t, r, cy) -> {
                    if (at == null) {
                        return;
                    }
                    boolean pixel = !"smooth".equals(at.lightStyle.get());
                    u.segmented(t, r, cy, new String[]{at.optionName(at.lightStyle, "pixel"), at.optionName(at.lightStyle, "smooth")},
                            pixel ? 0 : 1, i -> at.lightStyle.set(i == 0 ? "pixel" : "smooth"));
                },
                (gg, u, t, r, cy) -> u.button(t, r, cy, SolMenu.tr("sol.menu.settings.cosmetics_open"), false,
                        () -> ScreenOpener.open(io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen.on(menu, "settings"))),
                (gg, u, t, r, cy) -> u.button(t, r, cy, SolMenu.tr("sol.menu.settings.open"), false,
                        () -> ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.KeybindSearch(menu))),
                (gg, u, t, r, cy) -> u.button(t, r, cy, SolMenu.tr("sol.menu.settings.open"), false,
                        () -> ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.ScreenshotGallery(menu))),
                (gg, u, t, r, cy) -> u.smallSwitch(t, r - 30f, cy - 9f, cfg.fastQuit, () -> {
                    cfg.fastQuit = !cfg.fastQuit;
                    SolConfig.save();
                }),
                (gg, u, t, r, cy) -> u.smallSwitch(t, r - 30f, cy - 9f, cfg.packFolders, () -> {
                    cfg.packFolders = !cfg.packFolders;
                    SolConfig.save();
                }),
                (gg, u, t, r, cy) -> {
                    // Click to choose another folder; Threadrinth's app folder is offered when it's on this computer.
                    String name = MainFolder.isDefault() ? ".minecraft" : MainFolder.folder().getFileName().toString();
                    float bw = u.button(t, r, cy, name, false, () -> chooseFolder(menu));
                    java.nio.file.Path launcher = MainFolder.launcherFolder();
                    if (launcher != null && !launcher.equals(MainFolder.folder())) {
                        u.button(t, r - bw - 6f, cy, SolMenu.tr("cosmeticsmod.hub.skins.use_launcher"), false, () -> useFolder(menu, launcher));
                    }
                },
                (gg, u, t, r, cy) -> {
                    // The version, and looking for (or installing) a newer one from GitHub Releases.
                    String state = io.github.georgwav.cosmetics.sol.client.Updates.state();
                    var rel = io.github.georgwav.cosmetics.sol.client.Updates.available();
                    float bw;
                    if (state.equals("available") && rel != null) {
                        bw = u.button(t, r, cy, SolMenu.tr("sol.menu.settings.update_install", rel.version()), true,
                                io.github.georgwav.cosmetics.sol.client.Updates::install);
                    } else if (state.equals("staged")) {
                        bw = u.pill(t, r, cy, SolMenu.tr("sol.menu.settings.update_restart"), true);
                    } else if (state.equals("checking") || state.equals("downloading")) {
                        bw = u.pill(t, r, cy, SolMenu.tr("sol.menu.loading"), false);
                    } else {
                        bw = u.button(t, r, cy, SolMenu.tr("sol.menu.settings.update_check"), false,
                                () -> io.github.georgwav.cosmetics.sol.client.Updates.check(true));
                    }
                    u.pill(t, r - bw - 8f, cy, VERSION, false);
                },
        };
        float gy = y + 42f, gh = h - 42f;
        g.clip(x - 4f, gy - 4f, x + w + 4f, gy + gh);
        float colW = (w - 24f) / 2f;
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
        for (int i = 0; i < rows.length; i++) {
            float rx = x + (i % 2) * (colW + 24f), ry = gy + 13f + (i / 2) * 40f - scroll.value();
            g.textMid(f, SolMenu.tr("sol.menu.settings." + labels[i]), rx, ry, 0xFFFFFFFF);
            rows[i].control(g, ui, tk, rx + colW, ry);
            g.rect(rx, ry + 20f, colW, Math.max(1f / g.d.u, 1f), 0x0DFFFFFF);
        }
        g.endClip();
        scroll.setMax(((rows.length + 1) / 2) * 40f - gh);
        scroll.step(menu.dt());
    }

    @Override
    public boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    /** Asks for the main folder (Threadrinth's app folder has the launcher's skins). */
    private static void chooseFolder(SolMenu menu) {
        try {
            java.nio.file.Path picked = io.github.georgwav.cosmetics.ui.FolderPicker.pick(
                    SolMenu.tr("cosmeticsmod.hub.skins.choose_title"), MainFolder.folder());
            if (picked != null) {
                useFolder(menu, picked);
            }
        } catch (UnsupportedOperationException e) {
            menu.toast(SolMenu.tr("cosmeticsmod.hub.skins.no_picker"));
        }
    }

    private static void useFolder(SolMenu menu, java.nio.file.Path folder) {
        MainFolder.choose(folder);
        menu.toast(SolMenu.tr("sol.notify.main_folder", MainFolder.folder().toString()));
    }
}
