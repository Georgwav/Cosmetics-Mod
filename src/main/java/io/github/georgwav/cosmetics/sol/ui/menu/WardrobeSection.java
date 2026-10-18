package io.github.georgwav.cosmetics.sol.ui.menu;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.ui.wardrobe.WardrobeScreen;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.Mannequin;
import io.github.georgwav.cosmetics.ui.PreviewParticles;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import io.github.georgwav.cosmetics.ui.TilePictures;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The menu's Wardrobe: what you wear, at a glance and a click. You on the left, turning as you drag, with your saved
 * outfits under you; the cosmetics on the right by kind, with a search. Click a tile to put it on or take it off,
 * point at one to try it on, and its gear opens its page in the Wardrobe. Cosmetics and Outfits in the header open
 * the full Wardrobe there.
 */
final class WardrobeSection implements Section {
    private static final float PREVIEW_W = 236f, TILE = 118f, GAP = 10f;
    /** The kinds a chip can show, in the Wardrobe's order. */
    private static final WardrobeScreen.View[] KINDS = {WardrobeScreen.View.WEARING, WardrobeScreen.View.ALL,
            WardrobeScreen.View.CAPES, WardrobeScreen.View.WINGS, WardrobeScreen.View.HATS, WardrobeScreen.View.FACE,
            WardrobeScreen.View.BODY, WardrobeScreen.View.BACK, WardrobeScreen.View.SHIELDS, WardrobeScreen.View.AURAS,
            WardrobeScreen.View.PETS, WardrobeScreen.View.LIMBS};

    private WardrobeScreen.View kind = WardrobeScreen.View.ALL;
    private final Scroll scroll = new Scroll();
    private final TextField search = new TextField(64, q -> scroll.reset());
    private TilePictures pictures;
    private final PreviewParticles particles = new PreviewParticles();
    private Mannequin mannequin;
    /** The cosmetic pointed at (tried on in the preview), or null. */
    private String tryingOn;
    // The preview: how it's turned, and where it is in the game's GUI pixels (set while drawing, used by game()).
    private float spin = 25f, shownSpin = 25f, pitch, dragX, dragY;
    private long lastDrag;
    private int px1, py1, px2, py2;
    private boolean previewShown;

    @Override
    public void draw(SolMenu menu, Gfx g, Ui ui, Tokens tk, float x, float y, float w, float h) {
        previewShown = false;
        float headY = y + 15f;
        SolFont h2 = g.font(SolFont.Family.SEMIBOLD, 22f);
        String title = SolMenu.tr("sol.section.wardrobe");
        g.textMid(h2, title, x, headY, 0xFFFFFFFF);
        // The full Wardrobe, on its cosmetics or its outfits.
        float bx = x + g.width(h2, title) + 16f;
        SolFont bf = g.font(SolFont.Family.MEDIUM, 12f);
        for (String what : new String[]{"cosmetics", "outfits"}) {
            String label = SolMenu.tr("sol.menu.wardrobe." + what) + "  ↗";
            float bw = g.width(bf, label) + 20f;
            ui.button(tk, bx + bw, headY, label, false,
                    () -> ScreenOpener.open(WardrobeScreen.on(menu, what.equals("outfits") ? "outfits" : "all")));
            bx += bw + 8f;
        }
        float sw = Math.min(260f, w * 0.26f);
        search.draw(g, ui, tk, x + w - sw, headY - 13f, sw, 26f, "search", SolMenu.tr("sol.menu.wardrobe.search"), menu.dt());

        float top = y + 40f, bottom = y + h - 4f;
        List<String> worn = CosmeticsConfig.get().localEquipped();
        List<BakedCosmetic> all = WardrobeScreen.wardrobe();
        if (all.isEmpty()) {
            g.text(g.font(SolFont.Family.MEDIUM, 13f), SolMenu.tr("sol.menu.wardrobe.empty"), x, top + 18f, 0x99FFFFFF);
            tryOn(null);
            return;
        }
        String hovered = grid(menu, g, ui, tk, all, worn, x + PREVIEW_W + 16f, top, w - PREVIEW_W - 16f, bottom - top);
        preview(menu, g, ui, tk, worn, x, top, PREVIEW_W, bottom - top);
        tryOn(hovered);
    }

    /** Wears what's pointed at for the preview only (nothing while nothing is pointed at). */
    private void tryOn(String id) {
        if (id == null ? tryingOn != null : !id.equals(tryingOn)) {
            tryingOn = id;
        }
        EquippedCosmetics.preview(tryingOn);
    }

    // ------------------------------------------------------------------ the cosmetics

