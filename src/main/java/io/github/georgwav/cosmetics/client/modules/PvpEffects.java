package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Setting;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * PvP Effects: a sound of your choice when you hit something, and a kill effect (a burst of totem sparks, flames or
 * souls, with a crack of fireworks) where something you hit in the last three seconds dies. Only on your screen.
 */
public final class PvpEffects extends Module {
    private final Setting.Choice hitSound = add(new Setting.Choice("hit_sound", "none", List.of("none", "click", "bell", "pling", "orb")));
    private final Setting.Choice kill = add(new Setting.Choice("kill_effect", "burst", List.of("none", "burst", "flames", "souls")));
    private LivingEntity target;
    private long hitAt;

    public PvpEffects() {
        super("pvp_effects", Category.VIEW, "burst");
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (enabled() && level.isClientSide() && player == mc.player) {
                if (entity instanceof LivingEntity living) {
                    target = living;
                    hitAt = System.currentTimeMillis();
                }
                switch (hitSound.get()) {
                    case "click" -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.6f));
                    case "bell" -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL, 1.4f));
                    case "pling" -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING, 1.5f));
                    case "orb" -> mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f));
                    default -> {
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    protected void tick(Minecraft mc) {
        if (target == null) {
            return;
        }
        if (System.currentTimeMillis() - hitAt > 3000L || mc.level == null) {
            target = null;
            return;
        }
        if (target.isDeadOrDying() || !target.isAlive()) {
            effect(mc, target);
            target = null;
        }
    }

    private void effect(Minecraft mc, LivingEntity dead) {
        ParticleOptions p = switch (kill.get()) {
            case "flames" -> ParticleTypes.FLAME;
            case "souls" -> ParticleTypes.SOUL;
            case "burst" -> ParticleTypes.TOTEM_OF_UNDYING;
            default -> null;
        };
        if (p == null) {
            return;
        }
        double x = dead.getX(), y = dead.getY() + dead.getBbHeight() * 0.6, z = dead.getZ();
        var random = mc.level.getRandom();
        for (int i = 0; i < 40; i++) {
            double a = random.nextDouble() * Math.PI * 2, up = random.nextDouble() * 0.6 + 0.1;
            double speed = 0.15 + random.nextDouble() * 0.25;
            mc.level.addParticle(p, x, y, z, Math.cos(a) * speed, up, Math.sin(a) * speed);
        }
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.5f));
    }
}
