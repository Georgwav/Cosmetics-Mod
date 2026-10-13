package io.github.georgwav.cosmetics.ui;

import io.github.georgwav.cosmetics.aura.AuraSpawner;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.model.BakedCosmetic;
import io.github.georgwav.cosmetics.model.CosmeticDefinition;
import io.github.georgwav.cosmetics.render.Tint;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The particles of particle cosmetics (auras) around the player in the Wardrobe preview. The game's particles
 * live in the world, so the preview simulates its own with the same spawn maths ({@link AuraSpawner}), as for a
 * player standing still, and draws them over the preview with the preview's turn and tilt.
 */
final class PreviewParticles {
    private static final int MAX = 400;
    private static final float TICKS_PER_SECOND = 20f;

    private final List<Particle> particles = new ArrayList<>();
    private final Map<String, float[]> carry = new HashMap<>();
    private final Random random = new Random();
    private final long startNanos = System.nanoTime();
    private long lastNanos;

    private static final class Particle {
        double x, y, z, vx, vy, vz;
        float age;
        final AuraSpawner.Spawn spawn;

        Particle(AuraSpawner.Spawn spawn) {
            this.spawn = spawn;
            x = spawn.x();
            y = spawn.y();
            z = spawn.z();
            vx = spawn.vx();
            vy = spawn.vy();
            vz = spawn.vz();
        }
    }

    /** Moves the particles on and gives off new ones for {@code worn}, the body facing {@code bodyYaw} degrees. */
    void update(BakedCosmetic[] worn, float bodyYaw) {
        long now = System.nanoTime();
        float seconds = lastNanos == 0L ? 0f : Math.min(0.25f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;
        CosmeticsConfig config = CosmeticsConfig.get();
        if (config.particles && config.particleDensity > 0f) {
            for (BakedCosmetic cosmetic : worn) {
                List<CosmeticDefinition.ParticleEmitter> emitters = cosmetic.definition().particles();
                CosmeticsConfig.CosmeticOptions options = config.options(cosmetic.id());
                if (emitters.isEmpty() || !options.particles || !config.slotVisible(cosmetic.slot())) {
                    continue;
                }
                Integer color = Tint.particleColor(options, cosmetic, (now - startNanos) / 1.0e9);
                for (int i = 0; i < emitters.size(); i++) {
                    CosmeticDefinition.ParticleEmitter emitter = emitters.get(i);
                    // The preview stands still: trails that only run while moving stay off.
                    if (!emitter.when().active(false)) {
                        continue;
                    }
                    float[] left = carry.computeIfAbsent(cosmetic.id() + "#" + i, k -> new float[1]);
                    int count = AuraSpawner.count(left, emitter.rate(), seconds,
                            config.particleDensity * options.particleAmount);
                    for (int n = 0; n < count && particles.size() < MAX; n++) {
                        particles.add(new Particle(AuraSpawner.adjusted(AuraSpawner.spawn(emitter, bodyYaw, random),
                                options.particleSize, color)));
                    }
                }
            }
        }
        float ticks = seconds * TICKS_PER_SECOND;
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.age += ticks;
            if (p.age >= p.spawn.lifetime()) {
                it.remove();
                continue;
            }
            p.x += p.vx * ticks;
            p.y += p.vy * ticks;
            p.z += p.vz * ticks;
            p.vy -= p.spawn.gravity() * ticks;
            double drag = Math.pow(p.spawn.friction(), ticks);
            p.vx *= drag;
            p.vy *= drag;
            p.vz *= drag;
        }
    }

    /**
     * Draws the particles as the preview shows the player: {@code scale} pixels a block around ({@code centerX},
     * {@code centerY}), the feet {@code feetY} blocks below it, turned by {@code pose}.
     */
    void draw(Canvas g, float centerX, float centerY, float scale, float feetY, Quaternionf pose) {
        Vector3f v = new Vector3f();
        for (Particle p : particles) {
            float t = Math.min(1f, p.age / Math.max(1, p.spawn.lifetime()));
            float size = p.spawn.startSize() + (p.spawn.endSize() - p.spawn.startSize()) * t;
            float alpha = p.spawn.startAlpha() + (p.spawn.endAlpha() - p.spawn.startAlpha()) * t;
            if (alpha <= 0.02f) {
                continue;
            }
            v.set((float) p.x, (float) p.y, (float) p.z).rotate(pose);
            float sx = centerX + scale * v.x;
            float sy = centerY + scale * (v.y + feetY);
            float half = Math.max(0.5f, size * scale);
            int argb = ((int) (Math.min(1f, alpha) * 255) << 24) | (p.spawn.color() & 0xFFFFFF);
            g.fill(Math.round(sx - half), Math.round(sy - half), Math.round(sx + half), Math.round(sy + half), argb);
        }
    }

    void clear() {
        particles.clear();
        carry.clear();
        lastNanos = 0L;
    }
}
