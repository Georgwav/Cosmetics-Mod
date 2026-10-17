package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Nametags: your own name tag over your head in third person, other players' tags hidden if you'd rather, their size,
 * backing and text shadow, and players' health after their name.
 */
public final class Nametags extends Module {
    private final Setting.Toggle own = add(new Setting.Toggle("own", true));
    private final Setting.Toggle others = add(new Setting.Toggle("others", true));
    private final Setting.Slider size = add(new Setting.Slider("size", 100, 50, 200, 5, v -> Math.round(v) + "%"));
    /** The backing behind the text: the game's (from the accessibility option), or this dark. */
    private final Setting.Choice backing = add(new Setting.Choice("backing", "game", java.util.List.of("game", "none", "light", "dark")));
    /** The name drawn with the game's text shadow. */
    private final Setting.Toggle shadow = add(new Setting.Toggle("shadow", false));
    /** Players' health (with absorption) after their name, green to red as it drops. */
    private final Setting.Toggle health = add(new Setting.Toggle("health", false));

    public Nametags() {
        super("nametags", Category.VIEW, "tag");
    }

    /** Name tags' size (1 = the game's). */
    public static float scale() {
        Nametags n = io.github.georgwav.cosmetics.client.Modules.NAMETAGS;
        return n.enabled() ? (float) (n.size.get() / 100.0) : 1f;
    }

    /** Whether name tags' text has a shadow. */
    public static boolean shadow() {
        Nametags n = io.github.georgwav.cosmetics.client.Modules.NAMETAGS;
        return n.enabled() && n.shadow.get();
    }

    /** The backing's opacity, given the game's. */
    public static float backing(float game) {
        Nametags n = io.github.georgwav.cosmetics.client.Modules.NAMETAGS;
        if (!n.enabled()) {
            return game;
        }
        return switch (n.backing.get()) {
            case "none" -> 0f;
            case "light" -> 0.15f;
            case "dark" -> 0.55f;
            default -> game;
        };
    }

    /** For the smoke test: name tags given a health. */
    public static int healthShown;

    /** The name tag's text: a player's with their health after it when that's on, anything else as it is. */
    public static Component withHealth(Entity entity, Component name) {
        Nametags n = Modules.NAMETAGS;
        if (name == null || !n.enabled() || !n.health.get() || !(entity instanceof Player p)) {
            return name;
        }
        float hp = p.getHealth() + p.getAbsorptionAmount();
        float share = hp / Math.max(1f, p.getMaxHealth());
        int colour = share > 0.6f ? 0x55FF55 : share > 0.3f ? 0xFFDD55 : 0xFF5555;
        healthShown++;
        String value = hp == Math.floor(hp) ? Integer.toString((int) hp) : String.format(java.util.Locale.ROOT, "%.1f", hp);
        return name.copy().append(Component.literal(" \u2764 " + value).withStyle(s -> s.withColor(colour)));
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
