package io.github.georgwav.cosmetics.render;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.georgwav.cosmetics.compat.Compat;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.config.SlotPriority;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.BoneRig;
import io.github.georgwav.cosmetics.model.Cloth;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.model.CosmeticOption;
import io.github.georgwav.cosmetics.model.CosmeticSlot;
import io.github.georgwav.cosmetics.model.PetPosition;
import io.github.georgwav.cosmetics.model.MeshRig;
import io.github.georgwav.cosmetics.model.Molang;
import io.github.georgwav.cosmetics.model.RenderMode;
import io.github.georgwav.cosmetics.model.TextureFrames;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
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
        /**
         * @param color    ARGB tint for every vertex (white = the texture's own colours)
         * @param frame    which frame of the layer's texture animation shows (0 when it has none)
         * @param clip     for a skinned layer, which of its rig's clips plays
         * @param clipTime for a skinned layer, seconds into that clip
         * @param cloth    for a cloth cloak, the bent shape to draw the layer's cloth mesh along; else null
         */
        void draw(PoseStack poseStack, BakedCosmetic cosmetic, BakedCosmetic.Layer layer, int light, int color, int frame,
                  int clip, float clipTime, Cloth.Shape cloth);
    }

    /** Colour for layers that keep the texture's own colours. */
    private static final int WHITE = 0xFFFFFFFF;

    /** Reused for joint and placement rotations. Render thread only. */
    private static final Quaternionf ROTATION = new Quaternionf();

    /** Animation clock start; animations and the rainbow tint read seconds since then. */
    private static final long START_NANOS = System.nanoTime();

    private CosmeticRenderer() {
    }

    /**
     * @param root  the parent model's root part, or {@code null} on versions where it carries no transform
     * @param head  the parent model's head part
     * @param body  the parent model's body part
     * @param limbs the parent model's left arm, right arm, left leg and right leg (any may be null)
     */
    public static void render(PoseStack poseStack, int packedLight, PlayerRenderInfo info,
                              ModelPart root, ModelPart head, ModelPart body, ModelPart[] limbs, Sink sink) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (!config.enabled || RenderGuard.isLayerDisabled() || info.uuid == null || info.invisible
                || (info.local ? !config.showOwnCosmetics : !config.showOtherPlayersCosmetics)
                || beyond(info.distanceSq, config.maxRenderDistance)) {
            return;
        }
        boolean shadowPass = Compat.isShadowPass();
        if (shadowPass && (!config.renderInShadowPass || beyond(info.distanceSq, config.shadowDistance))) {
            RenderStats.skippedShadowPass++;
            return;
        }
        BakedCosmetic[] cosmetics = EquippedCosmetics.forPlayer(info.uuid, info.local);
        if (cosmetics.length == 0) {
            return;
        }
        long start = RenderStats.TIMING ? System.nanoTime() : 0L;
        long now = System.nanoTime();
        double seconds = (now - START_NANOS) / 1.0e9;
        if (info.local && !shadowPass) {
            RenderStats.localPlayerFrames++;
        }
        int figura = -1; // looked up lazily, at most once per player per frame

        for (BakedCosmetic cosmetic : cosmetics) {
            if (RenderGuard.isDisabled(cosmetic.id())) {
                continue;
            }
            CosmeticSlot slot = cosmetic.slot();
            if (slot.anchor() == CosmeticSlot.Anchor.HELD) {
                continue; // drawn in place of the held item (see renderHeld)
            }
            if (!config.slotVisible(slot)
                    || (slot == CosmeticSlot.CAPE && info.elytra && config.hideCapeWithElytra)
                    || (slot.anchor() == CosmeticSlot.Anchor.HEAD && info.helmet && config.hideHeadwearWithHelmet)) {
                continue;
            }
            if (config.priority(slot) == SlotPriority.DEFER) {
                if (figura < 0) {
                    figura = config.deferToFigura && Compat.hasFiguraAvatar(info.uuid) ? 1 : 0;
                }
                if (figura == 1 || (slot == CosmeticSlot.CAPE && info.vanillaCapeVisible)) {
                    RenderStats.deferredToOtherMods++;
                    continue;
                }
            }
            int sides = cosmetic.definition().sides();
            CosmeticsConfig.CosmeticOptions options = config.options(cosmetic.id());
            PetPosition pet = slot == CosmeticSlot.PET ? petPosition(cosmetic, options) : null;
            BoneRig bones = cosmetic.bones();
            boolean following = false;
            if (bones != null) {
                try {
                    poseBones(bones, cosmetic, options, info.uuid, seconds, bonesContext(info, false));
                    following = bones.followsParts();
                } catch (RuntimeException e) {
                    RenderGuard.cosmeticFailed(cosmetic.id(), e);
                    continue;
                }
            }
            for (int part = 0; part < 2; part++) {
                ModelPart anchor = pet == PetPosition.HEAD ? (part == 0 ? head : null) : switch (slot.anchor()) {
                    case HEAD -> part == 0 ? head : null;
                    case ARMS -> (sides & (part == 0 ? CosmeticDefinition.LEFT : CosmeticDefinition.RIGHT)) != 0
                            ? limbs[part] : null;
                    case LEGS -> (sides & (part == 0 ? CosmeticDefinition.LEFT : CosmeticDefinition.RIGHT)) != 0
                            ? limbs[2 + part] : null;
                    default -> part == 0 ? body : null;
                };
                // A hidden anchor means something (a first-person body mod, Figura, EMF) hid that body part.
                if (anchor == null || !anchor.visible || (root != null && !root.visible)) {
                    continue;
                }

                poseStack.pushPose();
                try {
                    if (root != null) {
                        root.translateAndRotate(poseStack);
                    }
                    if (following && part == 0) {
                        drawFollowing(poseStack, cosmetic, options, packedLight, seconds, shadowPass, head, body, limbs,
                                sink);
                    }
                    anchor.translateAndRotate(poseStack);
                    if (slot == CosmeticSlot.CAPE) {
                        applyCapeSway(poseStack, info, config);
                    } else if (slot == CosmeticSlot.PET) {
                        placePet(poseStack, pet, seconds);
                    } else if (slot == CosmeticSlot.FEET) {
                        poseStack.translate(0f, 12f / 16f, 0f); // the sole: legs hang 12 px from the hip
                    } else if (slot == CosmeticSlot.ARM) {
                        // The middle of the arm: it spans -2 to 10 px down, and its box sits 1 px outwards of the pivot.
                        poseStack.translate((part == 0 ? 1f : -1f) / 16f, 4f / 16f, 0f);
                    }
                    applyPlacement(poseStack, options, cosmetic);
                    applyTurn(poseStack, options, cosmetic);
                    int color = Tint.argb(options, cosmetic, seconds);
                    // Options the cosmetic doesn't allow keep the creator's setting: glowing, animated at speed 1.
                    boolean glow = options.glow || !cosmetic.allows(CosmeticOption.GLOW);
                    boolean animate = options.animate || !cosmetic.allows(CosmeticOption.ANIMATION);
                    float animationSpeed = cosmetic.allows(CosmeticOption.ANIMATION) ? options.animationSpeed : 1f;
                    int style = cosmetic.allows(CosmeticOption.STYLE) ? cosmetic.styleIndex(options.style) : cosmetic.defaultStyle();
                    if (pet != null && (options.style == null || !cosmetic.allows(CosmeticOption.STYLE))) {
                        // A pet plays the animation for where it is (floating: "air"), when it has one.
                        int fitting = cosmetic.styleNamed(pet.preferredStyle());
                        if (fitting >= 0) {
                            style = fitting;
                        }
                    }
                    // Capes also follow the cape settings: a skeletal cape's own waving stops with cape physics off
                    // (or no swing) and speeds up or slows down with the swing strength.
                    float capeMotion = slot == CosmeticSlot.CAPE ? (config.capePhysics ? config.capeSwing : 0f) : 1f;
                    CosmeticDefinition.Wings wings = cosmetic.wings();
                    float wingAngle = 0f;
                    double wingPhase = 0.0;
                    if (wings != null) {
                        WingMotion.Pose wing = WingMotion.pose(info.uuid, info.moveSpeed, info.sneaking, info.flying, wings, now);
                        wingAngle = animate ? wing.angle : wing.base;
                        wingPhase = animate ? wing.phase : 0.0;
                        applyWingOffsets(poseStack, wings, wing.sneak, info.chestArmor);
                    }
                    // A flat cape drawn as cloth: blown back by how fast the player moves, rippling, as strong as the
                    // cape swing setting; still with cape physics off.
                    // Wavy capes: every flat cape is cloth, its lower part lagging behind the top's swing.
                    boolean wavy = slot == CosmeticSlot.CAPE && config.wavyCapes && config.capePhysics;
                    float[] cloth = slot == CosmeticSlot.CAPE && (options.cloth || wavy) && config.capePhysics
                            ? new float[]{Math.min(1f, info.capeLean / 100f), (info.uuid.hashCode() & 0xFFFF) * 1.0e-4f,
                            config.capeSwing} : null;
                    float[] lag = wavy ? WavyCapes.lag(info.uuid, (float) Math.toRadians(
                            6.0 + (info.capeLean / 2.0 + info.capeFlap) * config.capeSwing), seconds) : null;
                    drawLayers(poseStack, cosmetic, packedLight, color, glow, animate, animationSpeed, style, capeMotion,
                            wings, wingAngle, wingPhase, seconds, shadowPass, cloth, lag,
                            following ? BoneRig.Follow.NONE : null, sink);
                } catch (RuntimeException | LinkageError e) {
                    RenderGuard.cosmeticFailed(cosmetic.id(), e);
                } finally {
                    poseStack.popPose();
                }
            }
        }
        if (RenderStats.TIMING) {
            RenderStats.nanos += System.nanoTime() - start;
        }
    }

    /** Draws every layer of one cosmetic at the pose stack's current pose. */
    private static void drawLayers(PoseStack poseStack, BakedCosmetic cosmetic, int packedLight, int color,
                                   boolean glow, boolean animate, float animationSpeed, int style, float capeMotion,
                                   CosmeticDefinition.Wings wings, float wingAngle, double wingPhase, double seconds,
                                   boolean shadowPass, float[] cloth, float[] clothLag, BoneRig.Follow only,
                                   Sink sink) {
        CosmeticsConfig config = CosmeticsConfig.get();
        BoneRig bones = cosmetic.bones();
        // Its texture recoloured as its settings ask (CosmeticRenderTypes picks the copy up while drawing).
        RecoloredTextures.begin(Recolor.of(config.options(cosmetic.id()), config.allowedOptions(cosmetic)));
        try {
            for (BakedCosmetic.Layer layer : cosmetic.layers()) {
                if ((layer.mode() == RenderMode.EMISSIVE && !(config.emissiveParts && glow))
                        || (layer.mode() == RenderMode.TRANSLUCENT && !config.translucentParts)) {
                    continue;
                }
                int bone = bones != null ? layer.bone() : -1;
                // Only the layers following this body part (those that follow none at the cosmetic's own anchor), and
                // none whose bone is scaled to nothing.
                if ((only != null && bones != null && bones.follow(bone) != only) || (bone >= 0 && bones.isHidden(bone))) {
                    continue;
                }
                int light = layer.mode() == RenderMode.EMISSIVE ? MeshEmitter.FULL_BRIGHT : packedLight;
                // A cosmetic none of whose parts take colour is coloured all over when any cosmetic may be.
                int layerColor = layer.tinted() || (config.colorAnyCosmetic && !cosmetic.hasTintedLayer()) ? color : WHITE;
                TextureFrames frames = layer.textureFrames();
                int frame = frames != null && animate ? frames.frameAt(seconds, animationSpeed) : 0;
                // Pushes for this layer only; undone even if drawing throws, so the next cosmetic (and
                // the rest of the player) still sees the stack it expects.
                int pushes = 0;
                try {
                    if (animate && layer.animations().length > 0) {
                        poseStack.pushPose();
                        pushes++;
                        for (CosmeticDefinition.Animation animation : layer.animations()) {
                            applyAnimation(poseStack, animation, animation.valueAt(seconds, animationSpeed));
                        }
                    }
                    CosmeticDefinition.WingPart wingPart = layer.wing();
                    if (wingPart != null && wings != null) {
                        poseStack.pushPose();
                        pushes++;
                        applyWing(poseStack, wings, wingPart, wingAngle);
                    }
                    MeshRig rig = layer.rig();
                    int clip = 0;
                    float clipTime = 0f;
                    if (rig != null) {
                        clip = rig.clipForStyle(style);
                        // With wings, a skeletal mesh plays one loop of its clip per flap.
                        clipTime = wings != null ? rig.clipTimeAtCycle(clip, wingPhase)
                                : rig.clipTime(clip, seconds, animate ? animationSpeed * capeMotion : 0f);
                        poseStack.pushPose();
                        pushes++;
                        applyRig(poseStack, rig, layer.joint(), clip, clipTime);
                    }
                    if (bone >= 0 && !bones.isIdentity(bone)) {
                        poseStack.pushPose();
                        pushes++;
                        applyBone(poseStack, bones.correction(), bone * 12);
                    }
                    // cloth = {blow, phase, strength}; skeletal layers animate themselves instead.
                    Cloth.Shape shape = cloth != null && rig == null
                            ? Cloth.shape(layer.clothMesh().length(), cloth[0], seconds, cloth[1], cloth[2], clothLag) : null;
                    for (int copy = 0; copy < RenderStats.COPIES; copy++) {
                        sink.draw(poseStack, cosmetic, layer, light, layerColor, frame, clip, clipTime, shape);
                    }
                } finally {
                    for (; pushes > 0; pushes--) {
                        poseStack.popPose();
                    }
                }
                RenderStats.meshesDrawn++;
                if (shadowPass) {
                    RenderStats.shadowPassMeshes++;
                }
            }
        } finally {
            RecoloredTextures.end();
        }
    }

    /**
     * Draws a held cosmetic (a shield) in place of the held item: at the pose stack's current pose, which is
     * where vanilla would draw the item's model (after the item's display transform), so the cosmetic is built
     * like the vanilla model of that item. Returns false, drawing nothing, when the cosmetic shouldn't show
     * (the mod or the slot is off, the player is hidden, or the cosmetic failed before); the caller then
     * draws the vanilla item. {@code holder}'s hand, in first person or not, blocking or not, for a model whose
     * animations show it differently then.
     */
    public static boolean renderHeld(PoseStack poseStack, int packedLight, BakedCosmetic cosmetic, boolean local,
                                     java.util.UUID holder, boolean firstPerson, boolean blocking, Sink sink) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (!config.enabled || RenderGuard.isLayerDisabled() || RenderGuard.isDisabled(cosmetic.id())
                || (local ? !config.showOwnCosmetics : !config.showOtherPlayersCosmetics)
                || !config.slotVisible(cosmetic.slot())) {
            return false;
        }
        long start = RenderStats.TIMING ? System.nanoTime() : 0L;
        double seconds = (System.nanoTime() - START_NANOS) / 1.0e9;
        poseStack.pushPose();
        try {
            // Item models are drawn upside down and facing back (vanilla scales by 1, -1, -1 here too), so
            // the cosmetic is modelled in the same space as the vanilla model.
            poseStack.scale(1f, -1f, -1f);
            CosmeticsConfig.CosmeticOptions options = config.options(cosmetic.id());
            applyPlacement(poseStack, options, cosmetic);
            // "Turn around", and a half turn more in first person for a shield made to face the other way there.
            applyTurn(poseStack, options.turned != (firstPerson && cosmetic.definition().firstPersonTurned()), cosmetic);
            boolean glow = options.glow || !cosmetic.allows(CosmeticOption.GLOW);
            boolean animate = options.animate || !cosmetic.allows(CosmeticOption.ANIMATION);
            float animationSpeed = cosmetic.allows(CosmeticOption.ANIMATION) ? options.animationSpeed : 1f;
            int style = cosmetic.allows(CosmeticOption.STYLE) ? cosmetic.styleIndex(options.style) : cosmetic.defaultStyle();
            if (cosmetic.bones() != null) {
                Molang.Context context = MOLANG.clear().set(Molang.Query.IS_FIRST_PERSON, firstPerson)
                        .set(Molang.Query.IS_BLOCKING, blocking);
                poseBones(cosmetic.bones(), cosmetic, options, holder, seconds, context);
            }
            drawLayers(poseStack, cosmetic, packedLight, Tint.argb(options, cosmetic, seconds), glow, animate,
                    animationSpeed, style, 1f, null, 0f, 0.0, seconds, Compat.isShadowPass(), null, null, null, sink);
            RenderStats.heldDrawn++;
        } catch (RuntimeException | LinkageError e) {
            RenderGuard.cosmeticFailed(cosmetic.id(), e);
        } finally {
            poseStack.popPose();
        }
        if (RenderStats.TIMING) {
            RenderStats.nanos += System.nanoTime() - start;
        }
        return true;
    }

    /**
     * Draws a cosmetic on its own, without a player, at the pose stack's pose: the Wardrobe's tile pictures. It
     * stands still: wings at their resting angle, skeletal pieces at the start of their style, a texture
     * animation on its first frame. It has the player's colour for it.
     */
    public static void drawAlone(PoseStack poseStack, BakedCosmetic cosmetic, Sink sink) {
        CosmeticsConfig.CosmeticOptions options = CosmeticsConfig.get().options(cosmetic.id());
        boolean glow = options.glow || !cosmetic.allows(CosmeticOption.GLOW);
        int style = cosmetic.allows(CosmeticOption.STYLE) ? cosmetic.styleIndex(options.style) : cosmetic.defaultStyle();
        CosmeticDefinition.Wings wings = cosmetic.wings();
        float wingAngle = wings != null ? wings.idle().base() : 0f;
        if (cosmetic.bones() != null) {
            cosmetic.bones().poseStill();
        }
        applyTurn(poseStack, options, cosmetic);
        drawLayers(poseStack, cosmetic, MeshEmitter.FULL_BRIGHT, Tint.argb(options, cosmetic, 0.0), glow, false, 1f,
                style, 0f, wings, wingAngle, 0.0, 0.0, false, null, null, null, sink);
    }

    /**
     * The held cosmetic that replaces this item for this player, or null: the first one of the player's
     * cosmetics in a held slot that this item takes (a shield cosmetic for a shield).
     */
    public static BakedCosmetic heldFor(java.util.UUID player, boolean local, CosmeticSlot slot) {
        CosmeticsConfig config = CosmeticsConfig.get();
        if (player == null || !config.enabled || !config.slotVisible(slot)) {
            return null;
        }
        for (BakedCosmetic cosmetic : EquippedCosmetics.forPlayer(player, local)) {
            if (cosmetic.slot() == slot && !RenderGuard.isDisabled(cosmetic.id())) {
                return cosmetic;
            }
        }
        return null;
    }

    /** Where a pet sits: the player's choice, else where its creator put it. */
    public static PetPosition petPosition(BakedCosmetic cosmetic, CosmeticsConfig.CosmeticOptions options) {
        return options.petPosition != null ? options.petPosition : cosmetic.definition().petPosition();
    }

    /**
     * Moves from the pet's anchor (the body, or the head for {@link PetPosition#HEAD}) to where it stands: the
     * top of a shoulder (+X is the wearer's left), the top of the head, or floating beside the shoulder, a little
     * behind it, bobbing gently.
     */
    private static void placePet(PoseStack poseStack, PetPosition pet, double seconds) {
        switch (pet) {
            case RIGHT_SHOULDER -> poseStack.translate(-6f / 16f, 0f, 0f);
            case HEAD -> poseStack.translate(0f, -8f / 16f, 0f);
            case FLOATING -> {
                float bob = (float) Math.sin(seconds * Math.PI * 0.8) * 0.8f;
                poseStack.translate(12f / 16f, (-7f + bob) / 16f, 5f / 16f);
            }
            default -> poseStack.translate(6f / 16f, 0f, 0f);
        }
    }

    /** The user's per-cosmetic offset and size, around the anchor's pivot, if the cosmetic allows them. */
    /** Turns a cosmetic the player turned round half a turn around the upright line through its middle. */
    private static void applyTurn(PoseStack poseStack, CosmeticsConfig.CosmeticOptions options, BakedCosmetic cosmetic) {
        applyTurn(poseStack, options.turned, cosmetic);
    }

    /** Half a turn around the cosmetic's own middle when {@code turned}. */
    private static void applyTurn(PoseStack poseStack, boolean turned, BakedCosmetic cosmetic) {
        if (!turned) {
            return;
        }
        float[] middle = cosmetic.middle();
        poseStack.translate(middle[0], 0f, middle[2]);
        rotate(poseStack, ROTATION.rotationY((float) Math.PI));
        poseStack.translate(-middle[0], 0f, -middle[2]);
    }

    private static void applyPlacement(PoseStack poseStack, CosmeticsConfig.CosmeticOptions options,
                                       BakedCosmetic cosmetic) {
        if ((options.offsetX != 0f || options.offsetY != 0f || options.offsetZ != 0f)
                && cosmetic.allows(CosmeticOption.POSITION)) {
            poseStack.translate(options.offsetX / 16f, options.offsetY / 16f, options.offsetZ / 16f);
        }
        if (options.scale != 1f && cosmetic.allows(CosmeticOption.SIZE)) {
            poseStack.scale(options.scale, options.scale, options.scale);
        }
    }

    /** The wings' offsets: always, blended in while sneaking, and while a chestplate is worn. */
    private static void applyWingOffsets(PoseStack poseStack, CosmeticDefinition.Wings wings, float sneak, boolean armor) {
        float[] o = wings.offset();
        float[] s = wings.sneakOffset();
        float[] a = wings.armorOffset();
        float x = o[0] + s[0] * sneak + (armor ? a[0] : 0f);
        float y = o[1] + s[1] * sneak + (armor ? a[1] : 0f);
        float z = o[2] + s[2] * sneak + (armor ? a[2] : 0f);
        if (x != 0f || y != 0f || z != 0f) {
            poseStack.translate(x / 16f, y / 16f, z / 16f);
        }
    }

    /**
     * Turns one wing around its pivot: the left wing by {@code angle} around the wing axis (right-hand
     * rule), the right wing the other way, after moving each sideways by the spread.
     */
    private static void applyWing(PoseStack poseStack, CosmeticDefinition.Wings wings, CosmeticDefinition.WingPart wing,
                                  float angle) {
        float spread = wing.side() * wings.spread() / 16f;
        float px = wing.pivotX() / 16f;
        float py = wing.pivotY() / 16f;
        float pz = wing.pivotZ() / 16f;
        float radians = (float) Math.toRadians(angle * wing.side());
        poseStack.translate(px + spread, py, pz);
        rotate(poseStack, switch (wings.axis()) {
            case 0 -> ROTATION.rotationX(radians);
            case 2 -> ROTATION.rotationZ(radians);
            default -> ROTATION.rotationY(radians);
        });
        poseStack.translate(-px, -py, -pz);
    }

    /** Body parts bone layers can follow, in drawing order. */
    private static final BoneRig.Follow[] FOLLOWS = {BoneRig.Follow.BODY, BoneRig.Follow.HEAD, BoneRig.Follow.RIGHT_ARM,
            BoneRig.Follow.LEFT_ARM, BoneRig.Follow.RIGHT_LEG, BoneRig.Follow.LEFT_LEG};

    /** Queries for bone animations, refilled for each cosmetic drawn. Render thread only. */
    private static final Molang.Context MOLANG = new Molang.Context();

    /** Where each player is in each cosmetic's animation states. Render thread only. */
    private static final java.util.Map<java.util.UUID, java.util.Map<String, BoneRig.Playback>> PLAYBACKS =
            new java.util.HashMap<>();
    private static final java.util.UUID NOBODY = new java.util.UUID(0L, 0L);
    private static double lastPlaybackSweep;

    /** Reused for bone corrections. Render thread only. */
    private static final Matrix4f BONE = new Matrix4f();
    private static final Matrix3f BONE_NORMAL = new Matrix3f();

    /** What the bone animations' queries see of the player being drawn. */
    private static Molang.Context bonesContext(PlayerRenderInfo info, boolean inGui) {
        return MOLANG.clear()
                .set(Molang.Query.IS_IN_GUI, inGui)
                .set(Molang.Query.IS_ELYTRA_FLYING, info.gliding)
                .set(Molang.Query.IS_SWIMMING, info.swimming)
                .set(Molang.Query.IS_SNEAKING, info.sneaking)
                .set(Molang.Query.IS_FLYING, info.flying && !info.gliding)
                .set(Molang.Query.IS_BLOCKING, info.blocking)
                .set(Molang.Query.IS_ON_GROUND, info.onGround)
                .set(Molang.Query.IS_SPRINTING, info.sprinting)
                .set(Molang.Query.IS_RIDING, info.riding)
                .set(Molang.Query.IS_MOVING, info.moveSpeed > 0.01f)
                .set(Molang.Query.GROUND_SPEED, info.moveSpeed * 20f)
                .set(Molang.Query.MODIFIED_DISTANCE_MOVED, info.walked);
    }

    /** Works out a cosmetic's bones for one player now (each animation's speed and on/off from its settings). */
    private static void poseBones(BoneRig bones, BakedCosmetic cosmetic, CosmeticsConfig.CosmeticOptions options,
                                  java.util.UUID player, double seconds, Molang.Context context) {
        boolean animate = options.animate || !cosmetic.allows(CosmeticOption.ANIMATION);
        float speed = cosmetic.allows(CosmeticOption.ANIMATION) ? options.animationSpeed : 1f;
        context.set(Molang.Query.LIFE_TIME, seconds);
        java.util.Map<String, BoneRig.Playback> mine = PLAYBACKS.computeIfAbsent(player != null ? player : NOBODY,
                k -> new java.util.HashMap<>());
        BoneRig.Playback playback = mine.get(cosmetic.id());
        if (playback == null || playback.controllers() != bones.controllerCount()) {
            playback = bones.newPlayback();
            mine.put(cosmetic.id(), playback);
        }
        playback.lastUsed = seconds;
        bones.pose(playback, seconds, animate ? speed : 0f, context);
        if (seconds - lastPlaybackSweep > 30.0) {
            // Players who left (and cosmetics taken off) stop being remembered after a minute.
            lastPlaybackSweep = seconds;
            PLAYBACKS.values().forEach(m -> m.values().removeIf(p -> seconds - p.lastUsed > 60.0));
            PLAYBACKS.values().removeIf(java.util.Map::isEmpty);
        }
    }

    /**
     * Draws the layers of a player-skeleton model that follow a body part, each from that part: from the model's
     * root, turned and moved with the part, then back by where the part sits at rest, so the geometry (made where
     * it sits on the player) moves with the part.
     */
    private static void drawFollowing(PoseStack poseStack, BakedCosmetic cosmetic, CosmeticsConfig.CosmeticOptions options,
                                      int packedLight, double seconds, boolean shadowPass, ModelPart head, ModelPart body,
                                      ModelPart[] limbs, Sink sink) {
        int color = Tint.argb(options, cosmetic, seconds);
        boolean glow = options.glow || !cosmetic.allows(CosmeticOption.GLOW);
        boolean animate = options.animate || !cosmetic.allows(CosmeticOption.ANIMATION);
        float animationSpeed = cosmetic.allows(CosmeticOption.ANIMATION) ? options.animationSpeed : 1f;
        int style = cosmetic.allows(CosmeticOption.STYLE) ? cosmetic.styleIndex(options.style) : cosmetic.defaultStyle();
        for (BoneRig.Follow follow : FOLLOWS) {
            ModelPart part = switch (follow) {
                case HEAD -> head;
                case BODY -> body;
                case LEFT_ARM -> limbs[0];
                case RIGHT_ARM -> limbs[1];
                case LEFT_LEG -> limbs[2];
                case RIGHT_LEG -> limbs[3];
                default -> null;
            };
            if (part == null || !part.visible) {
                continue;
            }
            poseStack.pushPose();
            try {
                part.translateAndRotate(poseStack);
                applyPlacement(poseStack, options, cosmetic);
                poseStack.translate(-follow.pivotX / 16f, -follow.pivotY / 16f, 0f);
                drawLayers(poseStack, cosmetic, packedLight, color, glow, animate, animationSpeed, style, 1f, null, 0f,
                        0.0, seconds, shadowPass, null, null, follow, sink);
            } finally {
                poseStack.popPose();
            }
        }
    }

    /** Moves a bone's geometry from where it was baked to where its animation puts it ({@link BoneRig#correction}). */
    private static void applyBone(PoseStack poseStack, float[] c, int m) {
        // JOML takes columns: column 0 is the first entry of each row.
        BONE.set(c[m], c[m + 3], c[m + 6], 0f,
                c[m + 1], c[m + 4], c[m + 7], 0f,
                c[m + 2], c[m + 5], c[m + 8], 0f,
                c[m + 9], c[m + 10], c[m + 11], 1f);
        PoseStack.Pose last = poseStack.last();
        last.pose().mul(BONE);
        // Normals by the inverse transpose, so scaled bones still light right (they are normalised when drawn).
        last.normal().mul(BONE_NORMAL.set(BONE).invert().transpose());
    }

    /**
     * Places a skeletal mesh: pixels, then its placement (offset, rotation around the pivot, scale), then
     * for a rigid piece its joint's current transform. Skinned geometry is already in the rig's space.
     */
    private static void applyRig(PoseStack poseStack, MeshRig rig, int joint, int clip, float clipTime) {
        poseStack.scale(1f / 16f, 1f / 16f, 1f / 16f);
        float[] offset = rig.offset();
        float[] pivot = rig.pivot();
        float[] q = rig.rotation();
        poseStack.translate(offset[0] + pivot[0], offset[1] + pivot[1], offset[2] + pivot[2]);
        if (q[3] != 1f) {
            rotate(poseStack, ROTATION.set(q[0], q[1], q[2], q[3]));
        }
        poseStack.translate(-pivot[0], -pivot[1], -pivot[2]);
        float[] scale = rig.scale();
        if (scale[0] != 1f || scale[1] != 1f || scale[2] != 1f) {
            poseStack.scale(scale[0], scale[1], scale[2]);
        }
        if (joint >= 0) {
            float[] joints = rig.evaluate(clip, clipTime);
            int o = joint * 7;
            poseStack.translate(joints[o], joints[o + 1], joints[o + 2]);
            rotate(poseStack, ROTATION.set(joints[o + 3], joints[o + 4], joints[o + 5], joints[o + 6]));
        }
    }

    /** One animation step at its current value (degrees for spin/swing, pixels for bob). */
    private static void applyAnimation(PoseStack poseStack, CosmeticDefinition.Animation animation, float value) {
        if (animation.type() == CosmeticDefinition.AnimationType.BOB) {
            float d = value / 16f;
            poseStack.translate(animation.axis() == 0 ? d : 0f, animation.axis() == 1 ? d : 0f, animation.axis() == 2 ? d : 0f);
            return;
        }
        float px = animation.pivotX() / 16f, py = animation.pivotY() / 16f, pz = animation.pivotZ() / 16f;
        float radians = (float) Math.toRadians(value);
        Quaternionf rotation = switch (animation.axis()) {
            case 0 -> new Quaternionf().rotationX(radians);
            case 2 -> new Quaternionf().rotationZ(radians);
            default -> new Quaternionf().rotationY(radians);
        };
        poseStack.translate(px, py, pz);
        rotate(poseStack, rotation);
        poseStack.translate(-px, -py, -pz);
    }

    public static void rotate(PoseStack poseStack, Quaternionf rotation) {
        //? if >=26.3 {
        /*poseStack.rotate(rotation);
        *///?} else {
        poseStack.mulPose(rotation);
        //?}
    }

    /** True if a distance limit (in blocks, 0 = none) excludes something at this squared distance. */
    private static boolean beyond(double distanceSq, int limit) {
        return limit > 0 && distanceSq > (double) limit * limit;
    }

    /**
     * Same placement and sway as the vanilla cape: 2px behind the body pivot, then the vanilla rotation
     * sequence driven by the vanilla cape physics values. Cape cosmetics are modelled like the vanilla
     * cape cube ({@code from [-5, 0, -1] to [5, 16, 0]}).
     */
    private static void applyCapeSway(PoseStack poseStack, PlayerRenderInfo info, CosmeticsConfig config) {
        poseStack.translate(0.0F, 0.0F, 0.125F);
        float swing = config.capePhysics ? config.capeSwing : 0.0F;
        float flap = info.capeFlap * swing;
        float lean = info.capeLean * swing;
        float lean2 = info.capeLean2 * swing;
        // X, then Z, then Y, like the vanilla cape.
        Quaternionf rotation = new Quaternionf()
                .rotationX((float) Math.toRadians(6.0F + lean / 2.0F + flap))
                .rotateZ((float) Math.toRadians(lean2 / 2.0F))
                .rotateY((float) Math.toRadians(180.0F - lean2 / 2.0F));
        rotate(poseStack, rotation);
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
