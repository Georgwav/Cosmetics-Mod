package io.github.georgwav.cosmetics.sol.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.PetPosition;
import io.github.georgwav.cosmetics.model.RenderMode;
import io.github.georgwav.cosmetics.render.CosmeticRenderer;
import io.github.georgwav.cosmetics.render.MeshEmitter;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.render.SoftRaster;
import io.github.georgwav.cosmetics.sol.render.FrameInfo;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import io.github.georgwav.cosmetics.ui.SkinPixels;
import io.github.georgwav.cosmetics.ui.TilePictures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pets that follow you (a pet's "follows you" and "flies after you" places): each one moves on its own, twenty times a
 * second. On the ground it runs after its owner to a spot behind and beside them, steps up blocks, falls down them,
 * sits when they stop; in the air it flies after them and hovers low when they stop. It's drawn in the world by
 * itself (so it shows in first person, and stays where it is when its owner turns), through the effects' world draw:
 * its mesh posed for its own walking and sitting each frame, lit by the light where it stands. Sol's "mini you" is a
 * little copy of its owner in their own skin. Render thread only.
 */
public final class FollowPets implements FxWorld.Layer {
    public static final FollowPets INSTANCE = new FollowPets();
    /** The id of Sol's little copy of you, drawn in its owner's skin. */
    public static final String MINI_YOU = "sol_mini_you";
    private static final float MINI_SCALE = 0.45f;

    /** One owner's following pet. */
    static final class Pet {
        BakedCosmetic cosmetic;
        boolean flies, mini;
        double x, y, z, ox, oy, oz;
        float yaw, oyaw, speed, walked, owalked, vy;
        boolean placed, sitting;
        int still, blocked, seen;
        /** Its mesh this frame, one copy per layer. */
        final List<PoseSnapshot> meshes = new ArrayList<>();
        final List<Boolean> glowing = new ArrayList<>();
        int used;
    }

    private final Map<UUID, List<Pet>> pets = new HashMap<>();
    private int ticks;
    /** For the checks: pets drawn, and frames with any. */
    public int drawnFrames, moved;
    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    private FollowPets() {
    }

    /** How many pets follow anyone now. */
    public int count() {
        int n = 0;
        for (List<Pet> l : pets.values()) {
            n += l.size();
        }
        return n;
    }

    /** Where a player's first following pet is (x, y, z), or null. */
    public double[] where(UUID owner) {
        List<Pet> l = pets.get(owner);
        return l == null || l.isEmpty() ? null : new double[]{l.get(0).x, l.get(0).y, l.get(0).z};
    }

    /** Whether a player's first following pet sits now. */
    public boolean sitting(UUID owner) {
        List<Pet> l = pets.get(owner);
        return l != null && !l.isEmpty() && l.get(0).sitting;
    }

    // ------------------------------------------------------------------ moving

    /** Once a client tick. */
    public void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        Player you = mc.player;
        if (level == null || you == null) {
            pets.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        ticks++;
        CosmeticsConfig config = CosmeticsConfig.get();
        boolean any = config.enabled && config.slotVisible(CosmeticSlot.PET) && !RenderGuard.isLayerDisabled();
        for (Player p : level.players()) {
            boolean local = p == you;
            if (!any || p.isSpectator() || p.distanceToSqr(you) > 64 * 64 || !CosmeticRenderer.showsFor(p.getUUID(), local)) {
                continue;
            }
            BakedCosmetic[] worn = EquippedCosmetics.forPlayer(p.getUUID(), local);
            int k = 0;
            for (BakedCosmetic c : worn) {
                if (c.slot() != CosmeticSlot.PET || RenderGuard.isDisabled(c.id())) {
                    continue;
                }
                PetPosition at = CosmeticRenderer.petPosition(c, config.options(c.id()));
                if (!at.follows()) {
                    continue;
                }
                List<Pet> list = pets.computeIfAbsent(p.getUUID(), u -> new ArrayList<>());
                while (list.size() <= k) {
                    list.add(new Pet());
                }
                Pet pet = list.get(k);
                if (pet.cosmetic != c) {
                    pet.cosmetic = c;
                    pet.placed = false;
                }
                pet.flies = at == PetPosition.FOLLOW_FLYING;
                pet.mini = c.id().endsWith(MINI_YOU);
                pet.seen = ticks;
                step(level, p, pet, k);
                k++;
            }
        }
        // Owners gone, and pets taken off.
        pets.values().forEach(l -> l.removeIf(pet -> pet.seen != ticks));
        pets.values().removeIf(List::isEmpty);
    }

    /** One tick of a pet: after its owner to its spot (behind and beside them), over the ground or through the air. */
    private void step(ClientLevel level, Player owner, Pet pet, int index) {
        pet.ox = pet.x;
        pet.oy = pet.y;
        pet.oz = pet.z;
        pet.oyaw = pet.yaw;
        pet.owalked = pet.walked;
        // Its spot: a block and a bit behind, to the left (the next pet to the right, a little further back).
        double a = Math.toRadians(owner.getYRot());
        double fx = -Math.sin(a), fz = Math.cos(a), rx = -Math.cos(a), rz = -Math.sin(a);
        double side = (index % 2 == 0 ? -0.9 : 0.9), back = 1.1 + (index / 2) * 0.7;
        double tx = owner.getX() + rx * side - fx * back, tz = owner.getZ() + rz * side - fz * back;
        double ty = pet.flies ? owner.getY() + 1.1 : owner.getY();
        double dx = tx - pet.x, dz = tz - pet.z, dy = ty - pet.y;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (!pet.placed || d > 20.0 || Math.abs(dy) > 12.0 || (pet.blocked > 30 && d > 3.0)) {
            // Too far behind (a teleport, a long fall, stuck behind a wall): it's at its spot.
            pet.x = pet.ox = tx;
            pet.z = pet.oz = tz;
            pet.y = pet.oy = pet.flies ? ty : ground(level, tx, owner.getY() + 1.0, tz, owner.getY());
            pet.yaw = pet.oyaw = owner.getYRot();
            pet.placed = true;
            pet.blocked = 0;
            pet.speed = 0f;
            return;
        }
        double ownerSpeed = Math.hypot(owner.getX() - owner.xo, owner.getZ() - owner.zo);
        float want = d > 0.35 ? (float) Math.min(Math.max(0.16, ownerSpeed * 1.15), Math.min(0.05 + d * 0.22, 0.6)) : 0f;
        // Speeds up and slows down over a few ticks.
        pet.speed += (want - pet.speed) * 0.45f;
        if (pet.speed < 0.005f) {
            pet.speed = 0f;
        }
        double mx = d > 1e-4 ? dx / d * pet.speed : 0, mz = d > 1e-4 ? dz / d * pet.speed : 0;
        if (pet.flies) {
            pet.x += mx;
            pet.z += mz;
            // Hovers low beside you while you stand still.
            double hover = pet.sitting ? ty - 0.7 : ty;
            pet.y += (hover - pet.y) * 0.2;
        } else {
            double nx = pet.x + mx, nz = pet.z + mz;
            float g = ground(level, nx, pet.y + 1.3, nz, pet.y - 4.0);
            if (g > pet.y + 1.25) {
                // A wall more than a block high: it waits (and is put at its spot if it stays stuck).
                pet.blocked++;
                pet.speed *= 0.5f;
            } else {
                pet.blocked = 0;
                pet.x = nx;
                pet.z = nz;
                if (g > pet.y) {
                    // Steps up, quickly.
                    pet.y = Math.min(g, pet.y + 0.5);
                    pet.vy = 0f;
                } else {
                    pet.vy = Math.max(-1.2f, pet.vy - 0.08f);
                    pet.y = Math.max(g, pet.y + pet.vy);
                    if (pet.y <= g) {
                        pet.vy = 0f;
                    }
                }
            }
        }
        pet.walked += pet.speed;
        if (pet.speed > 0.02f && d > 1e-4) {
            float face = (float) Math.toDegrees(Math.atan2(-dx, dz));
            pet.yaw += Mth.wrapDegrees(face - pet.yaw) * 0.35f;
        } else if (pet.sitting) {
            pet.yaw += Mth.wrapDegrees(owner.getYRot() - pet.yaw) * 0.15f;
        }
        // Sits once its owner has stood still a second with it at its spot; up again when they move off.
        boolean resting = ownerSpeed < 0.01 && d < 0.7;
        pet.still = resting ? pet.still + 1 : 0;
        if (pet.still > 20) {
            pet.sitting = true;
        } else if (!resting && d > 1.2) {
            pet.sitting = false;
        }
        moved++;
    }

    /** The top of the ground under (x, z), looking down from {@code from} to {@code to}; {@code to} when there's none. */
    private float ground(ClientLevel level, double x, double from, double z, double to) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        for (int by = Mth.floor(from); by >= Mth.floor(to); by--) {
            pos.set(bx, by, bz);
            BlockState s = level.getBlockState(pos);
            if (!s.isAir()) {
                var shape = s.getCollisionShape(level, pos);
                if (!shape.isEmpty()) {
                    return (float) (by + shape.max(net.minecraft.core.Direction.Axis.Y));
                }
            }
        }
        return (float) to;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public boolean active() {
        return !pets.isEmpty();
    }

    private static final PoseStack POSE = new PoseStack();
    private static final Quaternionf TURN = new Quaternionf();
    private static final Map<String, FxTex> TEXTURES = new HashMap<>();
    private static final PoseSnapshot.Pose MINI_POSE = new PoseSnapshot.Pose();
    private Pet drawing;

    @Override
    public void draw(WorldOut out, float now) {
        if (out.glowPass()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            return;
        }
        float pt = FrameInfo.partialTick;
        boolean any = false;
        for (Map.Entry<UUID, List<Pet>> e : pets.entrySet()) {
            for (Pet pet : e.getValue()) {
                if (!pet.placed) {
                    continue;
                }
                double x = pet.ox + (pet.x - pet.ox) * pt, y = pet.oy + (pet.y - pet.oy) * pt, z = pet.oz + (pet.z - pet.oz) * pt;
                float yaw = pet.oyaw + Mth.wrapDegrees(pet.yaw - pet.oyaw) * pt;
                float walked = pet.owalked + (pet.walked - pet.owalked) * pt;
                pos.set(Mth.floor(x), Mth.floor(y + 0.2), Mth.floor(z));
                float light = 0.32f + 0.68f * level.getMaxLocalRawBrightness(pos) / 15f;
                int shade = Colors.argb(255, Math.round(255 * light), Math.round(255 * light), Math.round(255 * light));
                try {
                    if (pet.mini) {
                        drawMini(out, pet, e.getKey(), x, y, z, yaw, walked, shade);
                    } else {
                        drawCosmetic(out, pet, e.getKey(), x, y, z, yaw, walked, shade);
                    }
                    any = true;
                } catch (RuntimeException | LinkageError ex) {
                    RenderGuard.cosmeticFailed(pet.cosmetic.id(), ex);
                }
            }
        }
        if (any) {
            drawnFrames++;
        }
    }

    /** A pet from its cosmetic: posed for its walking and sitting, its layers' meshes caught and drawn, nearest last. */
    private void drawCosmetic(WorldOut out, Pet pet, UUID owner, double x, double y, double z, float yaw, float walked, int shade) {
        POSE.setIdentity();
        // As the game turns a model: round to its facing, then into the models' upside-down space.
        CosmeticRenderer.rotate(POSE, TURN.rotationY((float) Math.toRadians(180f - yaw)));
        POSE.scale(-1f, -1f, 1f);
        pet.used = 0;
        drawing = pet;
        UUID own = new UUID(owner.getMostSignificantBits() ^ 0x5017L, owner.getLeastSignificantBits());
        CosmeticRenderer.drawAlone(POSE, pet.cosmetic, own, MeshEmitter.FULL_BRIGHT,
                CosmeticRenderer.petContext(pet.speed > 0.02f, pet.speed * 20f, walked, pet.sitting, pet.flies), SINK);
        drawing = null;
        out.origin(x, y, z);
        for (int i = 0; i < pet.used; i++) {
            PoseSnapshot m = pet.meshes.get(i);
            m.drawFaces(out, 0f, 0f, 0f, 1f, null, pet.glowing.get(i) ? 0xFFFFFFFF : shade, false);
        }
    }

    /** Each layer the cosmetic draws: caught into the pet's next mesh, in the layer's own picture. */
    private final CosmeticRenderer.Sink SINK = (poseStack, cosmetic, layer, light, color, frame, clip, clipTime, cloth) -> {
        Pet pet = drawing;
        if (pet == null) {
            return;
        }
        if (pet.used >= pet.meshes.size()) {
            pet.meshes.add(new PoseSnapshot());
            pet.glowing.add(false);
        }
        PoseSnapshot m = pet.meshes.get(pet.used);
        m.beginMesh();
        MeshEmitter.emitLayer(layer, 0f, clip, clipTime, poseStack.last(), m.meshInput(), light, color, cloth);
        boolean glow = layer.mode() == RenderMode.EMISSIVE;
        m.endMesh(texture(layer.texture()), !glow);
        pet.glowing.set(pet.used, glow);
        pet.used++;
    };

    private static FxTex texture(String id) {
        FxTex t = TEXTURES.get(id);
        if (t == null) {
            SoftRaster.Texture px = TilePictures.read(id);
            t = px == null ? new FxTex("pet_missing", 1, 1, new int[]{0xFFFF00FF})
                    : new FxTex("pet_" + TEXTURES.size(), px.width(), px.height(), px.argb());
            TEXTURES.put(id, t);
        }
        return t;
    }

    /** Sol's mini you: a little copy of its owner in their own skin, walking after them, sitting when they stop. */
    private void drawMini(WorldOut out, Pet pet, UUID owner, double x, double y, double z, float yaw, float walked, int shade) {
        Minecraft mc = Minecraft.getInstance();
        Player p = mc.level.getPlayerByUUID(owner);
        if (p == null) {
            return;
        }
        if (pet.meshes.isEmpty()) {
            pet.meshes.add(new PoseSnapshot());
            pet.glowing.add(false);
        }
        PoseSnapshot.Pose pose = MINI_POSE;
        pose.x = pose.y = pose.z = 0;
        pose.bodyYaw = yaw;
        pose.headYaw = pet.sitting ? p.getYHeadRot() : yaw;
        pose.pitch = 0f;
        // Little legs take quick steps.
        pose.walk = walked * 3.2f;
        pose.walkSpeed = Math.min(1f, pet.speed * 6f);
        pose.crouch = false;
        pose.lean = 0f;
        pose.sit = pet.sitting && !pet.flies;
        PoseSnapshot m = pet.meshes.get(0);
        m.capture(SkinPixels.of(p), pose, false);
        m.shade(true);
        float size = MINI_SCALE * (float) (CosmeticsConfig.get().options(pet.cosmetic.id()).scale);
        // Sitting, the copy's hips are on the ground.
        out.origin(x, y - (pose.sit ? 0.62 * size : 0.0), z);
        m.drawFaces(out, 0f, 0f, 0f, size, null, shade, false);
    }
}
