package io.github.georgwav.cosmetics.sol.ui.wardrobe;

import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.CosmeticsConfig.CosmeticOptions;
import io.github.georgwav.cosmetics.model.ColorStyle;
import io.github.georgwav.cosmetics.model.CosmeticOption;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.ObjDoubleConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Every control the Cosmetics screen had, as data: the Wardrobe's settings pages and a cosmetic's page are drawn
 * from these lists, and CosmeticsParityTest checks that every setting of {@link CosmeticsConfig} and
 * {@link CosmeticOptions} is bound by one of them (and that each control changes the field it names), and that every
 * {@link CosmeticOption} decides whether some control shows. Pure Java: no game classes, so it is unit-tested.
 */
public final class CosmeticControls {
    private CosmeticControls() {
    }

    /** A control: its page and section, its label's language key, and the setting (field name) it changes. */
    public sealed interface Control permits Toggle, Slider, Choice, Special {
        String section();

        String key();

        String field();
    }

    /** On or off. */
    public record Toggle(String section, String key, String field, Predicate<CosmeticsConfig> get,
                         BiConsumer<CosmeticsConfig, Boolean> set) implements Control {
    }

    /** A number from {@code min} to {@code max} in {@code step}s; {@code format} names how its value reads. */
    public record Slider(String section, String key, String field, double min, double max, double step,
                         ToDoubleFunction<CosmeticsConfig> get, ObjDoubleConsumer<CosmeticsConfig> set, String format)
            implements Control {
    }

    /** One of a few values; {@code names} gives each value's language key (null is the "automatic" one). */
    public record Choice<T>(String section, String key, String field, List<T> values, Function<CosmeticsConfig, T> get,
                            BiConsumer<CosmeticsConfig, T> set, Function<T, String> names) implements Control {
    }

    /**
     * A control drawn by its page because it is more than one value (the slots, the priorities, key binds, other
     * players' cosmetics); {@code what} names it for the page.
     */
    public record Special(String section, String key, String field, String what) implements Control {
    }

    // ------------------------------------------------------------------ the settings pages

    /** The settings pages, in order (the Cosmetics screen's Display, Performance and Compatibility tabs). */
    public static final List<String> PAGES = List.of("display", "performance", "compatibility", "maintenance");

