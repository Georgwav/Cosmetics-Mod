package io.github.georgwav.cosmetics.sol.hud;

import io.github.georgwav.cosmetics.sol.Feature;
import io.github.georgwav.cosmetics.sol.Guard;
import io.github.georgwav.cosmetics.sol.compat.AppleSkinBridge;
import io.github.georgwav.cosmetics.sol.config.SolConfig;
import io.github.georgwav.cosmetics.sol.theme.ThemeState;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Design;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.ui.kit.SolScreen;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import io.github.georgwav.cosmetics.ui.PictureTexture;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.Random;

/**
 * Sol UI's take on the game's own HUD, from the brief. Each part sits where the game puts it, so mods drawing beside
 * it still line up, and the HUD editor moves it.
 * - Hotbar: glass slots; the selected one lifts, rings in the accent and catches a passing sweep of light. Slot
 *   numbers, counts in mono, durability bars from the accent to amber to red, the offhand on its own glass.
 * - Hearts: pixel hearts in the accent with a soft glow. Poison, wither, frozen, absorption and hardcore keep their
 *   meaning. The game's heart logic is ported: the blink after damage, the regeneration wave, the low-health jitter
 *   and half hearts.
 * - Hunger: shanks in the glow, emptying as the game's do; hunger keeps its green. A bright rim on the shanks that
 *   still hold saturation, and the shanks a held food would fill blink. With AppleSkin, its exhaustion line is
 *   drawn under the shanks, and AppleSkin's own overlays are cancelled ({@link AppleSkinBridge}).
 * - Armour: pixel chestplates in light silver.
 * - XP: a glowing line from the glow to the accent, the level above it in the accent.
 * - Air and mount health: the same pixel icons in the theme's colours. The jump and locator bars stay the game's.
 * Each part can be switched back to the game's own (SolConfig.solUiOff), and Sol UI off brings all of it back.
 */
public final class SolHud {
    /** The parts Sol draws in place of the game's. */
    public enum Part {
        HOTBAR("hotbar"), HEALTH("health"), ARMOR("armor"), FOOD("food"), AIR("air"), MOUNT("mount"), XP("xp"), LEVEL("level"),
        // Restyled rather than redrawn (SolHudCards): the game still lays them out and writes their text.
        BOSS("boss"), SIDEBAR("sidebar"), TAB("tab"), ACTION_BAR("action_bar");

        public final String id;

        Part(String id) {
            this.id = id;
        }
    }

    /** How many times Sol drew each part (the test scenario checks every hook drew). */
    public static final int[] DRAWN = new int[Part.values().length];

    private static final Design D = new Design();
    private static final Gfx G = new Gfx(D);
    private static final Random RANDOM = new Random();

    // The game's own heart state (Gui's), kept the same way so the hearts blink and settle as the game's do.
    private static int tickCount;
    private static int lastHealth, displayHealth;
    private static long lastHealthTime, healthBlinkTime;
    private static int lastBubblePop;

    private SolHud() {
    }

    /** The game's clock for its HUD (Util.getMillis, which moved package in 1.21.11). */
    private static long millis() {
        return System.nanoTime() / 1_000_000L;
    }

    /** Once a client tick, as the game's HUD counts its ticks (not while paused). */
    public static void tick(Minecraft mc) {
        if (!mc.isPaused()) {
            tickCount++;
        }
    }

