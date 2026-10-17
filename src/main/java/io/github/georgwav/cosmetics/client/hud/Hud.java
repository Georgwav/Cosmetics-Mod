package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.sol.hud.SolHud;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
*///?}
//? if >=1.21.11 {
import net.minecraft.resources.Identifier;
//?} elif >=1.21.6 {
/*import net.minecraft.resources.ResourceLocation;
*///?}

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Draws the HUD modules over the game's HUD (after it, so on top), and counts clicks for the CPS readouts. From
 * 1.21.6 through Fabric's HUD elements, before that through its HUD callback.
 */
public final class Hud {
    private static final Deque<Long> LEFT_CLICKS = new ArrayDeque<>();
    private static final Deque<Long> RIGHT_CLICKS = new ArrayDeque<>();
    private static boolean leftDown;
    private static boolean rightDown;
    /** The HUD editor is open: it draws the modules itself. */
    public static boolean editing;
    /** Times the HUD modules were drawn (the in-game check). */
    public static int framesDrawn;

    private Hud() {
    }

    public static void register() {
        // The client's crosshair in place of the game's while it's on (before 1.21.6 a mixin on Gui does this).
        //? if >=26.1 {
        /*HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
            if (Modules.CROSSHAIR.enabled()) {
                Modules.CROSSHAIR.drawOnHud(new Canvas(graphics), Minecraft.getInstance());
            } else {
                vanilla.extractRenderState(graphics, delta);
            }
        });
        *///?} elif >=1.21.6 {
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, vanilla -> (graphics, delta) -> {
            if (Modules.CROSSHAIR.enabled()) {
                Modules.CROSSHAIR.drawOnHud(new Canvas(graphics), Minecraft.getInstance());
            } else {
                vanilla.render(graphics, delta);
            }
        });
        //?}
        // The game's own HUD parts, each drawn moved by where the HUD editor put it (VanillaHud), and in Sol UI's look
        // where Sol draws that part (SolHud).
        //? if >=1.21.6 {
        element(VanillaHud.Part.HOTBAR, SolHud.Part.HOTBAR, VanillaHudElements.HOTBAR);
        element(VanillaHud.Part.HOTBAR, SolHud.Part.XP, VanillaHudElements.INFO_BAR);
        element(VanillaHud.Part.HOTBAR, SolHud.Part.LEVEL, VanillaHudElements.EXPERIENCE_LEVEL);
        element(VanillaHud.Part.HEALTH, SolHud.Part.HEALTH, VanillaHudElements.HEALTH_BAR);
        element(VanillaHud.Part.ARMOR, SolHud.Part.ARMOR, VanillaHudElements.ARMOR_BAR);
        element(VanillaHud.Part.FOOD, SolHud.Part.FOOD, VanillaHudElements.FOOD_BAR);
        element(VanillaHud.Part.FOOD, SolHud.Part.MOUNT, VanillaHudElements.MOUNT_HEALTH);
        element(VanillaHud.Part.AIR, SolHud.Part.AIR, VanillaHudElements.AIR_BAR);
        element(VanillaHud.Part.HELD_ITEM, null, VanillaHudElements.HELD_ITEM_TOOLTIP);
        element(VanillaHud.Part.ACTION_BAR, null, VanillaHudElements.OVERLAY_MESSAGE);
        element(VanillaHud.Part.BOSS_BAR, null, VanillaHudElements.BOSS_BAR);
        element(VanillaHud.Part.SCOREBOARD, null, VanillaHudElements.SCOREBOARD);
        //?}
        //? if >=26.1 {
        /*element(VanillaHud.Part.EFFECTS, null, VanillaHudElements.MOB_EFFECTS);
        *///?} elif >=1.21.6 {
        element(VanillaHud.Part.EFFECTS, null, VanillaHudElements.STATUS_EFFECTS);
        //?}
        //? if >=1.21.11 {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "modules"),
                (graphics, delta) -> draw(new Canvas(graphics)));
        //?} elif >=1.21.6 {
        /*HudElementRegistry.addLast(ResourceLocation.fromNamespaceAndPath(CosmeticsMod.MOD_ID, "modules"),
                (graphics, delta) -> draw(new Canvas(graphics)));
        *///?} else {
        /*HudRenderCallback.EVENT.register((graphics, delta) -> draw(new Canvas(graphics)));
        *///?}
    }

    // Wraps a game HUD element so it draws moved, and drawn by Sol UI while Sol draws that part; untouched otherwise.
    //? if >=1.21.11 {
    private static void element(VanillaHud.Part part, SolHud.Part sol, Identifier id) {
        HudElementRegistry.replaceElement(id, vanilla -> moved(part, sol, vanilla));
    }
    //?} elif >=1.21.6 {
    /*private static void element(VanillaHud.Part part, SolHud.Part sol, ResourceLocation id) {
        HudElementRegistry.replaceElement(id, vanilla -> moved(part, sol, vanilla));
    }
    *///?}

    //? if >=1.21.6 {
    private static HudElement moved(VanillaHud.Part part, SolHud.Part sol, HudElement vanilla) {
        return (graphics, delta) -> {
            boolean solDraws = sol != null && SolHud.draws(sol);
            float dx = VanillaHud.dx(part);
            float dy = VanillaHud.dy(part);
            if (!solDraws && dx == 0f && dy == 0f) {
                vanilla(vanilla, graphics, delta);
                return;
            }
            Canvas g = new Canvas(graphics);
            g.push();
            g.translate(dx, dy);
            if (solDraws) {
                SolHud.draw(sol, g, delta.getGameTimeDeltaPartialTick(false));
            } else {
                vanilla(vanilla, graphics, delta);
            }
            g.pop();
        };
    }
    //?}

    //? if >=26.1 {
    /*private static void vanilla(HudElement vanilla, net.minecraft.client.gui.GuiGraphicsExtractor graphics, net.minecraft.client.DeltaTracker delta) {
        vanilla.extractRenderState(graphics, delta);
    }
    *///?} elif >=1.21.6 {
    private static void vanilla(HudElement vanilla, net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) {
        vanilla.render(graphics, delta);
    }
    //?}

    private static void draw(Canvas g) {
        Minecraft mc = Minecraft.getInstance();
        countClicks(mc);
        // The game's HUD (and so this) isn't drawn while the HUD is hidden (F1).
        if (editing || mc.player == null) {
            return;
        }
        framesDrawn++;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long t0 = io.github.georgwav.cosmetics.sol.render.SolTiming.start();
        for (HudModule hud : Modules.hud()) {
            if (hud.enabled()) {
                hud.render(g, mc, w, h);
            }
        }
        io.github.georgwav.cosmetics.sol.render.SolTiming.end(io.github.georgwav.cosmetics.sol.render.SolTiming.Part.HUD, t0);
        io.github.georgwav.cosmetics.client.modules.Performance.drawGraph(g, w, h);
    }

    /** Draws one module where it sits (the HUD editor). */
    public static void drawModule(Canvas g, HudModule hud, int screenW, int screenH) {
        hud.render(g, Minecraft.getInstance(), screenW, screenH);
    }

    /** Clicks of the attack and use keys, read every frame. */
    private static void countClicks(Minecraft mc) {
        long now = System.currentTimeMillis();
        boolean left = mc.options.keyAttack.isDown();
        boolean right = mc.options.keyUse.isDown();
        if (left && !leftDown) {
            LEFT_CLICKS.addLast(now);
        }
        if (right && !rightDown) {
            RIGHT_CLICKS.addLast(now);
        }
        leftDown = left;
        rightDown = right;
        while (!LEFT_CLICKS.isEmpty() && now - LEFT_CLICKS.peekFirst() > 1000) {
            LEFT_CLICKS.pollFirst();
        }
        while (!RIGHT_CLICKS.isEmpty() && now - RIGHT_CLICKS.peekFirst() > 1000) {
            RIGHT_CLICKS.pollFirst();
        }
    }

    /** Clicks in the last second: left (attack) and right (use). */
    public static int leftCps() {
        return LEFT_CLICKS.size();
    }

    public static int rightCps() {
        return RIGHT_CLICKS.size();
    }
}