    /** Each page's controls in order; a section starts where a control's section differs from the one before. */
    public static final Map<String, List<Control>> GLOBAL = Map.of(
            "display", List.of(
                    toggle("general", "enabled", "enabled", c -> c.enabled, (c, v) -> c.enabled = v),
                    toggle("general", "show_own", "showOwnCosmetics", c -> c.showOwnCosmetics, (c, v) -> c.showOwnCosmetics = v),
                    toggle("general", "show_others", "showOtherPlayersCosmetics", c -> c.showOtherPlayersCosmetics,
                            (c, v) -> c.showOtherPlayersCosmetics = v),
                    toggle("general", "several_of_a_kind", "severalOfAKind", c -> c.severalOfAKind, (c, v) -> c.severalOfAKind = v),
                    toggle("general", "color_any_cosmetic", "colorAnyCosmetic", c -> c.colorAnyCosmetic,
                            (c, v) -> c.colorAnyCosmetic = v),
                    new Choice<>("general", "theme_color_all", "themeColorAll", Arrays.asList(null, "accent", "glow"),
                            c -> c.themeColorAll, (c, v) -> c.themeColorAll = v, v -> "theme_color_all." + (v == null ? "off" : v)),
                    toggle("general", "shield_item_icons", "shieldItemIcons", c -> c.shieldItemIcons, (c, v) -> c.shieldItemIcons = v),
                    new Special("keys", "key_open", null, "key_open"),
                    new Special("keys", "key_emotes", null, "key_emotes"),
                    new Special("slots", "slots", "slotVisible", "slots"),
                    toggle("details", "emissive", "emissiveParts", c -> c.emissiveParts, (c, v) -> c.emissiveParts = v),
                    toggle("details", "translucent", "translucentParts", c -> c.translucentParts, (c, v) -> c.translucentParts = v),
                    toggle("details", "hide_helmet", "hideHeadwearWithHelmet", c -> c.hideHeadwearWithHelmet,
                            (c, v) -> c.hideHeadwearWithHelmet = v),
                    toggle("details", "hide_elytra", "hideCapeWithElytra", c -> c.hideCapeWithElytra, (c, v) -> c.hideCapeWithElytra = v),
                    toggle("details", "elytra_close", "elytraCloseToBack", c -> c.elytraCloseToBack, (c, v) -> c.elytraCloseToBack = v),
                    toggle("details", "cape_physics", "capePhysics", c -> c.capePhysics, (c, v) -> c.capePhysics = v),
                    toggle("details", "wavy_capes", "wavyCapes", c -> c.wavyCapes, (c, v) -> c.wavyCapes = v),
                    new Slider("details", "cape_swing", "capeSwing", 0, 2, 0.1, c -> c.capeSwing, (c, v) -> c.capeSwing = (float) v,
                            "percent"),
                    toggle("particles", "particles", "particles", c -> c.particles, (c, v) -> c.particles = v),
                    new Slider("particles", "particle_density", "particleDensity", 0, 2, 0.1, c -> c.particleDensity,
                            (c, v) -> c.particleDensity = (float) v, "percent"),
                    toggle("particles", "own_particles_first_person", "ownParticlesInFirstPerson", c -> c.ownParticlesInFirstPerson,
                            (c, v) -> c.ownParticlesInFirstPerson = v),
                    toggle("emotes", "emotes_enabled", "emotes", c -> c.emotes, (c, v) -> c.emotes = v),
                    new Choice<>("emotes", "emote_camera", "emoteCamera", List.of(CosmeticsConfig.EmoteCamera.values()),
                            c -> c.emoteCamera, (c, v) -> c.emoteCamera = v, v -> "emote_camera." + v.name().toLowerCase(java.util.Locale.ROOT)),
                    toggle("emotes", "emote_stop_on_move", "emoteStopOnMove", c -> c.emoteStopOnMove, (c, v) -> c.emoteStopOnMove = v),
                    toggle("emotes", "elytra_emote_rockets", "elytraEmoteRockets", c -> c.elytraEmoteRockets,
                            (c, v) -> c.elytraEmoteRockets = v)),
            "performance", List.of(
                    new Slider("distance", "render_distance", "maxRenderDistance", 0, 256, 8, c -> c.maxRenderDistance,
                            (c, v) -> c.maxRenderDistance = (int) v, "blocks"),
                    new Special("shaders", "iris", null, "iris"),
                    toggle("shaders", "shadows", "renderInShadowPass", c -> c.renderInShadowPass, (c, v) -> c.renderInShadowPass = v),
                    new Slider("shaders", "shadow_distance", "shadowDistance", 0, 256, 8, c -> c.shadowDistance,
                            (c, v) -> c.shadowDistance = (int) v, "blocks"),
                    // Only in the file on main; here as an advanced slider.
                    new Slider("advanced", "max_mesh_triangles", "maxMeshTriangles", 1000, 200_000, 1000, c -> c.maxMeshTriangles,
                            (c, v) -> c.maxMeshTriangles = (int) v, "count")),
            "compatibility", List.of(
                    new Special("priority", "priority", "slotPriority", "priorities"),
                    toggle("priority", "defer_figura", "deferToFigura", c -> c.deferToFigura, (c, v) -> c.deferToFigura = v),
                    new Special("detected", "detected", null, "detected")),
            "maintenance", List.of(
                    new Special("maintenance", "loaded", null, "loaded"),
                    new Special("maintenance", "errors", null, "errors"),
                    new Special("maintenance", "reload", null, "reload"),
                    new Special("maintenance", "reset", null, "reset_settings"),
                    new Special("maintenance", "build", null, "build"),
                    // Only in the file on main: other players' cosmetics by UUID (the "equipped" keys besides "local").
                    new Special("players", "players", "equipped", "players")));

    /**
     * Settings with a place of their own rather than a row: what you wear (the grid), your saved outfits (the
     * Outfits view), every cosmetic's own settings (its page), and the emote wheels (the emote wheel and the Emotes
     * view's Wheel buttons).
     */
    public static final Map<String, String> ELSEWHERE = Map.of(
            "equipped", "the Wardrobe grid wears and takes off; Maintenance lists other players'",
            "savedOutfits", "the Outfits view and an outfit's page",
            "cosmeticOptions", "a cosmetic's page (COSMETIC below)",
            "emoteWheelSlots", "the emote wheel and the Emotes view's Wheel button",
            "elytraWheelSlots", "the emote wheel while gliding and the Emotes view's Elytra button");