    /** Whether Sol draws this part now (Sol UI on, the part not switched back, a player to show). */
    public static boolean draws(Part part) {
        SolConfig cfg = SolConfig.get();
        if (!cfg.solUi || cfg.solUiOff.contains(part.id) || !Guard.on(Feature.SOL_UI)) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return false;
        }
        return switch (part) {
            // A spectator's hotbar is the game's spectator menu.
            case HOTBAR -> mc.gameMode.getPlayerMode() != GameType.SPECTATOR;
            case HEALTH, ARMOR, FOOD, AIR -> mc.gameMode.canHurtPlayer();
            case XP -> experienceBarShown(mc);
            default -> true;
        };
    }

    /**
     * Whether the game shows the experience bar now rather than the jump bar or (from 1.21.6) the locator bar; those
     * stay the game's. The game's own choice, ported (Gui.nextContextualInfoState).
     */
    private static boolean experienceBarShown(Minecraft mc) {
        boolean xp = mc.gameMode.hasExperience();
        boolean jump = mc.player.jumpableVehicle() != null;
        //? if >=1.21.6 {
        if (mc.player.connection.getWaypointManager().hasWaypoints()) {
            boolean jumping = mc.player.getJumpRidingScale() > 0f
                    || (jump && mc.player.jumpableVehicle().getJumpCooldown() > 0);
            if (jump && jumping) {
                return false;
            }
            return xp && mc.player.experienceDisplayStartTick + 100 > mc.player.tickCount;
        }
        //?}
        return !jump && xp;
    }

    /** Draws a part, guarded: a failure switches Sol UI off and the game's own HUD comes back. */
    public static void draw(Part part, Canvas c, float partialTick) {
        DRAWN[part.ordinal()]++;
        Guard.run(Feature.SOL_UI, () -> {
            Minecraft mc = Minecraft.getInstance();
            Player player = mc.getCameraEntity() instanceof Player p ? p : null;
            if (player == null) {
                return;
            }
            Tokens tk = ThemeState.tokens();
            int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
            switch (part) {
                case HOTBAR -> hotbar(c, mc, player, tk, w, h, partialTick);
                case HEALTH -> hearts(c, mc, player, tk, w, h);
                case ARMOR -> armor(c, player, tk, w, h);
                case FOOD -> food(c, mc, player, tk, w, h);
                case AIR -> air(c, mc, player, tk, w, h);
                case MOUNT -> mount(c, player, tk, w, h);
                case XP -> experience(c, player, tk, w, h);
                case LEVEL -> level(c, mc, player, tk, w, h);
                default -> {
                }
            }
        });
    }

    // ------------------------------------------------------------------ the kit in GUI pixels

    /** The kit with design units = the game's GUI units, so Sol's glass lines up with the game's HUD. */
    static Gfx begin(Canvas c) {
        Minecraft mc = Minecraft.getInstance();
        D.gui(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), mc.getWindow().getGuiScale());
        c.push();
        c.scale(1f / D.gs, 1f / D.gs);
        G.begin(c);
        return G;
    }

    static void end(Canvas c) {
        G.end();
        c.pop();
    }

    /** Text with a soft dark shadow, so it reads on any sky. */
    static void shadowed(Gfx g, SolFont f, String s, float x, float cy, int argb, int align) {
        float px = 1f / g.d.u * Math.max(1f, g.d.gs * 0.5f);
        draw(g, f, s, x + px, cy + px, 0xB0000000, align);
        draw(g, f, s, x, cy, argb, align);
    }

    private static void draw(Gfx g, SolFont f, String s, float x, float cy, int argb, int align) {
        if (align < 0) {
            g.textMid(f, s, x, cy, argb);
        } else if (align == 0) {
            g.textCentre(f, s, x, cy, argb);
        } else {
            g.textRight(f, s, x, cy, argb);
        }
    }

    // ------------------------------------------------------------------ hotbar

    private static void hotbar(Canvas c, Minecraft mc, Player player, Tokens tk, int w, int h, float pt) {
        int cx = w / 2;
        ItemStack offhand = player.getOffhandItem();
        HumanoidArm offArm = player.getMainArm().getOpposite();
        //? if >=1.21.5 {
        int selected = player.getInventory().getSelectedSlot();
        //?} else {
        /*int selected = player.getInventory().selected;
        *///?}
        float t = (float) (millis() % 1_000_000L) / 1000f;
        Gfx g = begin(c);
        // The glass behind the nine slots, where the game's hotbar frame is.
        glass(g, tk, cx - 91, h - 22, 182, 22, 5f);
        if (!offhand.isEmpty()) {
            glass(g, tk, offArm == HumanoidArm.LEFT ? cx - 91 - 29 : cx + 91 + 7, h - 22, 22, 22, 5f);
        }
        for (int m = 0; m < 9; m++) {
            float sx = cx - 90 + m * 20 + 1, sy = h - 20;
            boolean sel = m == selected;
            if (sel) {
                sy -= 2f;
                g.glow(sx, sy, 18f, 18f, 3.5f, 6f, Colors.withAlpha(tk.glow, 0.4f));
            }
            g.round(sx, sy, 18f, 18f, 3.5f, sel ? Colors.withAlpha(tk.accent, 0.18f) : 0x1C000000);
            g.stroke(sx, sy, 18f, 18f, 3.5f, sel ? 1.2f / g.d.u * g.d.gs : 1f / g.d.u, sel ? tk.accent : 0x1AFFFFFF);
            if (sel && !SolScreen.calm()) {
                // A light sweeping across the selected slot every 2.8 s.
                float k = (t % 2.8f) / 2.8f;
                float bx = sx - 10f + k * 38f;
                g.clip(sx, sy, sx + 18f, sy + 18f);
                g.glowSprite(bx, sy + 9f, 5f, 14f, 0x40FFFFFF);
                g.endClip();
            }
        }
        // The items, drawn by the game in GUI units over the glass.
        Canvas gc = g.game();
        gc.push();
        gc.scale(g.d.gs, g.d.gs);
        for (int m = 0; m < 9; m++) {
            int ix = cx - 90 + m * 20 + 2, iy = h - 19 - (m == selected ? 2 : 0);
            item(gc, player.getInventory().getItem(m), ix, iy, pt);
        }
        int oy = h - 19, ox = offArm == HumanoidArm.LEFT ? cx - 91 - 26 : cx + 91 + 10;
        if (!offhand.isEmpty()) {
            item(gc, offhand, ox, oy, pt);
        }
        gc.pop();
        g.layer();
        // Over the items: slot numbers, counts and durability, and cooldowns (raised over them as the game's are).
        c.push();
        c.lift(200f);
        SolFont num = g.font(SolFont.Family.MONO, 5f), cnt = g.font(SolFont.Family.SEMIBOLD, 6.5f);
        for (int m = 0; m < 9; m++) {
            float sx = cx - 90 + m * 20 + 1, sy = h - 20 - (m == selected ? 2 : 0);
            g.textMid(num, Integer.toString(m + 1), sx + 1.8f, sy + 3.2f, m == selected ? Colors.withAlpha(tk.accent, 0.9f) : 0x66FFFFFF);
            decorations(g, player, player.getInventory().getItem(m), sx, sy, tk, cnt, pt);
        }
        if (!offhand.isEmpty()) {
            decorations(g, player, offhand, ox - 1, oy - 1, tk, cnt, pt);
        }
        // The attack indicator, when it's set to the hotbar: a small glass meter filling in the accent.
        if (mc.options.attackIndicator().get() == AttackIndicatorStatus.HOTBAR) {
            float f = player.getAttackStrengthScale(0f);
            if (f < 1f) {
                float ax = offArm == HumanoidArm.RIGHT ? cx - 91 - 22 : cx + 91 + 6, ay = h - 20;
                glass(g, tk, ax, ay, 18, 18, 4f);
                float fill = 16f * f;
                g.round(ax + 1, ay + 17 - fill, 16f, fill, 3f, Colors.withAlpha(tk.accent, 0.8f));
            }
        }
        G.end();
        c.pop();
        c.pop();
    }

    static void glass(Gfx g, Tokens tk, float x, float y, float w, float h, float r) {
        // A touch darker than the menus' glass: the HUD sits over bright sand and snow too.
        g.round(x, y, w, h, r, Colors.withAlpha(tk.panel, Math.max(0.62f, tk.glass)));
        g.stroke(x, y, w, h, r, 1f / g.d.u, Colors.withAlpha(tk.rim, 0.5f));
        g.highlight(x, y, w, h, r, 0x24FFFFFF);
    }

    /** An item as the game's hotbar draws it, with its pop when picked up. */
    private static void item(Canvas c, ItemStack stack, int x, int y, float pt) {
        if (stack.isEmpty()) {
            return;
        }
        float pop = stack.getPopTime() - pt;
        if (pop > 0f) {
            float k = 1f + pop / 5f;
            c.push();
            c.translate(x + 8, y + 12);
            c.scale(1f / k, (k + 1f) / 2f);
            c.translate(-(x + 8), -(y + 12));
            c.item(stack, x, y);
            c.pop();
        } else {
            c.item(stack, x, y);
        }
    }

    /** Count in the corner, durability from the accent to amber to red, the cooldown shading from the top. */
    private static void decorations(Gfx g, Player player, ItemStack stack, float sx, float sy, Tokens tk, SolFont cnt, float pt) {
        if (stack.isEmpty()) {
            return;
        }
        if (stack.isBarVisible()) {
            float k = stack.getBarWidth() / 13f;
            int col = k > 0.5f ? tk.accent : k > 0.25f ? Colors.mix(Tokens.AMBER, tk.accent, (k - 0.25f) / 0.25f)
                    : Colors.mix(Tokens.RED, Tokens.AMBER, k / 0.25f);
            g.round(sx + 2.5f, sy + 14.5f, 13f, 1.6f, 0.8f, 0xA0000000);
            g.round(sx + 2.5f, sy + 14.5f, 13f * k, 1.6f, 0.8f, col);
        }
        //? if >=1.21.2 {
        float cd = player.getCooldowns().getCooldownPercent(stack, pt);
        //?} else {
        /*float cd = player.getCooldowns().getCooldownPercent(stack.getItem(), pt);
        *///?}
        if (cd > 0f) {
            g.round(sx + 1f, sy + 1f + 16f * (1f - cd), 16f, 16f * cd, 2.5f, 0x80FFFFFF);
        }
        if (stack.getCount() > 1) {
            shadowed(g, cnt, Integer.toString(stack.getCount()), sx + 17f, sy + 14f, 0xFFFFFFFF, 1);
        }
    }

    // ------------------------------------------------------------------ hearts, armour, hunger, air (the game's layout)

    /** The game's health layout: {left, bottom row y, heart rows, row height, health, max}. */
    private record Layout(int left, int right, int y, int rows, int rowHeight, int health, float max, int absorb) {
    }

    private static Layout layout(Player player, int w, int h) {
        int health = Mth.ceil(player.getHealth());
        float max = Math.max((float) player.getAttributeValue(Attributes.MAX_HEALTH), Math.max(displayHealth, health));
        int absorb = Mth.ceil(player.getAbsorptionAmount());
        int rows = Mth.ceil((max + absorb) / 2f / 10f);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        return new Layout(w / 2 - 91, w / 2 + 91, h - 39, rows, rowHeight, health, max, absorb);
    }

    private static void hearts(Canvas c, Minecraft mc, Player player, Tokens tk, int w, int h) {
        // The game's blink and settling of the shown health (Gui.renderPlayerHealth).
        int i = Mth.ceil(player.getHealth());
        boolean blink = healthBlinkTime > tickCount && (healthBlinkTime - tickCount) / 3L % 2L == 1L;
        long now = millis();
        //? if >=26.3 {
        /*int cooldown = player.damageCooldownTime;
        *///?} else {
        int cooldown = player.invulnerableTime;
        //?}
        if (i < lastHealth && cooldown > 0) {
            lastHealthTime = now;
            healthBlinkTime = tickCount + 20;
        } else if (i > lastHealth && cooldown > 0) {
            lastHealthTime = now;
            healthBlinkTime = tickCount + 10;
        }
        if (now - lastHealthTime > 1000L) {
            displayHealth = i;
            lastHealthTime = now;
        }
        lastHealth = i;
        Layout l = layout(player, w, h);
        int regen = player.hasEffect(MobEffects.REGENERATION) ? tickCount % Mth.ceil(l.max() + 5f) : -1;
        RANDOM.setSeed(tickCount * 312871L);
        PictureTexture t = HudIcons.texture(tk);
        boolean hardcore = player.level().getLevelData().isHardcore();
        HudIconAtlas.Icon full, half;
        if (player.hasEffect(MobEffects.POISON)) {
            full = HudIconAtlas.Icon.POISON_FULL;
            half = HudIconAtlas.Icon.POISON_HALF;
        } else if (player.hasEffect(MobEffects.WITHER)) {
            full = HudIconAtlas.Icon.WITHER_FULL;
            half = HudIconAtlas.Icon.WITHER_HALF;
        } else if (player.isFullyFrozen()) {
            full = HudIconAtlas.Icon.FROZEN_FULL;
            half = HudIconAtlas.Icon.FROZEN_HALF;
        } else {
            full = HudIconAtlas.Icon.HEART_FULL;
            half = HudIconAtlas.Icon.HEART_HALF;
        }
        boolean withered = full == HudIconAtlas.Icon.WITHER_FULL;
        int hearts = Mth.ceil(l.max() / 2.0);
        int absorbHearts = Mth.ceil(l.absorb() / 2.0);
        int twice = hearts * 2;
        // A soft glow under the full hearts (the brief: "filled with the theme's accent and a soft glow").
        if (full == HudIconAtlas.Icon.HEART_FULL && i > 0) {
            Gfx g = begin(c);
            int lit = Math.min(10, Mth.ceil(i / 2.0));
            g.glowSprite(l.left() + lit * 4f, l.y() + 4.5f, lit * 4f + 6f, 7f, Colors.withAlpha(tk.glow, 0.22f));
            end(c);
        }
        for (int s = hearts + absorbHearts - 1; s >= 0; s--) {
            int row = s / 10, col = s % 10;
            int x = l.left() + col * 8;
            int y = l.y() - row * l.rowHeight();
            if (l.health() + l.absorb() <= 4) {
                y += RANDOM.nextInt(2);
            }
            if (s < hearts && s == regen) {
                y -= 2;
            }
            HudIcons.draw(c, t, blink ? HudIconAtlas.Icon.HEART_CONTAINER_BLINK : HudIconAtlas.Icon.HEART_CONTAINER, x, y);
            int x2 = s * 2;
            if (s >= hearts) {
                int a = x2 - twice;
                if (a < l.absorb()) {
                    boolean halfAbs = a + 1 == l.absorb();
                    HudIconAtlas.Icon icon = withered ? (halfAbs ? half : full)
                            : halfAbs ? HudIconAtlas.Icon.ABSORB_HALF : HudIconAtlas.Icon.ABSORB_FULL;
                    HudIcons.draw(c, t, icon, x, y);
                }
            }
            if (blink && x2 < displayHealth) {
                HudIcons.draw(c, t, x2 + 1 == displayHealth ? HudIconAtlas.Icon.BLINK_HALF : HudIconAtlas.Icon.BLINK_FULL, x, y);
            }
            if (x2 < l.health()) {
                HudIcons.draw(c, t, x2 + 1 == l.health() ? half : full, x, y);
            }
            if (hardcore) {
                HudIcons.draw(c, t, HudIconAtlas.Icon.HARDCORE_MARK, x, y);
            }
        }
    }

    private static void armor(Canvas c, Player player, Tokens tk, int w, int h) {
        int value = player.getArmorValue();
        if (value <= 0) {
            return;
        }
        Layout l = layout(player, w, h);
        int y = l.y() - (l.rows() - 1) * l.rowHeight() - 10;
        PictureTexture t = HudIcons.texture(tk);
        for (int o = 0; o < 10; o++) {
            int x = l.left() + o * 8;
            HudIconAtlas.Icon icon = o * 2 + 1 < value ? HudIconAtlas.Icon.ARMOR_FULL
                    : o * 2 + 1 == value ? HudIconAtlas.Icon.ARMOR_HALF : HudIconAtlas.Icon.ARMOR_EMPTY;
            HudIcons.draw(c, t, icon, x, y);
        }
    }

    private static void food(Canvas c, Minecraft mc, Player player, Tokens tk, int w, int h) {
        // The mount's hearts take the hunger bar's place while riding something with health.
        if (vehicleHearts(vehicleWithHealth(player)) > 0) {
            return;
        }
        Layout l = layout(player, w, h);
        var data = player.getFoodData();
        int level = data.getFoodLevel();
        float saturation = data.getSaturationLevel();
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        PictureTexture t = HudIcons.texture(tk);
        RANDOM.setSeed(tickCount * 312871L + 1L);
        // What the food in hand would bring, blinking (1.1 s).
        FoodProperties held = player.getMainHandItem().get(DataComponents.FOOD);
        int after = held == null ? level : Math.min(20, level + held.nutrition());
        boolean blinkOn = !SolScreen.calm() && (millis() % 1100L) < 650L;
        float sat = saturation;
        for (int i = 0; i < 10; i++) {
            int y = l.y();
            if (saturation <= 0f && tickCount % (level * 3 + 1) == 0) {
                y += RANDOM.nextInt(3) - 1;
            }
            int x = l.right() - i * 8 - 9;
            HudIcons.draw(c, t, hunger ? HudIconAtlas.Icon.HUNGER_EMPTY : HudIconAtlas.Icon.FOOD_EMPTY, x, y);
            int fullAt = i * 2 + 1;
            if (fullAt < level) {
                HudIcons.draw(c, t, hunger ? HudIconAtlas.Icon.HUNGER_FULL : HudIconAtlas.Icon.FOOD_FULL, x, y);
            } else if (fullAt == level) {
                HudIcons.draw(c, t, hunger ? HudIconAtlas.Icon.HUNGER_HALF : HudIconAtlas.Icon.FOOD_HALF, x, y);
            } else if (blinkOn && fullAt <= after) {
                HudIcons.draw(c, t, fullAt < after ? HudIconAtlas.Icon.FOOD_FULL : HudIconAtlas.Icon.FOOD_HALF, x, y);
            }
            // The shanks that still hold saturation get a bright rim.
            if (sat > 0f && fullAt <= level) {
                HudIcons.draw(c, t, HudIconAtlas.Icon.SATURATION_RIM, x, y);
                sat -= 2f;
            }
        }
        // AppleSkin's exhaustion, as a thin line under the shanks.
        float exhaustion = AppleSkinBridge.exhaustion(player);
        if (exhaustion > 0f) {
            Gfx g = begin(c);
            float k = Math.min(1f, exhaustion / 4f);
            g.round(l.right() - 80f, l.y() + 9.5f, 80f, 1f, 0.5f, 0x40000000);
            g.round(l.right() - 80f * k, l.y() + 9.5f, 80f * k, 1f, 0.5f, Colors.withAlpha(tk.glow, 0.85f));
            end(c);
        }
    }

    private static LivingEntity vehicleWithHealth(Player player) {
        Entity vehicle = player.getVehicle();
        return vehicle instanceof LivingEntity living ? living : null;
    }

    private static int vehicleHearts(LivingEntity vehicle) {
        if (vehicle == null || !vehicle.showVehicleHealth()) {
            return 0;
        }
        return Math.min(30, (int) (vehicle.getMaxHealth() + 0.5f) / 2);
    }

    private static void air(Canvas c, Minecraft mc, Player player, Tokens tk, int w, int h) {
        int max = player.getMaxAirSupply();
        int supply = Math.clamp(player.getAirSupply(), 0, max);
        boolean inWater = player.isEyeInFluid(FluidTags.WATER);
        if (!inWater && supply >= max) {
            return;
        }
        Layout l = layout(player, w, h);
        int vehicle = vehicleHearts(vehicleWithHealth(player));
        int y = l.y() - 10 - (vehicle == 0 ? 10 : 0);
        y -= (Mth.ceil(vehicle / 10.0) - 1) * 10;
        int full = Mth.ceil((supply - 2) * 10f / max);
        int now = Mth.ceil(supply * 10f / max);
        int emptyDelay = supply != 0 && inWater ? 1 : 0;
        int empty = 10 - Mth.ceil((supply + emptyDelay) * 10f / max);
        boolean popping = full != now;
        PictureTexture t = HudIcons.texture(tk);
        if (!inWater) {
            lastBubblePop = 0;
        }
        for (int q = 1; q <= 10; q++) {
            int x = l.right() - (q - 1) * 8 - 9;
            if (q <= full) {
                HudIcons.draw(c, t, HudIconAtlas.Icon.AIR_FULL, x, y);
            } else if (popping && q == now && inWater) {
                HudIcons.draw(c, t, HudIconAtlas.Icon.AIR_POPPING, x, y);
                popSound(player, q, empty);
            } else if (q > 10 - empty) {
                int jitter = empty == 10 && tickCount % 2 == 0 ? RANDOM.nextInt(2) : 0;
                HudIcons.draw(c, t, HudIconAtlas.Icon.AIR_EMPTY, x, y + jitter);
            }
        }
    }

    /** The game plays a pop as each bubble bursts (from 1.21.2; its HUD plays it, so Sol's must too). */
    private static void popSound(Player player, int bubble, int empty) {
        //? if >=1.21.2 {
        if (lastBubblePop != bubble) {
            float volume = 0.5f + 0.1f * Math.max(0, empty - 3 + 1);
            float pitch = 1f + 0.1f * Math.max(0, empty - 5 + 1);
            player.playSound(net.minecraft.sounds.SoundEvents.BUBBLE_POP, volume, pitch);
            lastBubblePop = bubble;
        }
        //?}
    }

    private static void mount(Canvas c, Player player, Tokens tk, int w, int h) {
        LivingEntity vehicle = vehicleWithHealth(player);
        int hearts = vehicleHearts(vehicle);
        if (hearts == 0) {
            return;
        }
        int health = (int) Math.ceil(vehicle.getHealth());
        int y = h - 39, right = w / 2 + 91;
        PictureTexture t = HudIcons.texture(tk);
        for (int n = 0; hearts > 0; n += 20) {
            int row = Math.min(hearts, 10);
            hearts -= row;
            for (int p = 0; p < row; p++) {
                int x = right - p * 8 - 9;
                HudIcons.draw(c, t, HudIconAtlas.Icon.MOUNT_CONTAINER, x, y);
                if (p * 2 + 1 + n < health) {
                    HudIcons.draw(c, t, HudIconAtlas.Icon.MOUNT_FULL, x, y);
                } else if (p * 2 + 1 + n == health) {
                    HudIcons.draw(c, t, HudIconAtlas.Icon.MOUNT_HALF, x, y);
                }
            }
            y -= 10;
        }
    }

    // ------------------------------------------------------------------ experience

    private static void experience(Canvas c, Player player, Tokens tk, int w, int h) {
        float x = w / 2f - 91f, y = h - 28f;
        Gfx g = begin(c);
        g.round(x, y, 182f, 3f, 1.5f, 0x80080610);
        g.stroke(x, y, 182f, 3f, 1.5f, 1f / g.d.u, 0x1FFFFFFF);
        float k = Mth.clamp(player.experienceProgress, 0f, 1f);
        if (k > 0f) {
            float fw = 182f * k;
            g.glow(x, y, fw, 3f, 1.5f, 4f, Colors.withAlpha(tk.glow, 0.35f));
            g.hgradient(x, y, fw, 3f, new float[]{0f, 1f}, new int[]{tk.glow, tk.accent}, 2);
        }
        end(c);
    }

    private static void level(Canvas c, Minecraft mc, Player player, Tokens tk, int w, int h) {
        if (!mc.gameMode.hasExperience() || player.experienceLevel <= 0) {
            return;
        }
        Gfx g = begin(c);
        SolFont f = g.font(SolFont.Family.SEMIBOLD, 8f);
        String s = Integer.toString(player.experienceLevel);
        float cy = h - 34f;
        g.glowSprite(w / 2f, cy, g.width(f, s) / 2f + 6f, 6f, Colors.withAlpha(tk.accent, 0.3f));
        shadowed(g, f, s, w / 2f, cy, tk.accent, 0);
        end(c);
    }
}
