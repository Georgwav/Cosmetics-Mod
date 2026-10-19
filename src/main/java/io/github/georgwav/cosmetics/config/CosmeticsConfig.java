package io.github.georgwav.cosmetics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User settings, stored as {@code config/cosmeticsmod.json} and edited in game on the settings screen.
 * The render path only reads plain fields, so it never touches the file system or allocates.
 */
public final class CosmeticsConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile CosmeticsConfig instance = new CosmeticsConfig();

    // ---------------------------------------------------------------- general

    /** Master switch. */
    /**
     * Which defaults the file was written with, for one-time changes to them (0 = before this field):
     * 2 made capes override the vanilla cape.
     */
    public int configVersion;
    static final int CONFIG_VERSION = 5;

    public boolean enabled = true;
    /** Draw the cosmetics you wear yourself (third person, inventory, mirrors...). */
    public boolean showOwnCosmetics = true;
    /** Draw other players' cosmetics. */
    public boolean showOtherPlayersCosmetics = true;

    // ---------------------------------------------------------------- display

    /** Per-slot visibility, e.g. hide every cape while keeping hats. */
    public Map<CosmeticSlot, Boolean> slotVisible = defaultVisibility();
    /** Hide hats and face cosmetics while a helmet or other head item is worn. */
    public boolean hideHeadwearWithHelmet = true;
    /**
     * Wear several cosmetics of one kind (two hats, two pairs of wings) at once. Off, as in other clients:
     * putting one on takes off the one of its kind you wore.
     */
    public boolean severalOfAKind = false;
    /**
     * Colour, rainbow and saturation settings on every cosmetic, even ones whose pack turned them off (imported
     * wings, say). A cosmetic none of whose parts take colour is then coloured all over.
     */
    public boolean colorAnyCosmetic = true;
    /** Your shield cosmetic also shows on the shield item in your inventory and hotbar. */
    public boolean shieldItemIcons = true;
    /** Hide capes while an elytra is worn, like the vanilla cape. */
    public boolean hideCapeWithElytra = true;
    /**
     * Your elytra lies on your back like a cape until you spread it to glide: half as deep, against the back, its
     * lower edge 5 degrees out instead of the game's 15; see {@link io.github.georgwav.cosmetics.render.ElytraLook}.
     */
    public boolean elytraCloseToBack = true;
    /** Capes sway with movement like the vanilla cape. Off: they hang still. */
    public boolean capePhysics = true;
    /** How strongly capes react to movement, 0..2 (1 = vanilla). */
    public float capeSwing = 1.0f;
    /**
     * Wavy capes, like the Wavey Capes mod: every flat cape cosmetic bends as cloth, its lower part lagging behind
     * the top as you start, stop, jump and land. Needs cape physics. Only this mod's capes; the vanilla cape is
     * left to the game (or to Wavey Capes, which can be installed alongside).
     */
    public boolean wavyCapes = true;
    /** Particle cosmetics (auras, trails) give off particles. */
    public boolean particles = true;
    /** How many particles, 0..2 (1 = as the cosmetic says). */
    public float particleDensity = 1.0f;
    /** Your own particles while you're in first person (off keeps them out of your view). */
    public boolean ownParticlesInFirstPerson = true;
    /** Draw glowing (emissive) parts. */
    public boolean emissiveParts = true;
    /** Draw see-through (translucent) parts. */
    public boolean translucentParts = true;

    // ---------------------------------------------------------------- emotes

    /** Emotes can be played (the emote wheel key, and Play in the Wardrobe's Emotes list). */
    public boolean emotes = true;
    /** Where the camera goes while you emote. */
    public EmoteCamera emoteCamera = EmoteCamera.FRONT;
    /** Walking, jumping, sneaking or getting hurt stops an emote. */
    public boolean emoteStopOnMove = true;
    /**
     * Using an item (right-click: a firework rocket to fly on) keeps an elytra emote going; attacking or sneaking
     * still stops it. Off: anything you do stops it.
     */
    public boolean elytraEmoteRockets = true;
    /**
     * The emote wheel's slots, {@link #WHEEL_SLOTS} to a page, each an emote id or "" for an empty slot. Starts as
     * one empty page; you fill it from the wheel.
     */
    public List<String> emoteWheelSlots = emptyPage();
    /** The elytra wheel's slots (the emote wheel opened while gliding), like {@link #emoteWheelSlots}. */
    public List<String> elytraWheelSlots = emptyPage();
    /** Before config version 3: the emotes on each wheel, in order. Read once into the slots. */
    @Deprecated
    public List<String> emoteWheel = new ArrayList<>();
    @Deprecated
    public List<String> elytraEmoteWheel = new ArrayList<>();

    /** Slots on one page of an emote wheel. */
    public static final int WHEEL_SLOTS = 8;
    /** Most pages a wheel can have. */
    public static final int MAX_WHEEL_PAGES = 20;

    private static List<String> emptyPage() {
        return new ArrayList<>(java.util.Collections.nCopies(WHEEL_SLOTS, ""));
    }

    /**
     * {@code slots} as whole pages: nulls are empty slots, the list is padded to a full page (at least one) and cut
     * at {@link #MAX_WHEEL_PAGES}.
     */
    static List<String> wheelPages(List<String> slots) {
        List<String> result = new ArrayList<>();
        if (slots != null) {
            for (String id : slots) {
                result.add(id == null ? "" : id);
            }
        }
        int pages = Math.max(1, Math.min(MAX_WHEEL_PAGES, (result.size() + WHEEL_SLOTS - 1) / WHEEL_SLOTS));
        while (result.size() < pages * WHEEL_SLOTS) {
            result.add("");
        }
        return new ArrayList<>(result.subList(0, pages * WHEEL_SLOTS));
    }

    /** Where the camera goes while you emote: in front of you, behind you, or nowhere (it stays as it is). */
    public enum EmoteCamera { FRONT, BACK, UNCHANGED }

    // ---------------------------------------------------------------- performance

    /** Players further away than this many blocks don't get cosmetics drawn. 0 = no limit. */
    public int maxRenderDistance = 64;
    /**
     * Cosmetics cast shadows with Iris shaderpacks by default. Turn this off to skip cosmetics in the
     * Iris shadow pass for extra FPS.
     */
    public boolean renderInShadowPass = true;
    /** In the Iris shadow pass, only players within this many blocks cast cosmetic shadows. 0 = no limit. */
    public int shadowDistance = 32;
    /**
     * Most mesh triangles (a quad counts as two) one cosmetic may have. A cosmetic over the limit isn't
     * loaded (with a clear message), so a runaway model can never cause stutter. Applies on resource reload.
     * Imported wings draw their model once per side (and again for a glow layer): 11,000-triangle wings are
     * 44,000.
     */
    public int maxMeshTriangles = 100_000;

    // ---------------------------------------------------------------- compatibility

    /**
     * What happens when another mod already uses a slot for a player. Capes default to
     * {@link SlotPriority#OVERRIDE} (a cape you put on replaces the vanilla one); everything else to
     * {@link SlotPriority#DEFER}: we never override another mod's model unless you say so.
     */
    public Map<CosmeticSlot, SlotPriority> slotPriority = defaultPriorities();
    /** Players with a Figura avatar don't get our cosmetics in DEFER slots. */
    public boolean deferToFigura = true;

    // ---------------------------------------------------------------- wardrobe

    /**
     * Which cosmetics each player wears. Keys are player UUIDs, or {@code "local"} for whoever is
     * playing on this client. Values are cosmetic ids like {@code "cosmeticsmod:top_hat"}.
     */
    public Map<String, List<String>> equipped = defaultEquipped();

    /** Saved outfits, in the order they were saved: a name, the cosmetics it puts on and their settings. */
    public List<Outfit> savedOutfits = new ArrayList<>();
    /** Outfits as files before format 5 kept them (name to cosmetic ids); moved into {@link #savedOutfits} on load. */
    public Map<String, List<String>> outfits = new LinkedHashMap<>();

    /** An outfit shown on you without being worn (see {@link #showOutfitSettings}); never saved. */
    private transient Outfit shownOutfit;

    /** A saved outfit. */
    public static final class Outfit {
        public String name;
        /** The cosmetic ids it puts on, in order. */
        public List<String> cosmetics = new ArrayList<>();
        /** Each of its cosmetics' settings as they were when saved; one not listed had its creator's settings. */
        public Map<String, CosmeticOptions> options = new LinkedHashMap<>();

        public Outfit() {
        }

        Outfit(String name, List<String> cosmetics) {
            this.name = name;
            this.cosmetics = new ArrayList<>(cosmetics);
        }

        /** The settings this outfit gives a cosmetic. */
        public CosmeticOptions optionsFor(String id) {
            CosmeticOptions options = this.options.get(id);
            return options != null ? options : DEFAULT_OPTIONS;
        }
    }

    /** Your settings for individual cosmetics (colour, size, position, glow, animation), keyed by cosmetic id. */
    public Map<String, CosmeticOptions> cosmeticOptions = new LinkedHashMap<>();

    /** Settings for one cosmetic. Defaults leave the cosmetic exactly as its creator made it. */
    public static final class CosmeticOptions {
        /**
         * RGB tint multiplied onto the texture (white keeps the texture's own colours), or null for the
         * cosmetic's own default colour.
         */
        public Integer color = null;
        /** Cycle through the colour wheel instead of using {@link #color}. */
        public boolean rainbow = false;
        /** Rainbow cycles per 10 seconds, 0.1..5. */
        public float rainbowSpeed = 1.0f;
        /**
         * How colourful it is, 0 (black and white) to 1 (as made): the tint or rainbow colour and the texture
         * itself (see {@link io.github.georgwav.cosmetics.render.Recolor}).
         */
        public float saturation = 1.0f;
        /** Degrees the texture's colours turn around the colour wheel, -180..180. */
        public float hue = 0f;
        /** The texture's brightness, 0..2 (1 = as made). */
        public float brightness = 1.0f;
        /** Size, 0.5..3. */
        public float scale = 1.0f;
        /** Position offset in pixels, -8..8 (+Y is down, -Z is the front). */
        public float offsetX = 0f;
        public float offsetY = 0f;
        public float offsetZ = 0f;
        /** Draw this cosmetic's glowing parts. */
        public boolean glow = true;
        /** Play this cosmetic's animation. */
        public boolean animate = true;
        /** Animation speed multiplier, 0.1..3. */
        public float animationSpeed = 1.0f;
        /** Which of the cosmetic's styles (named skeletal animations) to show, or null for its default. */
        public String style = null;
        /** For a flat cape: draw it as cloth that bends and ripples (Lunar's "cloth cloak") instead of a board. */
        public boolean cloth = false;
        /** For a cape: shown on your elytra too, as the game shows its own capes (see CapeElytra). */
        public boolean elytra = true;
        /** Give off this cosmetic's particles (auras, trails). */
        public boolean particles = true;
        /** How many of its particles, 0..3 (1 = as made). */
        public float particleAmount = 1.0f;
        /** How big its particles are, 0.25..3 (1 = as made). */
        public float particleSize = 1.0f;
        /** For a pet: where it sits, or null for where its creator put it. */
        public io.github.georgwav.cosmetics.model.PetPosition petPosition = null;
        /** Turned round to face the other way (for a cosmetic made back to front), around its own middle. */
        public boolean turned = false;
        /** Turned upside down (for a cosmetic made the wrong way up), around its own middle, still facing the same way. */
        public boolean upsideDown = false;
        /** How it takes the colour picked for it, or null to choose from its texture (see {@link io.github.georgwav.cosmetics.render.Recolor#paintFor}). */
        public io.github.georgwav.cosmetics.model.ColorStyle colorStyle = null;
        /**
         * Its own colours changed one by one (its gold kept, its red turned purple), or null for none: one entry per
         * colour of its texture, a kept one to itself (see {@link io.github.georgwav.cosmetics.render.Recolor}).
         */
        public List<ColorSwap> swaps = null;
        /**
         * Sol Client: the colour follows the theme ("accent" or "glow") instead of {@link #color}, so a halo turns
         * white in Deep Field and orange in Ember; null (the default, and never written) keeps its own colour.
         */
        public String themeColor = null;

        /** A copy, to keep in an outfit (or take out of one) without the two changing together. */
        public CosmeticOptions copy() {
            return GSON.fromJson(GSON.toJsonTree(this), CosmeticOptions.class);
        }

        /** A fingerprint of how it looks (colours, shape, style, glow, animation): changes when any of them does. */
        public int lookHash() {
            int swapHash = 0;
            if (swaps != null) {
                for (ColorSwap s : swaps) {
                    swapHash = swapHash * 31 + s.from * 17 + s.to;
                }
            }
            return java.util.Objects.hash(color, rainbow, rainbowSpeed, saturation, hue, brightness, scale, offsetX, offsetY,
                    offsetZ, glow, animate, animationSpeed, style, turned, upsideDown, colorStyle, themeColor, swapHash);
        }

        /** Whether every setting is the same as {@code other}'s. */
        public boolean sameAs(CosmeticOptions other) {
            return GSON.toJsonTree(this).equals(GSON.toJsonTree(other));
        }

        public boolean isDefault() {
            CosmeticOptions d = DEFAULT_OPTIONS;
            return color == null && rainbow == d.rainbow && rainbowSpeed == d.rainbowSpeed
                    && saturation == d.saturation && hue == d.hue && brightness == d.brightness && scale == d.scale
                    && offsetX == d.offsetX && offsetY == d.offsetY && offsetZ == d.offsetZ && glow == d.glow
                    && animate == d.animate && animationSpeed == d.animationSpeed && style == null && cloth == d.cloth
                    && elytra == d.elytra && particles == d.particles && particleAmount == d.particleAmount
                    && particleSize == d.particleSize && petPosition == null && turned == d.turned && upsideDown == d.upsideDown
                    && colorStyle == null && themeColor == null
                    && !swapsAny();
        }

        /** Whether a swap changes any colour. */
        public boolean swapsAny() {
            return swaps != null && swaps.stream().anyMatch(c -> ((c.from ^ c.to) & 0xFFFFFF) != 0);
        }

        void sanitize() {
            if (color != null) {
                color &= 0xFFFFFF;
            }
            rainbowSpeed = clamp(rainbowSpeed, 0.1f, 5f, 1f);
            saturation = clamp(saturation, 0f, 1f, 1f);
            hue = clamp(hue, -180f, 180f, 0f);
            brightness = clamp(brightness, 0f, 2f, 1f);
            scale = clamp(scale, 0.5f, MAX_SCALE, 1f);
            offsetX = clamp(offsetX, -8f, 8f, 0f);
            offsetY = clamp(offsetY, -8f, 8f, 0f);
            offsetZ = clamp(offsetZ, -8f, 8f, 0f);
            animationSpeed = clamp(animationSpeed, 0.1f, 3f, 1f);
            particleAmount = clamp(particleAmount, 0f, 3f, 1f);
            particleSize = clamp(particleSize, 0.25f, 3f, 1f);
            if (themeColor != null && !themeColor.equals("accent") && !themeColor.equals("glow")) {
                themeColor = null;
            }
            if (swaps != null) {
                swaps.removeIf(java.util.Objects::isNull);
                swaps.forEach(c -> {
                    c.from &= 0xFFFFFF;
                    c.to &= 0xFFFFFF;
                });
                if (swaps.size() > MAX_SWAPS) {
                    swaps = new ArrayList<>(swaps.subList(0, MAX_SWAPS));
                }
                if (!swapsAny()) {
                    swaps = null;
                }
            }
        }
    }

    /** Most colours one cosmetic's swaps hold. */
    public static final int MAX_SWAPS = 8;

    /** One of a cosmetic's own colours (RGB) and the colour (RGB) it is changed to; the same to keep it. */
    public static final class ColorSwap {
        public int from;
        public int to;

        public ColorSwap() {
        }

        public ColorSwap(int from, int to) {
            this.from = from & 0xFFFFFF;
            this.to = to & 0xFFFFFF;
        }
    }

    private static final CosmeticOptions DEFAULT_OPTIONS = new CosmeticOptions();

    /** Largest size a player can give a cosmetic. */
    public static final float MAX_SCALE = 3f;

    /** Colour options, which {@link #colorAnyCosmetic} turns on for every cosmetic. */
    private static final int COLOR_OPTIONS = io.github.georgwav.cosmetics.model.CosmeticOption.TINT.bit()
            | io.github.georgwav.cosmetics.model.CosmeticOption.RAINBOW.bit()
            | io.github.georgwav.cosmetics.model.CosmeticOption.SATURATION.bit();

    /** The options players may change on a cosmetic: what its file allows, plus colour with {@link #colorAnyCosmetic}. */
    public int allowedOptions(io.github.georgwav.cosmetics.model.BakedCosmetic cosmetic) {
        int allowed = cosmetic.definition().options();
        // Knives keep their packs' own colours.
        return colorAnyCosmetic && cosmetic.slot() != CosmeticSlot.MELEE ? allowed | COLOR_OPTIONS : allowed;
    }

    public boolean allows(io.github.georgwav.cosmetics.model.BakedCosmetic cosmetic,
                          io.github.georgwav.cosmetics.model.CosmeticOption option) {
        return (allowedOptions(cosmetic) & option.bit()) != 0;
    }

    public static CosmeticsConfig get() {
        return instance;
    }

    public SlotPriority priority(CosmeticSlot slot) {
        SlotPriority priority = slotPriority.get(slot);
        return priority != null ? priority : defaultPriority(slot);
    }

    public boolean slotVisible(CosmeticSlot slot) {
        Boolean visible = slotVisible.get(slot);
        return visible == null || visible;
    }

    /** Settings for a cosmetic, for reading. Never null; don't modify the result. */
    public CosmeticOptions options(String cosmeticId) {
        Outfit shown = shownOutfit;
        if (shown != null && shown.cosmetics.contains(cosmeticId)) {
            return shown.optionsFor(cosmeticId);
        }
        CosmeticOptions options = cosmeticOptions.get(cosmeticId);
        return options != null ? options : DEFAULT_OPTIONS;
    }

    /** Settings for a cosmetic, for editing (created on first use). */
    public CosmeticOptions editOptions(String cosmeticId) {
        return cosmeticOptions.computeIfAbsent(cosmeticId, id -> new CosmeticOptions());
    }

    /** Puts one cosmetic's settings back to how its creator made it. */
    public void resetOptions(String cosmeticId) {
        cosmeticOptions.remove(cosmeticId);
    }

    /** The saved outfit called {@code name}, or null. */
    public Outfit outfit(String name) {
        for (Outfit outfit : savedOutfits) {
            if (outfit.name.equals(name)) {
                return outfit;
            }
        }
        return null;
    }

    /** Saves what the local player wears, with those cosmetics' settings, as a new outfit "{prefix} N". */
    public Outfit saveOutfit(String namePrefix) {
        int n = 1;
        while (outfit(namePrefix + " " + n) != null) {
            n++;
        }
        Outfit outfit = new Outfit(namePrefix + " " + n, List.of());
        updateOutfit(outfit);
        savedOutfits.add(outfit);
        return outfit;
    }

    /** Makes the outfit what the local player wears now, with those cosmetics' settings now. */
    public void updateOutfit(Outfit outfit) {
        outfit.cosmetics = new ArrayList<>(localEquipped());
        outfit.options = new LinkedHashMap<>();
        for (String id : outfit.cosmetics) {
            CosmeticOptions options = cosmeticOptions.get(id);
            if (options != null && !options.isDefault()) {
                outfit.options.put(id, options.copy());
            }
        }
    }

    /**
     * Puts on exactly the outfit's cosmetics (what you wore before comes off), each with the settings it has in
     * the outfit.
     */
    public void wearOutfit(Outfit outfit) {
        List<String> wearing = localEquipped();
        wearing.clear();
        wearing.addAll(outfit.cosmetics);
        for (String id : outfit.cosmetics) {
            CosmeticOptions options = outfit.options.get(id);
            if (options != null) {
                cosmeticOptions.put(id, options.copy());
            } else {
                cosmeticOptions.remove(id);
            }
        }
    }

    /** Whether the local player wears exactly this outfit (in any order), with its settings. */
    public boolean wearsOutfit(Outfit outfit) {
        if (!new java.util.HashSet<>(outfit.cosmetics).equals(new java.util.HashSet<>(localEquipped()))) {
            return false;
        }
        for (String id : outfit.cosmetics) {
            CosmeticOptions mine = cosmeticOptions.get(id);
            if (!outfit.optionsFor(id).sameAs(mine != null ? mine : DEFAULT_OPTIONS)) {
                return false;
            }
        }
        return true;
    }

    /** Renames an outfit; false (and nothing changes) when the name is blank or another outfit has it. */
    public boolean renameOutfit(Outfit outfit, String name) {
        String clean = name == null ? "" : name.strip();
        if (clean.isEmpty() || (outfit(clean) != null && outfit(clean) != outfit)) {
            return false;
        }
        outfit.name = clean;
        return true;
    }

    /**
     * Shows {@code outfit}'s settings on its cosmetics without changing yours (null stops): what the settings screen
     * uses to show an outfit on you before you wear it.
     */
    public void showOutfitSettings(Outfit outfit) {
        shownOutfit = outfit;
    }

    /** The local player's equipped cosmetic ids (mutable; call {@code EquippedCosmetics.invalidate()} after changes). */
    public List<String> localEquipped() {
        return equipped.computeIfAbsent("local", k -> new ArrayList<>());
    }

    /**
     * Puts {@code id} on (or takes it off) in {@code wearing}. Unless several of a kind are allowed, putting it on
     * takes off the others of its kind ({@code kindOf} gives an id's kind, or null for one that isn't loaded).
     */
    public static void wear(List<String> wearing, String id, boolean on, java.util.function.Function<String, String> kindOf,
                            boolean severalOfAKind) {
        wearing.remove(id);
        if (!on) {
            return;
        }
        String kind = kindOf.apply(id);
        if (!severalOfAKind && kind != null) {
            wearing.removeIf(other -> kind.equals(kindOf.apply(other)));
        }
        wearing.add(id);
    }

    /** Puts every setting except the wardrobe (what you wear and how each cosmetic looks) back to its default. */
    public void resetSettings() {
        CosmeticsConfig defaults = new CosmeticsConfig();
        enabled = defaults.enabled;
        showOwnCosmetics = defaults.showOwnCosmetics;
        showOtherPlayersCosmetics = defaults.showOtherPlayersCosmetics;
        slotVisible = defaultVisibility();
        hideHeadwearWithHelmet = defaults.hideHeadwearWithHelmet;
        severalOfAKind = defaults.severalOfAKind;
        colorAnyCosmetic = defaults.colorAnyCosmetic;
        shieldItemIcons = defaults.shieldItemIcons;
        hideCapeWithElytra = defaults.hideCapeWithElytra;
        elytraCloseToBack = defaults.elytraCloseToBack;
        capePhysics = defaults.capePhysics;
        capeSwing = defaults.capeSwing;
        wavyCapes = defaults.wavyCapes;
        particles = defaults.particles;
        particleDensity = defaults.particleDensity;
        ownParticlesInFirstPerson = defaults.ownParticlesInFirstPerson;
        emissiveParts = defaults.emissiveParts;
        translucentParts = defaults.translucentParts;
        emotes = defaults.emotes;
        emoteCamera = defaults.emoteCamera;
        emoteStopOnMove = defaults.emoteStopOnMove;
        elytraEmoteRockets = defaults.elytraEmoteRockets;
        maxRenderDistance = defaults.maxRenderDistance;
        renderInShadowPass = defaults.renderInShadowPass;
        shadowDistance = defaults.shadowDistance;
        maxMeshTriangles = defaults.maxMeshTriangles;
        slotPriority = defaultPriorities();
        deferToFigura = defaults.deferToFigura;
    }

    public static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve(CosmeticsMod.MOD_ID + ".json");
    }

    public static void load() {
        Path path = path();
        CosmeticsConfig loaded = null;
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path)) {
                loaded = GSON.fromJson(reader, CosmeticsConfig.class);
            } catch (IOException | JsonParseException e) {
                CosmeticsMod.LOGGER.error("Could not read {}, using defaults", path, e);
            }
        }
        if (loaded == null) {
            loaded = new CosmeticsConfig();
        }
        loaded.sanitize();
        instance = loaded;
        save();
    }

    public static void save() {
        Path path = path();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            CosmeticsMod.LOGGER.error("Could not write {}", path, e);
        }
    }

    /** A list of ids without nulls or repeats, in order (a missing list gives an empty one). */
    private static List<String> cleanIds(List<String> ids) {
        return ids == null ? new ArrayList<>()
                : new ArrayList<>(new java.util.LinkedHashSet<>(ids.stream().filter(id -> id != null).toList()));
    }

    /** Repairs a hand-edited or older file: fills missing entries, clamps numbers into range. */
    void sanitize() {
        if (slotPriority == null) {
            slotPriority = defaultPriorities();
        }
        // Unknown slot or value names in a hand-edited file deserialize to null; drop them.
        slotPriority.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        if (slotVisible == null) {
            slotVisible = defaultVisibility();
        }
        slotVisible.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (newerSlot(slot)) {
                continue;
            }
            slotPriority.putIfAbsent(slot, defaultPriority(slot));
            slotVisible.putIfAbsent(slot, true);
        }
        if (configVersion < 2) {
            // Files from before capes overrode the vanilla cape by default still say DEFER for them.
            slotPriority.put(CosmeticSlot.CAPE, SlotPriority.OVERRIDE);
        }
        if (configVersion < 3) {
            // The wheels were lists (empty: every emote); now they are slots, empty until you fill them.
            emoteWheelSlots = cleanIds(emoteWheel);
            elytraWheelSlots = cleanIds(elytraEmoteWheel);
        }
        if (configVersion < 4 && maxMeshTriangles == 20_000) {
            // The old default left out detailed imported wings (each side counts); a limit someone set stays.
            maxMeshTriangles = new CosmeticsConfig().maxMeshTriangles;
        }
        emoteWheel = new ArrayList<>();
        elytraEmoteWheel = new ArrayList<>();
        configVersion = CONFIG_VERSION;
        if (equipped == null) {
            equipped = new LinkedHashMap<>();
        }
        equipped.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        equipped.values().forEach(list -> list.removeIf(id -> id == null));
        if (savedOutfits == null) {
            savedOutfits = new ArrayList<>();
        }
        // Outfits from before format 5 (name to cosmetics, no settings) become saved outfits.
        if (outfits != null) {
            outfits.forEach((name, ids) -> {
                if (name != null && ids != null) {
                    savedOutfits.add(new Outfit(name, ids));
                }
            });
        }
        outfits = new LinkedHashMap<>();
        java.util.Set<String> names = new java.util.HashSet<>();
        savedOutfits.removeIf(o -> o == null || o.name == null || o.name.isBlank() || !names.add(o.name.strip()));
        for (Outfit outfit : savedOutfits) {
            outfit.name = outfit.name.strip();
            outfit.cosmetics = cleanIds(outfit.cosmetics);
            if (outfit.options == null) {
                outfit.options = new LinkedHashMap<>();
            }
            outfit.options.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null
                    || !outfit.cosmetics.contains(e.getKey()));
            outfit.options.values().forEach(CosmeticOptions::sanitize);
            outfit.options.values().removeIf(CosmeticOptions::isDefault);
        }
        if (emoteCamera == null) {
            emoteCamera = EmoteCamera.FRONT;
        }
        emoteWheelSlots = wheelPages(emoteWheelSlots);
        elytraWheelSlots = wheelPages(elytraWheelSlots);
        if (!Float.isFinite(capeSwing)) {
            capeSwing = 1.0f;
        }
        capeSwing = Math.max(0f, Math.min(2f, capeSwing));
        particleDensity = Float.isFinite(particleDensity) ? Math.max(0f, Math.min(2f, particleDensity)) : 1.0f;
        maxRenderDistance = Math.max(0, Math.min(512, maxRenderDistance));
        shadowDistance = Math.max(0, Math.min(512, shadowDistance));
        maxMeshTriangles = Math.max(1_000, Math.min(200_000, maxMeshTriangles));
        if (cosmeticOptions == null) {
            cosmeticOptions = new LinkedHashMap<>();
        }
        cosmeticOptions.entrySet().removeIf(e -> e.getKey() == null || e.getValue() == null);
        cosmeticOptions.values().forEach(CosmeticOptions::sanitize);
        // Don't keep entries that change nothing.
        cosmeticOptions.values().removeIf(CosmeticOptions::isDefault);
    }

    private static float clamp(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }

    /**
     * Slots added after format 5 (knives): kept out of the slot maps until set (their getters default), so a file
     * from the cosmetics mod saves back unchanged.
     */
    private static boolean newerSlot(CosmeticSlot slot) {
        return slot == CosmeticSlot.MELEE;
    }

    private static Map<CosmeticSlot, SlotPriority> defaultPriorities() {
        Map<CosmeticSlot, SlotPriority> map = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!newerSlot(slot)) {
                map.put(slot, defaultPriority(slot));
            }
        }
        return map;
    }

    /** Capes replace the vanilla cape by default: wearing one of ours and seeing the vanilla one makes no sense. */
    static SlotPriority defaultPriority(CosmeticSlot slot) {
        return slot == CosmeticSlot.CAPE ? SlotPriority.OVERRIDE : SlotPriority.DEFER;
    }

    private static Map<CosmeticSlot, Boolean> defaultVisibility() {
        Map<CosmeticSlot, Boolean> map = new EnumMap<>(CosmeticSlot.class);
        for (CosmeticSlot slot : CosmeticSlot.values()) {
            if (!newerSlot(slot)) {
                map.put(slot, true);
            }
        }
        return map;
    }

    private static Map<String, List<String>> defaultEquipped() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("local", new ArrayList<>());
        return map;
    }
}
