package io.github.georgwav.cosmetics.ui.hub;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.modules.TimeChanger;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.CosmeticsScreen;
import io.github.georgwav.cosmetics.ui.KeyBinds;
import io.github.georgwav.cosmetics.ui.ScreenOpener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
//? if >=26.1 {
/*import net.minecraft.client.gui.GuiGraphicsExtractor;
*///?} else {
import net.minecraft.client.gui.GuiGraphics;
//?}
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The hub (Right Shift): one sphere of orbs you can turn in every direction, you in the middle. The orb in the middle
 * is read out by the island at the top, which is also where you use it; the bar at the bottom steps through the
 * orbs and searches them. Mods is a sphere of its own: the hub's sphere is drawn into its middle orb and the mods
 * sphere pours out of it.
 *
 * <p>Laid out on a 1440 x 810 design (the one on the design canvas), scaled to fit the window and drawn in screen
 * pixels so its round shapes are smooth (see {@link Shapes}).
 */
public final class HubScreen extends Screen {
    /** For the in-game test: frames drawn so far. */
    public static int framesRendered;

    static final String MAIN = "main";
    static final String MODS = "mods";

    private static final float DESIGN_W = 1440f;
    private static final float DESIGN_H = 810f;
    private static final float CX = 720f;
    private static final float CY = 400f;
    private static final float STEP = 1f / 60f;

    static final int ACCENT = 0xFFBDBDBD;
    static final boolean INK_DARK = lightness(ACCENT) > 0.35f;
    static final int INK = INK_DARK ? 0xFF111114 : 0xFFFFFFFF;
    private static final int WELL = 0xFF1C1C1F;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int TEXT_DIM = 0x9EFFFFFF;

    private enum Mode { IDLE, DRAG, COAST, SNAP, TRANS }

    private final Screen parent;
    private final Map<String, Sphere> spheres = new HashMap<>();
    private String level = MAIN;
    private float[] q = {1f, 0f, 0f, 0f};
    private int sel;
    private Mode mode = Mode.IDLE;

    // Turning: coasting speed, and the spring that settles an orb in the middle.
    private float vx;
    private float vy;
    private float[] qa;
    private float[] qb;
    private float t;
    private float tv;

    // Changing spheres: 1 = spread out, 0 = drawn into the middle orb.
    private float spread = 1f;
    private float spreadV;
    private String transTo;
    private int transEntry = -1;
    private boolean transOut;
    private long transStart;

    // The mouse, in design pixels.
    private boolean pressed;
    private boolean moved;
    private float pressX;
    private float pressY;
    private float lastX;
    private float lastY;
    private int pressOrb = -1;
    private int hover = -1;
    private float wheelSum;

    private long lastNanos;
    private float pending;
    // The island's width eases to fit what it shows.
    private float islandW = 440f;
    private float islandTarget = 440f;

    private EditBox search;
    private String query = "";
    private String notice;
    private long noticeUntil;

    // This frame's layout: design pixel -> screen pixel, and what can be clicked.
    private float gs = 1f;
    private float u = 1f;
    private float ox;
    private float oy;
    private final List<float[]> orbHits = new ArrayList<>();
    private final List<Ui.Hit> hits = new ArrayList<>();
    private final Map<String, Float> eased = Ui.newEasing();

    // A page open over the hub (the Mods list), or null; how open it is (0 = the middle orb, 1 = open).
    static final String MODS_PAGE = "mods";
    static final String SKINS_PAGE = "skins";
    private String page;
    private float pageP;
    private float pageV;
    /** The page a transition opens, or null when it goes to a sphere. */
    private String transPage;
    private final ModsPage modsPage = new ModsPage(this);
    private final SkinsPage skinsPage = new SkinsPage(this);
    /** The slider being dragged. */
    private Ui.Hit dragging;
    /** The key bind waiting for a key (or mouse button). */
    private KeyMapping listening;

    public HubScreen(Screen parent) {
        super(Component.translatable("cosmeticsmod.hub.title"));
        this.parent = parent;
        buildSpheres();
        q = sphere().turnedTo(q, 0);
    }

    /** For the in-game test: the hub already turned into its mods sphere. */
    public static HubScreen inMods() {
        HubScreen hub = new HubScreen(null);
        hub.level = MODS;
        hub.q = new float[]{1f, 0f, 0f, 0f};
        hub.q = hub.sphere().turnedTo(hub.q, 0);
        return hub;
    }

    /** The hub opening the Mods page on one module's settings (right-click in the HUD editor). */
    public static HubScreen moduleSettings(String moduleId) {
        HubScreen hub = inMods();
        hub.modsPage.show(moduleId);
        hub.openPage(MODS_PAGE);
        return hub;
    }

    /** For the in-game test: the Skins page already open. */
    public static HubScreen onSkinsPage() {
        HubScreen hub = new HubScreen(null);
        hub.page = SKINS_PAGE;
        hub.pageP = 1f;
        hub.spread = 0f;
        return hub;
    }

    /** For the in-game test: how many skins the last Skins page shows. */
    public static int skinsShown() {
        return SkinsPage.shown;
    }

    /** For the in-game test: the Mods page already open, on the crosshair's settings and preview. */
    public static HubScreen onModsPage() {
        HubScreen hub = inMods();
        hub.page = MODS_PAGE;
        hub.pageP = 1f;
        hub.spread = 0f;
        hub.modsPage.show("crosshair");
        return hub;
    }

    // ------------------------------------------------------------------ what's on the spheres

