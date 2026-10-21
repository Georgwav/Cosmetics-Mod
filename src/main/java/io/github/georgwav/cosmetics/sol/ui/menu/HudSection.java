package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.hud.HudModule;
import io.github.georgwav.cosmetics.client.hud.VanillaHud;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.config.SolProfiles;
import io.github.georgwav.cosmetics.sol.hud.SolHud;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.ModuleIcons;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.MiniSky;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HUD: every element of the HUD in one place. On the left the list: your HUD's elements (Stats, Coordinates, Music,
 * Lyrics, ...) each with its switch, then the game's own parts (hotbar, hearts, hunger, boss bars, scoreboard, ...)
 * each with whether Sol draws it in its look. In the middle a live picture of the layout over the theme's sky, the
 * element picked lit in the accent (click a box to pick it). On the right the picked element's settings: its look,
 * size and background for your elements, Sol UI's settings for that part of the game's HUD. "Edit layout" opens the
 * HUD editor, where everything snaps to edges, the middle and other boxes.
 */
final class HudSection implements Section {
    private static final float LIST_W = 252f, ROW_H = 26f, MINI_W = 420f, MINI_H = 236f, COL_GAP = 20f;

    /** One element of the list: a HUD module, or a part of the game's HUD with the Sol UI settings for it. */
    private record Item(String key, Module module, SolHud.Part part, Set<String> groups) {
        String label() {
            return module != null ? module.name() : SolMenu.tr("sol.menu.hud.part." + part.id);
        }
    }

    private static final List<Item> PARTS = List.of(
            part(SolHud.Part.HOTBAR, "hotbar"), part(SolHud.Part.HEALTH, "bars"), part(SolHud.Part.ARMOR, "bars"),
            part(SolHud.Part.FOOD, "bars"), part(SolHud.Part.AIR, "bars"), part(SolHud.Part.MOUNT, "bars"),
            part(SolHud.Part.XP, "bars"), part(SolHud.Part.BOSS, "boss_bars"), part(SolHud.Part.SIDEBAR, "scoreboard"),
            part(SolHud.Part.TAB, "tab_list"), part(SolHud.Part.ACTION_BAR, "titles"), part(SolHud.Part.MODULES, null),
            part(SolHud.Part.CHAT, null));

    /** A part of the game's HUD and the group of Sol UI's settings for it (the hotbar's and the bars' go together). */
    private static Item part(SolHud.Part p, String group) {
        Set<String> groups = group == null ? Set.of() : "hotbar".equals(group) || "bars".equals(group)
                ? Set.of("hotbar", "bars") : Set.of(group);
        return new Item("part:" + p.id, null, p, groups);
    }

    private SolTexture sky;
    private String skyFor;
    private int skyGeneration = -1;
    private final Scroll listScroll = new Scroll();
    private String picked = "module:stats";
    /** The settings column of each element, made when first picked (so its scroll and open pickers stay). */
    private final Map<String, ModulePage> pages = new HashMap<>();
    /** Where the list and the settings are this frame, for the wheel. */
    private float listX0, listX1, setX0;
    private float mouseX;

    private List<Item> items() {
        List<Item> out = new ArrayList<>();
        for (HudModule m : Modules.hud()) {
            if (m.available()) {
                out.add(new Item("module:" + m.id, m, null, null));
            }
        }
        out.addAll(PARTS);
        return out;
    }

    private Item pickedItem(List<Item> items) {
        for (Item i : items) {
            if (i.key().equals(picked)) {
                return i;
            }
        }
        return items.get(0);
    }