    /**
     * Settings no control changes, each on purpose: the file's format number, and the old shapes of the emote
     * wheels and outfits that loading moves into the new ones.
     */
    public static final Map<String, String> NOT_CONTROLS = Map.of(
            "configVersion", "the file's format number, written by the mod",
            "emoteWheel", "the emote wheel before pages (format 3), moved into emoteWheelSlots on load",
            "elytraEmoteWheel", "the elytra wheel before pages (format 3), moved into elytraWheelSlots on load",
            "outfits", "outfits before format 5 (names only), moved into savedOutfits on load");

    // ------------------------------------------------------------------ a cosmetic's page

    /**
     * When a control on a cosmetic's page shows: the option its file must allow (or null for none), and anything
     * else the cosmetic must be (a cape, a pet, animated...), named for the page to test.
     */
    public record Gate(CosmeticOption option, String also) {
    }

    /** A control on a cosmetic's page, bound to one field of its {@link CosmeticOptions}. */
    public sealed interface Own permits OwnToggle, OwnSlider, OwnChoice, OwnSpecial {
        String section();

        String key();

        String field();

        Gate gate();
    }

    public record OwnToggle(String section, String key, String field, Gate gate, Predicate<CosmeticOptions> get,
                            BiConsumer<CosmeticOptions, Boolean> set) implements Own {
    }

    public record OwnSlider(String section, String key, String field, Gate gate, double min, double max, double step,
                            ToDoubleFunction<CosmeticOptions> get, ObjDoubleConsumer<CosmeticOptions> set, String format)
            implements Own {
    }

    public record OwnChoice<T>(String section, String key, String field, Gate gate, List<T> values,
                               Function<CosmeticOptions, T> get, BiConsumer<CosmeticOptions, T> set, Function<T, String> names)
            implements Own {
    }

    /** Drawn by the page: the colour picker (with Follow theme), the cosmetic's own colours, its styles, a pet's place. */
    public record OwnSpecial(String section, String key, String field, Gate gate, String what) implements Own {
    }

