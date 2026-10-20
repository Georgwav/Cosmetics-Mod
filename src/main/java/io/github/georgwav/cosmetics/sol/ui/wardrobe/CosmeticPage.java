package io.github.georgwav.cosmetics.sol.ui.wardrobe;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticOption;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.PetPosition;
import io.github.georgwav.cosmetics.render.CapeElytra;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.RecoloredTextures;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.ColorPicker;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One cosmetic's page: wear it, then every option its file allows, in the Cosmetics screen's order (style, where a
 * pet sits, elytra, colour with the picker, rainbow, colouring, saturation, hue, brightness and its own colours,
 * size and position, cloth, glow, animation, particles), and Reset this cosmetic. The controls come from
 * {@link CosmeticControls#COSMETIC}; options the file switches off don't show and are drawn as the creator set them.
 */
final class CosmeticPage {
    /** The quick picks of the colour picker (RGB), as on the Cosmetics screen; white keeps the texture's colours. */
    static final List<Integer> COLORS = List.of(0xFFFFFF, 0xFF5555, 0xFFAA33, 0xFFFF55, 0x88FF55, 0x33CC66, 0x55FFFF,
            0x5588FF, 0xAA66FF, 0xFF77CC, 0xAAAAAA, 0x444444);

    private final WardrobeScreen screen;
    private final BakedCosmetic cosmetic;
    private final String id;
    private final Scroll scroll = new Scroll();
    private final ColorPicker tint;
    private final int[] palette;
    /** Which of its own colours is being changed (-1: none), and the picker for it. */
    private int swatch = -1;
    private ColorPicker swapPicker;

    CosmeticPage(WardrobeScreen screen, BakedCosmetic cosmetic) {
        this.screen = screen;
        this.cosmetic = cosmetic;
        this.id = cosmetic.id();
        CosmeticsConfig config = CosmeticsConfig.get();
        int def = cosmetic.defaultColor();
        this.tint = new ColorPicker(() -> {
            Integer c = config.options(id).color;
            return c != null ? c : def;
        }, v -> {
            CosmeticsConfig.CosmeticOptions o = config.editOptions(id);
            // Picking the cosmetic's own colour goes back to following it; any pick stops following the theme.
            o.color = (v & 0xFFFFFF) == (def & 0xFFFFFF) ? null : v & 0xFFFFFF;
            o.themeColor = null;
        });
        // A knife has no layers: its colours are its texture's.
        this.palette = RecoloredTextures.palette(cosmetic.slot() == CosmeticSlot.MELEE ? List.of(cosmetic.definition().texture())
                : Arrays.stream(cosmetic.layers()).map(BakedCosmetic.Layer::texture).distinct().toList());
    }

    private boolean shows(CosmeticControls.Gate gate) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (gate.option() != null) {
            boolean colour = gate.option() == CosmeticOption.TINT || gate.option() == CosmeticOption.RAINBOW
                    || gate.option() == CosmeticOption.SATURATION;
            if (!(colour ? config.allows(cosmetic, gate.option()) : cosmetic.allows(gate.option()))) {
                return false;
            }
        }
        if (gate.also() == null) {
            return true;
        }
        return switch (gate.also()) {
            case "styles" -> cosmetic.styles().length > 1;
            case "pet" -> cosmetic.slot() == CosmeticSlot.PET;
            case "cape" -> cosmetic.slot() == CosmeticSlot.CAPE;
            case "shield" -> cosmetic.slot() == CosmeticSlot.SHIELD;
            case "knife" -> cosmetic.slot() == CosmeticSlot.MELEE;
            case "knife_angel" -> {
                io.github.georgwav.cosmetics.melee.Knife k = io.github.georgwav.cosmetics.melee.KnifeRegistry.get(id);
                yield k != null && k.rig.angelSet;
            }
            case "rainbow_on" -> config.options(id).rainbow;
            case "tint_or_rainbow" -> config.allows(cosmetic, CosmeticOption.TINT) || config.allows(cosmetic, CosmeticOption.RAINBOW);
            case "palette" -> palette.length > 0;
            case "size_or_position" -> cosmetic.allows(CosmeticOption.SIZE) || cosmetic.allows(CosmeticOption.POSITION);
            case "flat_cape" -> cosmetic.slot() == CosmeticSlot.CAPE && Arrays.stream(cosmetic.layers()).anyMatch(l -> l.rig() == null);
            case "emissive" -> cosmetic.hasEmissive();
            case "animated" -> cosmetic.hasAnimation();
            case "particles" -> !cosmetic.definition().particles().isEmpty();
            default -> true;
        };
    }

    /** The note beside a control: why it does nothing right now (switched off in Display). */
    private static String note(String key) {
        CosmeticsConfig c = CosmeticsConfig.get();
        return switch (key) {
            case "cloth" -> c.capePhysics ? null : WardrobeScreen.t("cloth.physics_off");
            case "glow" -> c.emissiveParts ? null : WardrobeScreen.t("glow.off_globally");
            case "cosmetic_particles" -> c.particles ? null : WardrobeScreen.t("particles.off_globally");
            default -> null;
        };
    }

    void draw(Gfx g, Tokens tk, float x, float top, float w, float h) {
        Ui ui = screen.ui;
        CosmeticsConfig config = CosmeticsConfig.get();
        // The header: back to the list, the name, wear it.
        ui.button(tk, x + g.width(g.font(SolFont.Family.MEDIUM, 12f), WardrobeScreen.t("detail.back")) + 20f, top + 13f,
                WardrobeScreen.t("detail.back"), false, () -> screen.openCosmetic(null));
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 20f);
        g.textMid(b, g.fit(b, cosmetic.name(), w - 200f), x, top + 50f, 0xFFFFFFFF);
        boolean worn = config.localEquipped().contains(id);
        SolFont s = g.font(SolFont.Family.MEDIUM, 13f);
        String wear = WardrobeScreen.t("detail.wear");
        g.textRight(s, wear, x + w - 42f, top + 50f, 0xD9FFFFFF);
        ui.smallSwitch(tk, x + w - 30f, top + 41f, worn, () -> WardrobeScreen.setEquipped(id, !worn));
        float bodyTop = top + 74f;
        g.rect(x, bodyTop - 6f, w, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        g.clip(x - 4f, bodyTop, x + w + 6f, top + h);
        PageRows rows = new PageRows(g, ui, tk, x, bodyTop - scroll.value(), w, null);
        String section = null;
        for (CosmeticControls.Own c : CosmeticControls.COSMETIC) {
            if (!shows(c.gate())) {
                continue;
            }
            if (!c.section().equals(section)) {
                section = c.section();
                rows.section(WardrobeScreen.t("section." + section));
            }
            String label = WardrobeScreen.t(c.key());
            switch (c) {
                case CosmeticControls.OwnToggle t -> rows.toggle(label, note(t.key()), t.get().test(config.options(id)), () -> {
                    boolean v = !t.get().test(config.options(id));
                    t.set().accept(config.editOptions(id), v);
                    if (t.key().equals("glow")) {
                        // A cape's elytra has its glowing parts drawn in.
                        CapeElytra.forget(id);
                    }
                });
                case CosmeticControls.OwnSlider sl -> {
                    double v = sl.get().applyAsDouble(config.options(id));
                    rows.slider(label, note(sl.key()), (float) ((v - sl.min()) / (sl.max() - sl.min())),
                            CosmeticControls.format(sl.format(), v), k -> {
                                double raw = sl.min() + k * (sl.max() - sl.min());
                                sl.set().accept(config.editOptions(id), Math.round(raw / sl.step()) * sl.step());
                            });
                }
                case CosmeticControls.OwnChoice<?> ch -> choice(rows, label, ch, config);
                case CosmeticControls.OwnSpecial sp -> special(g, ui, tk, rows, sp, label, config);
            }
        }
        rows.y += 10f;
        float resetY = rows.y + 16f;
        ui.button(tk, x + w, resetY, WardrobeScreen.t("detail.reset"), false, () -> {
            config.resetOptions(id);
            swatch = -1;
        });
        rows.y += 40f;
        float end = rows.y;
        g.endClip();
        float viewH = top + h - bodyTop;
        scroll.setMax(end + scroll.value() - bodyTop - viewH + 12f);
        scroll.step(screen.dt());
        scroll.bar(g, x + w + 4f, bodyTop, viewH);
    }

    private <T> void choice(PageRows rows, String label, CosmeticControls.OwnChoice<T> ch, CosmeticsConfig config) {
        List<T> values = ch.values();
        String[] names = new String[values.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = WardrobeScreen.t(ch.names().apply(values.get(i)));
        }
        int sel = values.indexOf(ch.get().apply(config.options(id)));
        rows.choice(label, null, names, sel, i -> ch.set().accept(config.editOptions(id), values.get(i)));
        if (ch.key().equals("color_style")) {
            String hint = WardrobeScreen.t(ch.names().apply(ch.get().apply(config.options(id))) + ".hint");
            rows.text(hint, 0x8CFFFFFF);
        }
    }

    private void special(Gfx g, Ui ui, Tokens tk, PageRows rows, CosmeticControls.OwnSpecial sp, String label, CosmeticsConfig config) {
        switch (sp.what()) {
            case "style" -> {
                String[] styles = cosmetic.styles();
                String def = styles[cosmetic.defaultStyle()];
                String[] names = new String[styles.length];
                for (int i = 0; i < styles.length; i++) {
                    names[i] = styleName(styles[i]);
                }
                rows.choice(label, null, names, cosmetic.styleIndex(config.options(id).style),
                        i -> config.editOptions(id).style = styles[i].equals(def) ? null : styles[i]);
            }
            case "pet_position" -> {
                PetPosition[] all = PetPosition.values();
                PetPosition def = cosmetic.definition().petPosition();
                String[] names = new String[all.length];
                for (int i = 0; i < all.length; i++) {
                    names[i] = WardrobeScreen.t("pet_position." + all[i].name().toLowerCase(java.util.Locale.ROOT));
                }
                PetPosition cur = CosmeticRenderer.petPosition(cosmetic, config.options(id));
                rows.choice(label, null, names, Arrays.asList(all).indexOf(cur),
                        i -> config.editOptions(id).petPosition = all[i] == def ? null : all[i]);
            }
            case "preview_elytra" -> rows.toggle(label, null, WardrobeScreen.previewElytra,
                    () -> WardrobeScreen.previewElytra = !WardrobeScreen.previewElytra);
            case "shield_item_icon" -> {
                rows.toggle(label, null, config.shieldItemIcons, () -> {
                    config.shieldItemIcons = !config.shieldItemIcons;
                    CosmeticsConfig.save();
                });
                rows.text(WardrobeScreen.t("shield_item_icon.hint"), 0x8CFFFFFF);
            }
            case "color" -> {
                float top = rows.block(label, ColorPicker.HEIGHT + 30f);
                if (!Float.isNaN(top)) {
                    SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
                    g.textMid(f, label, rows.left(), top + 12f, 0xFFFFFFFF);
                    tint.draw(g, ui, tk, rows.left(), top + 26f, rows.width(), COLORS, WardrobeScreen.t("color.default_button"),
                            () -> {
                                CosmeticsConfig.CosmeticOptions o = config.editOptions(id);
                                o.color = null;
                                o.themeColor = null;
                            }, screen.dt());
                }
            }
            case "follow_theme" -> {
                // Default does as every cosmetic does (Settings › Theme colour on every cosmetic); Off keeps its own
                // colours even then.
                String cur = config.options(id).themeColor;
                String[] names = {WardrobeScreen.tr("sol.wardrobe.follow.default"), WardrobeScreen.tr("sol.wardrobe.follow.off"),
                        WardrobeScreen.tr("sol.wardrobe.follow.accent"), WardrobeScreen.tr("sol.wardrobe.follow.glow")};
                int sel = cur == null ? 0 : cur.equals("own") ? 1 : cur.equals("glow") ? 3 : 2;
                rows.choice(WardrobeScreen.tr("sol.wardrobe.follow"), null, names, sel,
                        i -> config.editOptions(id).themeColor = i == 0 ? null : i == 1 ? "own" : i == 2 ? "accent" : "glow");
            }
            case "swaps" -> swaps(g, ui, tk, rows, label, config);
            default -> {
            }
        }
    }

    /**
     * Its own colours (the red and the gold of a red and gold cape), each to change on its own: pick one, then its
     * colour in the picker below ("Keep it" puts it back); the others stay as they are.
     */
    private void swaps(Gfx g, Ui ui, Tokens tk, PageRows rows, String label, CosmeticsConfig config) {
        float cy = rows.row(label, null);
        if (Float.isNaN(cy)) {
            return;
        }
        int[] shown = new int[palette.length];
        for (int i = 0; i < palette.length; i++) {
            shown[i] = 0xFF000000 | swappedTo(config, palette[i]);
        }
        ui.swatches(tk, rows.right(), cy, shown, swatch, i -> {
            swatch = swatch == i ? -1 : i;
            int from = palette[i];
            swapPicker = new ColorPicker(() -> swappedTo(config, from), v -> swap(config, from, v));
        });
        if (swatch >= 0 && swatch < palette.length && swapPicker != null) {
            int from = palette[swatch];
            float top = rows.block(null, ColorPicker.HEIGHT + 6f);
            swapPicker.draw(g, ui, tk, rows.left(), top, rows.width(), CosmeticPage.COLORS, WardrobeScreen.t("swaps.keep"),
                    () -> swap(config, from, from), screen.dt());
        }
        CosmeticsConfig.CosmeticOptions o = config.options(id);
        rows.text(WardrobeScreen.t(o.color != null || o.rainbow || io.github.georgwav.cosmetics.render.Tint.follows(o) != null ? "swaps.hint_painted" : "swaps.hint"),
                0x8CFFFFFF);
    }

    /** What the cosmetic's colour {@code from} is changed to (itself when it isn't). */
    private int swappedTo(CosmeticsConfig config, int from) {
        List<CosmeticsConfig.ColorSwap> swaps = config.options(id).swaps;
        if (swaps != null) {
            for (CosmeticsConfig.ColorSwap c : swaps) {
                if (c.from == (from & 0xFFFFFF)) {
                    return c.to;
                }
            }
        }
        return from & 0xFFFFFF;
    }

    /**
     * Changes the cosmetic's colour {@code from} to {@code to}. Every colour of its palette is kept in the list (as
     * itself if unchanged), so the kept ones hold their colours; none changed is no list at all.
     */
    private void swap(CosmeticsConfig config, int from, int to) {
        CosmeticsConfig.CosmeticOptions options = config.editOptions(id);
        List<CosmeticsConfig.ColorSwap> swaps = new ArrayList<>();
        for (int colour : palette) {
            int target = colour == from ? to : swappedTo(config, colour);
            swaps.add(new CosmeticsConfig.ColorSwap(colour, target));
        }
        options.swaps = swaps;
        if (!options.swapsAny()) {
            options.swaps = null;
        }
    }

    /** {@code curved} becomes {@code Curved}, {@code soft_wave} becomes {@code Soft wave}. */
    static String styleName(String style) {
        String words = style.replace('_', ' ').replace('-', ' ');
        return words.isEmpty() ? style : Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    void unfocus() {
        tint.hexField().focus(false);
        if (swapPicker != null) {
            swapPicker.hexField().focus(false);
        }
    }

    boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    boolean atTop() {
        return scroll.value() == 0f;
    }

    boolean key(int key, boolean ctrl) {
        return tint.key(key, ctrl) || (swapPicker != null && swapPicker.key(key, ctrl));
    }

    boolean typed(String s) {
        return tint.typed(s) || (swapPicker != null && swapPicker.typed(s));
    }

    /** The colour's swatch (ARGB) for the page's look, from the cosmetic's own colour. */
    int accent(Tokens tk) {
        return Colors.withAlpha(tk.accent, 1f);
    }
}