    private void buildSpheres() {
        CosmeticsConfig c = CosmeticsConfig.get();
        HubItem shaders = new HubItem("shaders", "b", HubItem.Kind.TOGGLE).icon("shaders")
                .toggle(Compat::isShaderPackActive, Compat::setShadersEnabled)
                .missing(() -> Compat.IRIS ? null : t("missing", "Iris"));
        HubItem voxy = new HubItem("voxy", "b", HubItem.Kind.LATER).icon("voxy")
                .missing(() -> Compat.VOXY ? null : t("missing", "Voxy"));
        List<HubItem> main = List.of(
                new HubItem("you", "you", HubItem.Kind.ACTION).you().button("customize")
                        .action(() -> ScreenOpener.open(new CosmeticsScreen(this))),
                new HubItem("mods", "a", HubItem.Kind.LEVEL).icon("grid").to(MODS),
                new HubItem("settings", "b", HubItem.Kind.ACTION).icon("sliders")
                        .action(() -> ScreenOpener.open(new CosmeticsScreen(this, 1))),
                shaders,
                voxy,
                new HubItem("skins", "you", HubItem.Kind.ACTION).icon("shirt").action(() -> openPage(SKINS_PAGE)),
                new HubItem("emotes", "c", HubItem.Kind.ACTION).icon("smile")
                        .action(() -> ScreenOpener.open(CosmeticsScreen.emotes(this))),
                new HubItem("music", "d", HubItem.Kind.LATER).icon("music"),
                new HubItem("hud", "e", HubItem.Kind.ACTION).icon("layout")
                        .action(() -> ScreenOpener.open(new HudEditorScreen(this))),
                new HubItem("shots", "f", HubItem.Kind.ACTION).icon("camera").action(this::openScreenshots)
                        .missing(() -> FabricLoader.getInstance().isModLoaded("screenshot_viewer") ? null
                                : t("missing", "Screenshot Viewer")));
        spheres.put(MAIN, new Sphere(main, Map.of(
                "you", Sphere.unit(new float[]{0f, 0f, 1f}), "a", Sphere.unit(new float[]{1f, 0f, 0.55f}),
                "b", Sphere.unit(new float[]{-1f, 0f, 0.55f}), "c", Sphere.unit(new float[]{0f, -1f, 0.55f}),
                "d", Sphere.unit(new float[]{0f, 1f, 0.55f}), "e", Sphere.unit(new float[]{0.7f, -0.7f, -0.4f}),
                "f", Sphere.unit(new float[]{-0.7f, 0.7f, -0.4f})), 55, 0.6f, 250f));

        HubItem modsShaders = new HubItem("shaders", "render", HubItem.Kind.TOGGLE).icon("shaders")
                .toggle(Compat::isShaderPackActive, Compat::setShadersEnabled)
                .missing(() -> Compat.IRIS ? null : t("missing", "Iris"));
        HubItem modsVoxy = new HubItem("voxy", "render", HubItem.Kind.LATER).icon("voxy")
                .missing(() -> Compat.VOXY ? null : t("missing", "Voxy"));
        List<HubItem> mods = new ArrayList<>(List.of(
                new HubItem("all", "center", HubItem.Kind.ACTION).icon("list").action(() -> openPage(MODS_PAGE)),
                modsShaders,
                modsVoxy,
                HubItem.module(Modules.FULLBRIGHT, "render"),
                HubItem.module(Modules.CROSSHAIR, "render"),
                HubItem.module(Modules.WEATHER, "render"),
                HubItem.module(Modules.TIME, "render")
                        .missing(() -> TimeChanger.supported() ? null : t("not_on_version")),
                HubItem.module(Modules.KEYSTROKES, "modules"),
                HubItem.module(Modules.TOGGLE_SPRINT, "modules"),
                HubItem.module(Modules.FPS, "hud").live("fps"),
                HubItem.module(Modules.CLOCK, "hud").live("clock"),
                HubItem.module(Modules.COORDS, "hud"),
                HubItem.module(Modules.ARMOR, "hud"),
                HubItem.module(Modules.EFFECTS, "hud"),
                HubItem.module(Modules.PING, "hud"),
                new HubItem("others", "cosmetics", HubItem.Kind.TOGGLE).icon("users")
                        .toggle(() -> c.showOtherPlayersCosmetics, v -> c.showOtherPlayersCosmetics = v),
                new HubItem("particles", "cosmetics", HubItem.Kind.TOGGLE).icon("sparkle")
                        .toggle(() -> c.particles, v -> c.particles = v),
                new HubItem("wavy", "cosmetics", HubItem.Kind.TOGGLE).icon("wave")
                        .toggle(() -> c.wavyCapes, v -> c.wavyCapes = v),
                new HubItem("glow", "cosmetics", HubItem.Kind.TOGGLE).icon("sun")
                        .toggle(() -> c.emissiveParts, v -> c.emissiveParts = v),
                new HubItem("shadows", "cosmetics", HubItem.Kind.TOGGLE).icon("half")
                        .toggle(() -> c.renderInShadowPass, v -> c.renderInShadowPass = v)));
        // Mods the client works with, each an orb only when it's installed: its orb opens its settings.
        for (String[] dep : DEPENDENCIES) {
            FabricLoader.getInstance().getModContainer(dep[0]).ifPresent(mod -> {
                HubItem item = new HubItem("dep_" + dep[0], dep[2], HubItem.Kind.ACTION).icon(dep[1]).button("settings")
                        .action(() -> openModSettings(dep[0]));
                String name = mod.getMetadata().getName();
                item.label = () -> name;
                item.note = () -> t("dep." + dep[0]);
                mods.add(item);
            });
        }
        spheres.put(MODS, new Sphere(mods, Map.of(
                "center", Sphere.unit(new float[]{0f, 0f, 1f}), "render", Sphere.unit(new float[]{1f, 0f, 0.35f}),
                "modules", Sphere.unit(new float[]{-1f, 0f, 0.35f}), "hud", Sphere.unit(new float[]{0f, -1f, 0.2f}),
                "cosmetics", Sphere.unit(new float[]{0f, 1f, 0.2f}), "inventory", Sphere.unit(new float[]{0.7f, 0.7f, -0.3f}),
                "chat", Sphere.unit(new float[]{-0.7f, -0.7f, -0.3f}), "tools", Sphere.unit(new float[]{0f, 0f, -1f})),
                64, 0.3f, 290f));
    }

    private Sphere sphere() {
        return spheres.get(level);
    }

    /** Mods the client works with when installed: mod id, icon, where on the mods sphere. */
    private static final String[][] DEPENDENCIES = {
            {"zoomify", "zoom", "modules"}, {"freelook", "eye", "modules"},
            {"mousetweaks", "mouse", "inventory"}, {"appleskin", "apple", "inventory"},
            {"shulkerboxtooltip", "box", "inventory"}, {"inventoryprofilesnext", "sort", "inventory"},
            {"chatpatches", "chat", "chat"}, {"chat_heads", "users", "chat"}, {"nochatreports", "shield", "chat"},
            {"jade", "target", "tools"}, {"betterf3", "debug", "tools"}, {"xaerominimap", "map", "tools"},
            {"voicechat", "mic", "tools"},
    };

