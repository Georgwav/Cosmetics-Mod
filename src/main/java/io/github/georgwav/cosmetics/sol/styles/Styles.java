package io.github.georgwav.cosmetics.sol.styles;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.MainFolder;
import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Sol Styles at run time (B.2): scans the packs off the render thread, keeps the setup (saved in the profile), mixes
 * it into the Sol Styles pack, and applies each change the fastest correct way (B.2.6): colours and same-size
 * textures written live into the game, anything else by one resource reload 400 ms after the last change. The base
 * pack is mounted as itself under the mix. Scanning, mixing and recolouring run on Sol's worker; only the writes and
 * the reload touch the render thread.
 */
public final class Styles {
    /** The packs' ids in the game's pack list (their order there is set in {@link #addPacks}). */
    public static final String BASE_ID = "cosmeticsmod:styles_base", MIX_ID = "cosmeticsmod:styles_mix";
    /** How long after the last change a reload waits, so clicking through five styles reloads once. */
    static final long RELOAD_DELAY_MS = 400L;
    /** Rainbow parts step round the hue this often, a twelfth of the way each time. */
    static final long RAINBOW_STEP_MS = 200L;

    private static final Mixer.Plan EMPTY_PLAN = new Mixer.Plan(Map.of(), List.of());

    private static volatile StylesIndex.Snapshot index;
    private static volatile Mixer.Plan plan = EMPTY_PLAN;
    private static StyleSetup setup = StyleSetup.EMPTY;
    private static Builder builder;
    private static boolean started;
    private static volatile boolean working;
    private static volatile boolean dirty;
    private static long reloadAt = -1L;
    private static int lastTint = Integer.MIN_VALUE, lastGlint = Integer.MIN_VALUE;
    private static int rainbowStep;
    private static long rainbowAt;
    /** What the menu shows and the scenario logs. */
    public static volatile String lastApply = "";
    public static volatile long lastApplyMillis, lastScanMillis;
    /** Scans logged so far: the first is told, the rest kept to the debug log. */
    private static int scansLogged;
    public static volatile int liveWrites, reloads;
    /** How long the Styles tab took to open (its first frame), for the scenario's results. */
    public static volatile long menuOpenMicros = -1L;
    private static long reloadStarted;
    private static boolean announced;

    private Styles() {
    }

    // ------------------------------------------------------------------ where things are

    static Path cacheFolder() {
        return MainFolder.clientFolder().resolve("styles-cache");
    }

    static Path indexFile() {
        return MainFolder.clientFolder().resolve("styles-index.json");
    }

    /** Small partial packs: the texture editor saves its styles here (client/styles/<tab>/<name>/). */
    public static Path userFolder() {
        return MainFolder.clientFolder().resolve("styles");
    }

    /** The personal build's styles (styles/<tab>/<style>/ in the bundled pack, solstyles/ in the jar), or null. */
    static Path personalFolder() {
        Optional<Path> p = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(CosmeticsMod.MOD_ID)
                .flatMap(c -> c.findPath("solstyles"));
        return p.filter(Files::isDirectory).orElse(null);
    }

