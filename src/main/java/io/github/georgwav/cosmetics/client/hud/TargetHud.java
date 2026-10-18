package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;

/**
 * Target HUD: who you're fighting, by the crosshair: the one you look at, or the last one you hit in the last five
 * seconds. Their name, a health bar with the number (absorption counted), their armour and how far away they are.
 */
public final class TargetHud extends HudModule {
    private final Setting.Choice which = add(new Setting.Choice("target", "both", List.of("both", "looking", "hit")).group("behaviour"));
    private final Setting.Toggle playersOnly = add(new Setting.Toggle("players_only", false).group("behaviour"));
    private final Setting.Toggle armour = add(new Setting.Toggle("armour", true).group("look"));
    private final Setting.Toggle distance = add(new Setting.Toggle("distance", true).group("look"));

    private LivingEntity hit;
    private long hitAt;

    public TargetHud() {
        super("target_hud", "lookat", 0.5f, 0.5f, 56f, 18f);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, result) -> {
            if (level.isClientSide() && player == Minecraft.getInstance().player && entity instanceof LivingEntity living) {
                hit = living;
                hitAt = System.currentTimeMillis();
            }
            return InteractionResult.PASS;
        });
    }

    @Override
    protected void onDisable(Minecraft mc) {
        hit = null;
    }

    /** The target now, or null. */
    private LivingEntity target(Minecraft mc) {
        if (mc.player == null) {
            return null;
        }
        String w = which.get();
        if (!w.equals("hit")) {
            Entity looked = mc.crosshairPickEntity;
            if (looked instanceof LivingEntity l && ok(l)) {
                return l;
            }
        }
        if (hit != null && (System.currentTimeMillis() - hitAt >= 5000L || hit.level() != mc.level)) {
            // Let go of it, so an old world isn't kept in memory through it.
            hit = null;
        }
        if (!w.equals("looking") && hit != null && ok(hit)) {
            return hit;
        }
        return null;
    }

    private boolean ok(LivingEntity e) {
        return e.isAlive() && !e.isInvisible() && (!playersOnly.get() || e instanceof Player);
    }

    private record Shown(String name, float health, float max, float absorb, int armour, float dist) {
    }

    private Shown shown(Minecraft mc) {
        LivingEntity t = target(mc);
        if (t != null) {
            return new Shown(t.getName().getString(), t.getHealth(), t.getMaxHealth(), t.getAbsorptionAmount(), t.getArmorValue(),
                    mc.player.distanceTo(t));
        }
        // An example in the HUD editor.
        return preview() ? new Shown("Steve", 14f, 20f, 4f, 15, 3.4f) : null;
    }

    private static String num(float v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    // ------------------------------------------------------------------ the plain look

    @Override
    protected int plainWidth(Minecraft mc) {
        Shown s = shown(mc);
        return s == null ? 0 : Math.max(96, mc.font.width(s.name()) + 8);
    }

    @Override
    protected int plainHeight(Minecraft mc) {
        Shown s = shown(mc);
        return s == null ? 0 : 4 + 10 + 6 + (armour.get() || distance.get() ? 10 : 0);
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        Shown s = shown(mc);
        if (s == null) {
            return;
        }
        int w = plainWidth(mc);
        g.text(mc.font, s.name(), 4, 3, 0xFFFFFFFF);
        float k = Math.min(1f, s.health() / Math.max(1f, s.max()));
        int barW = w - 8;
        g.fill(4, 14, 4 + barW, 17, 0x60000000);
        g.fill(4, 14, 4 + Math.round(barW * k), 17, k > 0.5f ? 0xFF55FF55 : k > 0.25f ? 0xFFFFC040 : 0xFFFF4040);
        if (s.absorb() > 0f) {
            g.fill(4, 17, 4 + Math.round(barW * Math.min(1f, s.absorb() / Math.max(1f, s.max()))), 18, 0xFFFFD700);
        }
        String line = "";
        if (armour.get()) {
            line += Component.translatable("cosmeticsmod.module.target_hud.armour_value", s.armour()).getString();
        }
        if (distance.get()) {
            line += (line.isEmpty() ? "" : "  ") + num(Math.round(s.dist() * 10f) / 10f) + " m";
        }
        String hp = num(Math.round((s.health() + s.absorb()) * 10f) / 10f) + " ♥";
        if (!line.isEmpty()) {
            g.text(mc.font, line, 4, 21, 0xFFB0B0B0);
        }
        g.text(mc.font, hp, w - 4 - mc.font.width(hp), 3, 0xFFFF6B6B);
    }

    // ------------------------------------------------------------------ Sol UI's look

    private static final float PAD = 6f, W = 112f;

    @Override
    protected int solWidth(Minecraft mc) {
        Shown s = shown(mc);
        return s == null ? 0 : (int) Math.ceil(Math.max(W, HudKit.measure(SolFont.Family.MEDIUM, HudKit.TEXT, s.name(), scale()) + 48f + PAD * 2f));
    }

    @Override
    protected int solHeight(Minecraft mc) {
        Shown s = shown(mc);
        return s == null ? 0 : (int) Math.ceil(PAD * 2f + 7f + 6f + (armour.get() || distance.get() ? 8f : 0f));
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        Shown s = shown(mc);
        if (s == null) {
            return;
        }
        float w = solWidth(mc), h = solHeight(mc);
        HudKit.card(g, tk, 0f, 0f, w, h, HudKit.CARD_R);
        SolFont text = HudKit.text(g), mono = HudKit.mono(g), small = HudKit.small(g);
        float cy = PAD + 3f;
        g.textMid(text, s.name(), PAD, cy, 0xFFFFFFFF);
        String hp = num(Math.round((s.health() + s.absorb()) * 10f) / 10f);
        float k = Math.min(1f, s.health() / Math.max(1f, s.max()));
        int col = HudKit.state(tk, k, 0.5f, 0.25f);
        g.textRight(mono, hp, w - PAD, cy, col);
        HudKit.bar(g, PAD, cy + 6f, w - PAD * 2f, 2.5f, k, col);
        if (s.absorb() > 0f) {
            HudKit.bar(g, PAD, cy + 9.5f, (w - PAD * 2f) * Math.min(1f, s.absorb() / Math.max(1f, s.max())), 1.2f, 1f, Tokens.AMBER);
        }
        String line = "";
        if (armour.get()) {
            line += Component.translatable("cosmeticsmod.module.target_hud.armour_value", s.armour()).getString();
        }
        if (distance.get()) {
            line += (line.isEmpty() ? "" : "   ") + num(Math.round(s.dist() * 10f) / 10f) + " m";
        }
        if (!line.isEmpty()) {
            g.textMid(small, line, PAD, h - PAD - 2.5f, 0xA8FFFFFF);
        }
    }
}
