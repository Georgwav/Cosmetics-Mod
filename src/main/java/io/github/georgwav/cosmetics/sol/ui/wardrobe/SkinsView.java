package io.github.georgwav.cosmetics.sol.ui.wardrobe;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.client.skins.MojangCapes;
import io.github.georgwav.cosmetics.client.skins.OwnSkin;
import io.github.georgwav.cosmetics.client.skins.SkinLibrary;
import io.github.georgwav.cosmetics.client.skins.SkinUploader;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Scroll;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.Ui;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.FolderPicker;
import io.github.georgwav.cosmetics.ui.PictureTexture;
import net.minecraft.client.Minecraft;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Wardrobe › Skins: the skins saved in the launcher and your Mojang capes.
 * - Skins: those in Threadrinth's app folder (read from {@code app.db}, never written), when that is the main folder.
 *   Pick one and Wear sends it to your account, and you wear it at once.
 * - Capes: the capes your account owns, as the launcher's cape picker; click one to wear it, or No cape.
 */
final class SkinsView {
    /** A skin and its picture: the 64 x 64 ARGB pixels, and their texture once made (on the render thread). */
    private static final class Entry {
        final SkinLibrary.Skin skin;
        final int[] argb;
        PictureTexture texture;

        Entry(SkinLibrary.Skin skin, int[] argb) {
            this.skin = skin;
            this.argb = argb;
        }
    }

    /** A cape and its texture (64 x 32 in a 64 x 64 picture). */
    private static final class CapeEntry {
        final MojangCapes.Cape cape;
        int[] argb;
        PictureTexture texture;

        CapeEntry(MojangCapes.Cape cape) {
            this.cape = cape;
        }
    }

    /** How many skins the last Skins view shows, and whether the read finished (the in-game test). */
    public static int shown;
    public static boolean read;

    private static final float CARD_W = 118f, CARD_H = 196f, CAPE_W = 96f, CAPE_H = 150f, GAP = 12f;

    private final WardrobeScreen screen;
    private final Scroll scroll = new Scroll();
    private CompletableFuture<List<Entry>> loading;
    private List<Entry> skins = List.of();
    private String problem;
    private int selected = -1;
    private boolean uploading;
    private CompletableFuture<MojangCapes.Listing> capesLoading;
    private List<CapeEntry> capes = List.of();
    private String capesProblem;
    private boolean capeBusy;

    SkinsView(WardrobeScreen screen) {
        this.screen = screen;
    }

    private static String t(String key, Object... args) {
        return WardrobeScreen.tr("cosmeticsmod.hub.skins." + key, args);
    }

    // ------------------------------------------------------------------ loading

    /** Reads the launcher's skins in the background the first time the view shows (and after a new folder). */
    private void load() {
        Path db = MainFolder.database();
        problem = null;
        read = false;
        if (db == null) {
            loading = CompletableFuture.completedFuture(List.of());
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        UUID player = mc.player != null ? mc.player.getUUID() : null;
        loading = CompletableFuture.supplyAsync(() -> {
            try {
                List<Entry> entries = new ArrayList<>();
                for (SkinLibrary.Skin skin : SkinLibrary.load(db, player)) {
                    int[] argb = pixels(skin.png(), 64);
                    if (argb != null) {
                        entries.add(new Entry(skin, argb));
                    }
                }
                return entries;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, SkinsView::ownThread);
    }

    private void loadCapes() {
        capesProblem = null;
        Minecraft mc = Minecraft.getInstance();
        capesLoading = MojangCapes.list(mc.getUser().getAccessToken()).exceptionally(e -> new MojangCapes.Listing(-1, List.of()));
    }

    // A thread of its own, so a busy shared pool never holds the read up.
    private static void ownThread(Runnable task) {
        Thread thread = new Thread(task, "Sol skins");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * A skin's (or a cape's) PNG as 64 x 64 ARGB pixels: a 64 x 64 skin as it is, an old 64 x 32 skin with its right
     * arm and leg copied to the left ones, a 64 x 32 cape in the top half ({@code height} 32 asks for a cape).
     */
    static int[] pixels(byte[] png, int height) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() % 64 != 0) {
                return null;
            }
            int scale = image.getWidth() / 64;
            int h = image.getHeight() / scale;
            if (h != 64 && h != 32) {
                return null;
            }
            int[] argb = new int[64 * 64];
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < 64; x++) {
                    argb[y * 64 + x] = image.getRGB(x * scale, y * scale);
                }
            }
            if (height == 64 && h == 32) {
                copy(argb, 0, 16, 16, 32, 16, 48);
                copy(argb, 40, 16, 16, 32, 32, 48);
            }
            return argb;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static void copy(int[] argb, int fromX, int fromY, int w, int h, int toX, int toY) {
        for (int y = 0; y < h; y++) {
            System.arraycopy(argb, (fromY + y) * 64 + fromX, argb, (toY + y) * 64 + toX, w);
        }
    }

