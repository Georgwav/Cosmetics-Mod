package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
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
        for (HudModule hud : Modules.hud()) {
            if (hud.enabled()) {
                hud.render(g, mc, w, h);
            }
        }
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