    private ModulePage page(Item item) {
        return pages.computeIfAbsent(item.key(), k -> {
            if (item.module() != null) {
                return new ModulePage(item.module());
            }
            if (item.part() == SolHud.Part.CHAT) {
                return new ModulePage(Modules.SOL_CHAT);
            }
            return item.groups().isEmpty() ? null : new ModulePage(Modules.SOL_UI, item.groups());
        });
    }

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        mouseX = ui.mx;
        SolConfig cfg = SolConfig.get();
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.PIXEL_BOLD, 15f), mono = g.font(SolFont.Family.PIXEL, 11f);
        String title = SolMenu.tr("sol.section.hud");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        g.textMid(mono, SolMenu.tr("sol.menu.hud.count", SolProfiles.active()), x + g.width(h2, title) + 14f, headY, 0x8CFFFFFF);
        float bw = ui.button(tk, x + w, headY, SolMenu.tr("sol.menu.hud.edit"), true,
                () -> ScreenOpener.open(new io.github.georgwav.cosmetics.sol.ui.screens.HudEditor(menu)));
        // Sol UI: the game's HUD in Sol's look, all of it at once.
        SolFont sf = g.font(SolFont.Family.MEDIUM, 13f);
        String solUi = SolMenu.tr("sol.menu.hud.sol_ui");
        float swX = x + w - bw - 22f - 30f;
        ui.smallSwitch(tk, swX, headY - 9f, cfg.solUi, () -> {
            cfg.solUi = !cfg.solUi;
            SolConfig.save();
        });
        g.textRight(sf, solUi, swX + 30f - Ui.SWITCH_W - 10f, headY, 0xCCFFFFFF);

        List<Item> items = items();
        Item sel = pickedItem(items);
        float top = y + 40f, bottom = y + h;
        list(menu, g, ui, tk, cfg, items, sel, x, top, bottom);
        float mx0 = x + LIST_W + COL_GAP;
        mini(menu, g, ui, tk, items, sel, mx0, top);
        float sx = mx0 + MINI_W + COL_GAP;
        listX0 = x;
        listX1 = x + LIST_W;
        setX0 = sx;
        settings(menu, g, ui, tk, sel, sx, top, x + w - 8f, bottom);
    }

    // ------------------------------------------------------------------ the list

    private void list(SolMenu menu, Gfx g, Ui ui, Tokens tk, SolConfig cfg, List<Item> items, Item sel, float x, float top, float bottom) {
        Pix.fill(g, x, top, LIST_W, bottom - top, 0x0AFFFFFF);
        Pix.frame(g, x, top, LIST_W, bottom - top, 0x14FFFFFF);
        g.clip(x, top + 1f, x + LIST_W, bottom - 1f);
        float y = top + 6f - listScroll.value();
        SolFont f = g.font(SolFont.Family.MEDIUM, 13f), head = g.font(SolFont.Family.SEMIBOLD, 11f);
        boolean partsHead = false;
        y = header(g, head, SolMenu.tr("sol.menu.hud.yours"), x, y);
        for (Item it : items) {
            if (it.part() != null && !partsHead) {
                partsHead = true;
                y = header(g, head, SolMenu.tr("sol.menu.hud.game"), x, y + 4f);
            }
            boolean picked = it == sel;
            boolean on = it.module() != null ? it.module().enabled() : cfg.solUi && !cfg.solUiOff.contains(it.part().id);
            float rx = x + 6f, rw = LIST_W - 12f;
            boolean hover = ui.over(rx, y, rw, ROW_H);
            if (picked || hover) {
                Pix.fill(g, rx, y, rw, ROW_H, picked ? Colors.withAlpha(tk.accent, 0.16f) : 0x0FFFFFFF);
            }
            if (picked) {
                Pix.fill(g, rx, y + 6f, 2.5f, ROW_H - 12f, tk.accent);
            }
            ui.click(rx, y, rw, ROW_H, () -> this.picked = it.key());
            String icon = it.module() != null ? ModuleIcons.module(it.module().id) : ModuleIcons.module("sol_ui");
            Icons.draw(g, icon, rx + 9f, y + 6f, 14f, on ? tk.accent : 0x66FFFFFF, Icons.FILL, 0);
            g.textMid(f, g.fit(f, it.label(), rw - 76f), rx + 30f, y + ROW_H / 2f, on ? 0xFFFFFFFF : 0x99FFFFFF);
            ui.smallSwitch(tk, rx + rw - 36f, y + ROW_H / 2f - 9f, on, () -> {
                if (it.module() != null) {
                    ModulesSection.toggle(menu, it.module());
                } else {
                    toggle(cfg, it.part());
                }
            });
            y += ROW_H + 2f;
        }
        g.endClip();
        listScroll.setMax(y + listScroll.value() - bottom + 8f);
        listScroll.step(menu.dt());
        listScroll.bar(g, x + LIST_W - 4f, top + 4f, bottom - top - 8f);
    }

    private static float header(Gfx g, SolFont f, String text, float x, float y) {
        g.textMid(f, text.toUpperCase(java.util.Locale.ROOT), x + 14f, y + 11f, 0x80FFFFFF);
        return y + 22f;
    }

    /** Hands a part back to the game, or gives it to Sol (turning Sol UI on if it was off). */
    private static void toggle(SolConfig cfg, SolHud.Part part) {
        boolean give = !cfg.solUi || cfg.solUiOff.contains(part.id);
        cfg.solUi = true;
        for (SolHud.Part p : part == SolHud.Part.XP ? new SolHud.Part[]{SolHud.Part.XP, SolHud.Part.LEVEL} : new SolHud.Part[]{part}) {
            cfg.solUiOff.remove(p.id);
            if (!give) {
                cfg.solUiOff.add(p.id);
            }
        }
        SolConfig.save();
    }

    // ------------------------------------------------------------------ the layout

    private void mini(SolMenu menu, Gfx g, Ui ui, Tokens tk, List<Item> items, Item sel, float x, float my) {
        var e = ThemeState.baseEntry();
        if (sky == null || sky.released() || !e.id().equals(skyFor) || skyGeneration != ThemeState.tokens().generation) {
            if (sky != null) {
                sky.release();
            }
            skyFor = e.id();
            skyGeneration = ThemeState.tokens().generation;
            sky = SolTexture.of("hud_sky", 840, 472, true, MiniSky.render(e.base(), e.id(), 840, 472, 2f).toArgb(null));
        }
        g.clip(x, my, x + MINI_W, my + MINI_H);
        g.image(sky, x, my, MINI_W, MINI_H, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        Minecraft mc = menu.mc();
        int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
        float k = Math.min(MINI_W / Math.max(1, sw), MINI_H / Math.max(1, sh));
        // The middle guides the editor snaps to.
        g.rect(x + MINI_W / 2f, my, 1f, MINI_H, Colors.withAlpha(tk.accent, 0.3f));
        g.rect(x, my + MINI_H / 2f, MINI_W, 1f, Colors.withAlpha(tk.accent, 0.1f));
        SolFont bf = g.font(SolFont.Family.MEDIUM, 9f);
        float[] b = new float[4];
        for (Item it : items) {
            if (!box(it, mc, sw, sh, b)) {
                continue;
            }
            boolean picked = it == sel;
            float bx = x + b[0] * k, by = my + b[1] * k, bw = Math.max(14f, b[2] * k), bh = Math.max(8f, b[3] * k);
            if (picked) {
                Pix.frame(g, bx - 1f, by - 1f, bw + 2f, bh + 2f, tk.accent);
            }
            Pix.fill(g, bx, by, bw, bh, picked ? Colors.withAlpha(tk.accent, 0.28f) : 0x59000000);
            if (picked) {
                Pix.frame(g, bx, by, bw, bh, tk.accent);
            } else {
                io.github.georgwav.cosmetics.sol.ui.kit.Widgets.dashed(g, bx, by, bw, bh, 4f, Colors.withAlpha(tk.rim, 0.7f));
            }
            if (bw > 30f && bh > 9f) {
                g.textCentre(bf, g.fit(bf, it.label(), bw - 4f), bx + bw / 2f, by + bh / 2f, picked ? 0xFFFFFFFF : 0xB3FFFFFF);
            }
            ui.click(bx, by, bw, bh, () -> this.picked = it.key());
        }
        g.endClip();
        Pix.frame(g, x, my, MINI_W, MINI_H, 0x24FFFFFF);
        // Under the picture: what's picked, and how to move things.
        SolFont cap = g.font(SolFont.Family.MEDIUM, 12f);
        float ty = my + MINI_H + 18f;
        g.textMid(g.font(SolFont.Family.SEMIBOLD, 14f), sel.label(), x, ty, 0xFFFFFFFF);
        ty += 20f;
        for (String line : wrap(g, cap, SolMenu.tr(sel.module() != null ? "sol.menu.hud.hint_module" : "sol.menu.hud.hint_part"), MINI_W)) {
            g.textMid(cap, line, x, ty, 0x8CFFFFFF);
            ty += 16f;
        }
    }

    /** Where an element sits on the screen (GUI pixels) into {@code out}; false when it isn't shown. */
    private static boolean box(Item it, Minecraft mc, int sw, int sh, float[] out) {
        if (it.module() instanceof HudModule m) {
            if (!m.enabled()) {
                return false;
            }
            out[0] = m.x(sw, mc);
            out[1] = m.y(sh, mc);
            out[2] = m.width(mc) * m.scale();
            out[3] = m.height(mc) * m.scale();
            return out[2] > 0f && out[3] > 0f;
        }
        VanillaHud.Part vp = switch (it.part()) {
            case HOTBAR -> VanillaHud.Part.HOTBAR;
            case HEALTH -> VanillaHud.Part.HEALTH;
            case ARMOR -> VanillaHud.Part.ARMOR;
            case FOOD, MOUNT -> VanillaHud.Part.FOOD;
            case AIR -> VanillaHud.Part.AIR;
            case ACTION_BAR -> VanillaHud.Part.ACTION_BAR;
            case BOSS -> VanillaHud.Part.BOSS_BAR;
            case SIDEBAR -> VanillaHud.Part.SCOREBOARD;
            default -> null;
        };
        float[] b;
        if (vp != null) {
            b = vp.box(sw, sh);
            if (it.part() == SolHud.Part.XP) {
                b = new float[]{sw / 2f - 91f, sh - 29f, 182f, 5f};
            }
            // The hotbar and the bars over it at their size (Sol UI's), round the bottom middle.
            if (SolHud.draws(SolHud.Part.HOTBAR) && (vp == VanillaHud.Part.HOTBAR || vp == VanillaHud.Part.HEALTH
                    || vp == VanillaHud.Part.ARMOR || vp == VanillaHud.Part.FOOD || vp == VanillaHud.Part.AIR)) {
                float s = SolHud.bottomScale();
                b = new float[]{sw / 2f + (b[0] - sw / 2f) * s, sh + (b[1] - sh) * s, b[2] * s, b[3] * s};
            }
            b[0] += VanillaHud.dx(vp);
            b[1] += VanillaHud.dy(vp);
        } else if (it.part() == SolHud.Part.XP) {
            b = new float[]{sw / 2f - 91f, sh - 29f, 182f, 5f};
        } else if (it.part() == SolHud.Part.TAB) {
            b = new float[]{sw / 2f - 100f, 10f, 200f, 70f};
        } else if (it.part() == SolHud.Part.CHAT) {
            b = new float[]{2f, sh - 40f - 90f, 200f, 90f};
        } else {
            return false;
        }
        System.arraycopy(b, 0, out, 0, 4);
        return true;
    }

    // ------------------------------------------------------------------ the settings

    private void settings(SolMenu menu, Gfx g, Ui ui, Tokens tk, Item sel, float x, float top, float x1, float bottom) {
        ModulePage p = page(sel);
        if (p == null) {
            SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
            float y = top + 14f;
            for (String line : wrap(g, f, SolMenu.tr("sol.menu.hud.modules_look"), x1 - x)) {
                g.textMid(f, line, x, y, 0xB3FFFFFF);
                y += 18f;
            }
            return;
        }
        p.settings(menu, g, ui, tk, x, top, x1, bottom);
    }

    @Override
    public boolean scroll(float amount) {
        if (mouseX >= listX0 && mouseX <= listX1) {
            return listScroll.wheel(amount);
        }
        if (mouseX >= setX0) {
            ModulePage p = pages.get(picked);
            return p != null && p.scroll(amount);
        }
        return false;
    }

    @Override
    public boolean typed(String text) {
        ModulePage p = pages.get(picked);
        return p != null && p.typed(text);
    }

    @Override
    public boolean key(int key, boolean ctrl) {
        ModulePage p = pages.get(picked);
        return p != null && p.key(key, ctrl);
    }

    static List<String> wrap(Gfx g, SolFont f, String text, float width) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.length() == 0 ? word : line + " " + word;
            if (g.width(f, next) > width && line.length() > 0) {
                out.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (line.length() > 0) {
            out.add(line.toString());
        }
        return out;
    }

    @Override
    public void closed() {
        if (sky != null) {
            sky.release();
        }
        pages.values().forEach(p -> {
            if (p != null) {
                p.release();
            }
        });
        pages.clear();
    }
}