    /** A mod's own settings screen, as Mod Menu's "Configure" opens it; back from it comes back to the hub. */
    void openModSettings(String modId) {
        if (!Compat.MOD_MENU) {
            notify(t("needs_mod_menu"));
            return;
        }
        try {
            Object screen = Class.forName("com.terraformersmc.modmenu.ModMenu")
                    .getMethod("getConfigScreen", String.class, Screen.class).invoke(null, modId, this);
            if (screen instanceof Screen s) {
                ScreenOpener.open(s);
            } else {
                notify(t("mods.no_settings"));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("Couldn't open the settings of {}", modId, e);
            notify(t("mods.no_settings"));
        }
    }

    /** Screenshot Viewer's own screen (your screenshots); back from it comes back to the hub. */
    private void openScreenshots() {
        try {
            Screen screen = (Screen) Class.forName("io.github.lgatodu47.screenshot_viewer.screen.manage_screenshots.ManageScreenshotsScreen")
                    .getConstructor(Screen.class).newInstance(this);
            ScreenOpener.open(screen);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("Couldn't open Screenshot Viewer", e);
            notify(t("missing", "Screenshot Viewer"));
        }
    }

    /** Mod Menu's list of mods, if it is installed; back from it comes back here. */
    void openModMenu() {
        try {
            Screen list = (Screen) Class.forName("com.terraformersmc.modmenu.gui.ModsScreen")
                    .getConstructor(Screen.class).newInstance(this);
            ScreenOpener.open(list);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("Couldn't open Mod Menu's list", e);
            notify(t("missing", "Mod Menu"));
        }
    }

    private static String t(String key, Object... args) {
        return Component.translatable("cosmeticsmod.hub." + key, args).getString();
    }

    private String name(HubItem it) {
        if (it.you) {
            Minecraft mc = Minecraft.getInstance();
            String player = mc.player != null ? mc.player.getName().getString() : t("you_unknown");
            return t("you", player);
        }
        return it.label != null ? it.label.get() : t("item." + it.id);
    }

    // ------------------------------------------------------------------ screen

    @Override
    protected void init() {
        // Typing anywhere searches: the field is never drawn by the game, the bar at the bottom shows it.
        search = new EditBox(font, -1000, -1000, 200, 20, Component.translatable("cosmeticsmod.hub.search"));
        search.setMaxLength(48);
        search.setValue(query);
        search.setResponder(this::onQuery);
        addWidget(search);
        setInitialFocus(search);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        CosmeticsConfig.save();
        EquippedCosmetics.invalidate();
        ScreenOpener.open(parent);
    }

    @Override
    public void removed() {
        skinsPage.release();
        super.removed();
    }

    void notify(String text) {
        notice = text;
        noticeUntil = System.currentTimeMillis() + 2600;
    }

    // ------------------------------------------------------------------ turning and changing spheres

    private void spinBy(float yaw, float pitch) {
        float[] turn = Sphere.qmul(Sphere.qaxis(1f, 0f, 0f, pitch), Sphere.qaxis(0f, 1f, 0f, yaw));
        q = Sphere.qnorm(Sphere.qmul(turn, q));
    }

    private void snapTo(int i) {
        if (mode == Mode.TRANS) {
            transEntry = i;
            return;
        }
        sel = i;
        qa = q;
        qb = sphere().turnedTo(q, i);
        t = 0f;
        tv = 0f;
        mode = Mode.SNAP;
    }

    private void step(int d) {
        if (mode == Mode.TRANS) {
            return;
        }
        int n = sphere().items.size();
        snapTo(Math.floorMod(sel + d, n));
    }

    /** Into the mods sphere or back: this one spins and is drawn into its middle orb, then the other pours out. */
    private void enter(String to, int entry) {
        if (mode == Mode.TRANS || to.equals(level) || page != null) {
            return;
        }
        transPage = null;
        hover = -1;
        pressed = false;
        transTo = to;
        transEntry = entry;
        transOut = true;
        transStart = System.nanoTime();
        mode = Mode.TRANS;
    }

    /** Opens a page over the hub: the sphere is drawn into its middle orb, and the page grows out of it. */
    void openPage(String id) {
        if (mode == Mode.TRANS || page != null) {
            return;
        }
        hover = -1;
        pressed = false;
        transTo = level;
        transPage = id;
        transEntry = sel;
        transOut = true;
        transStart = System.nanoTime();
        mode = Mode.TRANS;
    }

    /** Back from the page to the sphere. */
    void closePage() {
        if (mode == Mode.TRANS || page == null) {
            return;
        }
        listening = null;
        dragging = null;
        transTo = level;
        transPage = null;
        transEntry = sel;
        transOut = true;
        transStart = System.nanoTime();
        mode = Mode.TRANS;
        if (!query.isEmpty()) {
            search.setValue("");
        }
    }

    KeyMapping listeningFor() {
        return listening;
    }

    void listenFor(KeyMapping key) {
        listening = key;
    }

    private void bindListened(InputConstants.Key key) {
        KeyBinds.bind(listening, key);
        listening = null;
    }

    /** Using the orb in the middle. */
    private void use(int i) {
        HubItem it = sphere().items.get(i);
        String missing = it.missing.get();
        if (missing != null) {
            notify(missing);
            return;
        }
        switch (it.kind) {
            case TOGGLE -> it.set.accept(!it.on.getAsBoolean());
            case LEVEL -> enter(it.to, -1);
            case ACTION -> it.action.run();
            case LATER -> notify(t("later"));
        }
    }

    /** Moves the animation on by whole steps of 1/60 s, so it runs the same at any frame rate. */
    private void advance() {
        long now = System.nanoTime();
        if (lastNanos != 0) {
            pending += Math.min(0.25f, (now - lastNanos) / 1e9f);
        }
        lastNanos = now;
        while (pending >= STEP) {
            pending -= STEP;
            stepOnce();
        }
    }

    private void stepOnce() {
        islandW += (islandTarget - islandW) * 0.25f;
        switch (mode) {
            case COAST -> {
                // Let go: it keeps spinning, slows down, then the nearest usable orb settles in the middle.
                spinBy(vx, vy);
                vx *= 0.94f;
                vy *= 0.94f;
                if (Math.hypot(vx, vy) < 0.0025) {
                    snapTo(sphere().nearest(q));
                }
            }
            case SNAP -> {
                tv = (tv + (1f - t) * 0.08f) * 0.8f;
                t += tv;
                if (Math.abs(1f - t) < 0.001f && Math.abs(tv) < 0.001f) {
                    q = qb;
                    mode = Mode.IDLE;
                } else {
                    q = Sphere.slerp(qa, qb, t);
                }
            }
            case TRANS -> stepTransition();
            default -> {
            }
        }
    }

    private void stepTransition() {
        if (transOut && page != null) {
            // The page shrinks back into the middle orb; then the sphere pours out of it.
            float k = Math.min(1f, (System.nanoTime() - transStart) / 300e6f);
            pageP = 1f - k * k;
            if (k >= 1f) {
                page = null;
                pageP = 0f;
                transOut = false;
                spread = 0f;
                spreadV = 0f;
            }
            return;
        }
        if (!transOut && page != null) {
            pageV = (pageV + (1f - pageP) * 0.05f) * 0.8f;
            pageP += pageV;
            if (Math.abs(1f - pageP) < 0.002f && Math.abs(pageV) < 0.002f) {
                pageP = 1f;
                mode = Mode.IDLE;
            }
            return;
        }
        if (transOut) {
            float k = Math.min(1f, (System.nanoTime() - transStart) / 420e6f);
            spread = 1f - k * k;
            if (k >= 1f && transPage != null) {
                transOut = false;
                page = transPage;
                transPage = null;
                pageP = 0f;
                pageV = 0f;
                spread = 0f;
                return;
            }
            if (k >= 1f) {
                String from = level;
                level = transTo;
                transOut = false;
                spreadV = 0f;
                List<HubItem> items = sphere().items;
                int entry = transEntry;
                if (entry < 0) {
                    entry = 0;
                    for (int i = 0; i < items.size(); i++) {
                        if (from.equals(items.get(i).to)) {
                            entry = i;
                        }
                    }
                }
                q = new float[]{1f, 0f, 0f, 0f};
                q = sphere().turnedTo(q, entry);
                sel = entry;
                transEntry = entry;
                spread = 0f;
            }
        } else {
            spreadV = (spreadV + (1f - spread) * 0.06f) * 0.8f;
            spread += spreadV;
            if (transEntry >= 0 && transEntry != sel) {
                sel = transEntry;
                q = sphere().turnedTo(q, transEntry);
            }
            if (Math.abs(1f - spread) < 0.002f && Math.abs(spreadV) < 0.002f) {
                spread = 1f;
                mode = Mode.IDLE;
            }
        }
    }

    // ------------------------------------------------------------------ search

    private void onQuery(String text) {
        query = text;
        if (page != null) {
            return;
        }
        int[] m = firstMatch(text);
        if (m != null) {
            goToMatch(m);
        }
    }

    /** The first orb whose name has the query in it: this sphere first, then the other. {sphere 0 main/1 mods, item} */
    private int[] firstMatch(String text) {
        String needle = text.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return null;
        }
        for (String lv : level.equals(MAIN) ? new String[]{MAIN, MODS} : new String[]{MODS, MAIN}) {
            List<HubItem> items = spheres.get(lv).items;
            for (int i = 0; i < items.size(); i++) {
                if (name(items.get(i)).toLowerCase(Locale.ROOT).contains(needle)) {
                    return new int[]{lv.equals(MAIN) ? 0 : 1, i};
                }
            }
        }
        return null;
    }

