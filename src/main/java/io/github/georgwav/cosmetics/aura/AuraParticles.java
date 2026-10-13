package io.github.georgwav.cosmetics.aura;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.render.RenderGuard;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Gives off the particles of particle cosmetics (auras, trails): every client tick, for every player in view
 * wearing one, each emitter adds as many particles to the game's particle engine as its rate says. The
 * particles then live in the world like vanilla ones: they stay behind when the player moves, work with
 * shaders and are drawn by the game.
 */
public final class AuraParticles {
    /** Particles of ours alive right now; past {@link #MAX_LIVE} no more are added. */
    static int live;
    /** Particles added since the game started (read by the in-game test). */
    public static long spawned;
    private static final int MAX_LIVE = 3000;
    /** At most this many per player per tick, whatever the rates. */
    private static final int MAX_PER_TICK = 40;
    private static final Random RANDOM = new Random();
    /** Per player, per cosmetic emitter: the fraction of a particle carried over to the next tick. */
    private static final Map<UUID, Map<String, float[]>> CARRY = new HashMap<>();
    private static boolean failed;

    private AuraParticles() {
    }

    /** Called at the end of every client tick. */
    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        if (level == null) {
            CARRY.clear();
            return;
        }
        CosmeticsConfig config = CosmeticsConfig.get();
        if (failed || client.isPaused() || !config.enabled || !config.particles || config.particleDensity <= 0f
                || RenderGuard.isLayerDisabled()) {
            return;
        }
        try {
            Set<UUID> seen = new HashSet<>();
            for (AbstractClientPlayer player : level.players()) {
                seen.add(player.getUUID());
                emit(client, level, player, config);
            }
            CARRY.keySet().retainAll(seen);
        } catch (RuntimeException | LinkageError e) {
            // Never take the game down over particles: stop giving them off and say why once.
            failed = true;
            CosmeticsMod.LOGGER.error("Particle cosmetics failed and are off until the game restarts", e);
        }
    }

    private static void emit(Minecraft client, ClientLevel level, AbstractClientPlayer player, CosmeticsConfig config) {
        boolean local = player == client.player;
        if (player.isInvisible() || (local ? !config.showOwnCosmetics : !config.showOtherPlayersCosmetics)) {
            return;
        }
        if (local && !config.ownParticlesInFirstPerson && client.options.getCameraType().isFirstPerson()) {
            return;
        }
        if (!local && client.player != null && config.maxRenderDistance > 0
                && player.distanceToSqr(client.player) > (double) config.maxRenderDistance * config.maxRenderDistance) {
            return;
        }
        BakedCosmetic[] cosmetics = EquippedCosmetics.forPlayer(player.getUUID(), local);
        double dx = player.getX() - player.xo;
        double dz = player.getZ() - player.zo;
        boolean moving = dx * dx + dz * dz > 4.0e-4;
        int budget = MAX_PER_TICK;
        for (BakedCosmetic cosmetic : cosmetics) {
            List<CosmeticDefinition.ParticleEmitter> emitters = cosmetic.definition().particles();
            if (emitters.isEmpty() || !config.slotVisible(cosmetic.slot()) || RenderGuard.isDisabled(cosmetic.id())) {
                continue;
            }
            for (int i = 0; i < emitters.size(); i++) {
                CosmeticDefinition.ParticleEmitter emitter = emitters.get(i);
                float[] carry = CARRY.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                        .computeIfAbsent(cosmetic.id() + "#" + i, k -> new float[1]);
                if (!emitter.when().active(moving)) {
                    carry[0] = 0f;
                    continue;
                }
                int count = AuraSpawner.count(carry, emitter.rate(), 0.05f, config.particleDensity);
                if (count == 0) {
                    continue;
                }
                TextureAtlasSprite[] sprites = sprites(client, emitter.sprites());
                for (int n = 0; n < count && budget > 0 && live < MAX_LIVE; n++, budget--) {
                    AuraSpawner.Spawn spawn = AuraSpawner.spawn(emitter, player.yBodyRot, RANDOM);
                    client.particleEngine.add(new AuraParticle(level, player.getX() + spawn.x(),
                            player.getY() + spawn.y(), player.getZ() + spawn.z(), spawn, sprites));
                    spawned++;
                }
            }
        }
    }

    /** The emitter's textures from the particle atlas (a missing one shows the game's missing texture). */
    private static TextureAtlasSprite[] sprites(Minecraft client, List<String> ids) {
        TextureAtlas atlas = (TextureAtlas) client.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[ids.size()];
        for (int i = 0; i < sprites.length; i++) {
            //? if >=1.21.11 {
            sprites[i] = atlas.getSprite(Identifier.parse(ids.get(i)));
            //?} else {
            /*sprites[i] = atlas.getSprite(ResourceLocation.parse(ids.get(i)));
            *///?}
        }
        return sprites;
    }
}
