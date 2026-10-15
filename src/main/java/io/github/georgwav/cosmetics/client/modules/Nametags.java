package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Nametags: your own name tag over your head in third person, and other players' tags hidden if you'd rather. */
public final class Nametags extends Module {
    private final Setting.Toggle own = add(new Setting.Toggle("own", true));
    private final Setting.Toggle others = add(new Setting.Toggle("others", true));

    public Nametags() {
        super("nametags", Category.VIEW, "tag");
    }

    /** Whether an entity's name tag shows, given the game's own answer. */
    public static boolean shows(Entity entity, boolean game) {
        Nametags n = Modules.NAMETAGS;
        if (!n.enabled()) {
            return game;
        }
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player) {
            return n.own.get() && !mc.options.getCameraType().isFirstPerson() && !entity.isInvisible() || game;
        }
        if (entity instanceof Player && !n.others.get()) {
            return false;
        }
        return game;
    }
}