    /** The kind chips and the tiles; returns the cosmetic pointed at that you don't wear yet (to try on), or null. */
    private String grid(SolMenu menu, Gfx g, Ui ui, Tokens tk, List<BakedCosmetic> all, List<String> worn, float x, float y,
                        float w, float h) {
        // The kinds that hold something (and Wearing and All always), each with its count.
        float cx = x;
        for (WardrobeScreen.View v : KINDS) {
            int n = 0;
            for (BakedCosmetic c : all) {
                if (v.includes(c, worn)) {
                    n++;
                }
            }
            if (n == 0 && v != WardrobeScreen.View.ALL && v != WardrobeScreen.View.WEARING) {
                if (kind == v) {
                    kind = WardrobeScreen.View.ALL;
                }
                continue;
            }
            String label = v.label() + "  " + n;
            float cw = g.width(g.font(SolFont.Family.MEDIUM, 12f), label) + 22f;
            if (cx + cw > x + w) {
                break;
            }
            WardrobeScreen.View pick = v;
            cx += ui.chip(tk, cx, y + 12f, label, kind == v, () -> {
                kind = pick;
                scroll.reset();
            }) + 6f;
        }

        String q = search.text().strip().toLowerCase(Locale.ROOT);
        List<BakedCosmetic> list = new ArrayList<>();
        for (BakedCosmetic c : all) {
            if (kind.includes(c, worn) && (q.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(q)
                    || c.id().toLowerCase(Locale.ROOT).contains(q))) {
                list.add(c);
            }
        }
        list.sort(Comparator.comparing((BakedCosmetic c) -> !worn.contains(c.id())).thenComparing(BakedCosmetic::name));
        float gy = y + 32f, gh = h - 32f;
        if (list.isEmpty()) {
            String none = q.isEmpty() ? SolMenu.tr("sol.menu.wardrobe.none_worn") : SolMenu.tr("sol.menu.wardrobe.nothing", search.text().strip());
            g.text(g.font(SolFont.Family.MEDIUM, 13f), none, x, gy + 22f, 0x99FFFFFF);
            return null;
        }
        int cols = Math.max(3, (int) ((w + GAP) / (TILE + GAP)));
        float tw = (w - GAP * (cols - 1) - 8f) / cols, pic = tw - 16f, th = pic + 34f;
        if (pictures == null) {
            pictures = new TilePictures();
        }
        pictures.beginFrame();
        SolFont nf = g.font(SolFont.Family.SEMIBOLD, 12f);
        String hovered = null;
        g.clip(x - 4f, gy - 4f, x + w + 4f, gy + gh);
        float top = gy - scroll.value();
        for (int i = 0; i < list.size(); i++) {
            BakedCosmetic c = list.get(i);
            float tx = x + (i % cols) * (tw + GAP), ty = top + 2f + (i / cols) * (th + GAP);
            if (ty + th < gy - 8f || ty > gy + gh + 8f) {
                continue;
            }
            boolean on = worn.contains(c.id());
            boolean inView = ty + th > gy && ty < gy + gh;
            boolean hover = inView && ui.over(tx, ty, tw, th);
            if (hover && !on) {
                hovered = c.id();
            }
            if (inView) {
                ui.click(tx, ty, tw, th, () -> {
                    search.focus(false);
                    WardrobeScreen.setEquipped(c.id(), !CosmeticsConfig.get().localEquipped().contains(c.id()));
                    CosmeticsConfig.save();
                });
            }
            if (on) {
                g.glow(tx, ty, tw, th, 12f, 18f, Colors.withAlpha(tk.glow, 0.22f));
            }
            g.round(tx, ty, tw, th, 12f, hover ? Colors.mix(tk.panel, 0xFFFFFFFF, 0.06f) : tk.panel);
            g.stroke(tx, ty, tw, th, 12f, Math.max(1f, g.d.u) * (on ? 1.5f : 1f), on ? tk.accent : hover ? 0x40FFFFFF : 0x1AFFFFFF);
            // The art: the cosmetic itself, turning while pointed at, on a soft spot of light.
            float ax = tx + 8f, ay = ty + 8f;
            g.radialB.set(g.d.sx(ax + pic / 2f), g.d.sy(ay + pic * 0.62f), pic * 0.62f * g.d.u)
                    .stop(0f, Colors.withAlpha(on ? tk.glow : 0xFFFFFFFF, on ? 0.2f : 0.08f)).stop(1f, 0);
            g.round(ax, ay, pic, pic, 9f, g.radialB);
            int s = Math.round(pic * g.d.u);
            int sx = Math.round(g.d.sx(ax + pic / 2f)) - s / 2, sy = Math.round(g.d.sy(ay + pic / 2f)) - s / 2;
            if (!pictures.draw(g.game(), c, sx, sy, s, hover)) {
                Icons.draw(g, iconOf(c), ax + pic / 2f - 14f, ay + pic / 2f - 14f, 28f, 0x66FFFFFF, Icons.FILL, 0);
            }
            g.layer();
            g.textCentre(nf, g.fit(nf, c.name(), tw - 14f), tx + tw / 2f, ty + 8f + pic + 15f, on ? 0xFFFFFFFF : 0xD9FFFFFF);
            if (on) {
                // Worn: a check in the corner.
                g.round(tx + 6f, ty + 6f, 20f, 20f, 10f, tk.accent);
                Icons.draw(g, "check", tx + 10f, ty + 10f, 12f, tk.onAccent, Icons.FILL, 0);
            }
            if (hover) {
                // Its page in the Wardrobe (registered after the tile, so the gear takes its own clicks).
                ui.tool(tk, tx + tw - 32f, ty + 6f, "gear", false,
                        () -> ScreenOpener.open(WardrobeScreen.on(menu, "cosmetic:" + c.id())));
            }
        }
        g.endClip();
        int rows = (list.size() + cols - 1) / cols;
        scroll.setMax(rows * (th + GAP) + 4f - gh);
        scroll.step(menu.dt());
        scroll.bar(g, x + w + 4f, gy, gh);
        return hovered;
    }

