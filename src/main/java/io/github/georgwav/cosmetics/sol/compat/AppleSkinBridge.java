package io.github.georgwav.cosmetics.sol.compat;

import io.github.georgwav.cosmetics.CosmeticsMod;
import io.github.georgwav.cosmetics.mixin.FoodDataMixin;
import io.github.georgwav.cosmetics.sol.hud.SolHud;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.player.Player;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.BooleanSupplier;

/**
 * AppleSkin next to Sol UI. Sol draws the same things itself (saturation rims, the held food's shanks, the
 * exhaustion line), so while Sol draws the hunger bar or the hearts, AppleSkin's overlays on them are cancelled
 * through its own events, and the game's HUD with AppleSkin's overlays comes back as soon as Sol hands the part back.
 *
 * AppleSkin's events ({@code squeek.appleskin.api.event.HUDOverlayEvent.*.EVENT}, each event with a public
 * {@code isCanceled}) take a context class that differs between versions, so they are reached by reflection; nothing
 * here loads without AppleSkin.
 */
public final class AppleSkinBridge {
    private static final String EVENTS = "squeek.appleskin.api.event.HUDOverlayEvent$";

    private AppleSkinBridge() {
    }

    private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("appleskin");

    /** Whether AppleSkin is installed: its saturation, held-food and exhaustion details are then Sol UI's to draw. */
    public static boolean present() {
        return PRESENT;
    }

    public static void init() {
        if (!FabricLoader.getInstance().isModLoaded("appleskin")) {
            return;
        }
        try {
            Class<?> handler = Class.forName("squeek.appleskin.api.handler.EventHandler");
            Field cancel = Class.forName("squeek.appleskin.api.event.HUDOverlayEvent").getField("isCanceled");
            cancelWhile(handler, cancel, "Exhaustion", () -> SolHud.draws(SolHud.Part.FOOD));
            cancelWhile(handler, cancel, "Saturation", () -> SolHud.draws(SolHud.Part.FOOD));
            cancelWhile(handler, cancel, "HungerRestored", () -> SolHud.draws(SolHud.Part.FOOD));
            cancelWhile(handler, cancel, "HealthRestored", () -> SolHud.draws(SolHud.Part.HEALTH));
            CosmeticsMod.LOGGER.info("[Sol] AppleSkin found: Sol UI draws its hunger details");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.warn("[Sol] AppleSkin's HUD events weren't found; its overlays may draw over Sol UI", e);
        }
    }

    private static void cancelWhile(Class<?> handler, Field cancel, String event, BooleanSupplier sol) throws ReflectiveOperationException {
        Object hook = Class.forName(EVENTS + event).getField("EVENT").get(null);
        Object listener = Proxy.newProxyInstance(AppleSkinBridge.class.getClassLoader(), new Class<?>[]{handler}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "interact" -> {
                    if (args != null && args.length == 1 && sol.getAsBoolean()) {
                        cancel.setBoolean(args[0], true);
                    }
                    return null;
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == args[0];
                }
                case "toString" -> {
                    return "Sol UI's AppleSkin " + event + " listener";
                }
                default -> {
                    return null;
                }
            }
        });
        Method register = hook.getClass().getMethod("register", Object.class);
        register.setAccessible(true);
        register.invoke(hook, listener);
    }

    /** The player's food exhaustion (0 to 4), as AppleSkin sends it; 0 without AppleSkin. */
    public static float exhaustion(Player player) {
        return ((FoodDataMixin) player.getFoodData()).cosmeticsmod$exhaustion();
    }
}
