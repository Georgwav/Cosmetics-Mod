package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.CosmeticsScreen;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import io.github.georgwav.cosmetics.ui.TilePictures;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * The Wardrobe, as the design has it: two blobs. The left one holds the kinds of cosmetics down its side and you in
 * the middle (drag to turn, Front and Back); the right one the cosmetics as tiles, from every pack or one. Clicking
 * a tile wears it or takes it off, pointing at one tries it on, its gear opens its own settings.
 */
final class WardrobePage implements HubPage {
    /** The kinds down the left, and what each holds. */
    private enum View {
        WEARING, ALL, CAPES, WINGS, HATS, FACE, BODY, BACK, SHIELDS, AURAS, PETS, LIMBS, EMOTES, OUTFITS;

        boolean includes(BakedCosmetic c, List<String> wearing) {
            return switch (this) {
                case WEARING -> wearing.contains(c.id());
                case ALL -> true;
                case CAPES -> c.slot() == CosmeticSlot.CAPE;
                case WINGS -> wings(c);
                case HATS -> c.slot() == CosmeticSlot.HEAD;
                case FACE -> c.slot() == CosmeticSlot.FACE;
                case BODY -> c.slot() == CosmeticSlot.BODY;
                case BACK -> c.slot() == CosmeticSlot.BACK && !wings(c);
                case SHIELDS -> c.slot() == CosmeticSlot.SHIELD;
                case AURAS -> c.slot() == CosmeticSlot.AURA;
                case PETS -> c.slot() == CosmeticSlot.PET;
                case LIMBS -> c.slot() == CosmeticSlot.ARM || c.slot() == CosmeticSlot.FEET;
                case EMOTES, OUTFITS -> false;
            };
        }

        private static boolean wings(BakedCosmetic c) {
            return c.wings() != null || "wings".equals(c.kind());
        }
    }

    /** Where cosmetics came from, by their pack's namespace. */
    private enum Source {
        ALL(null), BADLION("blc_import"), LUNAR("lunar_import"), NORISK("norisk_import"), BUILT_IN("cosmeticsmod"),
        OTHER(null);

        final String namespace;

        Source(String namespace) {
            this.namespace = namespace;
        }

        static Source of(BakedCosmetic c) {
            String ns = c.id().substring(0, Math.max(0, c.id().indexOf(':')));
            for (Source s : values()) {
                if (ns.equals(s.namespace)) {
                    return s;
                }
            }
            return OTHER;
        }
    }

    // The two blobs (design pixels, open): {centre x, centre y, width, height, wave seed, squareness, ripple}.
    private static final float[][] PANELS = {{328f, 412f, 616f, 640f, 0.4f, 4.2f, 5f}, {1050f, 412f, 748f, 640f, 2.1f, 4.2f, 5f}};
    private static final float NAV_X = 78f;
    private static final float NAV_W = 184f;
    private static final float NAV_TOP = 142f;
    private static final float FIG_X = 278f;
    private static final float FIG_W = 300f;
    private static final float FIG_TOP = 200f;
    private static final float FIG_H = 340f;
    private static final float GRID_X = 740f;
    private static final float GRID_RIGHT = 1362f;
    private static final float GRID_TOP = 188f;
    private static final float GRID_BOTTOM = 684f;
    private static final int COLUMNS = 5;
    private static final float TILE_GAP = 10f;
    private static final float TILE_H = 118f;

    private static View view = View.ALL;
    private static Source source = Source.ALL;
    /** How far you turned yourself round and tipped (degrees), kept while the game runs. */
    private static float spin;
    private static float pitch;

    private final HubScreen hub;
    private final TilePictures pictures = new TilePictures();
    private float shownSpin = spin;
    private float scroll;
    private float contentHeight;
    private String pointed;
    private String tryingOn;
    private float dragX = Float.NaN;
    private long dragAt;

