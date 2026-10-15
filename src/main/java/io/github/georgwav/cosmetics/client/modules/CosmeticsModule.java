package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.config.CosmeticsConfig;
import io.github.georgwav.cosmetics.source.EquippedCosmetics;

/**
 * Cosmetics as a module card: the same switch as the Wardrobe's Settings › Display › Cosmetics enabled (kept in the
 * cosmetics config). Everything else about cosmetics is in the Wardrobe.
 */
public final class CosmeticsModule extends Module {
    /** A trail behind your arrows, tridents and thrown things while they fly (only on your screen). */
    private final io.github.georgwav.cosmetics.client.Setting.Choice trails = add(new io.github.georgwav.cosmetics.client.Setting.Choice(
            "projectile_trails", "none", java.util.List.of("none", "sparkle", "flame", "hearts", "notes")));

    public CosmeticsModule() {
        super("cosmetics", Category.VIEW, "cape");
    }

    @Override
    protected void tick(net.minecraft.client.Minecraft mc) {
        String t = trails.get();
        if (t.equals("none") || mc.level == null || mc.player == null) {
            return;
        }
        net.minecraft.core.particles.SimpleParticleType type = switch (t) {
            case "flame" -> net.minecraft.core.particles.ParticleTypes.FLAME;
            case "hearts" -> net.minecraft.core.particles.ParticleTypes.HEART;
            case "notes" -> net.minecraft.core.particles.ParticleTypes.NOTE;
            default -> net.minecraft.core.particles.ParticleTypes.END_ROD;
        };
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            // Yours, and still flying (an arrow in a wall has stopped).
            if (e instanceof net.minecraft.world.entity.projectile.Projectile p && p.getOwner() == mc.player
                    && p.getDeltaMovement().lengthSqr() > 0.02) {
                trailsMade++;
                mc.level.addParticle(type, p.getX(), p.getY(), p.getZ(), 0.0, 0.0, 0.0);
            }
        }
    }

    /** For the in-game test: trail particles made. */
    public static int trailsMade;

    @Override
    public boolean enabled() {
        return CosmeticsConfig.get().enabled;
    }

    @Override
    public void setEnabled(boolean on) {
        CosmeticsConfig cfg = CosmeticsConfig.get();
        if (cfg.enabled != on) {
            cfg.enabled = on;
            CosmeticsConfig.save();
            EquippedCosmetics.invalidate();
        }
    }

    @Override
    public boolean onByDefault() {
        return true;
    }
}