    /** A cosmetic's page, in order, every option the Cosmetics screen's page had. */
    public static final List<Own> COSMETIC = List.of(
            new OwnSpecial("style", "style", "style", new Gate(CosmeticOption.STYLE, "styles"), "style"),
            new OwnSpecial("pet", "pet_position", "petPosition", new Gate(null, "pet"), "pet_position"),
            new OwnToggle("elytra", "elytra", "elytra", new Gate(null, "cape"), o -> o.elytra, (o, v) -> o.elytra = v),
            new OwnSpecial("elytra", "preview_elytra", null, new Gate(null, "cape"), "preview_elytra"),
            // The setting for every shield cosmetic, on the shield's own page too, where you look for it.
            new OwnSpecial("shield", "shield_item_icon", null, new Gate(null, "shield"), "shield_item_icon"),
            new OwnSpecial("color", "color", "color", new Gate(CosmeticOption.TINT, null), "color"),
            new OwnSpecial("color", "follow_theme", "themeColor", new Gate(CosmeticOption.TINT, null), "follow_theme"),
            new OwnToggle("color", "rainbow", "rainbow", new Gate(CosmeticOption.RAINBOW, null), o -> o.rainbow, (o, v) -> o.rainbow = v),
            new OwnSlider("color", "rainbow_speed", "rainbowSpeed", new Gate(CosmeticOption.RAINBOW, "rainbow_on"), 0.1, 5, 0.1,
                    o -> o.rainbowSpeed, (o, v) -> o.rainbowSpeed = (float) v, "times"),
            new OwnChoice<>("color", "color_style", "colorStyle", new Gate(null, "tint_or_rainbow"),
                    Arrays.asList(null, ColorStyle.WHOLE, ColorStyle.COLORED_PARTS, ColorStyle.CLASSIC), o -> o.colorStyle,
                    (o, v) -> o.colorStyle = v, v -> "color_style." + (v == null ? "auto" : v.name().toLowerCase(java.util.Locale.ROOT))),
            new OwnSlider("color", "saturation", "saturation", new Gate(CosmeticOption.SATURATION, null), 0, 1, 0.05,
                    o -> o.saturation, (o, v) -> o.saturation = (float) v, "percent"),
            new OwnSlider("color", "hue", "hue", new Gate(CosmeticOption.SATURATION, null), -180, 180, 5,
                    o -> o.hue, (o, v) -> o.hue = (float) v, "degrees"),
            new OwnSlider("color", "brightness", "brightness", new Gate(CosmeticOption.SATURATION, null), 0, 2, 0.05,
                    o -> o.brightness, (o, v) -> o.brightness = (float) v, "percent"),
            new OwnSpecial("color", "swaps", "swaps", new Gate(CosmeticOption.SATURATION, "palette"), "swaps"),
            new OwnSlider("shape", "size", "scale", new Gate(CosmeticOption.SIZE, null), 0.5, CosmeticsConfig.MAX_SCALE, 0.05,
                    o -> o.scale, (o, v) -> o.scale = (float) v, "percent"),
            new OwnToggle("shape", "turned", "turned", new Gate(null, "size_or_position"), o -> o.turned, (o, v) -> o.turned = v),
            new OwnToggle("shape", "upside_down", "upsideDown", new Gate(null, "size_or_position"), o -> o.upsideDown,
                    (o, v) -> o.upsideDown = v),
            new OwnSlider("shape", "offset_x", "offsetX", new Gate(CosmeticOption.POSITION, null), -8, 8, 0.5,
                    o -> o.offsetX, (o, v) -> o.offsetX = (float) v, "pixels"),
            // Up and forward read the other way round from the model's axes, as on the Cosmetics screen.
            new OwnSlider("shape", "offset_y", "offsetY", new Gate(CosmeticOption.POSITION, null), -8, 8, 0.5,
                    o -> -o.offsetY, (o, v) -> o.offsetY = (float) -v, "pixels"),
            new OwnSlider("shape", "offset_z", "offsetZ", new Gate(CosmeticOption.POSITION, null), -8, 8, 0.5,
                    o -> -o.offsetZ, (o, v) -> o.offsetZ = (float) -v, "pixels"),
            new OwnToggle("effects", "cloth", "cloth", new Gate(null, "flat_cape"), o -> o.cloth, (o, v) -> o.cloth = v),
            new OwnToggle("effects", "glow", "glow", new Gate(CosmeticOption.GLOW, "emissive"), o -> o.glow, (o, v) -> o.glow = v),
            new OwnToggle("effects", "animation", "animate", new Gate(CosmeticOption.ANIMATION, "animated"), o -> o.animate,
                    (o, v) -> o.animate = v),
            new OwnSlider("effects", "animation_speed", "animationSpeed", new Gate(CosmeticOption.ANIMATION, "animated"), 0.1, 3, 0.1,
                    o -> o.animationSpeed, (o, v) -> o.animationSpeed = (float) v, "times"),
            // Knives: Dark Star's model (Devil or Angel) and the arms' size.
            new OwnChoice<>("knife", "knife_set", "knifeSet", new Gate(null, "knife_angel"), Arrays.asList(null, "random", "devil", "angel"),
                    o -> o.knifeSet, (o, v) -> o.knifeSet = v, v -> "knife_set." + (v == null ? "auto" : v)),
            new OwnSlider("knife", "knife_hands", "knifeHands", new Gate(null, "knife"), 0.5, 1.5, 0.02,
                    o -> o.knifeHands == null ? 1.0 : o.knifeHands, (o, v) -> o.knifeHands = Math.abs(v - 1.0) < 1e-4 ? null : (float) v,
                    "percent"),
            new OwnToggle("cosmetic_particles", "cosmetic_particles", "particles", new Gate(null, "particles"), o -> o.particles,
                    (o, v) -> o.particles = v),
            new OwnSlider("cosmetic_particles", "particle_amount", "particleAmount", new Gate(null, "particles"), 0, 3, 0.1,
                    o -> o.particleAmount, (o, v) -> o.particleAmount = (float) v, "percent"),
            new OwnSlider("cosmetic_particles", "particle_size", "particleSize", new Gate(null, "particles"), 0.25, 3, 0.05,
                    o -> o.particleSize, (o, v) -> o.particleSize = (float) v, "percent"));

    private static Toggle toggle(String section, String key, String field, Predicate<CosmeticsConfig> get,
                                 BiConsumer<CosmeticsConfig, Boolean> set) {
        return new Toggle(section, key, field, get, set);
    }

    /** A slider's value as it reads ("120%", "1.5x", "+2.0 px", "-45°"); "blocks" and "count" are the page's. */
    public static String format(String format, double v) {
        return switch (format) {
            case "percent" -> Math.round(v * 100) + "%";
            case "times" -> String.format(java.util.Locale.ROOT, "%.1fx", v);
            case "pixels" -> (v > 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.1f px", v);
            case "degrees" -> (v > 0 ? "+" : "") + Math.round(v) + "°";
            default -> String.valueOf(Math.round(v));
        };
    }
}