    WardrobePage(HubScreen hub) {
        this.hub = hub;
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.screen." + key, args).getString();
    }

    void release() {
        pictures.close();
        EquippedCosmetics.preview(null);
    }

    @Override
    public float[][] panels() {
        return PANELS;
    }

    @Override
    public String title() {
        return Component.translatable("cosmeticsmod.hub.wardrobe.title").getString();
    }

    @Override
    public String line() {
        if (tryingOn != null) {
            BakedCosmetic c = CosmeticRegistry.get(tryingOn);
            if (c != null) {
                return t("preview.trying", c.name());
            }
        }
        return Component.translatable("cosmeticsmod.hub.wardrobe.wearing", CosmeticsConfig.get().localEquipped().size())
                .getString();
    }

    @Override
    public String icon() {
        return "sparkle";
    }

    @Override
    public String searchHint() {
        return t("wardrobe.search");
    }

    @Override
    public boolean overList(float x, float y) {
        return x >= GRID_X && x < GRID_RIGHT + 8f && y >= GRID_TOP && y < GRID_BOTTOM;
    }

    @Override
    public void scrollBy(double amount) {
        float max = Math.max(0f, contentHeight - (GRID_BOTTOM - GRID_TOP));
        scroll = Math.max(0f, Math.min(max, scroll - (float) amount * 60f));
    }

    // ------------------------------------------------------------------ what it holds

    private static List<BakedCosmetic> wardrobe() {
        return CosmeticRegistry.all().stream().filter(c -> !c.definition().prop()
                && (source == Source.ALL || Source.of(c) == source)).toList();
    }

    private static List<Source> sources() {
        EnumSet<Source> present = EnumSet.noneOf(Source.class);
        for (BakedCosmetic c : CosmeticRegistry.all()) {
            if (!c.definition().prop()) {
                present.add(Source.of(c));
            }
        }
        List<Source> list = new ArrayList<>();
        list.add(Source.ALL);
        list.addAll(present);
        return list;
    }

