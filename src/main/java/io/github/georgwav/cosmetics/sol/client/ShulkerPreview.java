package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.client.modules.InventoryTweaks;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.List;
import java.util.Optional;

/**
 * Inventory's shulker box previews: a box's tooltip shows its 27 slots (9 by 3) with the items in them, in a glass
 * grid tinted by the box's colour. The tooltip image comes from the item (ItemStack.getTooltipImage) and is drawn by a
 * tooltip component the game asks Fabric for.
 */
public final class ShulkerPreview {
    /** The contents (27 slots) and the box's colour (ARGB, 0 for the plain box). */
    public record Contents(List<ItemStack> items, int tint) implements TooltipComponent {
    }

    private ShulkerPreview() {
    }

    /** The preview for a shulker box with something in it, when Inventory has previews on. */
    public static Optional<TooltipComponent> of(ItemStack stack) {
        if (!InventoryTweaks.shulkerPreviews() || !(stack.getItem() instanceof BlockItem block)
                || !(block.getBlock() instanceof ShulkerBoxBlock box)) {
            return Optional.empty();
        }
        var contents = stack.get(DataComponents.CONTAINER);
        if (contents == null) {
            return Optional.empty();
        }
        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);
        if (items.stream().allMatch(ItemStack::isEmpty)) {
            return Optional.empty();
        }
        var colour = box.getColor();
        int tint = colour == null ? 0 : 0xFF000000 | colour.getTextureDiffuseColor();
        return Optional.of(new Contents(List.copyOf(items), tint));
    }

    public static void register() {
        //? if >=26.1 {
        /*net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback.EVENT.register(c -> c instanceof Contents s ? new Client(s) : null);
        *///?} else {
        net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback.EVENT.register(c -> c instanceof Contents s ? new Client(s) : null);
        //?}
    }

    /** The grid in the tooltip. */
    static final class Client implements ClientTooltipComponent {
        private static final int SLOT = 18, PAD = 3;
        private final Contents contents;

        Client(Contents contents) {
            this.contents = contents;
        }

        //? if >=1.21.2 {
        @Override
        public int getHeight(Font font) {
            return 3 * SLOT + PAD * 2 + 2;
        }
        //?} else {
        /*@Override
        public int getHeight() {
            return 3 * SLOT + PAD * 2 + 2;
        }
        *///?}

        @Override
        public int getWidth(Font font) {
            return 9 * SLOT + PAD * 2;
        }

        //? if >=26.1 {
        /*@Override
        public void extractImage(Font font, int x, int y, int w, int h, net.minecraft.client.gui.GuiGraphicsExtractor graphics) {
            draw(new Canvas(graphics), font, x, y);
        }
        *///?} elif >=1.21.2 {
        @Override
        public void renderImage(Font font, int x, int y, int w, int h, net.minecraft.client.gui.GuiGraphics graphics) {
            draw(new Canvas(graphics), font, x, y);
        }
        //?} else {
        /*@Override
        public void renderImage(Font font, int x, int y, net.minecraft.client.gui.GuiGraphics graphics) {
            draw(new Canvas(graphics), font, x, y);
        }
        *///?}

        private void draw(Canvas c, Font font, int x, int y) {
            Tokens tk = ThemeState.tokens();
            int edge = contents.tint() != 0 ? Colors.withAlpha(contents.tint(), 0.55f) : Colors.withAlpha(tk.rim, 0.4f);
            int w = 9 * SLOT + PAD * 2, h = 3 * SLOT + PAD * 2;
            c.fill(x, y, x + w, y + h, 0x40000000);
            c.fill(x, y, x + w, y + 1, edge);
            c.fill(x, y + h - 1, x + w, y + h, edge);
            c.fill(x, y, x + 1, y + h, edge);
            c.fill(x + w - 1, y, x + w, y + h, edge);
            for (int i = 0; i < 27; i++) {
                int sx = x + PAD + (i % 9) * SLOT, sy = y + PAD + (i / 9) * SLOT;
                c.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x26FFFFFF);
                ItemStack stack = contents.items().get(i);
                if (!stack.isEmpty()) {
                    c.item(stack, sx + 1, sy + 1);
                    c.itemDecorations(font, stack, sx + 1, sy + 1);
                }
            }
        }
    }
}
