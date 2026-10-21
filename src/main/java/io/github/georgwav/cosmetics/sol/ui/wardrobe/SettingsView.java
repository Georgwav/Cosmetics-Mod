package io.github.georgwav.cosmetics.sol.ui.wardrobe;

import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
import io.github.georgwav.cosmetics.emote.EmotePlayer;
import io.github.georgwav.cosmetics.model.CosmeticLoader;
import io.github.georgwav.cosmetics.model.CosmeticRegistry;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Pix;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The cosmetics settings, every one the Cosmetics screen had, on its pages:
 * - Display, Performance, Compatibility and Maintenance, as tabs;
 * - the rows come from {@link CosmeticControls#GLOBAL}, so the parity test covers what shows here;
 * - the search finds rows on the open page by their label or their section's name, as the settings search did.
 */
final class SettingsView {
    private static String page = CosmeticControls.PAGES.get(0);
    private static String buildLabel;

    private final WardrobeScreen screen;
    private final Scroll scroll = new Scroll();
    private final TextField search;
    private String query = "";
    /** A player's UUID typed in Maintenance, for giving them cosmetics. */
    private final TextField player = new TextField(36, s -> {
    });

    SettingsView(WardrobeScreen screen) {
        this.screen = screen;
        this.search = new TextField(48, q -> {
            query = q;
            scroll.reset();
        });
    }

    void draw(Gfx g, Tokens tk, float x, float top, float w, float h) {
        Ui ui = screen.ui;
        String[] names = new String[CosmeticControls.PAGES.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = WardrobeScreen.t("tab." + CosmeticControls.PAGES.get(i));
        }
        int sel = CosmeticControls.PAGES.indexOf(page);
        // The page tabs on the left (the segmented control is placed by its right edge), the search on the right.
        float segW = ui.segmentedWidth(names);
        ui.segmented(tk, x + segW, top + 17f, names, sel, i -> {
            page = CosmeticControls.PAGES.get(i);
            scroll.reset();
        });
        // The search beside the tabs when it has room for its hint, else on its own line under them.
        float sw = Math.min(300f, w - segW - 12f);
        boolean below = sw < 180f;
        if (below) {
            search.draw(g, ui, tk, x, top + 40f, w, 34f, "search", WardrobeScreen.t("settings_search"), screen.dt());
        } else {
            search.draw(g, ui, tk, x + w - sw, top, sw, 34f, "search", WardrobeScreen.t("settings_search"), screen.dt());
        }
        float bodyTop = top + (below ? 92f : 50f);
        g.rect(x, bodyTop - 6f, w, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        g.clip(x - 4f, bodyTop, x + w + 6f, top + h);
        PageRows rows = new PageRows(g, ui, tk, x, bodyTop - scroll.value(), w, query);
        String section = null;
        CosmeticsConfig config = CosmeticsConfig.get();
        for (CosmeticControls.Control c : CosmeticControls.GLOBAL.get(page)) {
            if (!c.section().equals(section)) {
                section = c.section();
                rows.section(WardrobeScreen.t("section." + section));
            }
            String label = WardrobeScreen.t(c.key());
            switch (c) {
                case CosmeticControls.Toggle t -> rows.toggle(label, note(t.key()), t.get().test(config), () -> {
                    boolean v = !t.get().test(config);
                    t.set().accept(config, v);
                    if (t.key().equals("emotes_enabled") && !v) {
                        EmotePlayer.stop();
                    }
                    EquippedCosmetics.invalidate();
                });
                case CosmeticControls.Slider s -> {
                    double v = s.get().applyAsDouble(config);
                    rows.slider(label, null, (float) ((v - s.min()) / (s.max() - s.min())), format(s.format(), v), k -> {
                        double raw = s.min() + k * (s.max() - s.min());
                        s.set().accept(config, Math.round(raw / s.step()) * s.step());
                    });
                }
                case CosmeticControls.Choice<?> ch -> choice(rows, label, ch, config);
                case CosmeticControls.Special sp -> special(g, ui, tk, rows, sp, label, config);
            }
        }
        if (!rows.any()) {
            rows.y += 8f;
            SolFont f = g.font(SolFont.Family.MEDIUM, 13f);
            g.text(f, g.fit(f, WardrobeScreen.t("settings_search.none", query.trim()), w), x, rows.y + 12f, 0x99FFFFFF);
            rows.y += 30f;
        }
        float end = rows.y + 12f;
        g.endClip();
        float viewH = top + h - bodyTop;
        scroll.setMax(end + scroll.value() - bodyTop - viewH);
        scroll.step(screen.dt());
        scroll.bar(g, x + w + 4f, bodyTop, viewH);
    }

    /** The note beside a setting: why it does nothing now. */
    private static String note(String key) {
        return switch (key) {
            case "defer_figura" -> Compat.FIGURA ? null : WardrobeScreen.t("not_installed");
            default -> null;
        };
    }

    private static String format(String format, double v) {
        if (format.equals("blocks")) {
            int b = (int) v;
            return b <= 0 ? WardrobeScreen.t("unlimited") : WardrobeScreen.t("blocks", b);
        }
        return CosmeticControls.format(format, v);
    }

    private static <T> void choice(PageRows rows, String label, CosmeticControls.Choice<T> ch, CosmeticsConfig config) {
        List<T> values = ch.values();
        String[] names = new String[values.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = WardrobeScreen.t(ch.names().apply(values.get(i)));
        }
        rows.choice(label, null, names, values.indexOf(ch.get().apply(config)), i -> ch.set().accept(config, values.get(i)));
    }

    private void special(Gfx g, Ui ui, Tokens tk, PageRows rows, CosmeticControls.Special sp, String label, CosmeticsConfig config) {
        switch (sp.what()) {
            case "key_open" -> keyRow(g, ui, tk, rows, label, KeyBinds.open, false);
            case "key_emotes" -> keyRow(g, ui, tk, rows, label, KeyBinds.emotes, true);
            case "slots" -> {
                for (CosmeticSlot slot : CosmeticSlot.values()) {
                    rows.toggle(WardrobeScreen.t("slot." + slot.id()), null, config.slotVisible(slot), () -> {
                        config.slotVisible.put(slot, !config.slotVisible(slot));
                        EquippedCosmetics.invalidate();
                    });
                }
            }
            case "iris" -> rows.info("Iris", Compat.IRIS ? WardrobeScreen.t(Compat.isShaderPackActive() ? "iris.active" : "iris.installed")
                    : WardrobeScreen.t("not_installed"), Compat.IRIS ? tk.accent : 0x73FFFFFF);
            case "priorities" -> {
                SlotPriority[] all = SlotPriority.values();
                String[] names = new String[all.length];
                for (int i = 0; i < all.length; i++) {
                    names[i] = WardrobeScreen.t("priority." + all[i].name().toLowerCase(Locale.ROOT));
                }
                for (CosmeticSlot slot : CosmeticSlot.values()) {
                    if (slot.anchor() == CosmeticSlot.Anchor.HELD) {
                        continue; // a held cosmetic replaces the item; no other mod's cosmetic competes for it
                    }
                    rows.choice(WardrobeScreen.t("slot." + slot.id()), null, names, java.util.Arrays.asList(all).indexOf(config.priority(slot)),
                            i -> config.slotPriority.put(slot, all[i]));
                }
            }
            case "detected" -> {
                Map<String, String> mods = Compat.detectedMods();
                if (mods.isEmpty()) {
                    rows.text(WardrobeScreen.t("detected.none"), 0x99FFFFFF);
                }
                mods.forEach((name, version) -> rows.info(name, version, tk.accent));
            }
            case "loaded" -> rows.info(label, String.valueOf(CosmeticRegistry.all().size()), 0x99FFFFFF);
            case "errors" -> {
                int n = RenderGuard.failureCount();
                rows.info(label, n == 0 ? WardrobeScreen.t("errors.none") : String.valueOf(n), n == 0 ? tk.accent : Tokens.AMBER);
            }
            case "reload" -> rows.button(label, null, label, false, () -> {
                // Read off the game's thread (thousands of cosmetics take a while), put in place on it.
                var manager = Minecraft.getInstance().getResourceManager();
                java.util.concurrent.CompletableFuture.supplyAsync(() -> CosmeticLoader.prepare(manager))
                        .thenAcceptAsync(prepared -> {
                            CosmeticLoader.apply(prepared);
                            EquippedCosmetics.invalidate();
                        }, Minecraft.getInstance());
            });
            case "reset_settings" -> rows.button(label, WardrobeScreen.tr("sol.wardrobe.reset_note"), label, false, () -> {
                // Every setting back to its default; what you wear, your outfits and each cosmetic's settings stay.
                config.resetSettings();
                EquippedCosmetics.invalidate();
                screen.toast(WardrobeScreen.tr("sol.wardrobe.reset_done"));
            });
            case "build" -> rows.info(label, buildLabel(), 0x8CFFFFFF);
            case "players" -> players(g, ui, tk, rows, config);
            default -> {
            }
        }
    }

    /**
     * A key bind, the same one as in Controls: click, then press a key (Esc for none); Reset puts its default back.
     * {@code hintAfter}: the "same keys as in Controls" line goes under this one.
     */
    private void keyRow(Gfx g, Ui ui, Tokens tk, PageRows rows, String label, KeyMapping mapping, boolean hintAfter) {
        if (mapping == null) {
            return;
        }
        boolean conflict = KeyBinds.conflicts(mapping);
        float cy = rows.row(label, conflict ? WardrobeScreen.tr("sol.wardrobe.keys.conflict") : null);
        if (!Float.isNaN(cy)) {
            boolean waiting = screen.listeningFor == mapping;
            float r = rows.right();
            r -= ui.button(tk, r, cy, WardrobeScreen.tr("sol.wardrobe.keys.reset"), false, () -> {
                screen.listeningFor = null;
                KeyBinds.bind(mapping, mapping.getDefaultKey());
                Minecraft.getInstance().options.save();
            }) + 6f;
            SolFont f = g.font(SolFont.Family.PIXEL, 12f);
            String name = KeyBinds.name(mapping);
            String shown = waiting ? "> " + name + " <" : name;
            float bw = Math.max(96f, g.width(f, shown) + 24f), bh = 26f;
            float bx = r - bw, by = cy - bh / 2f;
            boolean hover = ui.over(bx, by, bw, bh);
            int fi = ui.click(bx, by, bw, bh, () -> screen.listeningFor = waiting ? null : mapping);
            Pix.fill(g, bx, by, bw, bh, waiting ? tk.accent : hover ? 0x24FFFFFF : 0x14FFFFFF);
            Pix.frame(g, bx, by, bw, bh, waiting ? tk.accent : 0x1FFFFFFF);
            g.textCentre(f, g.fit(f, shown, bw - 12f), bx + bw / 2f, cy, waiting ? tk.onAccent : conflict ? Tokens.AMBER : 0xFFFFFFFF);
            if (ui.focused(fi)) {
                Pix.frame(g, bx - 2f, by - 2f, bw + 4f, bh + 4f, tk.accent);
            }
        }
        if (hintAfter) {
            rows.text(WardrobeScreen.t("keys.hint"), 0x8CFFFFFF);
        }
    }

    /**
     * Other players' cosmetics, by UUID: what you have them wear on your screen (main kept these in the file only).
     * Give a player what you wear now, or take theirs away.
     */
    private void players(Gfx g, Ui ui, Tokens tk, PageRows rows, CosmeticsConfig config) {
        rows.text(WardrobeScreen.tr("sol.wardrobe.players.hint"), 0x8CFFFFFF);
        List<String> ids = new ArrayList<>(config.equipped.keySet());
        ids.remove("local");
        for (String id : ids) {
            List<String> theirs = config.equipped.get(id);
            float cy = rows.row(shortId(id), WardrobeScreen.t("outfits.count", theirs == null ? 0 : theirs.size()));
            if (Float.isNaN(cy)) {
                continue;
            }
            float r = rows.right();
            r -= ui.button(tk, r, cy, WardrobeScreen.tr("sol.wardrobe.players.remove"), false, () -> {
                config.equipped.remove(id);
                EquippedCosmetics.invalidate();
            }) + 6f;
            ui.button(tk, r, cy, WardrobeScreen.tr("sol.wardrobe.players.give"), false, () -> {
                config.equipped.put(id, new ArrayList<>(config.localEquipped()));
                EquippedCosmetics.invalidate();
            });
        }
        float top = rows.block(WardrobeScreen.tr("sol.wardrobe.players.add"), 46f);
        if (Float.isNaN(top)) {
            return;
        }
        float fw = Math.min(360f, rows.width() - 140f);
        player.draw(g, ui, tk, rows.left(), top + 6f, fw, 34f, "people", WardrobeScreen.tr("sol.wardrobe.players.uuid"), screen.dt());
        UUID uuid = parse(player.text());
        boolean ok = uuid != null;
        ui.button(tk, rows.left() + fw + 12f + g.width(g.font(SolFont.Family.MEDIUM, 12f),
                WardrobeScreen.tr("sol.wardrobe.players.add")) + 20f, top + 23f, WardrobeScreen.tr("sol.wardrobe.players.add"), ok, () -> {
            UUID u = parse(player.text());
            if (u != null) {
                config.equipped.put(u.toString(), new ArrayList<>(config.localEquipped()));
                player.set("");
                EquippedCosmetics.invalidate();
            }
        });
        if (!player.text().isBlank() && !ok) {
            SolFont f = g.font(SolFont.Family.MEDIUM, 11.5f);
            g.text(f, WardrobeScreen.tr("sol.wardrobe.players.bad"), rows.left(), top + 50f, Colors.withAlpha(Tokens.AMBER, 0.9f));
            rows.y += 16f;
        }
    }

    private static UUID parse(String text) {
        String s = text.trim();
        if (s.length() == 32) {
            s = s.substring(0, 8) + "-" + s.substring(8, 12) + "-" + s.substring(12, 16) + "-" + s.substring(16, 20) + "-" + s.substring(20);
        }
        try {
            return s.length() == 36 ? UUID.fromString(s) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String shortId(String id) {
        return id.length() > 13 ? id.substring(0, 8) + "…" + id.substring(id.length() - 4) : id;
    }

    /** "0.1.0+mc26.2 (abc1234)": the version, and the commit for builds from CI. */
    static String buildLabel() {
        if (buildLabel == null) {
            buildLabel = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("cosmeticsmod").map(c -> {
                String version = c.getMetadata().getVersion().getFriendlyString();
                var build = c.getMetadata().getCustomValue("cosmeticsmod:build");
                String commit = build != null && build.getType() == net.fabricmc.loader.api.metadata.CustomValue.CvType.STRING
                        ? build.getAsString() : "";
                return commit.isEmpty() || commit.equals("local") ? version : version + " (" + commit + ")";
            }).orElse("");
        }
        return buildLabel;
    }

    void unfocus() {
        search.focus(false);
        player.focus(false);
    }

    boolean scroll(float amount) {
        return scroll.wheel(amount);
    }

    boolean atTop() {
        return scroll.value() == 0f;
    }

    boolean key(int key, boolean ctrl) {
        return search.key(key, ctrl) || player.key(key, ctrl);
    }

    boolean typed(String s) {
        return search.typed(s) || player.typed(s);
    }

    /** For the scenario: a page of the settings ("settings_performance"...). */
    void stage(String state) {
        for (String p : CosmeticControls.PAGES) {
            if (state.equals("settings_" + p)) {
                page = p;
            }
        }
    }
}