    private void goToMatch(int[] m) {
        String lv = m[0] == 0 ? MAIN : MODS;
        if (!lv.equals(level)) {
            enter(lv, m[1]);
        } else if (m[1] != sel || mode != Mode.IDLE) {
            snapTo(m[1]);
        }
    }

    // ------------------------------------------------------------------ input

    private float designX(double guiX) {
        return ((float) guiX * gs - ox) / u;
    }

    private float designY(double guiY) {
        return ((float) guiY * gs - oy) / u;
    }

    private int orbAt(float x, float y) {
        int best = -1;
        float bestZ = -1f;
        for (float[] o : orbHits) {
            float dx = x - o[0];
            float dy = y - o[1];
            if (dx * dx + dy * dy <= o[2] * o[2] && o[3] > bestZ) {
                bestZ = o[3];
                best = (int) o[4];
            }
        }
        return best;
    }

    private boolean press(double guiX, double guiY, int button) {
        if (listening != null && button != 0) {
            // A key bind waiting for a key takes the mouse button too (the left one only stops it waiting).
            bindListened(KeyBinds.mouse(button));
            return true;
        }
        if (button != 0) {
            return false;
        }
        float x = designX(guiX);
        float y = designY(guiY);
        for (Ui.Hit hit : hits) {
            if (hit.contains(x, y)) {
                if (hit.drag() != null) {
                    dragging = hit;
                    hit.drag().accept(x);
                } else {
                    hit.action().run();
                }
                return true;
            }
        }
        listening = null;
        if (page != null) {
            return true;
        }
        if (mode == Mode.TRANS || y < 90f || y > 726f) {
            return false;
        }
        pressed = true;
        moved = false;
        pressX = lastX = x;
        pressY = lastY = y;
        pressOrb = orbAt(x, y);
        return true;
    }

    private boolean drag(double guiX, double guiY) {
        if (dragging != null) {
            dragging.drag().accept(designX(guiX));
            return true;
        }
        if (!pressed) {
            return false;
        }
        float x = designX(guiX);
        float y = designY(guiY);
        if (!moved) {
            if (Math.hypot(x - pressX, y - pressY) < 6) {
                return true;
            }
            moved = true;
            mode = Mode.DRAG;
            vx = 0f;
            vy = 0f;
        }
        float yaw = (x - lastX) * 0.0065f;
        float pitch = -(y - lastY) * 0.0065f;
        lastX = x;
        lastY = y;
        spinBy(yaw, pitch);
        vx = vx * 0.5f + yaw * 0.5f;
        vy = vy * 0.5f + pitch * 0.5f;
        return true;
    }

    private boolean release(double guiX, double guiY) {
        if (dragging != null) {
            dragging = null;
            return true;
        }
        if (!pressed) {
            return false;
        }
        pressed = false;
        if (moved) {
            mode = Mode.COAST;
        } else if (pressOrb >= 0 && pressOrb == orbAt(designX(guiX), designY(guiY))) {
            if (pressOrb == sel && mode == Mode.IDLE) {
                use(pressOrb);
            } else {
                snapTo(pressOrb);
            }
        }
        return true;
    }