    /** Takes in what the background reads finished, and makes the textures. */
    private void collect() {
        if (loading == null) {
            load();
        }
        if (loading.isDone() && skins.isEmpty() && problem == null) {
            try {
                skins = loading.join();
            } catch (RuntimeException e) {
                problem = t("unreadable");
                CosmeticsMod.LOGGER.warn("Couldn't read the launcher's skins", e);
                skins = List.of();
            }
        }
        shown = skins.size();
        read = loading.isDone();
        for (int i = 0; i < skins.size(); i++) {
            Entry e = skins.get(i);
            if (e.texture == null) {
                e.texture = new PictureTexture("sol_skin_" + i, 64);
                e.texture.upload(e.argb);
            }
        }
        if (capesLoading == null) {
            loadCapes();
        }
        if (capesLoading.isDone() && capes.isEmpty() && capesProblem == null) {
            MojangCapes.Listing listing = capesLoading.join();
            if (listing.status() == 200) {
                List<CapeEntry> list = new ArrayList<>();
                for (MojangCapes.Cape c : listing.capes()) {
                    CapeEntry e = new CapeEntry(c);
                    list.add(e);
                    MojangCapes.texture(c.url()).thenAccept(png -> e.argb = pixels(png, 32)).exceptionally(x -> null);
                }
                capes = list;
                capesProblem = list.isEmpty() ? WardrobeScreen.tr("sol.wardrobe.capes.none") : null;
            } else {
                capesProblem = WardrobeScreen.tr(listing.status() == 401 || listing.status() == 403 ? "sol.wardrobe.capes.signed_out"
                        : "sol.wardrobe.capes.offline");
            }
        }
        for (int i = 0; i < capes.size(); i++) {
            CapeEntry e = capes.get(i);
            if (e.texture == null && e.argb != null) {
                e.texture = new PictureTexture("sol_cape_" + i, 64);
                e.texture.upload(e.argb);
            }
        }
    }

    void release() {
        for (Entry e : skins) {
            if (e.texture != null) {
                e.texture.release();
                e.texture = null;
            }
        }
        for (CapeEntry e : capes) {
            if (e.texture != null) {
                e.texture.release();
                e.texture = null;
            }
        }
    }

    private void reload() {
        release();
        skins = List.of();
        selected = -1;
        scroll.reset();
        load();
    }

    /** Asks for the main folder (Threadrinth's app folder has the launcher's skins). */
    private void chooseFolder() {
        try {
            Path picked = FolderPicker.pick(t("choose_title"), MainFolder.folder());
            if (picked != null) {
                useFolder(picked);
            }
        } catch (UnsupportedOperationException e) {
            screen.toast(t("no_picker"));
        }
    }

    private void useFolder(Path folder) {
        MainFolder.choose(folder);
        reload();
        screen.toast(MainFolder.database() != null ? t("folder_set") : t("no_database"));
    }

