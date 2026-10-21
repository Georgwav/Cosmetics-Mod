package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.kit.Widgets;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mods: what's installed and how it gets on with Sol. Mods Sol styles (with a Sol style switch), mods it reads or
 * steps aside for (a note), overlaps where you choose who does the job (Sol or the mod), and mods that simply run
 * alongside. Only installed mods are listed.
 */
final class ModsSection implements Section {
    /** How a known mod gets on with Sol. */
    enum Kind { STYLED, NOTE, OVERLAP }

    record Known(String id, String icon, Kind kind) {
    }

    static final Map<String, Known> KNOWN = Map.ofEntries(
            Map.entry("xaerominimap", new Known("xaerominimap", "map", Kind.STYLED)),
            Map.entry("xaeroworldmap", new Known("xaeroworldmap", "map", Kind.NOTE)),
            Map.entry("voicechat", new Known("voicechat", "mic", Kind.STYLED)),
            Map.entry("jade", new Known("jade", "lookat", Kind.STYLED)),
            Map.entry("appleskin", new Known("appleskin", "heart", Kind.NOTE)),
            Map.entry("sodium", new Known("sodium", "bolt", Kind.NOTE)),
            Map.entry("iris", new Known("iris", "sun", Kind.NOTE)),
            Map.entry("voxy", new Known("voxy", "grid", Kind.NOTE)),
            Map.entry("distanthorizons", new Known("distanthorizons", "grid", Kind.NOTE)),
            Map.entry("modmenu", new Known("modmenu", "puzzle", Kind.NOTE)),
            Map.entry("chat_heads", new Known("chat_heads", "chat", Kind.NOTE)),
            Map.entry("zoomify", new Known("zoomify", "zoom", Kind.NOTE)),
            Map.entry("ok_zoomer", new Known("ok_zoomer", "zoom", Kind.NOTE)),
            Map.entry("shulkerboxtooltip", new Known("shulkerboxtooltip", "chest", Kind.NOTE)),
            Map.entry("controlling", new Known("controlling", "key", Kind.NOTE)),
            Map.entry("freelook", new Known("freelook", "eye", Kind.OVERLAP)),
            Map.entry("perspectivemod", new Known("perspectivemod", "eye", Kind.OVERLAP)),
            Map.entry("screenshot_viewer", new Known("screenshot_viewer", "camera", Kind.NOTE)));

    private final Scroll scroll = new Scroll();

