package io.github.georgwav.cosmetics.client.hud;

import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.PoseSnapshot;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.hud.HudKit;
import io.github.georgwav.cosmetics.sol.theme.Tokens;
import io.github.georgwav.cosmetics.sol.ui.kit.Gfx;
import io.github.georgwav.cosmetics.sol.ui.kit.Icons;
import io.github.georgwav.cosmetics.sol.ui.kit.SolFont;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Kill feed (C.4): kills slide in at the top right, newest on top: who killed, what with (the item's icon, or a fist),
 * and who died. Yours from what you see die (anything you hit), everyone else's from the server's death messages in
 * chat. Your kills are in the accent, your deaths in red. Five lines, four seconds each, as set.
 */
public final class KillFeedHud extends HudModule {
    private final Setting.Slider lines = add(new Setting.Slider("feed_lines", 5, 1, 8, 1, v -> String.valueOf(Math.round(v)))
            .group("behaviour"));
    private final Setting.Slider seconds = add(new Setting.Slider("feed_seconds", 4, 2, 10, 1, v -> Math.round(v) + " s")
            .group("behaviour"));
    private final Setting.Toggle others = add(new Setting.Toggle("feed_others", true).group("behaviour"));
    private final Setting.Toggle icons = add(new Setting.Toggle("feed_icons", true).group("look"));

    /** A line: who, with what, whom; whether it's your kill or your death; when (ms). */
    private static final class Line {
        String killer = "", victim = "";
        ItemStack weapon = ItemStack.EMPTY;
        boolean yours, died;
        long at;
    }

    private static final int MAX = 8;
    private final Line[] feed = new Line[MAX];
    private int size;
    private final Line[] example = new Line[3];
    /** Where each line's icon goes (Sol UI's look), kept so a frame allocates nothing. */
    private final float[] iconX = new float[MAX];

    public KillFeedHud() {
        super("kill_feed", "feed", 1f, 0f, -4f, 4f);
        io.github.georgwav.cosmetics.sol.fx.SolPicks.module(this);
        for (int i = 0; i < MAX; i++) {
            feed[i] = new Line();
        }
        FxEvents.KILL.add(new FxEvents.Kill() {
            @Override
            public boolean listening() {
                return enabled();
            }

            @Override
            public void killed(LivingEntity dead, PoseSnapshot copy) {
                Player you = Minecraft.getInstance().player;
                if (you != null) {
                    add(you.getName().getString(), you.getMainHandItem(), dead.getName().getString());
                }
            }
        });
        net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay && enabled() && others.get()) {
                io.github.georgwav.cosmetics.sol.Guard.run(io.github.georgwav.cosmetics.sol.Feature.FX, () -> chat(message));
            }
        });
    }

    /** A death message with a killer ("death.attack.player", "death.attack.player.item", "death.fell.assist", ...). */
    private void chat(Component message) {
        if (!(message.getContents() instanceof TranslatableContents tc) || !tc.getKey().startsWith("death.")) {
            return;
        }
        Object[] args = tc.getArgs();
        if (args.length < 2) {
            return;
        }
        String victim = text(args[0]), killer = text(args[1]);
        Minecraft mc = Minecraft.getInstance();
        ItemStack weapon = ItemStack.EMPTY;
        if (mc.level != null) {
            for (Player p : mc.level.players()) {
                if (p.getName().getString().equals(killer)) {
                    weapon = p.getMainHandItem();
                    break;
                }
            }
        }
        add(killer, weapon, victim);
    }

    private static String text(Object o) {
        return o instanceof Component c ? c.getString() : String.valueOf(o);
    }

    /** Adds a line on top, unless the same kill is there already (your kill seen and then said in chat). */
    void add(String killer, ItemStack weapon, String victim) {
        long now = System.currentTimeMillis();
        for (int i = 0; i < size; i++) {
            Line l = feed[i];
            if (l.victim.equals(victim) && now - l.at < 2500L) {
                if (l.weapon.isEmpty() && !weapon.isEmpty()) {
                    l.weapon = weapon.copy();
                }
                return;
            }
        }
        Line l = feed[MAX - 1];
        System.arraycopy(feed, 0, feed, 1, MAX - 1);
        feed[0] = l;
        size = Math.min(MAX, size + 1);
        Minecraft mc = Minecraft.getInstance();
        String you = mc.player != null ? mc.player.getName().getString() : "";
        l.killer = killer;
        l.victim = victim;
        l.weapon = weapon.isEmpty() ? ItemStack.EMPTY : weapon.copy();
        l.yours = killer.equals(you);
        l.died = victim.equals(you);
        l.at = now;
    }

    @Override
    protected void onDisable(Minecraft mc) {
        size = 0;
    }

    /** Lines in the feed now (the scenario's check). */
    public int lines() {
        return live();
    }

    @Override
    protected void tick(Minecraft mc) {
        if (mc.level == null) {
            size = 0;
        }
    }

    /** The lines showing now, newest first (an example in the HUD editor). */
    private int count() {
        if (preview() && live() == 0) {
            if (example[0] == null) {
                String you = Minecraft.getInstance().getUser().getName();
                example[0] = line(you, new ItemStack(Items.DIAMOND_SWORD), "Steve", true, false);
                example[1] = line("Alex", new ItemStack(Items.BOW), "Notch", false, false);
                example[2] = line("Steve", new ItemStack(Items.IRON_AXE), "Alex", false, false);
            }
            return example.length;
        }
        return live();
    }

    private Line at(int i) {
        return preview() && live() == 0 ? example[i] : feed[i];
    }

    private static Line line(String killer, ItemStack weapon, String victim, boolean yours, boolean died) {
        Line l = new Line();
        l.killer = killer;
        l.weapon = weapon;
        l.victim = victim;
        l.yours = yours;
        l.died = died;
        l.at = Long.MAX_VALUE / 2;
        return l;
    }

    private int live() {
        long now = System.currentTimeMillis();
        long keep = Math.round(seconds.get() * 1000);
        int n = Math.min(size, (int) Math.round(lines.get()));
        for (int i = 0; i < n; i++) {
            if (now - feed[i].at > keep) {
                return i;
            }
        }
        return n;
    }

    /** How far a line has slid in (0..1, in steps) and how much of it shows (fading out over its last 0.3 s). */
    private float slide(Line l) {
        if (FxWorld.calm() || preview()) {
            return 1f;
        }
        return Ramp.steps(Math.min(1f, (System.currentTimeMillis() - l.at) / 200f), 4, true);
    }

    private float fade(Line l) {
        if (preview()) {
            return 1f;
        }
        long left = Math.round(seconds.get() * 1000) - (System.currentTimeMillis() - l.at);
        return Ramp.steps(Math.min(1f, left / 300f), 3, true);
    }

    // ------------------------------------------------------------------ the plain look

    private static final int P_ROW = 12;

    @Override
    protected boolean wholeBacking() {
        return false;
    }

    private int plainLine(Minecraft mc, Line l) {
        return mc.font.width(l.killer) + 4 + (icons.get() ? 10 : mc.font.width(">")) + 4 + mc.font.width(l.victim) + 6;
    }

    @Override
    protected int plainWidth(Minecraft mc) {
        int w = 0;
        for (int i = 0, n = count(); i < n; i++) {
            w = Math.max(w, plainLine(mc, at(i)));
        }
        return w;
    }

    @Override
    protected int plainHeight(Minecraft mc) {
        int n = count();
        return n == 0 ? 0 : n * P_ROW - 1;
    }

    @Override
    protected void draw(Canvas g, Minecraft mc) {
        int w = plainWidth(mc);
        for (int i = 0, n = count(); i < n; i++) {
            Line l = at(i);
            int lw = plainLine(mc, l);
            int x = w - lw + Math.round((1f - slide(l)) * 40f);
            int y = i * P_ROW;
            int a = Math.round(255 * fade(l));
            if (backing.get()) {
                g.fill(x, y, x + lw, y + P_ROW - 1, Math.round(Colors.a(backingArgb()) * fade(l)) << 24);
            }
            int cx = x + 3;
            int kc = l.yours ? io.github.georgwav.cosmetics.sol.fx.FxColour.accent() : 0xFFFFFFFF;
            g.text(mc.font, l.killer, cx, y + 2, (a << 24) | (kc & 0xFFFFFF), true);
            cx += mc.font.width(l.killer) + 4;
            if (icons.get()) {
                if (!l.weapon.isEmpty()) {
                    g.push();
                    g.translate(cx, y + 1);
                    g.scale(0.625f, 0.625f);
                    g.item(l.weapon, 0, 0);
                    g.pop();
                }
                cx += 10 + 4;
            } else {
                g.text(mc.font, ">", cx, y + 2, (a << 24) | 0xA0A0A0, true);
                cx += mc.font.width(">") + 4;
            }
            g.text(mc.font, l.victim, cx, y + 2, (a << 24) | (l.died ? 0xFF5555 : 0xFFFFFF), true);
        }
    }

    // ------------------------------------------------------------------ Sol UI's look

    private static final float PAD = 4f, ROW = 11f, GAP = 2f, ICON = 8f;

    private float solLine(Line l) {
        float s = scale();
        return PAD + HudKit.measure(SolFont.Family.MEDIUM, HudKit.TEXT, l.killer, s) + 3f + ICON + 3f
                + HudKit.measure(SolFont.Family.MEDIUM, HudKit.TEXT, l.victim, s) + PAD;
    }

    @Override
    protected int solWidth(Minecraft mc) {
        float w = 0f;
        for (int i = 0, n = count(); i < n; i++) {
            w = Math.max(w, solLine(at(i)));
        }
        return (int) Math.ceil(w);
    }

    @Override
    protected int solHeight(Minecraft mc) {
        int n = count();
        return n == 0 ? 0 : (int) Math.ceil(n * (ROW + GAP) - GAP);
    }

    @Override
    protected void drawSol(Gfx g, Tokens tk, Minecraft mc) {
        int n = count();
        if (n == 0) {
            return;
        }
        float w = solWidth(mc);
        SolFont text = HudKit.text(g);
        float[] xs = iconX;
        float base = HudKit.glass;
        for (int i = 0; i < n; i++) {
            Line l = at(i);
            float lw = solLine(l);
            float x = Math.round(w - lw + (1f - slide(l)) * 36f);
            float y = i * (ROW + GAP);
            float a = fade(l);
            HudKit.glass = base * a;
            HudKit.card(g, tk, x, y, lw, ROW, HudKit.CARD_R);
            if (l.yours) {
                // Your kill: the accent's edge down its left side.
                HudKit.box(g, x, y, 1.5f, ROW, Colors.withAlpha(0xFF000000 | tk.accent, a));
            }
            float cy = y + ROW / 2f;
            float cx = x + PAD;
            int kc = l.yours ? 0xFF000000 | tk.accent : 0xFFFFFFFF;
            g.textMid(text, l.killer, cx, cy, Colors.withAlpha(kc, a));
            cx += g.width(text, l.killer) + 3f;
            xs[i] = cx;
            if (icons.get() && l.weapon.isEmpty()) {
                Icons.draw(g, "fist", cx, cy - ICON / 2f, ICON, Colors.withAlpha(0xFFFFFFFF, 0.8f * a), 0f, 0);
            } else if (!icons.get()) {
                Icons.draw(g, "skull", cx, cy - ICON / 2f, ICON, Colors.withAlpha(0xFFFFFFFF, 0.7f * a), 0f, 0);
            }
            cx += ICON + 3f;
            int vc = l.died ? Tokens.RED : 0xFFFFFFFF;
            g.textMid(text, l.victim, cx, cy, Colors.withAlpha(vc, a));
        }
        HudKit.glass = base;
        if (!icons.get()) {
            return;
        }
        // The weapons, by the game, at half size.
        g.layer();
        Canvas gc = g.game();
        gc.push();
        gc.scale(g.d.u, g.d.u);
        for (int i = 0; i < n; i++) {
            Line l = at(i);
            if (l.weapon.isEmpty() || fade(l) < 0.5f) {
                continue;
            }
            gc.push();
            gc.translate(xs[i], i * (ROW + GAP) + (ROW - ICON) / 2f);
            gc.scale(ICON / 16f, ICON / 16f);
            gc.item(l.weapon, 0, 0);
            gc.pop();
        }
        gc.pop();
    }
}
