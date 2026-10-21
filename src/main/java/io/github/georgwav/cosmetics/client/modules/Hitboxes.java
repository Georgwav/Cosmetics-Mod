package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.sol.fx.WorldOut;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

/**
 * Hitboxes: Sol draws the boxes itself while the module is on, without F3+B: the whole box, or only its corners (short
 * lines out from each of the eight corners along the three edges that meet there); in a colour of your choice (another
 * for the one you look at), their lines thinner or thicker; on everyone, on living things or on players only, and on
 * yourself in third person if you like. F3+B stays the game's own: pressed, it shows the normal hitboxes on everything
 * and Sol's step aside.
 */
public final class Hitboxes extends Module implements FxWorld.Layer {
    /** The whole box, or only its corners. */
    private final Setting.Choice style = add(new Setting.Choice("style", "full", List.of("full", "corners")).group("look"));
    /** How far along each edge a corner's lines run. */
    private final Setting.Slider corner = add(new Setting.Slider("corner_length", 25, 10, 50, 5, v -> Math.round(v) + "%").group("look"));
    /** The boxes' colour. */
    private final Setting.Color color = add(new Setting.Color("color", Setting.Color.THEME).was(0xFFFFFFFF).themed()
            .names(Map.of("white", 0xFFFFFFFF, "accent", Setting.Color.THEME, "red", 0xFFFF4D4D, "green", 0xFF4CFF6A, "cyan", 0xFF40E8FF,
                    "yellow", 0xFFFFE040, "pink", 0xFFFF60D0)).group("look"));
    /** Another colour for the one you look at. */
    private final Setting.Toggle targetColour = add(new Setting.Toggle("target_colour", false).group("look"));
    private final Setting.Color target = add(new Setting.Color("target_color", 0xFFFF4D4D).themed().group("look"));
    /** The lines' width against the game's. */
    private final Setting.Slider width = add(new Setting.Slider("width", 1.0, 0.5, 4.0, 0.25, v -> Math.round(v * 100) + "%").group("look"));
    /** Whose boxes: everyone's, living things' (not items, arrows or XP orbs), or players' only. */
    private final Setting.Choice showOn = add(new Setting.Choice("show_on", "everyone", List.of("everyone", "living", "players")).group("which"));
    /** On yourself (in third person). */
    private final Setting.Toggle self = add(new Setting.Toggle("self", true).group("which"));

    /** How far off boxes are drawn, blocks. */
    private static final double RANGE = 64.0;
    /** Boxes drawn so far (for the self-test). */
    public static int drawn;

    public Hitboxes() {
        super("hitboxes", Category.VIEW, "hitbox");
        corner.shownWhen(() -> style.get().equals("corners"));
        target.shownWhen(targetColour::get);
        FxWorld.LAYERS.add(this);
    }

    /** The two switches it had (players only, living things only) become the one choice. */
    @Override
    protected void migrate(com.google.gson.JsonObject settings) {
        boolean players = flag(settings, "players_only"), living = flag(settings, "living_only");
        settings.remove("players_only");
        settings.remove("living_only");
        if ((players || living) && !settings.has("show_on")) {
            settings.addProperty("show_on", players ? "players" : "living");
        }
    }

