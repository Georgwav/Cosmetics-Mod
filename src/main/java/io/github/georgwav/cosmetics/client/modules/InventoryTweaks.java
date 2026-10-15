package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory: how many uses each tool and piece of armour has left, written small over its slot wherever the game
 * draws items (inventories, the hotbar), in white, amber when low and red when nearly broken.
 */
public final class InventoryTweaks extends Module {
    private final Setting.Toggle durability = add(new Setting.Toggle("durability", true));
    private final Setting.Choice as = add(new Setting.Choice("as", "number", java.util.List.of("number", "percent")));

    public InventoryTweaks() {
        super("inventory", Category.INVENTORY, "chest");
    }

    /** After the game's decorations of an item at (x, y): its uses left, at half size in the top left. */
    public static void decorate(Canvas g, ItemStack stack, int x, int y) {
        InventoryTweaks m = Modules.INVENTORY;
        if (!m.enabled() || !m.durability.get() || stack.isEmpty() || !stack.isDamageableItem() || !stack.isDamaged()) {
            return;
        }
        int left = stack.getMaxDamage() - stack.getDamageValue();
        float k = (float) left / stack.getMaxDamage();
        String text = m.as.get().equals("percent") ? Math.round(k * 100f) + "%" : Integer.toString(left);
        int color = k < 0.1f ? 0xFFFF5555 : k < 0.25f ? 0xFFFFC35C : 0xFFFFFFFF;
        var font = Minecraft.getInstance().font;
        g.push();
        g.lift(200f);
        g.translate(x + 1, y + 1);
        g.scale(0.5f, 0.5f);
        g.text(font, text, 0, 0, color, true);
        g.pop();
    }
}