    /** Threadrinth's app folder when one was found on this computer and isn't the main folder already. */
    private static Path launcherToOffer() {
        Path launcher = MainFolder.launcherFolder();
        return launcher != null && !launcher.equals(MainFolder.folder()) ? launcher : null;
    }

    /** Sends the picked skin to your account, and wears it here at once. */
    private void wear() {
        if (selected < 0 || selected >= skins.size() || uploading) {
            return;
        }
        Entry entry = skins.get(selected);
        SkinLibrary.Skin skin = entry.skin;
        Minecraft mc = Minecraft.getInstance();
        uploading = true;
        SkinUploader.upload(mc.getUser().getAccessToken(), skin.png(), skin.slim()).whenComplete((status, error) ->
                mc.execute(() -> {
                    uploading = false;
                    if (error != null) {
                        screen.toast(t("offline"));
                    } else if (status == 200) {
                        OwnSkin.set(entry.argb, skin.slim());
                        screen.toast(t("applied"));
                    } else if (status == 401) {
                        screen.toast(t("signed_out"));
                    } else {
                        screen.toast(t("failed", status));
                    }
                }));
    }

    /** Wears a cape (or none, with null) on your account. */
    private void wearCape(MojangCapes.Cape cape) {
        if (capeBusy) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String token = mc.getUser().getAccessToken();
        capeBusy = true;
        CompletableFuture<Integer> call = cape == null ? MojangCapes.hide(token) : MojangCapes.wear(token, cape.id());
        call.whenComplete((status, error) -> mc.execute(() -> {
            capeBusy = false;
            if (error != null) {
                screen.toast(WardrobeScreen.tr("sol.wardrobe.capes.offline"));
            } else if (status == 200) {
                List<CapeEntry> next = new ArrayList<>();
                for (CapeEntry e : capes) {
                    CapeEntry n = new CapeEntry(new MojangCapes.Cape(e.cape.id(), e.cape.alias(), e.cape.url(),
                            cape != null && e.cape.id().equals(cape.id())));
                    n.argb = e.argb;
                    n.texture = e.texture;
                    next.add(n);
                }
                capes = next;
                screen.toast(cape == null ? WardrobeScreen.tr("sol.wardrobe.capes.hidden")
                        : WardrobeScreen.tr("sol.wardrobe.capes.worn", cape.alias()));
            } else if (status == 401) {
                screen.toast(WardrobeScreen.tr("sol.wardrobe.capes.signed_out"));
            } else {
                screen.toast(t("failed", status));
            }
        }));
    }

    // ------------------------------------------------------------------ the view