    /** Keys the hub handles itself; the rest go to the search field. {@code asKey} is the key as a key bind takes it. */
    private boolean key(int key, InputConstants.Key asKey) {
        if (listening != null) {
            bindListened(key == InputConstants.KEY_ESCAPE ? InputConstants.UNKNOWN : asKey);
            return true;
        }
        if (page != null) {
            if (key == InputConstants.KEY_ESCAPE) {
                if (!query.isEmpty()) {
                    search.setValue("");
                } else {
                    closePage();
                }
                return true;
            }
            return false;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            if (!query.isEmpty()) {
                search.setValue("");
            } else if (!level.equals(MAIN)) {
                enter(MAIN, -1);
            } else {
                onClose();
            }
            return true;
        }
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            int[] m = firstMatch(query);
            if (m != null) {
                boolean here = (m[0] == 0) == level.equals(MAIN);
                goToMatch(m);
                if (here) {
                    use(m[1]);
                }
            } else if (mode == Mode.IDLE) {
                use(sel);
            }
            return true;
        }
        if (query.isEmpty() && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
            step(key == InputConstants.KEY_LEFT ? -1 : 1);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page != null) {
            if (currentPage().overList(designX(mouseX), designY(mouseY))) {
                currentPage().scrollBy(scrollY);
            }
            return true;
        }
        wheelSum += (float) (scrollY != 0 ? scrollY : -scrollX);
        if (Math.abs(wheelSum) >= 1f) {
            step(wheelSum > 0 ? -1 : 1);
            wheelSum = 0f;
        }
        return true;
    }

    // Click, drag and key signatures changed in 1.21.9.
    //? if >=1.21.9 {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return press(event.x(), event.y(), event.button()) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return drag(event.x(), event.y()) || super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return release(event.x(), event.y()) || super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return key(event.key(), KeyBinds.keyboard(event)) || super.keyPressed(event);
    }
    //?} else {
    /*@Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return press(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        return drag(mouseX, mouseY) || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return release(mouseX, mouseY) || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return key(keyCode, KeyBinds.keyboard(keyCode, scanCode)) || super.keyPressed(keyCode, scanCode, modifiers);
    }
    *///?}

    // ------------------------------------------------------------------ drawing

    // Minecraft's draw entry points; 26.1 renamed them. Behind the hub is the game's own menu background: the world
    // blurred and darkened. From 1.21.6 the game draws it before the screen; before that the screen draws it, once.
    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(g), mouseX, mouseY);
    }
    *///?} elif >=1.21.6 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        draw(new Canvas(g), mouseX, mouseY);
    }
    //?} else {
    /*@Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        draw(new Canvas(g), mouseX, mouseY);
    }
    *///?}

    private float sx(float x) {
        return ox + x * u;
    }

    private float sy(float y) {
        return oy + y * u;
    }

    private void draw(Canvas g, int mouseX, int mouseY) {
        framesRendered++;
        Minecraft mc = Minecraft.getInstance();
        gs = (float) mc.getWindow().getGuiScale();
        float screenW = width * gs;
        float screenH = height * gs;
        u = Math.min(screenW / DESIGN_W, screenH / DESIGN_H);
        ox = (screenW - DESIGN_W * u) / 2f;
        oy = (screenH - DESIGN_H * u) / 2f;
        advance();
        if (notice != null && System.currentTimeMillis() > noticeUntil) {
            notice = null;
        }
        float mx = designX(mouseX + 0.5);
        float my = designY(mouseY + 0.5);

        g.push();
        g.scale(1f / gs, 1f / gs);
        g.gradient(0, 0, (int) Math.ceil(screenW), (int) Math.ceil(screenH), 0x500C0B16, 0x900C0B16);
        hits.clear();
        if (page != null) {
            drawPage(g, mx, my);
        } else {
            drawSphere(g, mx, my);
        }
        g.nextLayer();
        drawIsland(g);
        drawBar(g);
        g.nextLayer();
        if (page == null) {
            drawTooltip(g);
        }
        g.pop();
    }

    /**
     * A page: one big panel grown out of the middle orb, its edge rippling all the time, squarer as it opens; its
     * contents once it's (nearly) open.
     */
    private void drawPage(Canvas g, float mx, float my) {
        float p = pageP;
        float q01 = Math.max(0f, Math.min(1f, p));
        float start = 155f;
        float a = (start + (1280f - start) * p) / 2f;
        float b = (start + (640f - start) * p) / 2f;
        float n = 2f + 6f * q01;
        float amp = 3f + 2f * q01;
        float cx = CX;
        float cy = CY + 12f * p;
        double time = System.nanoTime() / 1e9;
        int count = 160;
        float[] xs = new float[count];
        float[] ys = new float[count];
        float e = 2f / n;
        for (int i = 0; i < count; i++) {
            double th = i * Math.PI * 2 / count;
            double c = Math.cos(th);
            double sn = Math.sin(th);
            double x = a * Math.signum(c) * Math.pow(Math.abs(c), e);
            double y = b * Math.signum(sn) * Math.pow(Math.abs(sn), e);
            double r = Math.max(1e-6, Math.hypot(x, y));
            double wave = amp * (0.5 * Math.sin(2 * th + time * 0.7 + 1.2) + 0.35 * Math.sin(3 * th - time * 1.1 + 2.4)
                    + 0.25 * Math.sin(5 * th + time * 1.6 + 3.6));
            xs[i] = sx((float) (cx + x + x / r * wave));
            ys[i] = sy((float) (cy + y + y / r * wave));
        }
        Shapes.fill(g, Shapes.polygon(xs, ys, count), 0xF2070709);
        if (p > 0.85f) {
            Ui ui = new Ui(g, font, u, ox, oy, gs, mx, my, ACCENT, INK, hits, eased);
            currentPage().draw(ui, query);
        }
    }

    private HubPage currentPage() {
        return SKINS_PAGE.equals(page) ? skinsPage : modsPage;
    }

    // Orbs drawn this frame, back to front; the tooltip needs the hovered one.
    private float tipX;
    private float tipY;
    private String tipText;

    private void drawSphere(Canvas g, float mx, float my) {
        Sphere s = sphere();
        boolean moving = mode == Mode.DRAG || mode == Mode.COAST;
        int centered = moving ? s.nearest(q) : sel;
        String needle = query.trim().toLowerCase(Locale.ROOT);
        // While the spheres change, everything swirls around the middle and is drawn into it (or pours out of it).
        float swirl = (1f - spread) * 2.2f;
        float cs = (float) Math.cos(swirl);
        float sn = (float) Math.sin(swirl);
        float radius = s.radius * spread;
        double time = System.nanoTime() / 1e9;

        int n = s.dirs.length;
        float[][] orbs = new float[n][];
        Integer[] order = new Integer[n];
        for (int j = 0; j < n; j++) {
            float[] w = Sphere.qrot(q, s.dirs[j]);
            float depth = (w[2] + 1f) / 2f;
            float rx = w[0] * cs - w[1] * sn;
            float ry = w[0] * sn + w[1] * cs;
            int i = s.owner[j];
            boolean usable = i >= 0;
            boolean isCenter = usable && i == centered;
            // The one in the middle grows as it arrives, and swells while the sphere pours into it.
            float arrive = usable ? Math.max(0f, Math.min(1f, (w[2] - 0.95f) / 0.05f)) : 0f;
            float boost = 1f + 0.38f * arrive + (isCenter ? 0.9f * (1f - spread) : 0f);
            float shrink = isCenter ? 1f : 0.35f + 0.65f * spread;
            float scale = (0.42f + 0.58f * depth) * boost * shrink * (usable && hover == i ? 1.08f : 1f);
            float size = usable ? 68f : s.size[j];
            // Each orb floats up and down a little, out of step with the others.
            double period = usable ? 4.4 + (i % 3) * 0.7 : 3.6 + ((int) s.size[j] % 5) * 0.6;
            float bob = (float) Math.sin((time / period + j * 0.37) * Math.PI * 2) * 4f;
            float z = depth * 200f + (isCenter ? 210f : 1f);
            orbs[j] = new float[]{CX + radius * rx, CY + radius * ry + bob, size * scale, depth, i, z, isCenter ? 1 : 0};
            order[j] = j;
        }
        Arrays.sort(order, (a, b) -> Float.compare(orbs[a][5], orbs[b][5]));

        orbHits.clear();
        tipText = null;
        int hovered = -1;
        for (int j : order) {
            float[] o = orbs[j];
            int i = (int) o[4];
            if (i >= 0) {
                orbHits.add(new float[]{o[0], o[1], o[2] / 2f, o[5], i});
            }
        }
        if (!pressed && mode != Mode.TRANS) {
            hovered = orbAt(mx, my);
        }
        hover = hovered;

        for (int j : order) {
            float[] o = orbs[j];
            float depth = o[3];
            int i = (int) o[4];
            boolean usable = i >= 0;
            HubItem it = usable ? s.items.get(i) : null;
            boolean isOn = usable && it.kind == HubItem.Kind.TOGGLE && it.on.getAsBoolean();
            float opacity = depth > 0.5f ? 1f : (0.25f + 0.75f * depth) * (0.4f + 0.6f * spread);
            float cx = sx(o[0]);
            float cy = sy(o[1]);
            float r = o[2] * u / 2f;
            int haze = Math.round(46f * (1f - depth));
            int base = isOn ? ACCENT : 0xFF000000 | haze << 16 | haze << 8 | Math.min(255, Math.round(haze * 1.2f));
            int body = Shapes.shade(base, 0.55f + 0.45f * depth);
            // A darker rim at the bottom, the body, and a soft shine at the top left.
            Shapes.fill(g, Shapes.circle(cx, cy, r), Shapes.fade(Shapes.shade(body, 0.62f), opacity));
            Shapes.fill(g, Shapes.circle(cx, cy - 0.07f * r, r * 0.92f), Shapes.fade(body, opacity));
            // The shine fades out from its middle: a few circles, each smaller and adding a little more light.
            float shine = (isOn ? 0.35f : 0.05f + 0.13f * depth) * opacity;
            for (int ring = 0; ring < 4; ring++) {
                Shapes.fill(g, Shapes.circle(cx - 0.34f * r, cy - 0.44f * r, r * (0.42f - ring * 0.09f)),
                        Shapes.fade(0xFFFFFFFF, shine * 0.32f));
            }
            if (!usable) {
                continue;
            }
            boolean isCenter = o[6] > 0f;
            boolean match = !needle.isEmpty() && name(it).toLowerCase(Locale.ROOT).contains(needle);
            float iconOpacity = (0.3f + 0.7f * depth) * (!needle.isEmpty() && !match ? 0.3f : 1f) * (isCenter ? 1f : spread);
            float k = o[2] / 68f; // the orb's scale
            if (it.you) {
                drawFace(g, cx, cy, 36f * k * u);
            } else if (it.live != null) {
                String value = it.live.equals("fps") ? String.valueOf(Minecraft.getInstance().getFps()) : clock();
                HubFont f = HubFont.of(font, 17f * k * u, true);
                f.drawMid(g, value, cx - f.width(value) / 2f, cy, isOn ? INK : TEXT);
            } else if (it.icon != null) {
                int size = Math.round(26f * k * u);
                g.blit(icon(isOn && INK_DARK ? it.icon + "_dark" : it.icon), Math.round(cx - size / 2f),
                        Math.round(cy - size / 2f), size, size, 64);
            }
            if (iconOpacity < 0.98f) {
                // Far orbs (and ones the search doesn't match) fade their icon into the orb.
                Shapes.fill(g, Shapes.circle(cx, cy - 0.07f * r, r * 0.7f), Shapes.fade(body, (1f - iconOpacity) * opacity));
            }
            if (i == hover && !(isCenter && mode == Mode.IDLE) && depth > 0.55f) {
                tipX = o[0];
                tipY = o[1] - o[2] / 2f;
                tipText = name(it);
            }
        }
    }

    private void drawTooltip(Canvas g) {
        if (tipText == null) {
            return;
        }
        HubFont f = type(Ui.SMALL, true);
        float w = f.width(tipText) / u + 24f;
        float h = 28f;
        float x = tipX - w / 2f;
        float y = tipY - 8f - h;
        Shapes.fill(g, Shapes.roundRect(sx(x), sy(y), w * u, h * u, h * u / 2f), 0xFF000000);
        f.drawMid(g, tipText, sx(x + 12f), sy(y + h / 2f), TEXT);
    }

    /** The island: what the orb in the middle is, and the button that uses it. */
    private void drawIsland(Canvas g) {
        if (page != null) {
            drawPageIsland(g);
            return;
        }
        Sphere s = sphere();
        boolean moving = mode == Mode.DRAG || mode == Mode.COAST;
        boolean settled = mode == Mode.IDLE;
        int centered = moving ? s.nearest(q) : sel;
        HubItem it = s.items.get(centered);
        boolean isOn = it.kind == HubItem.Kind.TOGGLE && it.on.getAsBoolean();
        String missing = it.missing.get();
        String line = it.note != null ? it.note.get() : t("note." + it.id);
        if (it.kind == HubItem.Kind.TOGGLE) {
            line = t(isOn ? "on" : "off") + " · " + line;
        }
        if (missing != null) {
            line = missing;
        }
        if (notice != null) {
            line = notice;
        }
        if (moving) {
            line = t("turning");
        }
        if (mode == Mode.TRANS) {
            line = t(MODS.equals(transOut ? transTo : level) ? "opening_mods" : "back_to_hub");
        }

        boolean action = settled && missing == null && it.kind != HubItem.Kind.LATER;
        String label = !action ? null
                : it.kind == HubItem.Kind.TOGGLE ? t(isOn ? "turn_off" : "turn_on") : t("button." + it.button);
        HubFont nameFont = type(Ui.NAME, true);
        HubFont lineFont = type(Ui.SMALL, false);
        HubFont labelFont = type(Ui.SMALL, true);
        float bw = label == null ? 0f : labelFont.width(label) / u + 32f;
        float textW = Math.max(nameFont.width(name(it)), lineFont.width(line)) / u;
        boolean gear = action && it.moduleId != null;
        // The island grows to fit what it says, like the real one.
        islandTarget = Math.max(440f, Math.min(640f, 62f + textW + 12f + (label == null ? 0f : bw + 12f) + (gear ? 56f : 0f) + 10f));
        float w = islandW;
        float h = 60f;
        float left = CX - w / 2f;
        float top = 20f;
        // A soft shadow, then the black island.
        Shapes.fill(g, Shapes.roundRect(sx(left - 4f), sy(top + 10f), (w + 8f) * u, (h + 8f) * u, (h + 8f) * u / 2f), 0x30000000);
        Shapes.fill(g, Shapes.roundRect(sx(left), sy(top), w * u, h * u, h * u / 2f), 0xFF000000);
        float wellX = left + 30f;
        float wellY = top + 30f;
        Shapes.fill(g, Shapes.circle(sx(wellX), sy(wellY), 20f * u), isOn ? ACCENT : WELL);
        if (it.you) {
            drawFace(g, sx(wellX), sy(wellY), 24f * u);
        } else if (it.live != null) {
            String value = it.live.equals("fps") ? String.valueOf(Minecraft.getInstance().getFps()) : clock();
            labelFont.drawMid(g, value, sx(wellX) - labelFont.width(value) / 2f, sy(wellY), isOn ? INK : TEXT);
        } else if (it.icon != null) {
            int size = Math.round(20f * u);
            g.blit(icon(isOn && INK_DARK ? it.icon + "_dark" : it.icon), Math.round(sx(wellX) - size / 2f),
                    Math.round(sy(wellY) - size / 2f), size, size, 64);
        }

        float textX = left + 62f;
        float right = left + w - 10f;
        if (label != null) {
            float bx = right - bw;
            float by = top + 8f;
            int bg = it.kind == HubItem.Kind.TOGGLE && !isOn ? ACCENT : WELL;
            Shapes.fill(g, Shapes.roundRect(sx(bx), sy(by), bw * u, 44f * u, 22f * u), bg);
            labelFont.drawMid(g, label, sx(bx + bw / 2f) - labelFont.width(label) / 2f, sy(by + 22f), bg == ACCENT ? INK : TEXT);
            int index = centered;
            hits.add(new Ui.Hit(bx, by, bx + bw, by + 44f, () -> use(index), null));
            right = bx - 12f;
            if (gear) {
                // The module's own settings, on the Mods page.
                float gx = right - 44f;
                Shapes.fill(g, Shapes.circle(sx(gx + 22f), sy(by + 22f), 22f * u), WELL);
                int size = Math.round(18f * u);
                g.blit(icon("gear"), Math.round(sx(gx + 22f) - size / 2f), Math.round(sy(by + 22f) - size / 2f), size, size, 64);
                String id = it.moduleId;
                hits.add(new Ui.Hit(gx, by, gx + 44f, by + 44f, () -> {
                    modsPage.show(id);
                    openPage(MODS_PAGE);
                }, null));
                right = gx - 12f;
            }
        }
        float room = (right - textX) * u;
        nameFont.draw(g, nameFont.fit(name(it), room), sx(textX), sy(wellY - 13f) + nameFont.cap(), TEXT);
        lineFont.draw(g, lineFont.fit(line, room), sx(textX), sy(wellY + 4f) + lineFont.cap(), TEXT_DIM);
    }

    private void drawPageIsland(Canvas g) {
        HubPage current = currentPage();
        String name = current.title();
        String line = notice != null ? notice : current.line();
        String label = t("done");
        String own = current.actionLabel();
        HubFont nameFont = type(Ui.NAME, true);
        HubFont lineFont = type(Ui.SMALL, false);
        HubFont labelFont = type(Ui.SMALL, true);
        float bw = labelFont.width(label) / u + 32f;
        float ow = own == null ? 0f : labelFont.width(own) / u + 32f;
        float textW = Math.max(nameFont.width(name), lineFont.width(line)) / u;
        islandTarget = Math.max(440f, Math.min(680f, 62f + textW + 12f + bw + 12f + (own == null ? 0f : ow + 8f) + 10f));
        float w = islandW;
        float h = 60f;
        float left = CX - w / 2f;
        float top = 20f;
        Shapes.fill(g, Shapes.roundRect(sx(left - 4f), sy(top + 10f), (w + 8f) * u, (h + 8f) * u, (h + 8f) * u / 2f), 0x30000000);
        Shapes.fill(g, Shapes.roundRect(sx(left), sy(top), w * u, h * u, h * u / 2f), 0xFF000000);
        Shapes.fill(g, Shapes.circle(sx(left + 30f), sy(top + 30f), 20f * u), WELL);
        int size = Math.round(20f * u);
        g.blit(icon(current.icon()), Math.round(sx(left + 30f) - size / 2f), Math.round(sy(top + 30f) - size / 2f), size, size, 64);
        float right = left + w - 10f;
        if (mode == Mode.IDLE) {
            float bx = right - bw;
            Shapes.fill(g, Shapes.roundRect(sx(bx), sy(top + 8f), bw * u, 44f * u, 22f * u), WELL);
            labelFont.drawMid(g, label, sx(bx + bw / 2f) - labelFont.width(label) / 2f, sy(top + 30f), TEXT);
            hits.add(new Ui.Hit(bx, top + 8f, bx + bw, top + 52f, this::closePage, null));
            right = bx - 12f;
            if (own != null) {
                // The page's own button, filled: what you came here to do.
                float ax = right + 4f - ow;
                Shapes.fill(g, Shapes.roundRect(sx(ax), sy(top + 8f), ow * u, 44f * u, 22f * u), ACCENT);
                labelFont.drawMid(g, own, sx(ax + ow / 2f) - labelFont.width(own) / 2f, sy(top + 30f), INK);
                hits.add(new Ui.Hit(ax, top + 8f, ax + ow, top + 52f, current::action, null));
                right = ax - 12f;
            }
        }
        float room = (right - left - 62f) * u;
        nameFont.draw(g, nameFont.fit(name, room), sx(left + 62f), sy(top + 17f) + nameFont.cap(), TEXT);
        lineFont.draw(g, lineFont.fit(line, room), sx(left + 62f), sy(top + 34f) + lineFont.cap(), TEXT_DIM);
    }

    /** The bar at the bottom: back to the hub (in the mods sphere), previous, search, next. */
    private void drawBar(Canvas g) {
        boolean onPage = page != null;
        boolean hasBack = onPage || !level.equals(MAIN);
        Runnable back = onPage ? this::closePage : () -> enter(MAIN, -1);
        String backLabel = t("back");
        String hint = onPage ? currentPage().searchHint() : t("search");
        HubFont labelFont = type(Ui.SMALL, true);
        HubFont inputFont = type(Ui.SMALL, false);
        float backW = hasBack ? labelFont.width(backLabel) / u + 48f : 0f;
        float searchW = Math.max(280f, Math.min(420f, inputFont.width(hint) / u + 64f));
        float arrows = onPage ? 0f : 100f;
        float w = 4f + (hasBack ? backW + 6f : 0f) + arrows + searchW + 4f;
        float left = CX - w / 2f;
        float top = 744f;
        float h = 52f;
        Shapes.fill(g, Shapes.roundRect(sx(left - 4f), sy(top + 8f), (w + 8f) * u, (h + 8f) * u, (h + 8f) * u / 2f), 0x30000000);
        Shapes.fill(g, Shapes.roundRect(sx(left), sy(top), w * u, h * u, h * u / 2f), 0xFF000000);
        float x = left + 4f;
        float y = top + 4f;
        if (hasBack) {
            Shapes.fill(g, Shapes.roundRect(sx(x), sy(y), backW * u, 44f * u, 22f * u), WELL);
            int size = Math.round(16f * u);
            g.blit(icon("back"), Math.round(sx(x + 10f)), Math.round(sy(y + 22f) - size / 2f), size, size, 64);
            labelFont.drawMid(g, backLabel, sx(x + 32f), sy(y + 22f), TEXT);
            hits.add(new Ui.Hit(x, y, x + backW, y + 44f, back, null));
            x += backW + 6f;
        }
        if (!onPage) {
            roundButton(g, x, y, "back", () -> step(-1));
            x += 50f;
        }
        // The search field: the text you type anywhere in the hub shows here.
        Shapes.fill(g, Shapes.roundRect(sx(x), sy(y), searchW * u, 44f * u, 22f * u), 0xFF2A2A2E);
        Shapes.fill(g, Shapes.roundRect(sx(x + 1f), sy(y + 1f), (searchW - 2f) * u, 42f * u, 21f * u), 0xFF141416);
        int size = Math.round(17f * u);
        g.blit(icon("search"), Math.round(sx(x + 15f)), Math.round(sy(y + 22f) - size / 2f), size, size, 64);
        float room = (searchW - 58f) * u;
        float tx = sx(x + 40f);
        float midY = sy(y + 22f);
        if (query.isEmpty()) {
            inputFont.drawMid(g, inputFont.fit(hint, room), tx, midY, 0x80FFFFFF);
        } else {
            String shown = inputFont.fitEnd(query, room - 4f);
            inputFont.drawMid(g, shown, tx, midY, TEXT);
            if ((System.currentTimeMillis() / 530) % 2 == 0) {
                int caret = Math.round(tx + inputFont.width(shown) + Math.max(1f, u));
                int half = Math.round(inputFont.px * 0.6f);
                g.fill(caret, Math.round(midY) - half, caret + Math.max(1, Math.round(1.5f * u)), Math.round(midY) + half, 0xFFFFFFFF);
            }
        }
        x += searchW + 6f;
        if (!onPage) {
            roundButton(g, x, y, "next", () -> step(1));
        }
    }

    private void roundButton(Canvas g, float x, float y, String iconName, Runnable action) {
        Shapes.fill(g, Shapes.circle(sx(x + 22f), sy(y + 22f), 22f * u), WELL);
        int size = Math.round(18f * u);
        g.blit(icon(iconName), Math.round(sx(x + 22f) - size / 2f), Math.round(sy(y + 22f) - size / 2f), size, size, 64);
        hits.add(new Ui.Hit(x, y, x + 44f, y + 44f, action, null));
    }

    /** The player's face (with the hat layer of the skin), or a plain one before the skin is known. */
    private void drawFace(Canvas g, float cx, float cy, float size) {
        int s = Math.max(8, Math.round(size));
        int x = Math.round(cx - s / 2f);
        int y = Math.round(cy - s / 2f);
        var skin = skin();
        if (skin != null) {
            g.blitRegion(skin, x, y, s, s, 8f, 8f, 8, 8, 64, 64);
            int hat = Math.round(s * 1.125f);
            int hx = Math.round(cx - hat / 2f);
            int hy = Math.round(cy - hat / 2f);
            g.blitRegion(skin, hx, hy, hat, hat, 40f, 8f, 8, 8, 64, 64);
            return;
        }
        float p = s / 8f;
        g.fill(x, y, x + s, y + s, 0xFFD9A47C);
        g.fill(x, y, x + s, y + Math.round(2 * p), 0xFF3B2619);
        g.fill(x + Math.round(p), y + Math.round(4 * p), x + Math.round(2 * p), y + Math.round(5 * p), 0xFF2A3A6B);
        g.fill(x + Math.round(6 * p), y + Math.round(4 * p), x + Math.round(7 * p), y + Math.round(5 * p), 0xFF2A3A6B);
        g.fill(x + Math.round(3 * p), y + Math.round(6 * p), x + Math.round(5 * p), y + Math.round(7 * p), 0xFF9A5A42);
    }

    // The skin's texture, and the hub's icons. 1.21.9 moved the skin's texture into its body asset; 1.21.11 renamed
    // ResourceLocation to Identifier.
    //? if >=1.21.11 {
    private static final Map<String, Identifier> ICONS = new HashMap<>();

    static Identifier icon(String name) {
        return ICONS.computeIfAbsent(name, n -> Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "textures/gui/hub/" + n + ".png"));
    }

    private static Identifier skin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getSkin().body().texturePath();
    }
    //?} elif >=1.21.9 {
    /*private static final Map<String, ResourceLocation> ICONS = new HashMap<>();

    static ResourceLocation icon(String name) {
        return ICONS.computeIfAbsent(name, n -> ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "textures/gui/hub/" + n + ".png"));
    }

    private static ResourceLocation skin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getSkin().body().texturePath();
    }
    *///?} else {
    /*private static final Map<String, ResourceLocation> ICONS = new HashMap<>();

    static ResourceLocation icon(String name) {
        return ICONS.computeIfAbsent(name, n -> ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "textures/gui/hub/" + n + ".png"));
    }

    private static ResourceLocation skin() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getSkin().texture();
    }
    *///?}

    /** The hub's typeface at {@code size} design pixels. */
    private HubFont type(float size, boolean semibold) {
        return HubFont.of(font, size * u, semibold);
    }

    private static String clock() {
        LocalTime now = LocalTime.now();
        return String.format(Locale.ROOT, "%02d:%02d", now.getHour(), now.getMinute());
    }

    private static float lightness(int argb) {
        float[] c = new float[3];
        for (int k = 0; k < 3; k++) {
            float v = ((argb >> (16 - 8 * k)) & 0xFF) / 255f;
            c[k] = v <= 0.03928f ? v / 12.92f : (float) Math.pow((v + 0.055f) / 1.055f, 2.4f);
        }
        return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
    }
}
