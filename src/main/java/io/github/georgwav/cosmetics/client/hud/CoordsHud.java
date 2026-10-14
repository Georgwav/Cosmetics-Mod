package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Where you are: block coordinates, which way you face, and the biome. */
public final class CoordsHud extends TextHud {
    private final Setting.Toggle direction = add(new Setting.Toggle("direction", true));
    private final Setting.Toggle biome = add(new Setting.Toggle("biome", false));

    public CoordsHud() {
        super("coords", "pin", 0f, 0f, 4f, 40f);
    }

    @Override
    protected List<String> lines(Minecraft mc) {
        if (mc.player == null || mc.level == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        lines.add("XYZ " + mc.player.getBlockX() + " " + mc.player.getBlockY() + " " + mc.player.getBlockZ());
        if (direction.get()) {
            Direction facing = mc.player.getDirection();
            String axis = switch (facing) {
                case NORTH -> "-Z";
                case SOUTH -> "+Z";
                case WEST -> "-X";
                case EAST -> "+X";
                default -> "";
            };
            lines.add(Component.translatable("cosmeticsmod.module.coords.facing." + facing.getSerializedName()).getString()
                    + " (" + axis + ")");
        }
        if (biome.get()) {
            lines.add(biomeName(mc.level.getBiome(mc.player.blockPosition()).getRegisteredName()));
        }
        return lines;
    }

    /** "minecraft:dark_forest" as "Dark Forest". */
    private static String biomeName(String id) {
        String path = id.substring(id.indexOf(':') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : path.split("[_/]")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }
}