    void draw(Gfx g, Tokens tk, float x, float top, float w, float h) {
        collect();
        Ui ui = screen.ui;
        Path db = MainFolder.database();
        Path launcher = db == null ? launcherToOffer() : null;
        // The header: where the skins come from, and its buttons.
        float r = x + w;
        if (selected >= 0 && selected < skins.size()) {
            r -= ui.button(tk, r, top + 17f, uploading ? t("uploading") : WardrobeScreen.tr("sol.wardrobe.skins.wear"), true, this::wear) + 8f;
        }
        r -= ui.button(tk, r, top + 17f, t("choose"), false, this::chooseFolder) + 8f;
        if (launcher != null) {
            r -= ui.button(tk, r, top + 17f, t("use_launcher"), false, () -> useFolder(launcher)) + 8f;
        }
        SolFont f = g.font(SolFont.Family.MEDIUM, 12.5f);
        String folder = String.valueOf(MainFolder.folder().getFileName());
        String summary = db == null ? t("no_folder", folder) : t("summary", skins.size(), folder);
        g.textMid(f, g.fit(f, summary, r - x - 8f), x, top + 17f, 0x99FFFFFF);
        float bodyTop = top + 46f;
        g.rect(x, bodyTop - 6f, w, Math.max(1f / g.d.u, 1f), 0x14FFFFFF);
        g.clip(x - 4f, bodyTop, x + w + 6f, top + h);
        float y = bodyTop - scroll.value();
        SolFont head = g.font(SolFont.Family.MONO, 11f);
        g.textSpaced(head, WardrobeScreen.tr("sol.wardrobe.skins.title").toUpperCase(java.util.Locale.ROOT), x, y + 12f, 1.1f, 0x80FFFFFF);
        y += 24f;
        String empty = problem != null ? problem
                : db == null ? (launcher != null ? t("found_hint", launcher) : t("choose_hint"))
                : !loading.isDone() ? t("loading") : skins.isEmpty() ? t("empty") : null;
        if (empty != null) {
            for (String line : PageRows.wrap(g, f, empty, w)) {
                g.text(f, line, x, y + 12f, 0x99FFFFFF);
                y += 18f;
            }
            y += 8f;
        }
        int cols = Math.max(1, (int) ((w + GAP) / (CARD_W + GAP)));
        for (int i = 0; i < skins.size(); i++) {
            float cx = x + (i % cols) * (CARD_W + GAP), cy = y + (i / cols) * (CARD_H + GAP);
            if (cy + CARD_H >= bodyTop && cy <= top + h) {
                skinCard(g, ui, tk, i, cx, cy);
            }
        }
        y += ((skins.size() + cols - 1) / cols) * (CARD_H + GAP) + 10f;
        // Your capes.
        g.textSpaced(head, WardrobeScreen.tr("sol.wardrobe.capes.title").toUpperCase(java.util.Locale.ROOT), x, y + 12f, 1.1f, 0x80FFFFFF);
        y += 24f;
        String capesNote = capesProblem != null ? capesProblem : !capesLoading.isDone() ? t("loading") : null;
        if (capesNote != null) {
            g.text(f, g.fit(f, capesNote, w), x, y + 12f, 0x99FFFFFF);
            y += 26f;
        }
        if (!capes.isEmpty()) {
            int ccols = Math.max(1, (int) ((w + GAP) / (CAPE_W + GAP)));
            boolean anyWorn = capes.stream().anyMatch(c -> c.cape.active());
            for (int i = -1; i < capes.size(); i++) {
                int k = i + 1;
                float cx = x + (k % ccols) * (CAPE_W + GAP), cy = y + (k / ccols) * (CAPE_H + GAP);
                if (cy + CAPE_H >= bodyTop && cy <= top + h) {
                    capeCard(g, ui, tk, i < 0 ? null : capes.get(i), i < 0 ? !anyWorn : capes.get(i).cape.active(), cx, cy);
                }
            }
            y += ((capes.size() + ccols) / ccols) * (CAPE_H + GAP);
        }
        float end = y + 12f;
        g.endClip();
        float viewH = top + h - bodyTop;
        scroll.setMax(end + scroll.value() - bodyTop - viewH);
        scroll.step(screen.dt());
        scroll.bar(g, x + w + 4f, bodyTop, viewH);
    }

    private void skinCard(Gfx g, Ui ui, Tokens tk, int index, float x, float y) {
        Entry e = skins.get(index);
        boolean picked = index == selected;
        boolean hover = ui.over(x, y, CARD_W, CARD_H);
        int fi = ui.click(x, y, CARD_W, CARD_H, () -> selected = picked ? -1 : index);
        card(g, tk, x, y, CARD_W, CARD_H, picked, hover);
        if (e.texture != null) {
            front(g, e.texture, e.skin.slim(), x + CARD_W / 2f - 32f, y + 18f, 4f);
        }
        SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
        g.textCentre(f, t(e.skin.slim() ? "slim" : "wide"), x + CARD_W / 2f, y + CARD_H - 16f, picked ? tk.accent : 0x99FFFFFF);
        if (ui.focused(fi)) {
            g.stroke(x - 2f, y - 2f, CARD_W + 4f, CARD_H + 4f, 14f, 2f * g.d.u, tk.accent);
        }
    }

