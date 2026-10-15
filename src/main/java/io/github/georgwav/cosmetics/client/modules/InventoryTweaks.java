package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * Inventory: how many uses each tool and piece of armour has left, written small over its slot wherever the game
 * draws items (inventories, the hotbar), in white, amber when low and red when nearly broken; shulker boxes'
 * contents in their tooltips; and a key that brings the inventory's rows down through the hotbar.
 */
public final class InventoryTweaks extends Module {
    private final Setting.Toggle durability = add(new Setting.Toggle("durability", true));
    private final Setting.Choice as = add(new Setting.Choice("as", "number", java.util.List.of("number", "percent")));
    private final Setting.Toggle shulkers = add(new Setting.Toggle("shulker_previews", true));

    public InventoryTweaks() {
        super("inventory", Category.INVENTORY, "chest");
        addKey("hotbar_rows");
    }

    @Override
    protected void tick(Minecraft mc) {
        var key = key("hotbar_rows");
        while (key != null && key.consumeClick()) {
            rotateRows(mc);
        }
    }

    /**
     * Brings the inventory's top row down to the hotbar, each row up one and the hotbar to the bottom row, as the
     * game's own slot swaps (the number keys over a slot), so it works on any server. Only with no container open:
     * the server takes clicks on the inventory only while it's the open menu. Returns whether it swapped.
     */
    public static boolean rotateRows(Minecraft mc) {
        var player = mc.player;
        if (player == null || mc.gameMode == null || player.isSpectator() || player.containerMenu != player.inventoryMenu) {
            return false;
        }
        int id = player.inventoryMenu.containerId;
        // Hotbar with the bottom row, then the middle, then the top: H, R1, R2, R3 becomes R1, R2, R3, H.
        for (int row = 2; row >= 0; row--) {
            for (int col = 0; col < 9; col++) {
                //? if >=26.1 {
                /*mc.gameMode.handleContainerInput(id, 9 + row * 9 + col, col, net.minecraft.world.inventory.ContainerInput.SWAP, player);
                *///?} else {
                mc.gameMode.handleInventoryMouseClick(id, 9 + row * 9 + col, col, net.minecraft.world.inventory.ClickType.SWAP, player);
                //?}
            }
        }
        return true;
    }

    /** Whether shulker boxes show their contents in their tooltips. */
    public static boolean shulkerPreviews() {
        InventoryTweaks m = io.github.georgwav.cosmetics.client.Modules.INVENTORY;
        return m.enabled() && m.shulkers.get();
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
