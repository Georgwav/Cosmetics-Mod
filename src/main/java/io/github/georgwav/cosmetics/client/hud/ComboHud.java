package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Combo (C.4): your hit streak as a big pixel number that pops on every hit. It ends when you're hit or after a while
 * with no hit (2 s), and fades out in steps; a streak of one isn't shown. From five hits on, the stepped on-glow rings
 * it.
 */
public final class ComboHud extends HudModule {
    private final Setting.Slider timeout = add(new Setting.Slider("combo_time", 2.0, 1.0, 4.0, 0.5,
            v -> String.format(java.util.Locale.ROOT, "%.1f s", v)).group("behaviour"));
    private final Setting.Toggle hitEnds = add(new Setting.Toggle("combo_hit_ends", true).group("behaviour"));
    private final Setting.Toggle label = add(new Setting.Toggle("combo_label", true).group("look"));
    private final Setting.Color colour = add(new Setting.Color("combo_colour", Setting.Color.THEME).themed().group("look"));

    private int count, shown;
    private long lastHit, popAt, endAt;
    private int lastHurt;

    public ComboHud() {
        super("combo", "combo", 0.5f, 0.5f, -56f, 18f);
        io.github.georgwav.cosmetics.sol.fx.SolPicks.module(this);
        FxEvents.HIT.add(new FxEvents.Hit() {
            @Override
            public boolean listening() {
                return enabled();
            }

            @Override
            public void hit(LivingEntity target, double x, double y, double z, boolean crit, float healthBefore) {
                long now = System.nanoTime();
                if (count > 0 && now - lastHit > window()) {
                    count = 0;
                }
                count++;
                lastHit = now;
                if (count >= 2) {
                    shown = count;
                    popAt = now;
                    endAt = 0L;
                }
            }
        });
    }

    private long window() {
        return (long) (timeout.get() * 1e9);
    }

    @Override
    protected void tick(Minecraft mc) {
        Player you = mc.player;
        if (you == null) {
            count = shown = 0;
            return;
        }
        long now = System.nanoTime();
        // Being hit ends it (the hurt time jumps up as you take damage).
        int hurt = you.hurtTime;
        if (hitEnds.get() && hurt > lastHurt && count > 0) {
            end(now);
        }
        lastHurt = hurt;
        if (count > 0 && now - lastHit > window()) {
            end(now);
        }
    }

    private void end(long now) {
        if (shown >= 2 && endAt == 0L) {
            endAt = now;
        }
        count = 0;
    }

    @Override
    protected void onDisable(Minecraft mc) {
        count = shown = 0;
        endAt = 0L;
    }

    /** The number showing now (0 for none), and how much of it (it fades out in three steps once the streak ends). */
    private int number() {
        if (preview() && shown < 2) {
            return 7;
        }
        if (shown < 2) {
            return 0;
        }
        if (endAt != 0L && fade() <= 0f) {
            shown = 0;
            endAt = 0L;
            return 0;
        }
        return shown;
    }

    private float fade() {
        if (endAt == 0L || preview()) {
            return 1f;
        }
        return Ramp.steps(1f - (System.nanoTime() - endAt) / 0.3e9f, 3, true);
    }

    private float pop() {
        if (FxWorld.calm() || preview()) {
            return 1f;
        }
        return Ramp.pop((System.nanoTime() - popAt) / 1e9f, 0.15f, true);
    }

    private String word() {
        return Component.translatable("cosmeticsmod.module.combo.word").getString();
    }

    // ------------------------------------------------------------------ the plain look

    @Override
    protected int plainWidth(Minecraft mc) {
        int n = number();
        return n == 0 ? 0 : Math.max(mc.font.width(String.valueOf(n)) * 2, label.get() ? mc.font.width(word()) : 0) + 8;
    }

    @Override
    protected int plainHeight(Minecraft mc) {
        return number() == 0 ? 0 : 4 + 16 + (label.get() ? 10 : 0);
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int n = number();
        if (n == 0) {
            return;
        }
        int w = plainWidth(mc);
        int a = Math.round(255 * fade());
        int c = (a << 24) | (colour.argb() & 0xFFFFFF);
        String s = String.valueOf(n);
        g.push();
        g.translate(w / 2f, 2f);
        float k = pop() * 2f;
        g.scale(k, k);
        g.text(mc.font, s, -mc.font.width(s) / 2, 0, c, true);
        g.pop();
        if (label.get()) {
            String l = word();
            g.text(mc.font, l, (w - mc.font.width(l)) / 2, 21, (a << 24) | 0xC0C0C0, true);
        }
    }

    // ------------------------------------------------------------------ Sol UI's look

    private static final float PAD = 5f, NUMBER = 13f;

    @Override
    protected int solWidth(Minecraft mc) {
        int n = number();
        if (n == 0) {
            return 0;
        }
        float w = HudKit.measure(SolFont.Family.PIXEL_BOLD, NUMBER, String.valueOf(Math.max(n, 10)), scale());
        if (label.get()) {
            w = Math.max(w, HudKit.measure(HudKit.LABEL, HudKit.SMALL, word(), scale()));
        }
        return (int) Math.ceil(w + PAD * 2f);
    }

    @Override
    protected int solHeight(Minecraft mc) {
        return number() == 0 ? 0 : (int) Math.ceil(PAD * 2f + NUMBER + (label.get() ? 7f : 0f));
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        int n = number();
        if (n == 0) {
            return;
        }
        float w = solWidth(mc), h = solHeight(mc);
        float a = fade();
        int c = Colors.withAlpha(colour.argb() | 0xFF000000, a);
        float was = HudKit.glass;
        HudKit.glass *= a;
        HudKit.card(g, tk, 0f, 0f, w, h, HudKit.CARD_R);
        HudKit.glass = was;
        if (n >= 5) {
            HudKit.rings(g, 0f, 0f, w, h, Colors.fade(c, 0.8f));
        }
        // The number at whole pixel sizes: the pop picks a bigger one for a moment.
        SolFont big = g.font(SolFont.Family.PIXEL_BOLD, NUMBER * pop());
        float cy = PAD + NUMBER / 2f;
        g.textCentre(big, String.valueOf(n), w / 2f, cy, c);
        if (label.get()) {
            g.textCentre(HudKit.small(g), word(), w / 2f, h - PAD - 2.5f, Colors.withAlpha(0xFFFFFFFF, 0.66f * a));
        }
    }
}
