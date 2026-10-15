package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.resources.sounds.SoundInstance;

/**
 * Sounds: volumes for kinds of sound the game's own sliders don't separate (by the sound's name): explosions,
 * weather, hits, footsteps, blocks breaking and placed, mobs' idle sounds, and the click of menus.
 */
public final class SoundMixer extends Module {
    private final Setting.Slider explosions = add(slider("explosions"));
    private final Setting.Slider weather = add(slider("weather"));
    private final Setting.Slider hits = add(slider("hits"));
    private final Setting.Slider steps = add(slider("steps"));
    private final Setting.Slider blocks = add(slider("blocks"));
    private final Setting.Slider ambient = add(slider("mob_ambient"));
    private final Setting.Slider clicks = add(slider("clicks"));

    public SoundMixer() {
        super("sounds", Category.TOOLS, "speaker");
    }

    private static Setting.Slider slider(String id) {
        return new Setting.Slider(id, 100, 0, 100, 5, v -> Math.round(v) + "%");
    }

    /** A sound's volume after the module's slider for its kind. */
    public static float volume(SoundInstance sound, float volume) {
        SoundMixer m = Modules.SOUNDS;
        if (!m.enabled() || sound == null) {
            return volume;
        }
        //? if >=1.21.11 {
        String path = sound.getIdentifier().getPath();
        //?} else {
        /*String path = sound.getLocation().getPath();
        *///?}
        Setting.Slider s = path.contains("explode") || path.contains("explosion") ? m.explosions
                : path.startsWith("weather.") ? m.weather
                : path.startsWith("entity.player.attack") || path.endsWith(".hurt") ? m.hits
                : path.endsWith(".step") ? m.steps
                : path.startsWith("block.") && (path.endsWith(".break") || path.endsWith(".place") || path.endsWith(".hit")) ? m.blocks
                : path.startsWith("entity.") && path.endsWith(".ambient") ? m.ambient
                : path.startsWith("ui.button") ? m.clicks : null;
        return s == null ? volume : volume * (float) (s.get() / 100.0);
    }
}
