package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Armour & Items: what you wear and hold with the durability it has left, how many of what you hold you carry (arrows
 * for a bow), the cooldowns running in your hotbar, and the totems that popped this session. Sol UI draws one bar
 * per piece going from the accent to amber to red, the last piece pulsing when it's about to break; the plain look
 * writes numbers in green to red. A piece worn or held that falls under a tenth of its uses gets a warning (a toast
 * and a soft bell), once until it's mended or swapped.
 */
public final class ArmourItemsHud extends HudModule {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final int ROW = 18;
    private final Setting.Toggle hands = add(new Setting.Toggle("hands", true).group("shows"));
    private final Setting.Choice value = add(new Setting.Choice("value", "number", List.of("number", "percent")).group("durability"));
    private final Setting.Toggle counter = add(new Setting.Toggle("counter", true).group("shows"));
    private final Setting.Toggle cooldowns = add(new Setting.Toggle("cooldowns", true).group("shows"));
    private final Setting.Toggle totems = add(new Setting.Toggle("totems", true).group("shows"));
    /** A toast and a bell when something worn or held is about to break. */
    private final Setting.Toggle warnings = add(new Setting.Toggle("durability_warnings", true).group("durability"));
    private static final EquipmentSlot[] WATCHED = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    /** The item each watched slot was last warned about (null: none). */
    private final net.minecraft.world.item.Item[] warned = new net.minecraft.world.item.Item[WATCHED.length];

    /** Totems that popped for you this session (counted from the game's entity event 35). */
    public static int totemPops;

    public ArmourItemsHud() {
        super("armour_items", "shield", 1f, 1f, -4f, -4f);
    }