    private static String iconOf(BakedCosmetic c) {
        if (c.wings() != null || "wings".equals(c.kind())) {
            return "wings";
        }
        CosmeticSlot s = c.slot();
        return switch (s) {
            case CAPE -> "cape";
            case HEAD -> "crown";
            case FACE -> "eye";
            case AURA -> "flame";
            case PET -> "heart";
            case SHIELD -> "shield";
            default -> "shirt";
        };
    }

    // ------------------------------------------------------------------ you

    /** You on a glass panel, turning as you drag; your outfits under you, one click to wear. */
    private void preview(SolMenu menu, Gfx g, Ui ui, Tokens tk, List<String> worn, float x, float y, float w, float h) {
        g.glow(x, y, w, h, 16f, 26f, Colors.withAlpha(tk.glow, 0.12f));
        g.round(x, y, w, h, 16f, Colors.withAlpha(tk.panel, 0.6f));
        g.radialB.set(g.d.sx(x + w / 2f), g.d.sy(y + h * 0.5f), w * 0.8f * g.d.u).stop(0f, Colors.withAlpha(tk.glow, 0.16f)).stop(1f, 0);
        g.round(x, y, w, h, 16f, g.radialB);
        g.stroke(x, y, w, h, 16f, Math.max(1f, g.d.u), tk.rim);

        // The outfits, under you: wear one with a click, or save what you wear as a new one.
        CosmeticsConfig cfg = CosmeticsConfig.get();
        SolFont lf = g.font(SolFont.Family.MONO, 10.5f), cf = g.font(SolFont.Family.MEDIUM, 12f);
        float rowY = y + h - 16f;
        String caption = tryingOn != null && CosmeticRegistry.get(tryingOn) != null
                ? SolMenu.tr("sol.menu.wardrobe.trying", CosmeticRegistry.get(tryingOn).name())
                : SolMenu.tr("sol.menu.wardrobe.status", worn.size());
        g.textCentre(cf, g.fit(cf, caption, w - 20f), x + w / 2f, rowY, tryingOn != null ? tk.accent : 0x99FFFFFF);
        float oy = rowY - 34f;
        float ox = x + 12f, oRight = x + w - 12f;
        List<CosmeticsConfig.Outfit> outfits = cfg.savedOutfits;
        String save = "+ " + SolMenu.tr("sol.menu.wardrobe.save_short");
        // Up to two rows of outfit chips, the save chip last.
        float rowH = 30f;
        float lineX = ox;
        int line = 0;
        List<Object> chips = new ArrayList<>(outfits);
        chips.add(save);
        float startY = oy - rowH * (fitsOneRow(g, cf, chips, oRight - ox) ? 0 : 1);
        g.text(lf, SolMenu.tr("sol.menu.wardrobe.outfits").toUpperCase(Locale.ROOT), ox, startY - 22f, 0x80FFFFFF);
        for (Object o : chips) {
            String label = o instanceof CosmeticsConfig.Outfit out ? out.name : (String) o;
            float cw = g.width(cf, label) + 22f;
            if (lineX + cw > oRight && lineX > ox) {
                if (++line > 1) {
                    break;
                }
                lineX = ox;
            }
            float cy = startY + line * rowH;
            if (o instanceof CosmeticsConfig.Outfit out) {
                boolean wearing = cfg.wearsOutfit(out);
                lineX += ui.chip(tk, lineX, cy, g.fit(cf, label, oRight - ox - 22f), wearing, () -> {
                    cfg.wearOutfit(out);
                    EquippedCosmetics.invalidate();
                    CosmeticsConfig.save();
                    menu.toast(SolMenu.tr("sol.notify.outfit", out.name));
                }) + 6f;
            } else {
                lineX += ui.chip(tk, lineX, cy, label, false, () -> {
                    CosmeticsConfig.Outfit made = cfg.saveOutfit(SolMenu.tr("sol.menu.wardrobe.outfit_prefix"));
                    CosmeticsConfig.save();
                    menu.toast(SolMenu.tr("sol.notify.outfit_saved", made.name));
                }) + 6f;
            }
        }

        // You: dragging turns you (a drag that pauses starts again from where the pointer is).
        float py = y + 10f, pb = startY - 34f;
        ui.drag(x, py, w, pb - py, (mx, my) -> {
            long now = System.nanoTime();
            if (now - lastDrag > 250_000_000L) {
                dragX = mx;
                dragY = my;
            }
            lastDrag = now;
            spin += (mx - dragX) * 1.6f;
            pitch = Math.max(-60f, Math.min(60f, pitch - (my - dragY) * 1.2f));
            dragX = mx;
            dragY = my;
        });
        // The floor you stand on.
        g.radial(x + w / 2f, pb - 8f, 70f, 12f, g.radialB.set(0, 0, 1).stop(0f, 0x33000000).stop(1f, 0), 32);
        double gs = Minecraft.getInstance().getWindow().getGuiScale();
        px1 = (int) Math.round(g.d.sx(x + 8f) / gs);
        px2 = (int) Math.round(g.d.sx(x + w - 8f) / gs);
        py1 = (int) Math.round(g.d.sy(py) / gs);
        py2 = (int) Math.round(g.d.sy(pb) / gs);
        previewShown = py2 - py1 > 20;
    }

