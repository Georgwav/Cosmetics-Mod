package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.RenderMode;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Quaternionf;

/**
 * Version-independent part of the render layer: decides which cosmetics to draw for a player, places
 * them on the model and hands each mesh to a {@link Sink}. The version-specific layer only fills in a
 * {@link PlayerRenderInfo} and provides the sink (a buffer source before 1.21.9, the submit-node
 * collector after).
 */
public final class CosmeticRenderer {
    /** Receives one layer of a cosmetic at the pose stack's current pose. */
    public interface Sink {
        void draw(PoseStack poseStack, BakedCosmetic cosmetic, BakedCosmetic.Layer layer, int light);
    }

    private CosmeticRenderer() {
    }

    /**
     * @param root  the parent model's root part, or {@code null} on versions where it carries no transform
     * @param head  the parent model's head part
     * @param body  the parent model's body part
     */
    public static void render(PoseStack poseStack, int packedLight, PlayerRenderInfo info,
                              ModelPart root, ModelPart head, ModelPart body, Sink sink) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (!config.enabled || RenderGuard.isLayerDisabled() || info.uuid == null || info.invisible) {
            return;
        }
        boolean shadowPass = Compat.isShadowPass();
        if (shadowPass && !config.renderInShadowPass) {
            RenderStats.skippedShadowPass++;
            return;
        }
        BakedCosmetic[] cosmetics = EquippedCosmetics.forPlayer(info.uuid, info.local);
        if (cosmetics.length == 0) {
            return;
        }
        long start = RenderStats.TIMING ? System.nanoTime() : 0L;
        if (info.local && !shadowPass) {
            RenderStats.localPlayerFrames++;
        }
        int figura = -1; // looked up lazily, at most once per player per frame

        for (BakedCosmetic cosmetic : cosmetics) {
            if (RenderGuard.isDisabled(cosmetic.id())) {
                continue;
            }
            CosmeticSlot slot = cosmetic.slot();
            if (slot == CosmeticSlot.CAPE && info.elytra) {
                continue;
            }
            if (config.priority(slot) == SlotPriority.DEFER) {
                if (figura < 0) {
                    figura = Compat.hasFiguraAvatar(info.uuid) ? 1 : 0;
                }
                if (figura == 1 || (slot == CosmeticSlot.CAPE && info.vanillaCapeVisible)) {
                    RenderStats.deferredToOtherMods++;
                    continue;
                }
            }
            ModelPart anchor = slot.anchor() == CosmeticSlot.Anchor.HEAD ? head : body;
            // A hidden anchor means something (a first-person body mod, Figura, EMF) hid that body part.
            if (anchor == null || !anchor.visible || (root != null && !root.visible)) {
                continue;
            }

            poseStack.pushPose();
            try {
                if (root != null) {
                    root.translateAndRotate(poseStack);
                }
                anchor.translateAndRotate(poseStack);
                if (slot == CosmeticSlot.CAPE) {
                    applyCapeSway(poseStack, info);
                }
                for (BakedCosmetic.Layer layer : cosmetic.layers()) {
                    int light = layer.mode() == RenderMode.EMISSIVE ? MeshEmitter.FULL_BRIGHT : packedLight;
                    for (int copy = 0; copy < RenderStats.COPIES; copy++) {
                        sink.draw(poseStack, cosmetic, layer, light);
                    }
                    RenderStats.meshesDrawn++;
                    if (shadowPass) {
                        RenderStats.shadowPassMeshes++;
                    }
                }
            } catch (RuntimeException | LinkageError e) {
                RenderGuard.cosmeticFailed(cosmetic.id(), e);
            } finally {
                poseStack.popPose();
            }
        }
        if (RenderStats.TIMING) {
            RenderStats.nanos += System.nanoTime() - start;
        }
    }

    /**
     * Same placement and sway as the vanilla cape: 2px behind the body pivot, then the vanilla rotation
     * sequence driven by the vanilla cape physics values. Cape cosmetics are modelled like the vanilla
     * cape cube ({@code from [-5, 0, -1] to [5, 16, 0]}).
     */
    private static void applyCapeSway(PoseStack poseStack, PlayerRenderInfo info) {
        poseStack.translate(0.0F, 0.0F, 0.125F);
        // X, then Z, then Y, like the vanilla cape.
        Quaternionf rotation = new Quaternionf()
                .rotationX((float) Math.toRadians(6.0F + info.capeLean / 2.0F + info.capeFlap))
                .rotateZ((float) Math.toRadians(info.capeLean2 / 2.0F))
                .rotateY((float) Math.toRadians(180.0F - info.capeLean2 / 2.0F));
        //? if >=26.3 {
        /*poseStack.rotate(rotation);
        *///?} else {
        poseStack.mulPose(rotation);
        //?}
    }

    /**
     * Whether our cape should replace the vanilla cape for this player: only when the user set the cape
     * slot to OVERRIDE and the player actually wears one of our capes.
     */
    public static boolean shouldHideVanillaCape(java.util.UUID player, boolean local) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (!config.enabled || RenderGuard.isLayerDisabled() || player == null
                || config.priority(CosmeticSlot.CAPE) != SlotPriority.OVERRIDE) {
            return false;
        }
        for (BakedCosmetic cosmetic : EquippedCosmetics.forPlayer(player, local)) {
            if (cosmetic.slot() == CosmeticSlot.CAPE && !RenderGuard.isDisabled(cosmetic.id())) {
                return true;
            }
        }
        return false;
    }
}
