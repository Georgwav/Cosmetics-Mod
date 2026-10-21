package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.SolSounds;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * PvP Effects: your hit sounds, from one of Sol's sound packs or your own (client/sounds), with a sound of their own for
 * a hit, a critical hit and a kill; their volume and pitch, and crits only. The kill effects moved to Kill Effects (C.4);
 * only on your screen, heard only by you.
 */
public final class PvpEffects extends Module {
    private final Setting.Choice hitSound = add(new Setting.Choice("hit_sound", "tick", SolSounds.choices()).group("hit_sound"));
    private final Setting.Toggle onHit = add(new Setting.Toggle("sound_hit", true).group("hit_sound"));
    private final Setting.Toggle onCrit = add(new Setting.Toggle("sound_crit", true).group("hit_sound"));
    private final Setting.Toggle onKill = add(new Setting.Toggle("sound_kill", true).group("hit_sound"));
    private final Setting.Slider hitVolume = add(new Setting.Slider("hit_volume", 100, 10, 200, 10, v -> Math.round(v) + "%")
            .group("hit_sound"));
    private final Setting.Slider hitPitch = add(new Setting.Slider("hit_pitch", 100, 50, 200, 10, v -> Math.round(v) + "%")
            .group("hit_sound"));
    /** Only on a critical hit (falling, not on the ground, not sprinting into it). */
    private final Setting.Toggle critsOnly = add(new Setting.Toggle("crits_only", false).group("hit_sound"));

    public PvpEffects() {
        super("pvp_effects", Category.VIEW, "burst");
        Setting.Choice snd = hitSound;
        for (Setting<?> s : List.of(onHit, onCrit, onKill, hitVolume, hitPitch, critsOnly)) {
            s.shownWhen(() -> !snd.get().equals("off"));
        }
        FxEvents.HIT.add(new FxEvents.Hit() {
            @Override
            public boolean listening() {
                return enabled();
            }

            @Override
            public void hit(LivingEntity target, double x, double y, double z, boolean crit, float healthBefore) {
                if (critsOnly.get() && !crit) {
                    return;
                }
                if (crit ? onCrit.get() : onHit.get()) {
                    play(crit ? "crit" : "hit");
                }
            }
        });
        FxEvents.KILL.add(new FxEvents.Kill() {
            @Override
            public boolean listening() {
                return enabled() && onKill.get();
            }

            @Override
            public void killed(LivingEntity dead, PoseSnapshot copy) {
                // A kill effect with a sound of its own plays that one instead.
                var kill = Modules.KILL_EFFECTS;
                if (kill.enabled() && kill.fx.sound != null && !kill.fx.sound.get().equals("off")) {
                    return;
                }
                play("kill");
            }
        });
        io.github.georgwav.cosmetics.sol.fx.SolPicks.module(this);
        io.github.georgwav.cosmetics.sol.fx.SolPicks.set(hitSound, "tick");
    }

    private void play(String event) {
        SolSounds.play(hitSound.get(), event, (float) (hitVolume.get() / 100.0), (float) (hitPitch.get() / 100.0));
    }

    /** The old game sounds become the nearest of Sol's packs; the kill effect moved (Migrations). */
    @Override
    protected void migrate(com.google.gson.JsonObject settings) {
        if (settings.has("hit_sound") && settings.get("hit_sound").isJsonPrimitive()) {
            String old = settings.get("hit_sound").getAsString();
            String now = switch (old) {
                case "none" -> "off";
                case "click" -> "tick";
                case "bell", "pling" -> "chime";
                case "orb" -> "pop";
                default -> old;
            };
            settings.addProperty("hit_sound", now);
        }
    }
}
