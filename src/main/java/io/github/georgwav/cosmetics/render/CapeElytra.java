package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticBaker;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.model.CosmeticOption;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.RenderMode;
import io.github.georgwav.cosmetics.model.TextureFrames;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Your cape on your elytra, as the game shows its own capes there ("Show on elytra" on a cape's page, on by default).
 * The elytra mixin (WingsLayerMixin) asks {@link #texture} which texture to draw a player's elytra with; null leaves
 * the game's choice (its elytra, or the player's own cape).
 *
 * <p>The texture is made once per cape: from a texture of its own beside the cape's ({@code <texture>_elytra.png},
 * the 64 x 32 layout, a vertical strip of frames for an animated one), else from a 64 x 32 cape texture's own elytra
 * part when it is drawn on, else from the cape's design ({@link ElytraArt}), with its glowing parts drawn in, in the
 * shape of the game's elytra texture (the resource pack's, when it changes it). A cape
 * with a texture animation gets one texture per frame, shown as the cape shows them, smaller when it has many (one
 * cape's elytra takes at most {@link #BUDGET} bytes). Recoloured and tinted as the cape is, each frame repainted (at
 * most every {@link #REPAINT_NANOS}) when that changes. All dropped on resource reload. Render thread only.
 */
public final class CapeElytra {
    /** Most bytes one cape's elytra textures take, every frame together. */
    static final long BUDGET = 16L * 1024 * 1024;
    /** Most pixels a block an elytra made from a cape is drawn at. */
    static final int MAX_SCALE = 32;
    /** Fewest nanoseconds between two repaints of a frame (a colour slider being dragged, the rainbow turning). */
    private static final long REPAINT_NANOS = 120_000_000L;
    /** The chest's slot in the player's inventory (36 to 39 are the armour, feet to head). */
    private static final int CHEST_SLOT = 38;
    private static final int WHITE = 0xFFFFFFFF;
    /** The game's elytra texture, whose shape an elytra made from a cape takes: from 1.21.2, then before. */
    private static final String[] ELYTRA_TEXTURES = {"minecraft:textures/entity/equipment/wings/elytra.png",
            "minecraft:textures/entity/elytra.png"};

    /** Frames an elytra was drawn with a cape on it, and textures made for that (read by the in-game test). */
    public static long frames;
    public static int made;
    /** Frames the Wardrobe's preview was drawn wearing an elytra (read by the in-game test). */
    public static long previewFrames;

    private static final Map<String, Elytra> ELYTRAS = new HashMap<>();
    private static ItemStack elytraItem;

    /** One cape's elytra: its frames, as made and as painted. */
    private static final class Elytra {
        int width;
        int height;
        /** Each frame's pixels before the cape's colours; null when the cape has no elytra to make. */
        int[][] frames;
        /** Which frame shows when (the cape's), or null to show the first. */
        TextureFrames timing;
        Object[] ids;
        DynamicTexture[] textures;
        Look[] painted;
        long[] paintedAt;
        boolean failed;
    }

    /** How a cape's elytra is painted: the cape's texture changes and the colour it is tinted with. */
    private record Look(Recolor recolor, int tint) {
    }

    private CapeElytra() {
    }

    /**
     * The texture to draw the elytra of the player {@code entityId} with (an Identifier, a ResourceLocation before
     * 1.21.11), or null for the game's.
     */
    public static Object texture(int entityId) {
        var level = Minecraft.getInstance().level;
        Entity entity = level == null ? null : level.getEntity(entityId);
        if (!(entity instanceof AbstractClientPlayer player)) {
            return null;
        }
        BakedCosmetic cape = cape(player);
        if (cape == null) {
            return null;
        }
        try {
            Elytra elytra = ELYTRAS.get(cape.id());
            if (elytra == null) {
                elytra = make(cape);
                ELYTRAS.put(cape.id(), elytra);
            }
            if (elytra.failed || elytra.frames == null) {
                return null;
            }
            CosmeticsConfig.CosmeticOptions options = CosmeticsConfig.get().options(cape.id());
            double seconds = CosmeticRenderer.seconds();
            int frame = 0;
            if (elytra.timing != null && options.animate) {
                float speed = cape.allows(CosmeticOption.ANIMATION) ? options.animationSpeed : 1f;
                frame = elytra.timing.frameAt(seconds, speed) % elytra.frames.length;
            }
            Object id = paint(elytra, frame, look(cape, options, seconds));
            if (id != null) {
                frames++;
            }
            return id;
        } catch (RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("Couldn't put cape {} on the elytra", cape.id(), e);
            Elytra failed = new Elytra();
            failed.failed = true;
            ELYTRAS.put(cape.id(), failed);
            return null;
        }
    }

    /**
     * The cape of ours to show on {@code player}'s elytra, or null: the one drawn for them (the mod and their side on,
     * capes shown, not left to the game's own cape or a Figura avatar), with "Show on elytra" on.
     */
    static BakedCosmetic cape(AbstractClientPlayer player) {
        CosmeticsConfig config = CosmeticsConfig.get();
        boolean local = player == Minecraft.getInstance().player;
        if (!config.enabled || RenderGuard.isLayerDisabled() || !config.slotVisible(CosmeticSlot.CAPE)
                || !CosmeticRenderer.showsFor(player.getUUID(), local)) {
            return null;
        }
        BakedCosmetic cape = null;
        for (BakedCosmetic cosmetic : EquippedCosmetics.forPlayer(player.getUUID(), local)) {
            if (cosmetic.slot() == CosmeticSlot.CAPE && !RenderGuard.isDisabled(cosmetic.id())) {
                cape = cosmetic;
                break;
            }
        }
        if (cape == null || !config.options(cape.id()).elytra) {
            return null;
        }
        if (config.priority(CosmeticSlot.CAPE) == SlotPriority.DEFER
                && ((config.deferToFigura && Compat.hasFiguraAvatar(player.getUUID()))
                || CosmeticsLayer.vanillaCapeVisible(player))) {
            return null;
        }
        return cape;
    }

    /** How the cape's main part is painted now, as {@link CosmeticRenderer} paints it. */
    private static Look look(BakedCosmetic cape, CosmeticsConfig.CosmeticOptions options, double seconds) {
        CosmeticsConfig config = CosmeticsConfig.get();
        BakedCosmetic.Layer base = baseLayer(cape);
        int allowed = config.allowedOptions(cape);
        Recolor recolor = Recolor.of(options, allowed, cape.defaultColor());
        boolean picked = Tint.picked(options, allowed);
        boolean rainbow = Tint.rainbow(options, allowed);
        int color = Tint.argb(options, cape, seconds);
        if (rainbow) {
            // In steps, so the frame isn't repainted for every change of hue.
            color = 0xFF000000 | Recolor.rainbowStep(color);
        }
        boolean takesColor = base.tinted() || (config.colorAnyCosmetic && !cape.hasTintedLayer());
        Recolor.Paint paint = takesColor ? CosmeticRenderer.paint(options.colorStyle, picked, rainbow, base.texture())
                : Recolor.Paint.NONE;
        return new Look(Recolor.painted(recolor, paint, color),
                takesColor && paint != Recolor.Paint.COLORED ? color : WHITE);
    }

    /** The texture of {@code frame} painted as {@code look} says, painting it first when it isn't yet. */
    private static Object paint(Elytra elytra, int frame, Look look) {
        if (elytra.ids[frame] == null) {
            elytra.ids[frame] = RecoloredTextures.newId("cape_elytra/" + made++);
            elytra.textures[frame] = RecoloredTextures.newTexture(elytra.width, elytra.height);
            RecoloredTextures.register(elytra.ids[frame], elytra.textures[frame]);
        }
        long now = System.nanoTime();
        if (elytra.painted[frame] == null
                || (!elytra.painted[frame].equals(look) && now - elytra.paintedAt[frame] > REPAINT_NANOS)) {
            int[] source = elytra.frames[frame];
            int[] pixels = new int[source.length];
            if (look.recolor() != null) {
                look.recolor().applyAll(source, pixels);
            } else {
                System.arraycopy(source, 0, pixels, 0, source.length);
            }
            if (look.tint() != WHITE) {
                tint(pixels, look.tint());
            }
            RecoloredTextures.upload(elytra.textures[frame], pixels, elytra.width, elytra.height);
            elytra.painted[frame] = look;
            elytra.paintedAt[frame] = now;
        }
        return elytra.ids[frame];
    }

    /** Multiplies every pixel's colour by {@code argb}'s, as the game tints a texture. */
    static void tint(int[] pixels, int argb) {
        int tr = argb >> 16 & 0xFF;
        int tg = argb >> 8 & 0xFF;
        int tb = argb & 0xFF;
        for (int i = 0; i < pixels.length; i++) {
            int c = pixels[i];
            pixels[i] = (c & 0xFF000000) | ((c >> 16 & 0xFF) * tr / 255) << 16 | ((c >> 8 & 0xFF) * tg / 255) << 8
                    | (c & 0xFF) * tb / 255;
        }
    }

    /** The cape's main part: its first one that doesn't glow. */
    private static BakedCosmetic.Layer baseLayer(BakedCosmetic cape) {
        BakedCosmetic.Layer[] layers = cape.layers();
        for (BakedCosmetic.Layer layer : layers) {
            if (layer.mode() != RenderMode.EMISSIVE) {
                return layer;
            }
        }
        return layers[0];
    }

    /** Makes a cape's elytra frames (see the class); without textures yet, made as each frame is first shown. */
    private static Elytra make(BakedCosmetic cape) {
        Elytra elytra = new Elytra();
        if (cape.layers().length == 0) {
            return elytra;
        }
        BakedCosmetic.Layer base = baseLayer(cape);
        TextureFrames timing = base.textureFrames();
        int count = timing != null ? timing.count() : 1;
        try {
            if (!own(elytra, base.texture(), count) && !fromCape(elytra, cape, base, count)) {
                return elytra;
            }
        } catch (Exception e) {
            CosmeticsMod.LOGGER.warn("Couldn't read the textures of cape {} for the elytra", cape.id(), e);
            elytra.frames = null;
            return elytra;
        }
        elytra.timing = elytra.frames.length > 1 ? timing : null;
        int n = elytra.frames.length;
        elytra.ids = new Object[n];
        elytra.textures = new DynamicTexture[n];
        elytra.painted = new Look[n];
        elytra.paintedAt = new long[n];
        return elytra;
    }

    /** The cape's elytra texture of its own ({@code <texture>_elytra.png}), when there is one. */
    private static boolean own(Elytra elytra, String texture, int capeFrames) throws Exception {
        String file = texture.endsWith(".png") ? texture.substring(0, texture.length() - 4) + "_elytra.png" : null;
        int[][] read = file != null ? RecoloredTextures.read(file) : null;
        if (read == null) {
            return false;
        }
        int width = read[0][0];
        int frameHeight = width / 2;
        int count = frameHeight > 0 ? Math.max(1, read[0][1] / frameHeight) : 0;
        if (count == 0 || read[0][1] < frameHeight) {
            CosmeticsMod.LOGGER.warn("Elytra texture {} isn't the 64 x 32 layout (twice as wide as high)", file);
            return false;
        }
        // Frames of its own go with the cape's when it has as many; else the first shows.
        int[][] frames = new int[count == capeFrames ? count : 1][];
        for (int i = 0; i < frames.length; i++) {
            frames[i] = rows(read[1], width, i * frameHeight, frameHeight);
        }
        elytra.width = width;
        elytra.height = frameHeight;
        elytra.frames = frames;
        return true;
    }

    /** The elytra made from the cape's own texture: its elytra part when it has one, else its design. */
    private static boolean fromCape(Elytra elytra, BakedCosmetic cape, BakedCosmetic.Layer base, int count)
            throws Exception {
        Source main = Source.of(cape, base);
        if (main == null) {
            return false;
        }
        int[] first = main.frame(0);
        if (ElytraArt.hasOwnElytra(first, main.width, main.frameHeight)) {
            int[][] frames = new int[count][];
            for (int i = 0; i < count; i++) {
                frames[i] = main.frame(i);
            }
            elytra.width = main.width;
            elytra.height = main.frameHeight;
            elytra.frames = frames;
            return true;
        }
        ElytraArt.Rect[] rects = main.rects();
        int scale = ElytraArt.scaleFor(rects[0], MAX_SCALE);
        while (scale > 1 && (long) count * ElytraArt.WIDTH * scale * ElytraArt.HEIGHT * scale * 4 > BUDGET) {
            scale--;
        }
        // Its glowing parts, drawn in: the elytra can't glow.
        CosmeticsConfig config = CosmeticsConfig.get();
        boolean glow = config.emissiveParts && (config.options(cape.id()).glow || !cape.allows(CosmeticOption.GLOW));
        java.util.List<Source> glows = new java.util.ArrayList<>();
        if (glow) {
            for (BakedCosmetic.Layer layer : cape.layers()) {
                Source source = layer.mode() == RenderMode.EMISSIVE ? Source.of(cape, layer) : null;
                if (source != null) {
                    glows.add(source);
                }
            }
        }
        int[][] frames = new int[count][];
        for (int i = 0; i < count; i++) {
            int[] pixels = main.frame(i);
            frames[i] = ElytraArt.fromCape(pixels, main.width, main.frameHeight, rects[0], rects[1], scale);
            for (Source source : glows) {
                ElytraArt.Rect[] r = source.rects();
                ElytraArt.over(frames[i], ElytraArt.fromCape(source.frame(i % source.count), source.width,
                        source.frameHeight, r[0], r[1], scale));
            }
        }
        int width = ElytraArt.WIDTH * scale;
        int height = ElytraArt.HEIGHT * scale;
        // In the shape of the game's elytra (its texture at its 1.21.2+ place, else the older one).
        int[][] mask = null;
        for (String texture : ELYTRA_TEXTURES) {
            mask = RecoloredTextures.read(texture);
            if (mask != null) {
                break;
            }
        }
        if (mask != null) {
            for (int[] frame : frames) {
                ElytraArt.shape(frame, width, height, mask[1], mask[0][0], mask[0][1]);
            }
        }
        elytra.width = width;
        elytra.height = height;
        elytra.frames = frames;
        return true;
    }

    /**
     * One part's texture, read: its frames (stacked in one image, or one file each) and where the cape's outside
     * and inside are in a frame.
     */
    private static final class Source {
        final BakedCosmetic cape;
        final BakedCosmetic.Layer layer;
        final TextureFrames timing;
        final int count;
        int width;
        int frameHeight;
        private int[] strip;

        private Source(BakedCosmetic cape, BakedCosmetic.Layer layer) {
            this.cape = cape;
            this.layer = layer;
            this.timing = layer.textureFrames();
            this.count = timing != null ? timing.count() : 1;
        }

        /** Null when its texture can't be read. */
        static Source of(BakedCosmetic cape, BakedCosmetic.Layer layer) throws Exception {
            Source source = new Source(cape, layer);
            return source.read() ? source : null;
        }

        private boolean read() throws Exception {
            if (timing != null && !timing.strip()) {
                int[] first = file(timing.texture(0));
                return first != null;
            }
            int[][] read = RecoloredTextures.read(layer.texture());
            if (read == null) {
                return false;
            }
            width = read[0][0];
            frameHeight = read[0][1] / count;
            strip = read[1];
            return frameHeight > 0;
        }

        private int[] file(String texture) throws Exception {
            int[][] read = RecoloredTextures.read(texture);
            if (read == null) {
                return null;
            }
            width = read[0][0];
            frameHeight = read[0][1];
            return read[1];
        }

        /** The pixels of frame {@code i}, {@link #width} x {@link #frameHeight}. */
        int[] frame(int i) throws Exception {
            if (strip != null) {
                return rows(strip, width, i * frameHeight, frameHeight);
            }
            int w = width;
            int h = frameHeight;
            int[] pixels = file(timing.texture(i));
            if (pixels == null || width != w || frameHeight != h) {
                throw new IllegalStateException("frame " + i + " of " + layer.texture() + " is missing or another size");
            }
            return pixels;
        }

        /**
         * {outside, inside} of the cape in a frame, in pixels: its cape cube's north and south faces (the cape's
         * outside and inside, as capes are modelled like the game's), else the cape layouts' places.
         */
        ElytraArt.Rect[] rects() {
            CosmeticDefinition definition = cape.definition();
            for (CosmeticDefinition.Part part : definition.parts()) {
                String texture = part.texture() != null ? part.texture() : definition.texture();
                if (part.cubes().isEmpty() || part.mode() != layer.mode() || !Objects.equals(texture, layer.texture())) {
                    continue;
                }
                float[][] faces = CosmeticBaker.faceUvs(part.cubes().get(0));
                float[] north = faces[CosmeticDefinition.Face.NORTH.ordinal()];
                float[] south = faces[CosmeticDefinition.Face.SOUTH.ordinal()];
                if (north != null && south != null) {
                    float sx = width / (float) definition.textureWidth();
                    float sy = frameHeight / (float) definition.textureHeight();
                    return new ElytraArt.Rect[]{
                            new ElytraArt.Rect(north[0] * sx, north[1] * sy, north[2] * sx, north[3] * sy),
                            new ElytraArt.Rect(south[0] * sx, south[1] * sy, south[2] * sx, south[3] * sy)};
                }
            }
            return ElytraArt.capeLayout(width, frameHeight);
        }
    }

    /** {@code count} rows of an image {@code width} wide, from row {@code from}. */
    private static int[] rows(int[] argb, int width, int from, int count) {
        int[] out = new int[width * count];
        System.arraycopy(argb, from * width, out, 0, out.length);
        return out;
    }

    /**
     * Draws {@code draw} with an elytra in {@code player}'s chest slot (the Wardrobe's elytra preview), and puts
     * back what was there right after: the game reads the slot while drawing the player, and nothing else runs in
     * between, so only that drawing sees the elytra.
     */
    public static void wearingElytra(Player player, Runnable draw) {
        Inventory inventory = player.getInventory();
        ItemStack worn = inventory.getItem(CHEST_SLOT);
        if (worn.is(Items.ELYTRA)) {
            previewFrames++;
            draw.run();
            return;
        }
        if (elytraItem == null) {
            elytraItem = new ItemStack(Items.ELYTRA);
        }
        inventory.setItem(CHEST_SLOT, elytraItem);
        try {
            previewFrames++;
            draw.run();
        } finally {
            inventory.setItem(CHEST_SLOT, worn);
        }
    }

    /** Forgets every cape's elytra (resource reload: the textures may have changed). */
    public static void clear() {
        for (Elytra elytra : ELYTRAS.values()) {
            if (elytra.ids != null) {
                for (Object id : elytra.ids) {
                    if (id != null) {
                        RecoloredTextures.release(id);
                    }
                }
            }
        }
        ELYTRAS.clear();
    }

    /**
     * Forgets one cape's elytra, to make it again: its glowing parts, drawn in when it is made, were turned on or off.
     */
    public static void forget(String cosmetic) {
        Elytra elytra = ELYTRAS.remove(cosmetic);
        if (elytra != null && elytra.ids != null) {
            for (Object id : elytra.ids) {
                if (id != null) {
                    RecoloredTextures.release(id);
                }
            }
        }
    }
}