    private static void wear(String id, boolean on) {
        CosmeticsConfig config = CosmeticsConfig.get();
        CosmeticsConfig.wear(config.localEquipped(), id, on, other -> {
            BakedCosmetic c = CosmeticRegistry.get(other);
            return c != null ? c.kind() : null;
        }, config.severalOfAKind);
        EquippedCosmetics.invalidate();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void draw(Ui ui, String query) {
        pictures.beginFrame();
        if (source != Source.ALL && !sources().contains(source)) {
            source = Source.ALL;
        }
        List<String> wearing = CosmeticsConfig.get().localEquipped();
        List<BakedCosmetic> all = new ArrayList<>(wardrobe());
        all.sort(Comparator.comparing((BakedCosmetic c) -> !wearing.contains(c.id()))
                .thenComparing(BakedCosmetic::name, String.CASE_INSENSITIVE_ORDER));
        drawNav(ui, all, wearing);
        drawYou(ui, wearing);
        drawSources(ui);
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<BakedCosmetic> shown = all.stream().filter(c -> view.includes(c, wearing)
                && (needle.isEmpty() || c.name().toLowerCase(Locale.ROOT).contains(needle) || c.id().contains(needle))).toList();
        pointed = null;
        drawGrid(ui, shown, wearing, needle.isEmpty() ? null : query.trim());
        // Pointing at something you don't wear tries it on.
        tryingOn = pointed != null && !wearing.contains(pointed) ? pointed : null;
        EquippedCosmetics.preview(tryingOn);
    }

    /** The kinds of cosmetics down the left blob, each with how many there are. */
    private void drawNav(Ui ui, List<BakedCosmetic> all, List<String> wearing) {
        float y = NAV_TOP;
        for (View v : View.values()) {
            if (v == View.EMOTES) {
                y += 10f;
            }
            boolean current = v == view;
            boolean hot = ui.hover(NAV_X, y, NAV_W, 34f);
            if (current || hot) {
                ui.roundRect(NAV_X, y, NAV_W, 34f, 12f, current ? Ui.WELL : 0xFF151518);
            }
            String count = v == View.EMOTES ? String.valueOf(io.github.georgwav.cosmetics.emote.Emotes.all().size())
                    : v == View.OUTFITS ? String.valueOf(CosmeticsConfig.get().savedOutfits.size())
                    : String.valueOf(all.stream().filter(c -> v.includes(c, wearing)).count());
            float bw = Math.max(22f, ui.textWidth(count, 11f, true) + 12f);
            float bx = NAV_X + NAV_W - 8f - bw;
            ui.roundRect(bx, y + 7f, bw, 20f, 10f, current ? ui.accent : 0x14FFFFFF);
            ui.textMid(count, bx + (bw - ui.textWidth(count, 11f, true)) / 2f, y + 17f, current ? ui.ink : 0xB0FFFFFF, 11f, true);
            String name = ui.fit(t("view." + v.name().toLowerCase(Locale.ROOT)), bx - 8f - (NAV_X + 12f), Ui.SMALL, true);
            ui.textMid(name, NAV_X + 12f, y + 17f, current ? Ui.TEXT : 0xBBFFFFFF, Ui.SMALL, true);
            ui.click(NAV_X, y, NAV_W, 34f, () -> pick(v));
            y += 36f;
        }
    }

    private void pick(View v) {
        if (v == View.EMOTES) {
            ScreenOpener.open(CosmeticsScreen.emotes(hub));
            return;
        }
        if (v == View.OUTFITS) {
            ScreenOpener.open(CosmeticsScreen.outfits(hub));
            return;
        }
        view = v;
        scroll = 0f;
    }

    /** You, in the middle of the left blob: your name, what you wear, and you to turn round (Front and Back). */
    private void drawYou(Ui ui, List<String> wearing) {
        Minecraft mc = Minecraft.getInstance();
        float cx = FIG_X + FIG_W / 2f;
        String name = mc.getUser().getName();
        ui.text(ui.fit(name, FIG_W, 22f, true), cx - Math.min(FIG_W, ui.textWidth(name, 22f, true)) / 2f, 140f,
                Ui.TEXT, 22f, true);
        String worn = Component.translatable("cosmeticsmod.hub.wardrobe.wearing", wearing.size()).getString();
        ui.text(worn, cx - ui.textWidth(worn, Ui.SMALL, false) / 2f, 170f, Ui.TEXT_DIM, Ui.SMALL, false);

        // Drag to turn: the drag reports where the mouse is, so turning follows how far it moved since the last time.
        ui.drag(FIG_X, FIG_TOP, FIG_W, FIG_H, x -> {
            long now = System.nanoTime();
            if (!Float.isNaN(dragX) && now - dragAt < 150_000_000L) {
                spin += (float) (x - dragX) * 1.2f;
            }
            dragX = (float) x;
            dragAt = now;
        });
        BakedCosmetic shown = tryingOn != null ? CosmeticRegistry.get(tryingOn) : null;
        boolean back = shown != null && (shown.slot() == CosmeticSlot.CAPE || shown.slot() == CosmeticSlot.BACK
                || shown.wings() != null);
        float target = spin + (back ? 180f : 0f);
        shownSpin += (target - shownSpin) * 0.2f;
        if (mc.player != null) {
            // The game draws entities in GUI pixels: step out of the hub's screen pixels for it.
            float gs = ui.gs;
            int x0 = Math.round(ui.sx(FIG_X) / gs);
            int y0 = Math.round(ui.sy(FIG_TOP) / gs);
            int x1 = Math.round(ui.sx(FIG_X + FIG_W) / gs);
            int y1 = Math.round(ui.sy(FIG_TOP + FIG_H) / gs);
            int scale = Math.max(16, Math.round((y1 - y0) / 2.6f));
            ui.g.push();
            ui.g.scale(gs, gs);
            ui.g.entityTurned(x0, y0, x1, y1, scale, 0.0625f, ui.sx(ui.mx) / gs, ui.sy(ui.my) / gs, mc.player,
                    shownSpin, pitch);
            ui.g.pop();
        }
        String caption = shown != null ? t("preview.trying", shown.name()) : t("preview.drag");
        caption = ui.fit(caption, FIG_W, 12f, false);
        ui.text(caption, cx - ui.textWidth(caption, 12f, false) / 2f, 552f, Ui.TEXT_FAINT, 12f, false);

        // Front and Back.
        String front = Component.translatable("cosmeticsmod.hub.wardrobe.front").getString();
        String backLabel = Component.translatable("cosmeticsmod.hub.wardrobe.back").getString();
        float fw = ui.textWidth(front, Ui.SMALL, true) + 36f;
        float bw = ui.textWidth(backLabel, Ui.SMALL, true) + 36f;
        float left = cx - (fw + bw + 6f) / 2f;
        float top = 576f;
        ui.roundRect(left, top, fw + bw + 6f, 46f, 23f, 0xFF161618);
        boolean facing = Math.abs(((spin % 360f) + 360f) % 360f - 180f) > 90f;
        ui.roundRect(left + 3f + (facing ? 0f : fw), top + 3f, facing ? fw : bw, 40f, 20f, Ui.WELL);
        ui.textMid(front, left + 3f + 18f, top + 23f, Ui.TEXT, Ui.SMALL, true);
        ui.textMid(backLabel, left + 3f + fw + 18f, top + 23f, Ui.TEXT, Ui.SMALL, true);
        ui.click(left, top, fw + 3f, 46f, () -> spin = 0f);
        ui.click(left + 3f + fw, top, bw + 3f, 46f, () -> spin = 180f);
    }

    /** "From": every pack, or one. */
    private void drawSources(Ui ui) {
        String from = Component.translatable("cosmeticsmod.hub.wardrobe.from").getString();
        float x = GRID_X;
        float y = 140f;
        ui.textMid(from, x, y + 17f, 0x94FFFFFF, 12f, true);
        x += ui.textWidth(from, 12f, true) + 10f;
        for (Source s : sources()) {
            String label = Component.translatable("cosmeticsmod.hub.wardrobe.source." + s.name().toLowerCase(Locale.ROOT)).getString();
            float w = ui.textWidth(label, 12f, true) + 24f;
            if (x + w > GRID_RIGHT) {
                break;
            }
            boolean on = s == source;
            boolean hot = ui.hover(x, y, w, 34f);
            ui.roundRect(x, y, w, 34f, 17f, on ? ui.accent : hot ? 0xFF26262A : Ui.WELL);
            ui.textMid(label, x + 12f, y + 17f, on ? ui.ink : Ui.TEXT, 12f, true);
            ui.click(x, y, w, 34f, () -> {
                source = s;
                scroll = 0f;
            });
            x += w + 6f;
        }
    }

    /** The tiles: grouped by slot in All and Wearing, as one list in a kind. */
    private void drawGrid(Ui ui, List<BakedCosmetic> shown, List<String> wearing, String search) {
        ui.clip(GRID_X, GRID_TOP, GRID_RIGHT + 8f, GRID_BOTTOM);
        float y = GRID_TOP - scroll;
        float start = y;
        if (CosmeticRegistry.all().isEmpty()) {
            ui.text(ui.fit(t("wardrobe.empty"), GRID_RIGHT - GRID_X, Ui.SMALL, false), GRID_X, y + 10f, Ui.TEXT_DIM, Ui.SMALL, false);
        } else if (shown.isEmpty()) {
            String empty = search == null ? t("wardrobe.empty_view") : t("wardrobe.no_match", search);
            ui.text(ui.fit(empty, GRID_RIGHT - GRID_X, Ui.SMALL, false), GRID_X, y + 10f, Ui.TEXT_DIM, Ui.SMALL, false);
        } else if (view == View.ALL || view == View.WEARING) {
            for (CosmeticSlot slot : CosmeticSlot.values()) {
                List<BakedCosmetic> inSlot = shown.stream().filter(c -> c.slot() == slot).toList();
                if (inSlot.isEmpty()) {
                    continue;
                }
                ui.text(t("slot." + slot.id()), GRID_X + 2f, y + 6f, 0xB3FFFFFF, Ui.SMALL, true);
                y += 28f;
                y = tiles(ui, inSlot, wearing, y);
            }
        } else {
            y = tiles(ui, shown, wearing, y);
        }
        contentHeight = y - start;
        ui.endClip();
        scrollBy(0);
    }

    private float tiles(Ui ui, List<BakedCosmetic> cosmetics, List<String> wearing, float y) {
        float w = (GRID_RIGHT - GRID_X - (COLUMNS - 1) * TILE_GAP) / COLUMNS;
        for (int i = 0; i < cosmetics.size(); i++) {
            float x = GRID_X + (i % COLUMNS) * (w + TILE_GAP);
            float ty = y + (i / COLUMNS) * (TILE_H + TILE_GAP);
            if (ty + TILE_H >= GRID_TOP && ty <= GRID_BOTTOM) {
                tile(ui, cosmetics.get(i), wearing, x, ty, w);
            }
        }
        int rows = (cosmetics.size() + COLUMNS - 1) / COLUMNS;
        return y + rows * (TILE_H + TILE_GAP) + 6f;
    }

    /** One cosmetic: its picture (turning while pointed at), its name; worn ones ringed, with a dot. */
    private void tile(Ui ui, BakedCosmetic c, List<String> wearing, float x, float y, float w) {
        boolean worn = wearing.contains(c.id());
        boolean hot = ui.hover(x, y, w, TILE_H);
        if (hot) {
            pointed = c.id();
        }
        if (worn) {
            ui.roundRect(x, y, w, TILE_H, 22f, ui.accent);
            ui.roundRect(x + 2f, y + 2f, w - 4f, TILE_H - 4f, 20f, hot ? 0xFF1A1A1D : Ui.CARD);
        } else {
            ui.roundRect(x, y, w, TILE_H, 22f, hot ? 0xFF1A1A1D : Ui.CARD);
        }
        float pic = 64f;
        int px = Math.round(ui.sx(x + (w - pic) / 2f));
        int py = Math.round(ui.sy(y + 14f));
        int size = Math.round(pic * ui.u);
        if (!pictures.draw(ui.g, c, px, py, size, hot)) {
            // Nothing to picture (only particles) or not made yet: a few dots.
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + (System.nanoTime() / 1.0e9) * (hot ? 1.5 : 0.0);
                ui.circle(x + w / 2f + (float) Math.cos(a) * 16f, y + 46f + (float) Math.sin(a) * 16f, 2.5f, 0x66FFFFFF);
            }
        }
        String name = ui.fit(c.name(), w - 12f, 12f, true);
        ui.text(name, x + (w - ui.textWidth(name, 12f, true)) / 2f, y + TILE_H - 26f, worn || hot ? Ui.TEXT : 0xC8FFFFFF, 12f, true);
        if (worn) {
            ui.circle(x + 16f, y + 16f, 4.5f, ui.accent);
        }
        String id = c.id();
        if (hot || worn) {
            // The gear: this cosmetic's own settings (its click comes before the tile's, which is under it).
            ui.iconButton("gear", x + w - 21f, y + 21f, 15f, false,
                    () -> ScreenOpener.open(CosmeticsScreen.forCosmetic(hub, id)));
        }
        ui.click(x, y, w, TILE_H, () -> wear(id, !CosmeticsConfig.get().localEquipped().contains(id)));
    }
}
