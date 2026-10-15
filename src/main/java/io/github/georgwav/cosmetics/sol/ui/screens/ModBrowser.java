package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.client.Modrinth;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Notifications;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.TextField;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Get mods: Fabric mods on Modrinth for this Minecraft version, the most downloaded first or what you search for.
 * Install puts the mod (and what it needs) in the mods folder for the next start; nothing runs until you restart.
 */
public final class ModBrowser extends SolScreen {
    private final Ui ui = new Ui();
    private final Scroll scroll = new Scroll();
    private final TextField search = new TextField(60, t -> changed = System.nanoTime());
    private long changed;
    private String asked;
    private volatile List<Modrinth.Hit> hits;
    private volatile boolean failed;
    /** Project id → "installing", "done" or "failed". */
    private final Map<String, String> state = new ConcurrentHashMap<>();
    private final Set<String> downloaded = ConcurrentHashMap.newKeySet();

    public ModBrowser(Screen parent, String query) {
        super(Component.translatable("sol.mods.browser.title"), parent, Feature.MENU);
        search.set(query == null ? "" : query);
        search.focus(true);
        ask(search.text());
    }

    private void ask(String q) {
        asked = q.strip();
        hits = null;
        failed = false;
        String query = asked;
        SolExecutors.IO.execute(() -> {
            List<Modrinth.Hit> h = Modrinth.search(query);
            if (query.equals(asked)) {
                failed = h == null;
                hits = h == null ? List.of() : h;
            }
        });
    }

    @Override
    protected boolean press(float x, float y, int button) {
        return ui.press(x, y);
    }

    @Override
    protected boolean release(float x, float y, int button) {
        return ui.release();
    }

    @Override
    protected boolean scroll(float x, float y, float amount) {
        return scroll.wheel(amount);
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        return search.key(key, ctrl);
    }

    @Override
    protected boolean typed(String text) {
        return search.typed(text);
    }

    private static String count(long n) {
        return n >= 1_000_000L ? String.format(Locale.ROOT, "%.1fM", n / 1e6) : n >= 1000L ? String.format(Locale.ROOT, "%.0fk", n / 1e3) : Long.toString(n);
    }

    private void install(Modrinth.Hit hit) {
        state.put(hit.id(), "installing");
        SolExecutors.IO.execute(() -> {
            try {
                List<String> files = Modrinth.install(hit.id(), ConcurrentHashMap.newKeySet());
                downloaded.add(hit.id());
                state.put(hit.id(), "done");
                Notifications.post(Component.translatable("sol.notify.mod_installed", hit.title(), files.size()).getString());
            } catch (Exception e) {
                state.put(hit.id(), "failed");
                Notifications.post(Component.translatable("sol.notify.mod_failed", hit.title()).getString());
            }
        });
    }

    @Override
    protected void frame(Gfx g) {
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        if (!search.text().strip().equals(asked) && System.nanoTime() - changed > 450_000_000L) {
            ask(search.text());
            scroll.reset();
        }
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0xA6050508);
        SolFont h1 = g.font(SolFont.Family.SEMIBOLD, 26f), mono = g.font(SolFont.Family.MONO, 12f);
        SolFont b = g.font(SolFont.Family.SEMIBOLD, 14f), s = g.font(SolFont.Family.MEDIUM, 12f);
        float x0 = 160f, w = 960f, top = 44f;
        String title = Component.translatable("sol.mods.browser.title").getString();
        g.textMid(h1, title, x0, top, 0xFFFFFFFF);
        g.textMid(mono, Component.translatable("sol.mods.browser.for", "Modrinth",
                net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse(""))
                .getString(), x0 + g.width(h1, title) + 14f, top, 0x8CFFFFFF);
        ui.button(tk, x0 + w, top, Component.translatable("gui.done").getString(), true, this::onClose);
        search.draw(g, ui, tk, x0, top + 30f, w, 34f, "search", Component.translatable("sol.mods.browser.search").getString(), dt);
        List<Modrinth.Hit> list = hits;
        float ly = top + 82f, bottom = 700f;
        if (list == null) {
            g.textMid(s, Component.translatable("sol.menu.loading").getString(), x0, ly + 20f, 0x8CFFFFFF);
            return;
        }
        if (list.isEmpty()) {
            g.textMid(s, Component.translatable(failed ? "sol.mods.browser.offline" : "sol.mods.browser.none").getString(), x0, ly + 20f, 0x8CFFFFFF);
            return;
        }
        g.clip(x0 - 8f, ly - 4f, x0 + w + 8f, bottom);
        float y = ly - scroll.value();
        for (Modrinth.Hit hit : list) {
            if (y > ly - 80f && y < bottom + 10f) {
                float rh = 68f;
                g.round(x0, y, w, rh, 12f, 0x0DFFFFFF);
                g.stroke(x0, y, w, rh, 12f, Math.max(1f, g.d.u), 0x14FFFFFF);
                SolTexture icon = io.github.georgwav.cosmetics.sol.music.Covers.thumb(hit.icon());
                if (icon != null) {
                    g.image(icon, x0 + 12f, y + 12f, 44f, 44f, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
                } else {
                    g.round(x0 + 12f, y + 12f, 44f, 44f, 10f, 0x14FFFFFF);
                    Icons.draw(g, "puzzle", x0 + 23f, y + 23f, 22f, 0x99FFFFFF, Icons.FILL, 0);
                }
                String st = state.getOrDefault(hit.id(), Modrinth.installed(hit, downloaded) ? "installed" : "");
                float aw;
                float cy = y + rh / 2f;
                switch (st) {
                    case "installing" -> aw = ui.pill(tk, x0 + w - 14f, cy, Component.translatable("sol.mods.browser.installing").getString(), false);
                    case "done" -> aw = ui.pill(tk, x0 + w - 14f, cy, Component.translatable("sol.mods.browser.restart").getString(), true);
                    case "installed" -> aw = ui.pill(tk, x0 + w - 14f, cy, Component.translatable("sol.mods.browser.installed").getString(), false);
                    default -> aw = ui.button(tk, x0 + w - 14f, cy, Component.translatable(st.equals("failed")
                            ? "sol.mods.browser.retry" : "sol.mods.browser.install").getString(), true, () -> install(hit));
                }
                float room = w - 70f - aw - 80f;
                g.text(b, g.fit(b, hit.title(), room), x0 + 70f, y + 26f, 0xFFFFFFFF);
                g.textRight(mono, "↓ " + count(hit.downloads()), x0 + w - 24f - aw, y + 22f, 0x8CFFFFFF);
                String by = hit.author() == null ? "" : Component.translatable("sol.mods.browser.by", hit.author()).getString() + "  ·  ";
                g.text(s, g.fit(s, by + (hit.description() == null ? "" : hit.description()), room + 60f), x0 + 70f, y + 46f, 0x99FFFFFF);
            }
            y += 76f;
        }
        g.endClip();
        scroll.setMax(y + scroll.value() - bottom);
        scroll.step(dt);
        g.text(g.font(SolFont.Family.MEDIUM, 11f), Component.translatable("sol.mods.browser.note").getString(), x0, 712f,
                Colors.withAlpha(0xFFFFFFFF, 0.45f));
    }
}