    private void capeCard(Gfx g, Ui ui, Tokens tk, CapeEntry e, boolean worn, float x, float y) {
        boolean hover = ui.over(x, y, CAPE_W, CAPE_H);
        int fi = ui.click(x, y, CAPE_W, CAPE_H, () -> wearCape(e == null ? null : e.cape));
        card(g, tk, x, y, CAPE_W, CAPE_H, worn, hover);
        SolFont f = g.font(SolFont.Family.MEDIUM, 12f);
        if (e == null) {
            g.textCentre(f, WardrobeScreen.tr("sol.wardrobe.capes.no_cape"), x + CAPE_W / 2f, y + CAPE_H / 2f - 8f, 0x99FFFFFF);
        } else {
            if (e.texture != null) {
                // The cape's front: 10 x 16 of the 64 x 32 texture, at (1, 1).
                float px = 4.5f, cw = 10f * px, ch = 16f * px;
                Canvas c = g.game();
                c.blitRegion(e.texture.id, WardrobeScreen.screenX(g, x + (CAPE_W - cw) / 2f), WardrobeScreen.screenY(g, y + 14f),
                        WardrobeScreen.screenSize(g, cw), WardrobeScreen.screenSize(g, ch), 1f, 1f, 10, 16, 64, 64);
                g.layer();
            }
            g.textCentre(f, g.fit(f, e.cape.alias(), CAPE_W - 12f), x + CAPE_W / 2f, y + CAPE_H - 16f, worn ? tk.accent : 0xD9FFFFFF);
        }
        if (ui.focused(fi)) {
            g.stroke(x - 2f, y - 2f, CAPE_W + 4f, CAPE_H + 4f, 14f, 2f * g.d.u, tk.accent);
        }
    }

    private static void card(Gfx g, Tokens tk, float x, float y, float w, float h, boolean on, boolean hover) {
        if (on) {
            g.glow(x, y, w, h, 12f, 20f, Colors.withAlpha(tk.glow, 0.25f));
        }
        g.round(x, y, w, h, 12f, hover ? Colors.mix(tk.panel, 0xFFFFFFFF, 0.06f) : tk.panel);
        g.stroke(x, y, w, h, 12f, Math.max(1f, g.d.u), on ? tk.accent : hover ? 0x38FFFFFF : 0x1AFFFFFF);
        g.highlight(x, y, w, h, 12f, 0x24FFFFFF);
    }

    /**
     * A skin from the front, flat: head, body, arms and legs, each with its outer layer over it, {@code px} design
     * pixels per skin pixel (16 x 32 skin pixels in all).
     */
    private static void front(Gfx g, PictureTexture texture, boolean slim, float x, float y, float px) {
        int arm = slim ? 3 : 4;
        // {u, v, width, height, x, y, outer layer u, outer layer v}, in skin pixels.
        int[][] parts = {
                {8, 8, 8, 8, 4, 0, 40, 8},
                {20, 20, 8, 12, 4, 8, 20, 36},
                {44, 20, arm, 12, 4 - arm, 8, 44, 36},
                {36, 52, arm, 12, 12, 8, 52, 52},
                {4, 20, 4, 12, 4, 20, 4, 36},
                {20, 52, 4, 12, 8, 20, 4, 52},
        };
        Canvas c = g.game();
        for (int layer = 0; layer < 2; layer++) {
            for (int[] p : parts) {
                int x0 = WardrobeScreen.screenX(g, x + p[4] * px), y0 = WardrobeScreen.screenY(g, y + p[5] * px);
                int x1 = WardrobeScreen.screenX(g, x + (p[4] + p[2]) * px), y1 = WardrobeScreen.screenY(g, y + (p[5] + p[3]) * px);
                int u = layer == 0 ? p[0] : p[6], v = layer == 0 ? p[1] : p[7];
                c.blitRegion(texture.id, x0, y0, x1 - x0, y1 - y0, u, v, p[2], p[3], 64, 64);
            }
        }
        g.layer();
    }

    boolean scroll(float amount) {
        return scroll.wheel(amount);
    }
}
