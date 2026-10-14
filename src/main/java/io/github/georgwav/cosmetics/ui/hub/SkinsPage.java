package io.github.georgwav.cosmetics.ui.hub;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.client.skins.OwnSkin;
import io.github.georgwav.cosmetics.client.skins.SkinLibrary;
import io.github.georgwav.cosmetics.client.skins.SkinUploader;
import io.github.georgwav.cosmetics.ui.FolderPicker;
import io.github.georgwav.cosmetics.ui.PictureTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

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
 * The Skins page: the skins saved in the launcher (Threadrinth's presets, read from its folder), each drawn from
 * the front; pick one and Apply changes it on your Minecraft account. The launcher's folder is found, or chosen
 * here once.
 */
final class SkinsPage implements HubPage {
    private static final float LEFT = 148f;
    private static final float RIGHT = 1292f;
    private static final float TOP = 204f;
    private static final float BOTTOM = 692f;
    private static final float CARD_W = 150f;
    private static final float CARD_H = 236f;
    private static final float GAP = 14f;

    /** A skin with its picture: the 64 x 64 ARGB pixels, and their texture once made (on the render thread). */
    private static final class Entry {
        final SkinLibrary.Skin skin;
        final int[] argb;
        PictureTexture texture;

        Entry(SkinLibrary.Skin skin, int[] argb) {
            this.skin = skin;
            this.argb = argb;
        }
    }

    /** How many skins the last Skins page shows (the in-game test). */
    static int shown;
    /** Whether the last read of the launcher's skins has finished (the in-game test waits for it). */
    static boolean read;

    private final HubScreen hub;
    private CompletableFuture<List<Entry>> loading;
    private List<Entry> skins = List.of();
    private String problem;
    private int selected = -1;
    private boolean uploading;
    private float scroll;
    private float contentHeight;

    SkinsPage(HubScreen hub) {
        this.hub = hub;
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.hub.skins." + key, args).getString();
    }

    // ------------------------------------------------------------------ loading

    /** Reads the launcher's skins in the background the first time the page is drawn (and after a new folder). */
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
                    int[] argb = pixels(skin.png());
                    if (argb != null) {
                        entries.add(new Entry(skin, argb));
                    }
                }
                return entries;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, SkinsPage::ownThread);
    }

    // A thread of its own, so a busy shared pool never holds the read up.
    private static void ownThread(Runnable task) {
        Thread thread = new Thread(task, "Cosmetics skins");
        thread.setDaemon(true);
        thread.start();
    }

    /** A skin's PNG as 64 x 64 ARGB pixels; an old 64 x 32 skin gets its right arm and leg as the left ones. */
    static int[] pixels(byte[] png) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
            if (image == null || image.getWidth() != 64 || (image.getHeight() != 64 && image.getHeight() != 32)) {
                return null;
            }
            int[] argb = new int[64 * 64];
            image.getRGB(0, 0, 64, image.getHeight(), argb, 0, 64);
            if (image.getHeight() == 32) {
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

    /** Takes in what the background read finished, and makes the textures. */
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
                e.texture = new PictureTexture("hub_skin_" + i, 64);
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
    }

    private void reload() {
        release();
        skins = List.of();
        selected = -1;
        scroll = 0f;
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
            hub.notify(t("no_picker"));
        }
    }

    private void useFolder(Path folder) {
        MainFolder.choose(folder);
        reload();
        hub.notify(MainFolder.database() != null ? t("folder_set") : t("no_database"));
    }

    /** Threadrinth's app folder when one was found on this computer and isn't the main folder already. */
    private static Path launcherToOffer() {
        Path launcher = MainFolder.launcherFolder();
        return launcher != null && !launcher.equals(MainFolder.folder()) ? launcher : null;
    }

    // ------------------------------------------------------------------ the page

    @Override
    public String title() {
        return t("title");
    }

    @Override
    public String line() {
        if (uploading) {
            return t("uploading");
        }
        if (selected >= 0 && selected < skins.size()) {
            return t(skins.get(selected).skin.slim() ? "picked_slim" : "picked_wide");
        }
        return t("pick");
    }

    @Override
    public String icon() {
        return "shirt";
    }

    @Override
    public String searchHint() {
        return t("search");
    }

    @Override
    public String actionLabel() {
        return selected >= 0 && selected < skins.size() && !uploading ? t("apply") : null;
    }

    /** Sends the picked skin to your account. */
    @Override
    public void action() {
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
                        hub.notify(t("offline"));
                    } else if (status == 200) {
                        OwnSkin.set(entry.argb, skin.slim());
                        hub.notify(t("applied"));
                    } else if (status == 401) {
                        hub.notify(t("signed_out"));
                    } else {
                        hub.notify(t("failed", status));
                    }
                }));
    }

    @Override
    public boolean overList(float x, float y) {
        return x >= LEFT && x < RIGHT && y >= TOP && y < BOTTOM;
    }

    @Override
    public void scrollBy(double amount) {
        float max = Math.max(0f, contentHeight - (BOTTOM - TOP));
        scroll = Math.max(0f, Math.min(max, scroll - (float) amount * 60f));
    }

    @Override
    public void draw(Ui ui, String query) {
        collect();
        Path db = MainFolder.database();
        Path launcher = db == null ? launcherToOffer() : null;
        float headerRight = ui.buttonRight(t("choose"), RIGHT, 140f, 36f, false, this::chooseFolder) - 8f;
        if (launcher != null) {
            headerRight = ui.buttonRight(t("use_launcher"), headerRight, 140f, 36f, true, () -> useFolder(launcher)) - 8f;
        }
        headerRight -= 8f;
        ui.text(ui.fit(t("title"), headerRight - LEFT, Ui.TITLE, true), LEFT, 136f, Ui.TEXT, Ui.TITLE, true);
        String folder = String.valueOf(MainFolder.folder().getFileName());
        String summary = db == null ? t("no_folder", folder) : t("summary", skins.size(), folder);
        ui.text(ui.fit(summary, headerRight - LEFT, Ui.SMALL, false), LEFT, 172f, Ui.TEXT_DIM, Ui.SMALL, false);

        ui.clip(LEFT, TOP, RIGHT, BOTTOM);
        String empty = problem != null ? problem
                : db == null ? (launcher != null ? t("found_hint", launcher) : t("choose_hint"))
                : !loading.isDone() ? t("loading") : skins.isEmpty() ? t("empty") : null;
        if (empty != null) {
            ui.text(ui.fit(empty, RIGHT - LEFT, Ui.SMALL, false), LEFT + 6f, TOP + 10f, Ui.TEXT_DIM, Ui.SMALL, false);
        }
        int columns = Math.max(1, (int) ((RIGHT - LEFT + GAP) / (CARD_W + GAP)));
        float rowW = columns * CARD_W + (columns - 1) * GAP;
        float x0 = LEFT + (RIGHT - LEFT - rowW) / 2f;
        for (int i = 0; i < skins.size(); i++) {
            float x = x0 + (i % columns) * (CARD_W + GAP);
            float y = TOP - scroll + (i / columns) * (CARD_H + GAP);
            if (y + CARD_H >= TOP && y <= BOTTOM) {
                card(ui, i, x, y);
            }
        }
        int rows = (skins.size() + columns - 1) / columns;
        contentHeight = rows * (CARD_H + GAP);
        ui.endClip();
        scrollBy(0);
    }

    private void card(Ui ui, int index, float x, float y) {
        Entry e = skins.get(index);
        boolean picked = index == selected;
        boolean hot = ui.hover(x, y, CARD_W, CARD_H);
        if (picked) {
            ui.roundRect(x - 3f, y - 3f, CARD_W + 6f, CARD_H + 6f, 23f, ui.accent);
        }
        ui.roundRect(x, y, CARD_W, CARD_H, 20f, hot && !picked ? 0xFF18181B : Ui.CARD);
        if (e.texture != null) {
            front(ui, e.texture, e.skin.slim(), x + CARD_W / 2f - 40f, y + 22f, 5f);
        }
        String label = t(e.skin.slim() ? "slim" : "wide");
        ui.text(label, x + (CARD_W - ui.textWidth(label, Ui.SMALL, false)) / 2f, y + CARD_H - 30f, Ui.TEXT_DIM,
                Ui.SMALL, false);
        ui.click(x, y, CARD_W, CARD_H, () -> selected = picked ? -1 : index);
    }

    /**
     * A skin from the front, flat: head, body, arms and legs, each with its outer layer over it, {@code px} design
     * pixels per skin pixel (16 x 32 skin pixels in all).
     */
    private static void front(Ui ui, PictureTexture texture, boolean slim, float x, float y, float px) {
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
        for (int layer = 0; layer < 2; layer++) {
            for (int[] p : parts) {
                int x0 = Math.round(ui.sx(x + p[4] * px));
                int y0 = Math.round(ui.sy(y + p[5] * px));
                int x1 = Math.round(ui.sx(x + (p[4] + p[2]) * px));
                int y1 = Math.round(ui.sy(y + (p[5] + p[3]) * px));
                int u = layer == 0 ? p[0] : p[6];
                int v = layer == 0 ? p[1] : p[7];
                ui.g.blitRegion(texture.id, x0, y0, x1 - x0, y1 - y0, u, v, p[2], p[3], 64, 64);
            }
        }
    }
}
