package io.github.georgwav.cosmetics.melee;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.modules.PunchyModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;

/**
 * What you do with the knife in your hand: an attack click plays a light attack, or the heavy one when it lands a
 * critical hit (decided at the click, before the attack charge resets, by the game's own rule); holding attack on a
 * block repeats the light attack at the swing rate; the inspect key inspects; using an item, switching items or
 * opening a screen stops an inspect. Timing follows the held weapon (its attack speed and swing length) unless the
 * Knives settings say "Original". Sol never adds an attack: right-click stays the game's.
 */
public final class KnifeInput {
    private static boolean crit;
    private static int lastSlot = -1;

    private KnifeInput() {
    }

    /** An attack click is starting: whether it lands a critical hit. */
    public static void clicked(Minecraft mc) {
        crit = false;
        var p = mc.player;
        if (p != null && mc.hitResult instanceof EntityHitResult hit) {
            crit = critical(p, hit.getEntity());
        }
    }

    /** Whether hitting {@code target} now is a critical hit, as the game decides it. */
    static boolean critical(Player p, Entity target) {
        if (p.getAttackStrengthScale(0.5f) <= 0.9f) {
            return false;
        }
        //? if >=1.21.11 {
        return ((io.github.georgwav.cosmetics.mixin.PlayerCritAccess) p).cosmeticsmod$canCriticalAttack(target);
        //?} else {
        /*return p.fallDistance > 0f && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.hasEffect(MobEffects.BLINDNESS)
                && !p.isPassenger() && target instanceof LivingEntity && !p.isSprinting();
        *///?}
    }

    /** The click swung: the knife attacks. */
    public static void attacked(Minecraft mc) {
        KnifeView v = KnifeRenderer.current();
        if (v != null && mc.player != null) {
            v.behaviour.attack(KnifeRenderer.now(), crit, PunchyModule.knifeHeavyOnCrits(), cooldown(mc.player), swing(mc.player),
                    PunchyModule.knifeFit());
        }
        crit = false;
    }

    /** Attack held on a block swung again. */
    public static void swungOnBlock(Minecraft mc) {
        KnifeView v = KnifeRenderer.current();
        if (v != null && mc.player != null) {
            v.behaviour.swingRepeat(KnifeRenderer.now(), cooldown(mc.player), swing(mc.player), PunchyModule.knifeFit());
        }
    }

    /** The use key: an inspect stops. */
    public static void used() {
        KnifeView v = KnifeRenderer.current();
        if (v != null) {
            v.behaviour.cancelInspect(KnifeRenderer.now());
        }
    }

    /** Each tick: the inspect key, and what stops an inspect (switching items, a screen). */
    public static void tick(Minecraft mc) {
        var key = Modules.PUNCHY.key("inspect");
        KnifeView v = KnifeRenderer.current();
        while (key != null && key.consumeClick()) {
            if (v != null && io.github.georgwav.cosmetics.ui.ScreenOpener.current() == null) {
                v.behaviour.inspect(KnifeRenderer.now());
            }
        }
        var p = mc.player;
        if (p == null) {
            return;
        }
        //? if >=1.21.5 {
        int slot = p.getInventory().getSelectedSlot();
        //?} else {
        /*int slot = p.getInventory().selected;
        *///?}
        if (v != null && (slot != lastSlot || io.github.georgwav.cosmetics.ui.ScreenOpener.current() != null)) {
            v.behaviour.cancelInspect(KnifeRenderer.now());
        }
        lastSlot = slot;
    }

    /** Seconds until the attack is charged again (the attack strength delay). */
    static float cooldown(Player p) {
        return Math.max(0.05f, p.getCurrentItemAttackStrengthDelay() / 20f);
    }

    /** Seconds the arm swing takes: the weapon's swing (6 ticks unless it says), with Haste and Mining Fatigue. */
    static float swing(Player p) {
        int ticks = 6;
        //? if >=26.3 {
        /*var anim = p.getMainHandItem().get(net.minecraft.core.component.DataComponents.ATTACK_ANIMATION);
        if (anim != null) {
            ticks = anim.duration();
        }
        *///?} elif >=1.21.11 {
        var anim = p.getMainHandItem().get(net.minecraft.core.component.DataComponents.SWING_ANIMATION);
        if (anim != null) {
            ticks = anim.duration();
        }
        //?}
        if (MobEffectUtil.hasDigSpeed(p)) {
            ticks -= 1 + MobEffectUtil.getDigSpeedAmplification(p);
        } else {
            //? if >=1.21.5 {
            MobEffectInstance fatigue = p.getEffect(MobEffects.MINING_FATIGUE);
            //?} else {
            /*MobEffectInstance fatigue = p.getEffect(MobEffects.DIG_SLOWDOWN);
            *///?}
            if (fatigue != null) {
                ticks += (1 + fatigue.getAmplifier()) * 2;
            }
        }
        return Math.max(1, ticks) / 20f;
    }
}
