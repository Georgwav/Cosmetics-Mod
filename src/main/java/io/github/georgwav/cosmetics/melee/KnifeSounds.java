package io.github.georgwav.cosmetics.melee;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.HashMap;
import java.util.Map;

/**
 * A knife's sounds, from its mounted pack, through the game's sound engine: in the Players category at the knives'
 * volume, at normal pitch, heard only by you (the server's hit sounds stay as they are). Each sound is kept with the
 * animation that started it, so a cut-off animation (an inspect cancelled by an attack) stops its sounds too.
 */
final class KnifeSounds implements KnifeBehaviour.Sounds {
    private static final int KEPT = 16;
    private final String namespace;
    private final SoundInstance[] playing = new SoundInstance[KEPT];
    private final long[] owners = new long[KEPT];
    private final RandomSource random = RandomSource.create();
    //? if >=1.21.11 {
    private final Map<String, Identifier> ids = new HashMap<>();
    //?} else {
    /*private final Map<String, ResourceLocation> ids = new HashMap<>();
    *///?}
    private int next;

    KnifeSounds(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void play(String key, long owner) {
        float volume = PunchyModule.knifeVolume();
        if (volume <= 0f) {
            return;
        }
        var id = ids.computeIfAbsent(key, k -> {
            String full = k.indexOf(':') < 0 ? namespace + ":" + k : k;
            //? if >=1.21.11 {
            return Identifier.tryParse(full);
            //?} else {
            /*return ResourceLocation.tryParse(full);
            *///?}
        });
        if (id == null) {
            return;
        }
        try {
            SoundInstance sound = new SimpleSoundInstance(id, SoundSource.PLAYERS, volume, 1f, random, false, 0,
                    SoundInstance.Attenuation.NONE, 0.0, 0.0, 0.0, true);
            Minecraft.getInstance().getSoundManager().play(sound);
            int slot = next++ % KEPT;
            playing[slot] = sound;
            owners[slot] = owner;
        } catch (RuntimeException e) {
            CosmeticsMod.LOGGER.debug("Knife sound {}: {}", key, e.toString());
        }
    }

    @Override
    public void stop(long owner) {
        for (int i = 0; i < KEPT; i++) {
            if (owners[i] == owner && playing[i] != null) {
                Minecraft.getInstance().getSoundManager().stop(playing[i]);
                playing[i] = null;
            }
        }
    }
}
