package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.Items;
//? if <1.21.2 {
/*import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
*///?} elif <1.21.9 {
/*import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.state.PlayerRenderState;
import java.util.NoSuchElementException;
*///?} else {
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import java.util.NoSuchElementException;
//?}

/**
 * The player feature layer. It is added to the vanilla player renderer through Fabric API's layer
 * registration callback, so it runs inside Minecraft's own entity render pipeline: if another mod
 * (Entity Culling, Sodium's entity culling, ...) skips a player, this layer is skipped with it, and
 * Iris re-runs it during its shadow pass like any other part of the player.
 *
 * <p>Everything here is wrapped so a failure disables the cosmetic (or the layer) instead of
 * crashing the game; see {@link RenderGuard}.
 */
//? if <1.21.2 {
/*public final class CosmeticsLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
        implements CosmeticRenderer.Sink {
    private final PlayerRenderInfo info = new PlayerRenderInfo();
    private MultiBufferSource buffers;

    public CosmeticsLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, AbstractClientPlayer player,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                       float netHeadYaw, float headPitch) {
        try {
            if (!fillCommon(info, player)) {
                return;
            }
            fillCapeSway(info, player, partialTick);
            this.buffers = bufferSource;
            PlayerModel<AbstractClientPlayer> model = getParentModel();
            CosmeticRenderer.render(poseStack, packedLight, info, null, model.head, model.body, this);
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.layerFailed(e);
        } finally {
            this.buffers = null;
        }
    }

    @Override
    public void draw(PoseStack poseStack, BakedCosmetic cosmetic, BakedCosmetic.Layer layer, int light, int color,
                     int frame, int clip, float clipTime) {
        VertexConsumer consumer = buffers.getBuffer(CosmeticRenderTypes.get(layer, frame));
        MeshEmitter.emitLayer(layer, vOffset(layer, frame), clip, clipTime, poseStack.last(), consumer, light, color);
    }

    // Same maths as the vanilla 1.21.1 cape layer, minus the crouch bonus: our cape hangs off the body
    // part, which already tilts when crouching.
    private static void fillCapeSway(PlayerRenderInfo info, AbstractClientPlayer player, float partialTick) {
        double dx = Mth.lerp(partialTick, player.xCloakO, player.xCloak) - Mth.lerp(partialTick, player.xo, player.getX());
        double dy = Mth.lerp(partialTick, player.yCloakO, player.yCloak) - Mth.lerp(partialTick, player.yo, player.getY());
        double dz = Mth.lerp(partialTick, player.zCloakO, player.zCloak) - Mth.lerp(partialTick, player.zo, player.getZ());
        float bodyRot = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        double sin = Mth.sin(bodyRot * ((float) Math.PI / 180F));
        double cos = -Mth.cos(bodyRot * ((float) Math.PI / 180F));
        float flap = Mth.clamp((float) dy * 10.0F, -6.0F, 32.0F);
        float lean = Mth.clamp((float) (dx * sin + dz * cos) * 100.0F, 0.0F, 150.0F);
        float lean2 = Mth.clamp((float) (dx * cos - dz * sin) * 100.0F, -20.0F, 20.0F);
        float bob = Mth.lerp(partialTick, player.oBob, player.bob);
        float walk = Mth.lerp(partialTick, player.walkDistO, player.walkDist);
        flap += Mth.sin(walk * 6.0F) * 32.0F * bob;
        info.capeFlap = flap;
        info.capeLean = lean;
        info.capeLean2 = lean2;
    }
*///?} elif <1.21.9 {
/*public final class CosmeticsLayer<M extends EntityModel<? super PlayerRenderState>> extends RenderLayer<PlayerRenderState, M>
        implements CosmeticRenderer.Sink {
    private final PlayerRenderInfo info = new PlayerRenderInfo();
    private MultiBufferSource buffers;

    public CosmeticsLayer(RenderLayerParent<PlayerRenderState, M> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, PlayerRenderState state,
                       float yRot, float xRot) {
        try {
            if (!fillCommon(info, entity(state.id))) {
                return;
            }
            info.capeFlap = state.capeFlap;
            info.capeLean = state.capeLean;
            info.capeLean2 = state.capeLean2;
            this.buffers = bufferSource;
            ModelPart root = getParentModel().root();
            CosmeticRenderer.render(poseStack, packedLight, info, root, child(root, "head"), child(root, "body"), this);
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.layerFailed(e);
        } finally {
            this.buffers = null;
        }
    }

    @Override
    public void draw(PoseStack poseStack, BakedCosmetic cosmetic, BakedCosmetic.Layer layer, int light, int color,
                     int frame, int clip, float clipTime) {
        VertexConsumer consumer = buffers.getBuffer(CosmeticRenderTypes.get(layer, frame));
        MeshEmitter.emitLayer(layer, vOffset(layer, frame), clip, clipTime, poseStack.last(), consumer, light, color);
    }
*///?} else {
public final class CosmeticsLayer<M extends EntityModel<? super AvatarRenderState>> extends RenderLayer<AvatarRenderState, M>
        implements CosmeticRenderer.Sink {
    private final PlayerRenderInfo info = new PlayerRenderInfo();
    private SubmitNodeCollector collector;

    public CosmeticsLayer(RenderLayerParent<AvatarRenderState, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector nodeCollector, int lightCoords, AvatarRenderState state,
                       float yRot, float xRot) {
        try {
            if (!fillCommon(info, entity(state.id))) {
                return;
            }
            info.capeFlap = state.capeFlap;
            info.capeLean = state.capeLean;
            info.capeLean2 = state.capeLean2;
            this.collector = nodeCollector;
            ModelPart root = getParentModel().root();
            CosmeticRenderer.render(poseStack, lightCoords, info, root, child(root, "head"), child(root, "body"), this);
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.layerFailed(e);
        } finally {
            this.collector = null;
        }
    }

    @Override
    public void draw(PoseStack poseStack, BakedCosmetic cosmetic, BakedCosmetic.Layer layer, int light, int color,
                     int frame, int clip, float clipTime) {
        String id = cosmetic.id();
        float vOffset = vOffset(layer, frame);
        // Vertices are written later, when the collected nodes are drawn, so the guard goes inside too.
        collector.submitCustomGeometry(poseStack, CosmeticRenderTypes.get(layer, frame), (pose, consumer) -> {
            if (RenderGuard.isDisabled(id)) {
                return;
            }
            long start = RenderStats.TIMING ? System.nanoTime() : 0L;
            try {
                // A skinned layer is skinned here, when its vertices are written, for the clip time it was
                // submitted with.
                MeshEmitter.emitLayer(layer, vOffset, clip, clipTime, pose, consumer, light, color);
            } catch (RuntimeException | LinkageError e) {
                RenderGuard.cosmeticFailed(id, e);
            }
            if (RenderStats.TIMING) {
                RenderStats.nanos += System.nanoTime() - start;
            }
        });
    }
//?}

    /** V offset of a frame in a vertical-strip texture animation; 0 for every other layer. */
    private static float vOffset(BakedCosmetic.Layer layer, int frame) {
        return layer.textureFrames() != null ? layer.textureFrames().vOffset(frame) : 0f;
    }

    /** Fills what every version can read straight from the player entity. */
    private static boolean fillCommon(PlayerRenderInfo info, Entity entity) {
        info.reset();
        if (!(entity instanceof AbstractClientPlayer player)) {
            // Mannequins and other non-player avatars don't get player cosmetics.
            return false;
        }
        info.uuid = player.getUUID();
        info.local = player == Minecraft.getInstance().player;
        info.invisible = player.isInvisible();
        var skin = player.getSkin();
        //? if <1.21.9 {
        /*boolean hasCape = skin.capeTexture() != null;
        *///?} else {
        boolean hasCape = skin.cape() != null;
        //?}
        info.vanillaCapeVisible = hasCape && player.isModelPartShown(PlayerModelPart.CAPE);
        info.elytra = player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA);
        info.helmet = !player.getItemBySlot(EquipmentSlot.HEAD).isEmpty();
        var self = Minecraft.getInstance().player;
        info.distanceSq = info.local || self == null ? 0.0 : player.distanceToSqr(self);
        return true;
    }

    /** Resolves the entity behind a render state. */
    static Entity entity(int entityId) {
        var level = Minecraft.getInstance().level;
        return level == null ? null : level.getEntity(entityId);
    }

    //? if >=1.21.2 {
    private static ModelPart child(ModelPart root, String name) {
        try {
            return root.getChild(name);
        } catch (NoSuchElementException e) {
            // Some model-replacing mods restructure the player model; then there is nothing to anchor to.
            return null;
        }
    }
    //?}
}