    private static boolean flag(com.google.gson.JsonObject settings, String key) {
        try {
            return settings.has(key) && settings.get(key).getAsBoolean();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Whether the game's own hitboxes are on (F3+B). */
    private static boolean gameBoxes(Minecraft mc) {
        //? if >=1.21.9 {
        return mc.debugEntries.isCurrentlyEnabled(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES);
        //?} else {
        /*return mc.getEntityRenderDispatcher().shouldRenderHitBoxes();
        *///?}
    }

    /** Whether {@code entity} gets a box, by the module's choices. */
    private boolean wanted(Entity entity, Minecraft mc) {
        if (entity.isInvisible()) {
            return false;
        }
        if (entity == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) {
            return false;
        }
        if (entity == mc.player && !self.get()) {
            return false;
        }
        return switch (showOn.get()) {
            case "players" -> entity instanceof Player;
            case "living" -> entity instanceof LivingEntity;
            default -> true;
        };
    }

    @Override
    public boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return enabled() && mc.level != null && !gameBoxes(mc);
    }

    @Override
    public void draw(WorldOut out, float now) {
        if (out.glowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float pt = FrameInfo.partialTick;
        double cx = FrameInfo.camX, cy = FrameInfo.camY, cz = FrameInfo.camZ;
        int base = 0xFF000000 | color.argb(), aimed = 0xFF000000 | target.argb();
        Entity looked = targetColour.get() ? mc.crosshairPickEntity : null;
        boolean corners = style.get().equals("corners");
        float share = (float) (corner.get() / 100.0);
        // The game's line is about 2.5 pixels on a 1080-high window: a line that wide at any distance.
        float perBlock = (float) (2.0 * Math.tan(Math.toRadians(FrameInfo.fovY) / 2.0) / Math.max(1, FrameInfo.height));
        float px = 2.5f * Math.max(1f, FrameInfo.height / 1080f) * width.get().floatValue() * perBlock;
        // A hair toward the camera, so the bottom edges never flicker into the ground.
        out.bias = 0.02f;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e.distanceToSqr(cx, cy, cz) > RANGE * RANGE || !wanted(e, mc)) {
                continue;
            }
            Vec3 at = e.getPosition(pt);
            AABB box = e.getBoundingBox().move(at.x - e.getX(), at.y - e.getY(), at.z - e.getZ());
            Vfx.origin(out, box.minX, box.minY, box.minZ);
            float sx = (float) box.getXsize(), sy = (float) box.getYsize(), sz = (float) box.getZsize();
            double mx = (box.minX + box.maxX) / 2 - cx, my = (box.minY + box.maxY) / 2 - cy, mz = (box.minZ + box.maxZ) / 2 - cz;
            float w = Math.max(0.004f, px * (float) Math.sqrt(mx * mx + my * my + mz * mz));
            int c = e == looked ? aimed : base;
            if (corners) {
                corners(out, sx, sy, sz, share, w, c);
            } else {
                full(out, sx, sy, sz, w, c);
            }
            drawn++;
        }
        out.bias = 0f;
    }

    /** The box's twelve edges. */
    private static void full(WorldOut out, float sx, float sy, float sz, float w, int c) {
        for (int i = 0; i < 4; i++) {
            float x = (i & 1) == 0 ? 0f : sx, z = (i & 2) == 0 ? 0f : sz;
            // Up each corner, and round the bottom and the top.
            Vfx.line(out, FxSoft.SQUARE, x, 0f, z, x, sy, z, w, c, c);
        }
        for (int k = 0; k < 2; k++) {
            float y = k == 0 ? 0f : sy;
            Vfx.line(out, FxSoft.SQUARE, 0f, y, 0f, sx, y, 0f, w, c, c);
            Vfx.line(out, FxSoft.SQUARE, 0f, y, sz, sx, y, sz, w, c, c);
            Vfx.line(out, FxSoft.SQUARE, 0f, y, 0f, 0f, y, sz, w, c, c);
            Vfx.line(out, FxSoft.SQUARE, sx, y, 0f, sx, y, sz, w, c, c);
        }
    }

    /** At each of the eight corners, a line along each of the three edges meeting there, {@code share} of its length. */
    private static void corners(WorldOut out, float sx, float sy, float sz, float share, float w, int c) {
        float lx = sx * share, ly = sy * share, lz = sz * share;
        for (int i = 0; i < 8; i++) {
            float x = (i & 1) == 0 ? 0f : sx, y = (i & 2) == 0 ? 0f : sy, z = (i & 4) == 0 ? 0f : sz;
            float dx = x == 0f ? lx : -lx, dy = y == 0f ? ly : -ly, dz = z == 0f ? lz : -lz;
            Vfx.line(out, FxSoft.SQUARE, x, y, z, x + dx, y, z, w, c, c);
            Vfx.line(out, FxSoft.SQUARE, x, y, z, x, y + dy, z, w, c, c);
            Vfx.line(out, FxSoft.SQUARE, x, y, z, x, y, z + dz, w, c, c);
        }
    }
}