    private static boolean fitsOneRow(Gfx g, SolFont f, List<Object> chips, float room) {
        float wsum = 0f;
        for (Object o : chips) {
            String label = o instanceof CosmeticsConfig.Outfit out ? out.name : (String) o;
            wsum += g.width(f, label) + 28f;
        }
        return wsum - 6f <= room;
    }

    @Override
    public void game(SolMenu menu, Canvas c, int mouseX, int mouseY, float dt) {
        if (!previewShown) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BakedCosmetic shown = tryingOn != null ? CosmeticRegistry.get(tryingOn) : null;
        // Capes, wings and backpacks are worn on the back: turn round to show them.
        boolean back = shown != null && (shown.slot() == CosmeticSlot.CAPE || shown.slot() == CosmeticSlot.BACK || shown.wings() != null);
        if (lastDrag == 0L || System.nanoTime() - lastDrag > 4_000_000_000L) {
            // Left alone, you turn slowly so every side shows.
            spin += dt * 14f;
        }
        float target = spin + (back ? 180f : 0f);
        shownSpin += (target - shownSpin) * Math.min(1f, dt * 10f);
        if (mc.player == null) {
            if (mannequin == null) {
                mannequin = new Mannequin("menu_wardrobe", 256);
            }
            int side = Math.min(px2 - px1, py2 - py1);
            mannequin.draw(c, (px1 + px2 - side) / 2, (py1 + py2 - side) / 2, side, -shownSpin, 8f + pitch, false);
            return;
        }
        int scale = Math.max(16, (int) ((py2 - py1) / 2.9f));
        c.entityTurned(px1, py1, px2, py2, scale, 0.0625f, mouseX, mouseY, mc.player, shownSpin, pitch);
        // The auras' particles, turned the way you are.
        particles.update(EquippedCosmetics.forPlayer(mc.player.getUUID(), true), 180f + shownSpin);
        float feetY = mc.player.getBbHeight() / mc.player.getScale() / 2f + 0.0625f;
        org.joml.Quaternionf pose = new org.joml.Quaternionf().rotateZ((float) Math.PI).rotateX((float) Math.toRadians(pitch));
        c.clip(px1, py1, px2, py2);
        particles.draw(c, (px1 + px2) / 2f, (py1 + py2) / 2f, scale, feetY, pose);
        c.endClip();
    }

    @Override
    public boolean typed(String text) {
        return search.typed(text);
    }

    @Override
    public boolean key(int key, boolean ctrl) {
        return search.key(key, ctrl);
    }

    @Override
    public boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    @Override
    public void opened() {
        search.focus(false);
    }

    @Override
    public void left() {
        tryOn(null);
        previewShown = false;
    }

    @Override
    public void closed() {
        tryOn(null);
        previewShown = false;
        if (pictures != null) {
            pictures.close();
            pictures = null;
        }
        if (mannequin != null) {
            mannequin.release();
            mannequin = null;
        }
    }
}