    /** Whether Bauloo Styles is installed (then Styles leaves Bauloo's own tabs to it). */
    public static boolean bauloo() {
        for (var mod : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
            if (mod.getMetadata().getId().toLowerCase(java.util.Locale.ROOT).contains("bauloo")) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ state

    public static StylesIndex.Snapshot index() {
        return index;
    }

    public static Mixer.Plan plan() {
        return plan;
    }

    public static StyleSetup setup() {
        return setup;
    }

    /** Whether a change is being worked on or waits for its reload (the menu's "Applying" pill). */
    public static boolean applying() {
        return working || dirty || reloadAt >= 0L || reloadStarted > 0L;
    }

    /** A source's bytes, for the mix pack and the menu's thumbnails; null when it can't be read. */
    public static byte[] read(Mixer.Source s) {
        Builder b = builder();
        try {
            synchronized (b) {
                return b.read(s);
            }
        } catch (IOException | RuntimeException e) {
            io.github.georgwav.cosmetics.sol.util.LogOnce.warn("styles:read:" + s.identity(), "[Sol] Styles: couldn't read {}: {}", s.identity(), e.toString());
            return null;
        }
    }

    private static synchronized Builder builder() {
        if (builder == null) {
            builder = new Builder(cacheFolder(), VanillaIndex.locate());
        }
        return builder;
    }

    // ------------------------------------------------------------------ lifecycle

    /** The scan running (or done), so the game's first pack load can wait for it. */
    private static volatile java.util.concurrent.CompletableFuture<StylesIndex.Snapshot> scanning;

    /** The first scan, at start (before the game first loads its packs, so the mix is in that load). */
    public static void start() {
        if (started) {
            return;
        }
        started = true;
        scanning = java.util.concurrent.CompletableFuture.supplyAsync(Styles::scan, SolExecutors.CPU);
    }

    /** Scans the packs again (the menu's Refresh, a pack added), then mixes. */
    public static void rescan() {
        scanning = java.util.concurrent.CompletableFuture.supplyAsync(Styles::scan, SolExecutors.CPU);
        scanning.thenRun(() -> Minecraft.getInstance().execute(Styles::changed));
    }

    private static StylesIndex.Snapshot scan() {
        long t0 = System.nanoTime();
        try {
            VanillaIndex vanilla = VanillaIndex.load(cacheFolder().resolve("vanilla-" + PartTable.get().minecraft() + ".bin"));
            List<StylesIndex.Candidate> c = StylesIndex.candidates(personalFolder(), resourcePacks(), userFolder());
            StylesIndex.Snapshot s = StylesIndex.build(vanilla, c, StylesIndex.readCache(indexFile()));
            try {
                StylesIndex.writeCache(indexFile(), s, null);
            } catch (IOException e) {
                CosmeticsMod.LOGGER.warn("[Sol] Styles: couldn't write the index: {}", e.toString());
            }
            index = s;
            lastScanMillis = (System.nanoTime() - t0) / 1_000_000L;
            if (scansLogged++ == 0) {
                CosmeticsMod.LOGGER.info("[Sol] Styles: {} packs ({} read again) in {} ms", s.packs().size(), s.rescanned(), lastScanMillis);
            } else {
                CosmeticsMod.LOGGER.debug("[Sol] Styles: {} packs ({} read again) in {} ms", s.packs().size(), s.rescanned(), lastScanMillis);
            }
            return s;
        } catch (RuntimeException | LinkageError e) {
            Guard.fail(Feature.STYLES, e);
            return null;
        }
    }

    private static Path resourcePacks() {
        Path rp = Minecraft.getInstance() == null ? null : Minecraft.getInstance().getResourcePackDirectory();
        return rp != null ? rp : MainFolder.minecraftFolder().resolve("resourcepacks");
    }

    /**
     * Waits (a little) for the first scan and mixes the profile's setup now, so the game's first load of its packs
     * already has it and no second reload is needed. Called when the game looks for packs.
     */
    private static void readyForLoad() {
        var s = scanning;
        if (s != null && index == null) {
            try {
                s.get(3, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                // Not ready: the mix follows when it is.
            }
        }
        StylesIndex.Snapshot idx = index;
        if (idx != null && plan == EMPTY_PLAN && !setup.isEmpty() && !working) {
            Mixer.Plan next = Mixer.plan(effective(setup), PartTable.get(), idx::get, idx.vanilla(), Styles::packText, lastTint = themeTintSafe(),
                    lastGlint = glintTintSafe());
            for (Mixer.Source src : next.files().values()) {
                if (src instanceof Mixer.Made) {
                    read(src);
                }
            }
            plan = next;
        }
    }

    private static int themeTintSafe() {
        try {
            return themeTint();
        } catch (RuntimeException e) {
            return 0xFFFFFF;
        }
    }

    private static int glintTintSafe() {
        try {
            return glintTint();
        } catch (RuntimeException | LinkageError e) {
            return 0xFFFFFF;
        }
    }

    /** The glint's colour now (RGB), for tools that follow it. */
    private static int glintTint() {
        return io.github.georgwav.cosmetics.client.modules.GlintModule.colour() & 0xFFFFFF;
    }

    /**
     * The setup as it's mixed: with Bauloo Styles installed, Bauloo's own tabs are left to it (its parts dropped), so
     * the two never fight over a texture.
     */
    static StyleSetup effective(StyleSetup s) {
        if (!bauloo() || s.parts().isEmpty()) {
            return s;
        }
        StyleSetup out = s;
        for (String id : s.parts().keySet()) {
            PartTable.Part p = PartTable.get().part(id);
            if (p != null && p.tab() != null) {
                out = out.with(id, StyleSetup.Choice.DEFAULT);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the crystal's motion (EndCrystalLookMixin)

    /** How fast the crystals turn and bob (1 = the game's), from the Crystal part's settings. */
    public static float crystalSpin() {
        try {
            return Math.max(0f, Math.min(3f, Float.parseFloat(setup.choice("crystal").extra("spin", "1"))));
        } catch (NumberFormatException e) {
            return 1f;
        }
    }

    /** Whether the crystals bob up and down. */
    public static boolean crystalBounce() {
        return !"false".equals(setup.choice("crystal").extra("bounce", "true"));
    }

    private static Float legacySpin;
    private static Boolean legacyBounce;

    /** Crystal PvP's old spin and bounce settings, read from an older save: moved into the Crystal part once. */
    public static void legacyCrystal(Float spin, Boolean bounce) {
        legacySpin = spin;
        legacyBounce = bounce;
    }

    private static void moveLegacyCrystal() {
        if (legacySpin == null && legacyBounce == null) {
            return;
        }
        StyleSetup.Choice c = setup.choice("crystal");
        if (legacySpin != null && legacySpin != 1f && c.extra("spin", null) == null) {
            c = c.withExtra("spin", Float.toString(legacySpin));
        }
        if (legacyBounce != null && !legacyBounce && c.extra("bounce", null) == null) {
            c = c.withExtra("bounce", "false");
        }
        legacySpin = null;
        legacyBounce = null;
        set(setup.with("crystal", c));
    }

    /** A new setup from the menu: saved in the profile and applied. Render thread. */
    public static void set(StyleSetup s) {
        if (s.equals(setup)) {
            return;
        }
        setup = s;
        io.github.georgwav.cosmetics.sol.config.SolProfiles.changed();
        changed();
    }

    /** The profile's setup (on a profile switch, and at start). Render thread. */
    public static void adopt(StyleSetup s) {
        setup = s == null ? StyleSetup.EMPTY : s;
        changed();
    }

    /** Something the mix depends on changed: mix again now (the live path is quick; a reload waits). */
    static void changed() {
        if (index == null) {
            return;
        }
        if (working) {
            dirty = true;
            return;
        }
        working = true;
        dirty = false;
        StyleSetup s = effective(setup);
        int tint = themeTint(), glint = glintTintSafe();
        lastTint = tint;
        lastGlint = glint;
        StylesIndex.Snapshot idx = index;
        Mixer.Plan old = plan;
        SolExecutors.cpu(Feature.STYLES, () -> {
            long t0 = System.nanoTime();
            Mixer.Plan next;
            List<Live> live;
            try {
                next = Mixer.plan(s, PartTable.get(), idx::get, idx.vanilla(), Styles::packText, tint, glint);
                // Sol's own files made now, off the render thread.
                for (Mixer.Source src : next.files().values()) {
                    if (src instanceof Mixer.Made) {
                        read(src);
                    }
                }
                live = livePath(old, next, s, idx);
            } finally {
                working = false;
            }
            long mixMs = (System.nanoTime() - t0) / 1_000_000L;
            Minecraft.getInstance().execute(() -> applyOnRenderThread(old, next, live, mixMs));
        });
    }

    /** One texture written live: its path, its new pixels and its size now. */
    private record Live(String path, Pixels.Image image, int w, int h) {
    }

    /**
     * The textures to write live, or null when the change needs a reload: anything but a texture changed, a texture
     * changed size, or the base changed.
     */
    private static List<Live> livePath(Mixer.Plan old, Mixer.Plan next, StyleSetup s, StylesIndex.Snapshot idx) {
        if (!java.util.Objects.equals(baseOf(old), s.base())) {
            return null;
        }
        Set<String> keys = new TreeSet<>(old.files().keySet());
        keys.addAll(next.files().keySet());
        PackInfo base = idx.get(s.base());
        List<Live> out = new ArrayList<>();
        for (String k : keys) {
            Mixer.Source a = old.files().get(k), b = next.files().get(k);
            if (a != null && b != null && a.identity().equals(b.identity())) {
                continue;
            }
            if (!k.endsWith(".png") || k.contains(":")) {
                return null;
            }
            Mixer.Source was = a != null ? a : under(k, base, idx.vanilla());
            Mixer.Source now = b != null ? b : under(k, base, idx.vanilla());
            if (was == null || now == null) {
                return null;
            }
            try {
                byte[] wb = read(was), nb = read(now);
                Pixels.Image wi = wb == null ? null : Builder.decode(wb), ni = nb == null ? null : Builder.decode(nb);
                if (wi == null || ni == null || wi.w() != ni.w() || wi.h() != ni.h()) {
                    return null;
                }
                out.add(new Live(k, ni, wi.w(), wi.h()));
            } catch (IOException e) {
                return null;
            }
        }
        return out;
    }

    private static Mixer.Source under(String path, PackInfo base, VanillaIndex vanilla) {
        if (base != null && base.has(path)) {
            return Mixer.fromPack(base, path);
        }
        VanillaIndex.Entry v = vanilla.get(path);
        return v == null ? null : new Mixer.FromGame(path, v.crc(), v.size());
    }

    private static String mountedBase;

    private static String baseOf(Mixer.Plan p) {
        return mountedBase;
    }

    private static void applyOnRenderThread(Mixer.Plan old, Mixer.Plan next, List<Live> live, long mixMs) {
        Guard.run(Feature.STYLES, () -> {
            plan = next;
            for (String n : next.notes()) {
                io.github.georgwav.cosmetics.sol.util.LogOnce.warn("styles:note:" + n, "[Sol] Styles: {}", n);
            }
            boolean needReload = live == null;
            if (!needReload && !live.isEmpty()) {
                long t0 = System.nanoTime();
                for (Live l : live) {
                    if (!LiveWrite.write(l.path(), l.image(), l.w(), l.h())) {
                        needReload = true;
                        break;
                    }
                }
                if (!needReload) {
                    liveWrites += live.size();
                    lastApplyMillis = (System.nanoTime() - t0) / 1_000_000L;
                    lastApply = "live";
                    // Often (each colour change while you drag): kept out of the log unless asked for.
                    CosmeticsMod.LOGGER.debug("[Sol] Styles: {} texture(s) written live in {} ms (mixed in {} ms)", live.size(), lastApplyMillis, mixMs);
                }
            }
            if (needReload) {
                reloadAt = System.currentTimeMillis() + RELOAD_DELAY_MS;
            }
        });
        if (dirty) {
            changed();
        }
    }

    /** The client tick: the waiting reload, the theme's colour and the rainbow. */
    public static void tick(Minecraft mc) {
        if (!started) {
            start();
        }
        if (!announced && index != null) {
            // The first scan is in: mix (a setup the first load didn't have yet comes in now).
            announced = true;
            moveLegacyCrystal();
            changed();
        }
        long now = System.currentTimeMillis();
        if (reloadAt >= 0L && now >= reloadAt && !working && io.github.georgwav.cosmetics.sol.render.Mc.overlay() == null) {
            reloadAt = -1L;
            reload(mc);
        }
        if (setup.isEmpty() || index == null) {
            return;
        }
        boolean themed = false, rainbow = false, glinted = false;
        for (StyleSetup.Choice c : setup.parts().values()) {
            themed |= c.paint() == StyleSetup.Paint.THEME || "builtin:theme".equals(c.source()) || c.extra().containsValue("theme");
            rainbow |= c.paint() == StyleSetup.Paint.RAINBOW;
            glinted |= c.paint() == StyleSetup.Paint.GLINT;
        }
        if (rainbow && now - rainbowAt >= RAINBOW_STEP_MS && !working) {
            rainbowAt = now;
            rainbowStep = (rainbowStep + 1) % 12;
            changed();
        } else if (themed && themeTint() != lastTint && !working) {
            changed();
        } else if (glinted && glintTintSafe() != lastGlint && !working) {
            changed();
        }
    }

    private static void reload(Minecraft mc) {
        reloadStarted = System.nanoTime();
        reloads++;
        mc.reloadResourcePacks().thenRun(() -> {
            LiveWrite.reloaded();
            lastApplyMillis = (System.nanoTime() - reloadStarted) / 1_000_000L;
            lastApply = "reload";
            reloadStarted = 0L;
            CosmeticsMod.LOGGER.info("[Sol] Styles: applied by a resource reload in {} ms", lastApplyMillis);
        });
    }

    /** The colour theme-following parts take now (the rainbow's step instead while one is on). */
    private static int themeTint() {
        for (StyleSetup.Choice c : setup.parts().values()) {
            if (c.paint() == StyleSetup.Paint.RAINBOW) {
                return io.github.georgwav.cosmetics.sol.util.Colors.fromHsv(rainbowStep / 12f, 0.75f, 1f) & 0xFFFFFF;
            }
        }
        return ThemeState.tokens().accent & 0xFFFFFF;
    }

    static String packText(PackInfo p, String path) {
        byte[] d = read(Mixer.fromPack(p, path));
        return d == null ? null : new String(d, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ the texture editor

    /** What a texture is now: the mix's file for it, else the base's, else the game's (null when nobody has it). */
    public static Mixer.Source current(String path) {
        Mixer.Source s = plan.files().get(path);
        if (s != null) {
            return s;
        }
        StylesIndex.Snapshot idx = index;
        if (idx == null) {
            return null;
        }
        PackInfo base = idx.get(setup.base());
        return under(path, base, idx.vanilla());
    }

    /** One of a pack's files (any file, read from its zip or folder), or null. */
    public static byte[] readPackFile(PackInfo pack, String path) {
        return read(Mixer.fromPack(pack, path));
    }

    /** A texture's new pixels written straight into the game (an editor stroke). Render thread; false when it can't. */
    public static boolean writeLive(String path, Pixels.Image img) {
        try {
            return LiveWrite.write(path, img, img.w(), img.h());
        } catch (RuntimeException | LinkageError e) {
            io.github.georgwav.cosmetics.sol.util.LogOnce.warn("styles:live:" + path, "[Sol] Styles: couldn't write {} live: {}", path, e.toString());
            return false;
        }
    }

    /**
     * Saves one texture as a style of your own (B.2.12): client/styles/{@code tab}/{@code name}/, a tiny pack with only
     * that file (and its .mcmeta), never in a source pack. Returns the style's pack key. The packs are scanned again,
     * so it shows up in the tiles.
     */
    public static String saveStyle(String tab, String name, String path, byte[] png, String mcmeta) throws IOException {
        Path folder = userFolder().resolve(tab).resolve(name);
        Path file = folder.resolve("assets/minecraft/" + path);
        Files.createDirectories(file.getParent());
        io.github.georgwav.cosmetics.sol.util.AtomicFiles.write(file, png);
        Path meta = folder.resolve("assets/minecraft/" + path + ".mcmeta");
        if (mcmeta != null) {
            io.github.georgwav.cosmetics.sol.util.AtomicFiles.writeString(meta, mcmeta);
        } else {
            Files.deleteIfExists(meta);
        }
        io.github.georgwav.cosmetics.sol.util.AtomicFiles.writeString(folder.resolve("pack.mcmeta"), """
                {
                  "pack": {
                    "description": "%s (made in Sol's texture editor)",
                    "pack_format": 34,
                    "supported_formats": [34, 999],
                    "min_format": 34,
                    "max_format": 999
                  }
                }
                """.formatted(name.replace("\\", "").replace("\"", "")));
        rescan();
        return "user:" + tab + "/" + name;
    }

    // ------------------------------------------------------------------ the packs the game loads

    /**
     * Hands the game the mix and the base pack (when one is set), whenever it looks for packs. The mix is pinned at
     * the very top, over every pack, so the parts picked always win; the base goes in at the top too but under the
     * pinned packs (the game puts a pack added later under them), so it stays under the mix even when it's picked
     * after the mix was mounted.
     */
    public static void addPacks(Consumer<Pack> out) {
        try {
            readyForLoad();
            StylesIndex.Snapshot idx = index;
            PackInfo base = idx == null ? null : idx.get(setup.base());
            mountedBase = base == null ? null : base.key();
            Path root = mixRoot();
            Pack mix = Pack.readMetaAndCreate(new PackLocationInfo(MIX_ID, Component.translatable("sol.styles.pack.mix"), PackSource.BUILT_IN, Optional.empty()),
                    supplier(root), PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, true));
            if (mix != null) {
                out.accept(mix);
            }
            if (base != null) {
                Path p = StylesIndex.path(base.location());
                Pack.ResourcesSupplier supplier = Files.isDirectory(p) ? new net.minecraft.server.packs.PathPackResources.PathResourcesSupplier(p)
                        : new net.minecraft.server.packs.FilePackResources.FileResourcesSupplier(p);
                Pack pack = Pack.readMetaAndCreate(new PackLocationInfo(BASE_ID, Component.translatable("sol.styles.pack.base", base.name()),
                        PackSource.BUILT_IN, Optional.empty()), supplier, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (pack != null) {
                    out.accept(pack);
                }
            }
        } catch (IOException | RuntimeException e) {
            CosmeticsMod.LOGGER.warn("[Sol] Styles: couldn't mount the Styles packs: {}", e.toString());
        }
    }

    /** The mix pack's own folder: only its pack.mcmeta. */
    private static Path mixRoot() throws IOException {
        Path root = cacheFolder().resolve("mix");
        Files.createDirectories(root);
        Path meta = root.resolve("pack.mcmeta");
        if (!Files.exists(meta)) {
            Files.writeString(meta, """
                    {
                      "pack": {
                        "description": "Sol Styles",
                        "pack_format": 34,
                        "supported_formats": [34, 999],
                        "min_format": 34,
                        "max_format": 999
                      }
                    }
                    """);
        }
        return root;
    }

    private static Pack.ResourcesSupplier supplier(Path root) {
        //? if >=26.3 {
        /*return new Pack.ResourcesSupplier() {
            @Override
            public net.minecraft.server.packs.PackMetadataResources openMetadata(PackLocationInfo info) {
                return new MixPack(info, root);
            }

            @Override
            public java.util.stream.Stream<net.minecraft.server.packs.PackResources> openResources(PackLocationInfo info, Pack.Metadata metadata) {
                return java.util.stream.Stream.of(new MixPack(info, root));
            }
        };
        *///?} else {
        return new Pack.ResourcesSupplier() {
            @Override
            public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) {
                return new MixPack(info, root);
            }

            @Override
            public net.minecraft.server.packs.PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return new MixPack(info, root);
            }
        };
        //?}
    }

    /** For the scenario's results: what Styles did. */
    public static Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        StylesIndex.Snapshot idx = index;
        m.put("packs", idx == null ? 0 : idx.packs().size());
        m.put("scanMs", lastScanMillis);
        m.put("mixFiles", plan.files().size());
        m.put("liveWrites", liveWrites);
        m.put("reloads", reloads);
        m.put("lastApply", lastApply);
        m.put("lastApplyMs", lastApplyMillis);
        m.put("menuOpenMicros", menuOpenMicros);
        return m;
    }
}
