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
 * PvP Effects: a sound of your choice when you hit something, and a kill effect where something you hit in the last
 * three seconds dies: a lightning strike, a burst of totem sparks with a crack of fireworks, or only a sound. Only on
 * your screen: the lightning is a bolt of your own world's, harmless and seen by no one else.
 */
public final class PvpEffects extends Module {
    private final Setting.Choice hitSound = add(new Setting.Choice("hit_sound", "none", List.of("none", "click", "bell", "pling", "orb"))
            .group("hit_sound"));
    private final Setting.Slider hitVolume = add(new Setting.Slider("hit_volume", 100, 10, 200, 10, v -> Math.round(v) + "%")
            .group("hit_sound"));
    private final Setting.Slider hitPitch = add(new Setting.Slider("hit_pitch", 100, 50, 200, 10, v -> Math.round(v) + "%")
            .group("hit_sound"));
    /** Only on a critical hit (falling, not on the ground, not sprinting into it). */
    private final Setting.Toggle critsOnly = add(new Setting.Toggle("crits_only", false).group("hit_sound"));
    private final Setting.Choice kill = add(new Setting.Choice("kill_effect", "burst", List.of("none", "lightning", "burst", "sound"))
            .group("kill_effect"));
    private final Setting.Choice burst = add(new Setting.Choice("burst_particles", "totem", List.of("totem", "flame", "soul", "heart", "crit",
            "end_rod")).group("kill_effect"));
    private final Setting.Slider burstAmount = add(new Setting.Slider("burst_amount", 40, 10, 150, 10, v -> Integer.toString((int) Math.round(v)))
            .group("kill_effect"));
    private final Setting.Slider killVolume = add(new Setting.Slider("kill_volume", 100, 0, 200, 10, v -> Math.round(v) + "%")
            .group("kill_effect"));
    /** Only when the one that dies is a player. */
    private final Setting.Toggle playersOnly = add(new Setting.Toggle("players_only", false).group("kill_effect"));
    private LivingEntity target;
    private long hitAt;
    /** Ids for our own bolts, far below any the server hands out. */
    private static int nextBoltId = -1_000_000;

    public PvpEffects() {
        super("pvp_effects", Category.VIEW, "burst");
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (enabled() && level.isClientSide() && player == mc.player) {
                if (entity instanceof LivingEntity living) {
                    target = living;
                    hitAt = System.currentTimeMillis();
                }
                if (!critsOnly.get() || crit(player)) {
                    float vol = (float) (hitVolume.get() / 100.0), pitch = (float) (hitPitch.get() / 100.0);
                    switch (hitSound.get()) {
                        case "click" -> play(mc, SoundEvents.UI_BUTTON_CLICK.value(), 1.6f * pitch, 0.25f * vol);
                        case "bell" -> play(mc, SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f * pitch, 0.25f * vol);
                        case "pling" -> play(mc, SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f * pitch, 0.25f * vol);
                        case "orb" -> play(mc, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.3f * pitch, 0.25f * vol);
                        default -> {
                        }
                    }
                }
            }
            return InteractionResult.PASS;
        });
    }

    private static void play(Minecraft mc, net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        if (volume > 0f) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, Math.max(0.5f, Math.min(2f, pitch)), volume));
        }
    }

    /** Whether the hit is a critical one, as the game decides it. */
    private static boolean crit(net.minecraft.world.entity.player.Player p) {
        return p.fallDistance > 0f && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger() && !p.isSprinting()
                && p.getAttackStrengthScale(0.5f) > 0.9f;
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
            if (!playersOnly.get() || target instanceof net.minecraft.world.entity.player.Player) {
                effect(mc, target);
            }
            target = null;
        }
    }

    private void effect(Minecraft mc, LivingEntity dead) {
        switch (kill.get()) {
            case "lightning" -> {
                //? if >=26.2 {
                /*var bolt = new net.minecraft.world.entity.LightningBolt(net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT, mc.level);
                *///?} else {
                var bolt = new net.minecraft.world.entity.LightningBolt(net.minecraft.world.entity.EntityType.LIGHTNING_BOLT, mc.level);
                //?}
                bolt.setId(nextBoltId--);
                bolt.setPos(dead.getX(), dead.getY(), dead.getZ());
                bolt.setVisualOnly(true);
                mc.level.addEntity(bolt);
                return;
            }
            case "sound" -> {
                play(mc, SoundEvents.PLAYER_LEVELUP, 1.4f, 0.6f * (float) (killVolume.get() / 100.0));
                return;
            }
            case "burst" -> {
            }
            default -> {
                return;
            }
        }
        ParticleOptions p = switch (burst.get()) {
            case "flame" -> ParticleTypes.FLAME;
            case "soul" -> ParticleTypes.SOUL_FIRE_FLAME;
            case "heart" -> ParticleTypes.HEART;
            case "crit" -> ParticleTypes.CRIT;
            case "end_rod" -> ParticleTypes.END_ROD;
            default -> ParticleTypes.TOTEM_OF_UNDYING;
        };
        int amount = (int) Math.round(burstAmount.get());
        double x = dead.getX(), y = dead.getY() + dead.getBbHeight() * 0.6, z = dead.getZ();
        var random = mc.level.getRandom();
        for (int i = 0; i < amount; i++) {
            double a = random.nextDouble() * Math.PI * 2, up = random.nextDouble() * 0.6 + 0.1;
            double speed = 0.15 + random.nextDouble() * 0.25;
            mc.level.addParticle(p, x, y, z, Math.cos(a) * speed, up, Math.sin(a) * speed);
        }
        play(mc, SoundEvents.FIREWORK_ROCKET_BLAST, 1.2f, 0.5f * (float) (killVolume.get() / 100.0));
    }
}
