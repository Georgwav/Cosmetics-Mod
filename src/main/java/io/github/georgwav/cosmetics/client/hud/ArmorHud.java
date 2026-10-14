package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/** What you wear and hold, each with the durability it has left (or how many). */
public final class ArmorHud extends HudModule {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int ROW = 18;
    private final Setting.Toggle hands = add(new Setting.Toggle("hands", true));
    private final Setting.Choice value = add(new Setting.Choice("value", "number", List.of("number", "percent")));

    public ArmorHud() {
        super("armor", "shield", 1f, 1f, -4f, -4f);
    }

    private List<ItemStack> items(Minecraft mc) {
        List<ItemStack> items = new ArrayList<>();
        if (mc.player != null) {
            for (EquipmentSlot slot : ARMOR) {
                items.add(mc.player.getItemBySlot(slot));
            }
            if (hands.get()) {
                items.add(mc.player.getItemBySlot(EquipmentSlot.MAINHAND));
                items.add(mc.player.getItemBySlot(EquipmentSlot.OFFHAND));
            }
            items.removeIf(ItemStack::isEmpty);
        }
        if (items.isEmpty() && preview()) {
            items = List.of(new ItemStack(Items.DIAMOND_HELMET), new ItemStack(Items.DIAMOND_CHESTPLATE),
                    new ItemStack(Items.DIAMOND_LEGGINGS), new ItemStack(Items.DIAMOND_BOOTS));
        }
        return items;
    }

    /** What a row says: durability left, or how many when it stacks. */
    private String text(ItemStack stack) {
        if (stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            return value.get().equals("percent") ? Math.round(100f * left / stack.getMaxDamage()) + "%" : String.valueOf(left);
        }
        return stack.getCount() > 1 ? String.valueOf(stack.getCount()) : "";
    }

    /** Green when whole, through yellow, to red when nearly broken. */
    private static int color(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return 0xFFFFFFFF;
        }
        float left = 1f - (float) stack.getDamageValue() / stack.getMaxDamage();
        int r = (int) (255 * Math.min(1f, 2f * (1f - left)));
        int gr = (int) (255 * Math.min(1f, 2f * left));
        return 0xFF000000 | r << 16 | gr << 8 | 0x40;
    }

    @Override
    public int width(Minecraft mc) {
        List<ItemStack> items = items(mc);
        if (items.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (ItemStack stack : items) {
            w = Math.max(w, mc.font.width(text(stack)));
        }
        return 20 + w + (w > 0 ? 3 : 0);
    }

    @Override
    public int height(Minecraft mc) {
        int n = items(mc).size();
        return n == 0 ? 0 : n * ROW + 2;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int y = 1;
        for (ItemStack stack : items(mc)) {
            g.item(stack, 2, y + 1);
            String text = text(stack);
            if (!text.isEmpty()) {
                g.text(mc.font, text, 20, y + 5, color(stack), false);
            }
            y += ROW;
        }
    }
}
