package io.github.georgwav.cosmetics.sol.ui.screens;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.FileThumbs;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Your screenshots: the newest first in a grid of glass frames; one opens large, with the arrows to step through,
 * and Copy (to the clipboard as an image), Show in folder and Delete (asks once). The list and the pictures are read
 * off the render thread.
 */
public final class ScreenshotGallery extends SolScreen {
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());
    private static final int COLS = 4;
    private final Ui ui = new Ui();
    private final Scroll scroll = new Scroll();
    private final FileThumbs thumbs = new FileThumbs("shot_thumb", 192, 108, 24);
    private final FileThumbs large = new FileThumbs("shot_view", 960, 540, 2);
    private volatile List<Path> shots;
    private int open = -1;
    private boolean confirmDelete;

    public ScreenshotGallery(Screen parent) {
        super(Component.translatable("sol.gallery.title"), parent, Feature.MENU);
        refresh();
    }

    public static Path folder() {
        return net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots");
    }

    private void refresh() {
        SolExecutors.IO.execute(() -> {
            List<Path> list = new ArrayList<>();
            try (Stream<Path> files = Files.list(folder())) {
                files.filter(p -> {
                    String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    return n.endsWith(".png") || n.endsWith(".jpg");
                }).forEach(list::add);
            } catch (java.io.IOException | RuntimeException e) {
                // No folder yet: none.
            }
            list.sort((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()));
            shots = list;
        });
    }

    @Override
    protected void closed() {
        thumbs.release();
        large.release();
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
        return open < 0 && scroll.wheel(amount);
    }

    @Override
    protected boolean key(int key, boolean ctrl, boolean shift) {
        List<Path> list = shots;
        if (open >= 0 && list != null) {
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_LEFT) {
                step(-1);
                return true;
            }
            if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RIGHT) {
                step(1);
                return true;
            }
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
            ui.tab(shift);
            return true;
        }
        if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN) {
            return ui.activate();
        }
        return false;
    }

    @Override
    protected boolean back() {
        if (open >= 0) {
            open = -1;
            confirmDelete = false;
            return true;
        }
        return false;
    }

    private void step(int d) {
        List<Path> list = shots;
        if (list == null || list.isEmpty()) {
            return;
        }
        open = Math.floorMod(open + d, list.size());
        confirmDelete = false;
    }

    @Override
    protected void frame(Gfx g) {
        Tokens tk = ThemeState.tokens();
        ui.begin(g, mx, my);
        g.rect(g.d.left(), g.d.top(), g.d.right() - g.d.left(), g.d.bottom() - g.d.top(), 0x99050508);
        List<Path> list = shots;
        SolFont h1 = g.font(SolFont.Family.SEMIBOLD, 26f), mono = g.font(SolFont.Family.MONO, 12f), small = g.font(SolFont.Family.MEDIUM, 12f);
        float x0 = 64f, top = 44f;
        g.textMid(h1, Component.translatable("sol.gallery.title").getString(), x0, top, 0xFFFFFFFF);
        String count = list == null ? "" : Component.translatable("sol.gallery.count", list.size()).getString();
        g.textMid(mono, count, x0 + g.width(h1, Component.translatable("sol.gallery.title").getString()) + 14f, top, 0x8CFFFFFF);
        float bw = ui.button(tk, 1216f, top, Component.translatable("sol.gallery.folder").getString(), false, this::showFolder);
        ui.button(tk, 1216f - bw - 8f, top, Component.translatable("gui.back").getString(), false, this::onClose);
        if (list == null) {
            return;
        }
        if (list.isEmpty()) {
            g.textCentre(g.font(SolFont.Family.MEDIUM, 15f), Component.translatable("sol.gallery.empty").getString(), 640f, 360f, 0x99FFFFFF);
            return;
        }
        if (open >= 0 && open < list.size()) {
            view(g, tk, list, small, mono);
            return;
        }
        float gy = 84f, gw = 1152f, cw = (gw - (COLS - 1) * 16f) / COLS, ch = cw * 9f / 16f, rowH = ch + 34f;
        g.clip(x0 - 8f, gy - 4f, x0 + gw + 8f, 716f);
        for (int i = 0; i < list.size(); i++) {
            float cx = x0 + (i % COLS) * (cw + 16f), cy = gy + (i / COLS) * rowH - scroll.value();
            if (cy + rowH < gy - 20f || cy > 720f) {
                continue;
            }
            Path p = list.get(i);
            int index = i;
            ui.click(cx, cy, cw, ch, () -> {
                open = index;
                confirmDelete = false;
            });
            boolean hover = ui.over(cx, cy, cw, ch);
            g.glow(cx, cy, cw, ch, 8f, hover ? 14f : 8f, Colors.withAlpha(hover ? tk.accent : tk.glow, hover ? 0.35f : 0.12f));
            SolTexture t = thumbs.get(p);
            if (t != null) {
                g.image(t, cx, cy, cw, ch, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
            } else {
                g.round(cx, cy, cw, ch, 8f, Colors.withAlpha(tk.panel, 0.7f));
                Icons.draw(g, "camera", cx + cw / 2f - 14f, cy + ch / 2f - 14f, 28f, 0x66FFFFFF, Icons.FILL, 0);
            }
            g.stroke(cx, cy, cw, ch, 8f, Math.max(1f, g.d.u), hover ? tk.accent : 0x26FFFFFF);
            g.text(small, g.fit(small, WHEN.format(Instant.ofEpochMilli(p.toFile().lastModified())), cw), cx, cy + ch + 18f, 0xB3FFFFFF);
        }
        g.endClip();
        scroll.setMax(((list.size() + COLS - 1) / COLS) * rowH - (716f - gy));
        scroll.step(dt);
    }

    private void view(Gfx g, Tokens tk, List<Path> list, SolFont small, SolFont mono) {
        Path p = list.get(open);
        float w = 1040f, h = w * 9f / 16f, x = 640f - w / 2f, y = 84f;
        g.glow(x, y, w, h, 10f, 24f, Colors.withAlpha(tk.glow, 0.2f));
        SolTexture t = large.get(p);
        SolTexture thumb = thumbs.get(p);
        if (t != null || thumb != null) {
            g.image(t != null ? t : thumb, x, y, w, h, 0f, 0f, 1f, 1f, 0xFFFFFFFF, false);
        } else {
            g.round(x, y, w, h, 10f, Colors.withAlpha(tk.panel, 0.7f));
        }
        g.stroke(x, y, w, h, 10f, Math.max(1f, g.d.u), 0x33FFFFFF);
        float by = y + h + 32f;
        g.textMid(small, p.getFileName().toString() + "  ·  " + WHEN.format(Instant.ofEpochMilli(p.toFile().lastModified())), x, by, 0xCCFFFFFF);
        g.textRight(mono, (open + 1) + " / " + list.size(), x + w, by, 0x8CFFFFFF);
        float cx = 640f;
        ui.tool(tk, cx - 160f, by + 22f, "prev", false, () -> step(-1));
        float right = cx + 40f;
        ui.button(tk, right, by + 35f, Component.translatable("sol.gallery.copy").getString(), true, () -> copy(p));
        ui.button(tk, right + 128f, by + 35f, Component.translatable("sol.gallery.show").getString(), false, this::showFolder);
        ui.button(tk, right + 236f, by + 35f, Component.translatable(confirmDelete ? "sol.gallery.delete_sure" : "sol.gallery.delete").getString(),
                false, () -> delete(p));
        ui.tool(tk, cx + 300f, by + 22f, "next", false, () -> step(1));
    }

    private void copy(Path p) {
        Thread t = new Thread(() -> {
            boolean ok = io.github.georgwav.cosmetics.client.modules.Screenshots.copy(p.toFile());
            minecraft.execute(() -> io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(Component.translatable(
                    ok ? "sol.notify.screenshot_copied" : "sol.notify.screenshot_no_copy").getString()));
        }, "Sol screenshot copy");
        t.setDaemon(true);
        t.start();
    }

    private void showFolder() {
        io.github.georgwav.cosmetics.sol.util.Browser.open(folder().toUri().toString());
    }

    private void delete(Path p) {
        if (!confirmDelete) {
            confirmDelete = true;
            return;
        }
        confirmDelete = false;
        try {
            Files.deleteIfExists(p);
        } catch (java.io.IOException e) {
            io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(Component.translatable("sol.notify.screenshot_not_deleted").getString());
            return;
        }
        thumbs.forget(p);
        large.forget(p);
        List<Path> list = new ArrayList<>(shots);
        list.remove(p);
        shots = list;
        if (list.isEmpty()) {
            open = -1;
        } else {
            open = Math.min(open, list.size() - 1);
        }
    }
}
