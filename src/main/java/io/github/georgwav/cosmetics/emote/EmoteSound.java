package io.github.georgwav.cosmetics.emote;

import io.github.georgwav.cosmetics.CosmeticsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * An emote's sound (its {@code "sound"}, music for a dance): played for you only, not from a place in the world, on
 * the Players volume slider, from the emote's start. It fades out over a moment when the emote stops early. A sound no
 * resource pack has stays silent, without a word in the log on each play.
 */
final class EmoteSound extends AbstractTickableSoundInstance {
    private static final float FADE_SECONDS = 0.4f;
    private static String missingLogged;
    private final float full;
    private long fadeFrom;

    private EmoteSound(SoundEvent event, float volume) {
        super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.full = volume;
        this.volume = volume;
        this.relative = true;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.looping = false;
        this.x = 0.0;
        this.y = 0.0;
        this.z = 0.0;
    }

    /** Whether a resource pack (or the mod) has {@code emote}'s sound. */
    static boolean available(Emote emote) {
        try {
            return emote.sound() != null && Minecraft.getInstance().getSoundManager().getSoundEvent(id(emote)) != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    //? if >=1.21.11 {
    private static Identifier id(Emote emote) {
        return Identifier.parse(emote.sound());
    }
    //?} else {
    /*private static ResourceLocation id(Emote emote) {
        return ResourceLocation.parse(emote.sound());
    }
    *///?}

    /** Starts {@code emote}'s sound; null when it has none or no pack has it. */
    static EmoteSound start(Emote emote) {
        if (emote.sound() == null || emote.soundVolume() <= 0f) {
            return null;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (!available(emote)) {
                if (!emote.sound().equals(missingLogged)) {
                    missingLogged = emote.sound();
                    CosmeticsMod.LOGGER.info("Emote '{}': no resource pack has its sound {}, so it plays without",
                            emote.id(), emote.sound());
                }
                return null;
            }
            EmoteSound s = new EmoteSound(SoundEvent.createVariableRangeEvent(id(emote)), emote.soundVolume());
            mc.getSoundManager().play(s);
            return s;
        } catch (RuntimeException e) {
            CosmeticsMod.LOGGER.debug("Emote '{}': sound {}: {}", emote.id(), emote.sound(), e.toString());
            return null;
        }
    }

    /** Fades it out (the emote was stopped). */
    void fadeOut() {
        if (fadeFrom == 0L) {
            fadeFrom = System.nanoTime();
        }
    }

    /** Stops it at once (another emote took over). */
    void end() {
        Minecraft.getInstance().getSoundManager().stop(this);
    }

    @Override
    public void tick() {
        if (fadeFrom == 0L) {
            return;
        }
        float k = 1f - (System.nanoTime() - fadeFrom) / 1.0e9f / FADE_SECONDS;
        if (k <= 0f) {
            stop();
        } else {
            volume = full * k;
        }
    }
}