    @Override
    protected void tick(Minecraft mc) {
        if (!warnings.get() || mc.player == null) {
            java.util.Arrays.fill(warned, null);
            return;
        }
        for (int i = 0; i < WATCHED.length; i++) {
            ItemStack stack = mc.player.getItemBySlot(WATCHED[i]);
            boolean low = stack.isDamageableItem() && stack.getMaxDamage() - stack.getDamageValue() < stack.getMaxDamage() * 0.1f;
            if (!low) {
                warned[i] = null;
            } else if (warned[i] != stack.getItem()) {
                warned[i] = stack.getItem();
                int left = stack.getMaxDamage() - stack.getDamageValue();
                io.github.georgwav.cosmetics.sol.ui.kit.Notifications.post(net.minecraft.network.chat.Component.translatable(
                        "cosmeticsmod.module.armour_items.warning", stack.getHoverName(), left).getString());
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BELL.value(), 0.7f, 0.6f));
            }
        }
    }

    /** One row: its item, its text, and how much is left (0 to 1), or -1 for none. */
    private record Row(ItemStack icon, String text, float left, boolean worn) {
    }

    private List<Row> rows(Minecraft mc) {
        List<Row> rows = new ArrayList<>();
        if (mc.player != null) {
            for (EquipmentSlot slot : ARMOR) {
                piece(rows, mc.player.getItemBySlot(slot), true);
            }
            ItemStack held = mc.player.getMainHandItem();
            if (hands.get()) {
                piece(rows, held, false);
                piece(rows, mc.player.getOffhandItem(), false);
            }
            if (counter.get() && !held.isEmpty()) {
                Inventory inv = mc.player.getInventory();
                if (held.is(Items.BOW) || held.is(Items.CROSSBOW)) {
                    int arrows = count(inv, Items.ARROW) + count(inv, Items.SPECTRAL_ARROW) + count(inv, Items.TIPPED_ARROW);
                    rows.add(new Row(new ItemStack(Items.ARROW), "×" + arrows, -1f, false));
                } else if (held.getMaxStackSize() > 1) {
                    int all = count(inv, held.getItem());
                    if (all > held.getCount()) {
                        rows.add(new Row(held, "×" + all, -1f, false));
                    }
                }
            }
            if (cooldowns.get()) {
                for (int i = 0; i < 9; i++) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack.isEmpty()) {
                        continue;
                    }
                    //? if >=1.21.2 {
                    float cd = mc.player.getCooldowns().getCooldownPercent(stack, 0f);
                    //?} else {
                    /*float cd = mc.player.getCooldowns().getCooldownPercent(stack.getItem(), 0f);
                    *///?}
                    if (cd > 0f) {
                        rows.add(new Row(stack, "", 1f - cd, false));
                    }
                }
            }
            if (totems.get() && totemPops > 0) {
                rows.add(new Row(new ItemStack(Items.TOTEM_OF_UNDYING), "×" + totemPops, -1f, false));
            }
        }
        if (rows.isEmpty() && preview()) {
            for (var item : new net.minecraft.world.item.Item[]{Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
                    Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS}) {
                ItemStack s = new ItemStack(item);
                rows.add(new Row(s, text(s), 1f, true));
            }
        }
        return rows;
    }

    private static int count(Inventory inv, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private void piece(List<Row> rows, ItemStack stack, boolean worn) {
        if (stack.isEmpty()) {
            return;
        }
        float left = stack.isDamageableItem() ? 1f - (float) stack.getDamageValue() / stack.getMaxDamage() : -1f;
        rows.add(new Row(stack, text(stack), left, worn));
    }

    /** What a row says: durability left, or how many when it stacks. */
    private String text(ItemStack stack) {
        if (stack.isDamageableItem()) {
            int left = stack.getMaxDamage() - stack.getDamageValue();
            return value.get().equals("percent") ? Math.round(100f * left / stack.getMaxDamage()) + "%" : String.valueOf(left);
        }
        return stack.getCount() > 1 ? String.valueOf(stack.getCount()) : "";
    }

    /** Green when whole, through yellow, to red when nearly broken (the plain look). */
    private static int color(float left) {
        if (left < 0f) {
            return 0xFFFFFFFF;
        }
        int r = (int) (255 * Math.min(1f, 2f * (1f - left)));
        int gr = (int) (255 * Math.min(1f, 2f * left));
        return 0xFF000000 | r << 16 | gr << 8 | 0x40;
    }

    // ---------------------------------------------------------------- the plain look

    @Override
    protected int plainWidth(Minecraft mc) {
        List<Row> rows = rows(mc);
        if (rows.isEmpty()) {
            return 0;
        }
        int w = 0;
        for (Row r : rows) {
            w = Math.max(w, mc.font.width(r.text()));
        }
        return 20 + w + (w > 0 ? 3 : 0);
    }

    @Override
    protected int plainHeight(Minecraft mc) {
        int n = rows(mc).size();
        return n == 0 ? 0 : n * ROW + 2;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int y = 1;
        for (Row r : rows(mc)) {
            g.item(r.icon(), 2, y + 1);
            if (!r.text().isEmpty()) {
                g.text(mc.font, r.text(), 20, y + 5, color(r.left()), false);
            }
            y += ROW;
        }
    }

    // ---------------------------------------------------------------- Sol UI

    private static final float PAD = 5f, S_ROW = 13f, ICON = 10f, BAR_W = 44f;

    @Override
    protected int solWidth(Minecraft mc) {
        List<Row> rows = rows(mc);
        if (rows.isEmpty()) {
            return 0;
        }
        float text = 0f;
        for (Row r : rows) {
            if (r.left() < 0f) {
                text = Math.max(text, HudKit.measure(SolFont.Family.PIXEL_BOLD, HudKit.SMALL, r.text(), scale()));
            }
        }
        return (int) Math.ceil(PAD * 2f + ICON + 4f + Math.max(BAR_W, text));
    }

    @Override
    protected int solHeight(Minecraft mc) {
        int n = rows(mc).size();
        return n == 0 ? 0 : (int) Math.ceil(PAD * 2f + n * S_ROW - 3f);
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        List<Row> rows = rows(mc);
        float w = solWidth(mc), h = solHeight(mc);
        HudKit.card(g, tk, 0f, 0f, w, h, HudKit.CARD_R);
        SolFont mono = g.font(SolFont.Family.PIXEL_BOLD, HudKit.SMALL);
        // The worn piece nearest to breaking pulses when it's under a tenth.
        int weakest = -1;
        float least = 0.1f;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            if (r.worn() && r.left() >= 0f && r.left() < least) {
                least = r.left();
                weakest = i;
            }
        }
        float y = PAD, tx = PAD + ICON + 4f, bw = w - tx - PAD;
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            float cy = y + ICON / 2f;
            if (r.left() >= 0f) {
                int col = HudKit.state(tk, r.left(), 0.3f, 0.12f);
                if (i == weakest) {
                    col = Colors.fade(col, HudKit.pulse());
                }
                if (!r.text().isEmpty()) {
                    g.textRight(mono, r.text(), tx + bw, cy - 2f, 0xD0FFFFFF);
                }
                HudKit.bar(g, tx, cy + 1.5f, bw, 2f, r.left(), col);
            } else {
                g.textMid(mono, r.text(), tx, cy, 0xFFFFFFFF);
            }
            y += S_ROW;
        }
        // The items, by the game, in GUI units at 10/16 size.
        g.layer();
        Canvas gc = g.game();
        gc.push();
        gc.scale(g.d.u, g.d.u);
        y = PAD;
        for (Row r : rows) {
            gc.push();
            gc.translate(PAD, y);
            gc.scale(ICON / 16f, ICON / 16f);
            gc.item(r.icon(), 0, 0);
            gc.pop();
            y += S_ROW;
        }
        gc.pop();
    }
}