    private static boolean ignored(ModContainer mod) {
        String id = mod.getMetadata().getId();
        return id.equals("minecraft") || id.equals("java") || id.equals("fabricloader") || id.equals("cosmeticsmod")
                || id.startsWith("fabric-") || id.equals("fabric-api") || id.equals("mixinextras") || mod.getContainingMod().isPresent();
    }

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        List<ModContainer> styled = new ArrayList<>(), overlaps = new ArrayList<>(), alongside = new ArrayList<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            if (ignored(mod)) {
                continue;
            }
            Known k = KNOWN.get(mod.getMetadata().getId());
            if (k == null) {
                alongside.add(mod);
            } else if (k.kind() == Kind.OVERLAP) {
                overlaps.add(mod);
            } else {
                styled.add(mod);
            }
        }
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.PIXEL_BOLD, 15f), mono = g.font(SolFont.Family.PIXEL, 11f);
        String title = SolMenu.tr("sol.section.mods");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        g.textMid(mono, SolMenu.tr("sol.menu.mods.count", styled.size() + overlaps.size() + alongside.size(), overlaps.size()),
                x + g.width(h2, title) + 14f, headY, 0x8CFFFFFF);
        List<String> missing = new ArrayList<>(), missingIds = new ArrayList<>();
        // The performance pack Sol suggests (fabric.mod.json).
        for (String id : new String[]{"lithium", "ferritecore", "immediatelyfast", "entityculling", "modernfix"}) {
            if (!FabricLoader.getInstance().isModLoaded(id)) {
                missing.add(SolMenu.tr("sol.mods." + id));
                missingIds.add(id);
            }
        }
        float hr = x + w;
        if (Compat.MOD_MENU) {
            // Mod Menu's own list of every mod.
            hr -= ui.button(tk, hr, headY, SolMenu.tr("sol.menu.mods.mod_menu"), false, () -> {
                if (!ModScreens.openModList(menu)) {
                    menu.toast(SolMenu.tr("sol.notify.no_screen"));
                }
            }) + 8f;
        }
        hr -= ui.button(tk, hr, headY, SolMenu.tr("sol.menu.mods.browse"), true,
                () -> io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.ModBrowser(menu, ""))) + 8f;
        if (!missing.isEmpty()) {
            // The speed mods worth having: the browser, searching for the first one missing.
            String first = missingIds.get(0);
            ui.button(tk, hr, headY, SolMenu.tr("sol.menu.mods.worth", String.join(", ", missing)), false,
                    () -> io.github.georgwav.cosmetics.ui.ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.ModBrowser(menu, first)));
        }

        float gy = y + 42f, gh = h - 42f;
        g.clip(x - 4f, gy - 4f, x + w + 4f, gy + gh);
        float colW = (w - 20f) * 0.528f, col2 = w - 20f - colW;
        float ly = gy - scroll.value();
        float left = group(menu, g, ui, tk, x, ly, colW, SolMenu.tr("sol.menu.mods.works"), styled, Kind.STYLED);
        float right = group(menu, g, ui, tk, x + colW + 20f, ly, col2, SolMenu.tr("sol.menu.mods.overlaps"), overlaps, Kind.OVERLAP);
        right = group(menu, g, ui, tk, x + colW + 20f, ly + right + 14f, col2, SolMenu.tr("sol.menu.mods.alongside"), alongside, null)
                + right + 14f;
        g.endClip();
        scroll.setMax(Math.max(left, right) - gh + 8f);
        scroll.step(menu.dt());
        scroll.bar(g, x + w + 4f, gy, gh);
    }

    /** One group (heading and rows); returns its height. */
    private float group(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, String heading, List<ModContainer> mods,
                        Kind kind) {
        SolFont head = g.font(SolFont.Family.MONO, 11f), b = g.font(SolFont.Family.SEMIBOLD, 13.5f), s = g.font(SolFont.Family.MEDIUM, 11.5f);
        g.textSpaced(head, heading.toUpperCase(java.util.Locale.ROOT), x, y + 8f, 1.1f, 0x80FFFFFF);
        float ry = y + 22f;
        if (mods.isEmpty()) {
            g.text(s, SolMenu.tr("sol.menu.mods.none"), x, ry + 12f, 0x80FFFFFF);
            return 22f + 22f;
        }
        for (ModContainer mod : mods) {
            String id = mod.getMetadata().getId();
            Known k = KNOWN.get(id);
            float rh = 52f;
            g.round(x, ry, w, rh, 12f, 0x0AFFFFFF);
            g.stroke(x, ry, w, rh, 12f, Math.max(1f, g.d.u), 0x12FFFFFF);
            g.round(x + 12f, ry + 9f, 34f, 34f, 9f, 0x12FFFFFF);
            Icons.draw(g, k != null ? k.icon() : "puzzle", x + 20f, ry + 17f, 18f, 0xBFFFFFFF, Icons.FILL, 0);
            String noteKey = "sol.mods." + id + ".note";
            String note = Widgets.has(noteKey) ? SolMenu.tr(noteKey)
                    : SolMenu.tr(kind == null ? "sol.menu.mods.nothing_to_set" : "sol.menu.mods.works_as_is");
            float actionsW;
            float cy = ry + rh / 2f;
            if (kind == Kind.OVERLAP) {
                // The installed mod keeps the job until Sol is picked (D7).
                String choice = SolConfig.get().overlaps.getOrDefault(id, "mod");
                String[] options = {SolMenu.tr("sol.menu.mods.by_sol"), mod.getMetadata().getName()};
                actionsW = ui.segmented(tk, x + w - 12f, cy, options, choice.equals("sol") ? 0 : 1, i -> {
                    SolConfig.get().overlaps.put(id, i == 0 ? "sol" : "mod");
                    SolConfig.save();
                    menu.toast(SolMenu.tr("sol.notify.overlap", options[i]));
                });
            } else if (id.equals("iris")) {
                // The shaders switch: Iris's own setting.
                boolean on = Compat.isShaderPackActive();
                ui.smallSwitch(tk, x + w - 12f - 30f, cy - 9f, on, () -> Compat.setShadersEnabled(!on));
                actionsW = 30f + g.width(s, SolMenu.tr("sol.menu.mods.shaders")) + 8f;
                g.textRight(s, SolMenu.tr("sol.menu.mods.shaders"), x + w - 12f - 30f - 8f, cy, 0xB3FFFFFF);
            } else if (k != null && k.kind() == Kind.STYLED) {
                boolean on = !"off".equals(SolConfig.get().overlaps.getOrDefault("style:" + id, "on"));
                ui.smallSwitch(tk, x + w - 12f - 30f, cy - 9f, on, () -> {
                    SolConfig.get().overlaps.put("style:" + id, on ? "off" : "on");
                    SolConfig.save();
                });
                actionsW = 30f + g.width(s, SolMenu.tr("sol.menu.mods.sol_style")) + 8f;
                g.textRight(s, SolMenu.tr("sol.menu.mods.sol_style"), x + w - 12f - 30f - 8f, cy, 0xB3FFFFFF);
            } else {
                String pillKey = "sol.mods." + id + ".pill";
                String pill = Widgets.has(pillKey) ? SolMenu.tr(pillKey)
                        : SolMenu.tr(kind == null ? "sol.menu.mods.alongside_pill" : "sol.menu.mods.as_it_is");
                actionsW = ui.pill(tk, x + w - 12f, cy, pill, k != null);
            }
            // Its own screen: Controlling's key binds, Screenshot Viewer's screenshots, or its settings through Mod Menu.
            Runnable own = id.equals("controlling") ? () -> opened(menu, ModScreens.openKeyBinds(menu))
                    : id.equals("screenshot_viewer") ? () -> opened(menu, ModScreens.openScreenshots(menu))
                    : !id.equals("modmenu") && ModScreens.hasSettings(id) ? () -> opened(menu, ModScreens.openSettings(menu, id)) : null;
            if (own != null) {
                String icon = id.equals("controlling") ? "key" : id.equals("screenshot_viewer") ? "camera" : "gear";
                ui.tool(tk, x + w - 12f - actionsW - 10f - 26f, cy - 13f, icon, false, own);
                actionsW += 36f;
            }
            float room = w - 12f - 34f - 12f - actionsW - 24f;
            g.text(b, g.fit(b, mod.getMetadata().getName(), room), x + 58f, ry + 22f, 0xFFFFFFFF);
            g.text(s, g.fit(s, note, room), x + 58f, ry + 39f, 0x8FFFFFFF);
            ry += rh + 6f;
        }
        return ry - y;
    }

    private static void opened(SolMenu menu, boolean ok) {
        if (!ok) {
            menu.toast(SolMenu.tr("sol.notify.no_screen"));
        }
    }

    @Override
    public boolean scroll(float amount) {
        return scroll.wheel(amount);
    }
}
