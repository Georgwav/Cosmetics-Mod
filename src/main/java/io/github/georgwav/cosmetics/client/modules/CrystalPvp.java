package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
import io.github.georgwav.cosmetics.sol.fx.Fx;
import io.github.georgwav.cosmetics.sol.fx.FxBits;
import io.github.georgwav.cosmetics.sol.fx.FxSoft;
import io.github.georgwav.cosmetics.sol.fx.FxEvents;
import io.github.georgwav.cosmetics.sol.fx.FxKind;
import io.github.georgwav.cosmetics.sol.fx.FxModule;
import io.github.georgwav.cosmetics.sol.fx.FxOut;
import io.github.georgwav.cosmetics.sol.fx.FxPool;
import io.github.georgwav.cosmetics.sol.fx.FxPreview;
import io.github.georgwav.cosmetics.sol.fx.FxSettings;
import io.github.georgwav.cosmetics.sol.fx.FxWorld;
import io.github.georgwav.cosmetics.sol.fx.Ramp;
import io.github.georgwav.cosmetics.sol.fx.Vfx;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Crystal PvP, each part on its own switch: the crystal optimizer (an end crystal you hit is gone from your screen at
 * once when the hit will break it, instead of a moment later when the server says so, so the next one goes down
 * sooner; one the server keeps comes back after a second and a half); the crystals' size (their spin and bounce are the Crystal part's, in Sol Styles); a line in chat when anyone near you pops a totem, with their count;
 * a warning when your offhand has no totem but your inventory does; and a red pulse round the screen when your health
 * runs low; and explosion effects: a shimmer where a crystal is set down, and a blast (flash, fireball, shock rings,
 * sparks, smoke) where anything explodes near you. All on your side only: nothing is sent that the game doesn't send anyway.
 */
public final class CrystalPvp extends FxModule {
    /** Explosion effects: a shimmer where a crystal is set down, a blast where anything explodes near you. */
    public final FxSettings rings = fx("rings", java.util.List.of("one"),
            new FxSettings.Spec().toggle(true).noAmount());
    final Setting.Toggle optimizer = add(new Setting.Toggle("optimizer", true).group("crystal"));
    final Setting.Slider size = add(new Setting.Slider("crystal_size", 100, 25, 150, 5, v -> Math.round(v) + "%").group("crystal"));
    private final Setting.Toggle pops = add(new Setting.Toggle("pop_messages", true).group("combat"));
    private final Setting.Toggle offhand = add(new Setting.Toggle("offhand_warning", true).group("combat"));
    private final Setting.Toggle lowHealth = add(new Setting.Toggle("low_health", true).group("combat"));
    private final Setting.Slider lowAt = add(new Setting.Slider("low_health_at", 4, 1, 10, 0.5, v -> trimmed(v) + " hearts").group("combat"));
    private final Setting.Color lowColor = add(new Setting.Color("low_health_color", 0xFFFF2A2A).themed().group("combat"));

    /** Totems each player has popped since they last died (or since you saw them first). */
    private final Map<UUID, Integer> popped = new HashMap<>();
    /**
     * Crystals the optimizer took from view, by id, until the server removes them too. A hit the server turns down
     * (out of reach, lag, a plugin) leaves its crystal there: after {@link #CONFIRM_MS} it's put back in view.
     */
    private final Map<Integer, Hidden> hidden = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CONFIRM_MS = 1500L;

    private record Hidden(long at, net.minecraft.client.multiplayer.ClientLevel level, double x, double y, double z, boolean bottom) {
    }
    private boolean warned;
    private long warnedAt;

    public CrystalPvp() {
        super("crystal_pvp", Category.VIEW, "spark");
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (enabled() && optimizer.get() && level.isClientSide() && player == mc.player && entity instanceof EndCrystal
                    && breaks(player, entity)) {
                // The packet to attack it is still sent (by the game, after this); the crystal goes from view now.
                hidden.put(entity.getId(), new Hidden(System.currentTimeMillis(), mc.level, entity.getX(), entity.getY(), entity.getZ(),
                        ((EndCrystal) entity).showsBottom()));
                entity.remove(Entity.RemovalReason.KILLED);
            }
            return InteractionResult.PASS;
        });
        FxEvents.BLAST.add(new FxEvents.Blast() {
            @Override
            public boolean listening() {
                return enabled() && rings.on();
            }

            @Override
            public void blast(double x, double y, double z, boolean crystal) {
                ring(FxWorld.POOL, x, crystal ? y : y - 0.5, z, crystal, 0f);
            }
        });
    }

    /** A crystal set down ({@code placed}) or a blast at (x, y, z), {@code delay} seconds from now. */
    private void ring(FxPool pool, double x, double y, double z, boolean placed, float delay) {
        Fx f = start(pool, rings, RINGS, x, y, z, placed ? PLACED_LIFE : BLAST_LIFE);
        rings.apply(f, 0);
        f.i0 = placed ? 1 : 0;
        f.c = ground(pool, x, z, y + 0.5) - (float) y;
        FxPool.delay(f, delay);
    }

    @Override
    public float tryIt(FxPool pool, FxPreview.Dummy dummy) {
        if (!rings.on()) {
            return 0.5f;
        }
        // A crystal set down at its feet, then going off.
        ring(pool, dummy.x, dummy.y + 0.2, dummy.z, true, 0f);
        ring(pool, dummy.x, dummy.y + 0.2, dummy.z, false, 0.55f);
        return 2f;
    }

    private static final float PLACED_LIFE = 0.7f, BLAST_LIFE = 1.5f;

    /**
     * A crystal set down: a ring of light snaps out over the ground under it and a hoop runs up its height, with a
     * flash and a few glints. A blast: a white-hot flash and flare, a fireball swelling and going dark into smoke, two
     * shock rings racing out and rising, sparks thrown wide and a scorch left on the ground.
     */
    static final FxKind RINGS = new FxKind("crystal_rings", 12) {
        @Override
        public void draw(Fx fx, float age, float now, FxOut out) {
            float t = fx.time(age);
            Vfx.origin(out, fx);
            float s = fx.size, gy = fx.c, e = fx.early(now), gk = FxBits.glowK(fx);
            int c = fx.tone(Ramp.MAIN, 0, now), c2 = fx.tone(Ramp.MAIN, 1, now);
            if (fx.i0 == 1) {
                placed(fx, t, out, s, gy, e, gk, c, c2);
            } else {
                blast(fx, t, out, s, gy, e, gk, c, c2);
            }
        }
    };

    private static void placed(Fx fx, float t, FxOut out, float s, float gy, float e, float gk, int c, int c2) {
        boolean glow = out.glowPass();
        int hot = Vfx.white(c, 0.65f);
        // The ring snapping out under it.
        if (t < 0.45f) {
            float k = t / 0.45f;
            float r = (0.25f + 1.5f * Vfx.outExpo(k)) * s;
            float a = (float) Math.pow(1f - k, 1.5f) * e;
            if (glow) {
                Vfx.ring(out, FxSoft.FRONT, 0f, gy + 0.03f, 0f, r, 0.55f * s, Vfx.with(Vfx.white(c, 0.25f), a * gk), true);
                Vfx.pool(out, 0f, gy, 0f, 1.6f * s, Vfx.with(c, 0.6f * a * gk));
            } else {
                Vfx.ring(out, FxSoft.TUBE, 0f, gy + 0.03f, 0f, r, 0.06f * s, Vfx.with(hot, 0.6f * a), true);
            }
        }
        // The hoop running up its height.
        if (t < 0.55f) {
            float k = t / 0.55f;
            float y = gy + 2.1f * s * Vfx.outCubic(k);
            float r = (0.75f - 0.35f * k) * s;
            float a = Vfx.life(t, 0.55f, 0.05f, 0.3f) * e;
            if (glow) {
                Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, 0.16f * s, Vfx.with(c2, a * gk), false);
                Vfx.glow(out, 0f, y, 0f, r * 1.6f, Vfx.with(c2, 0.25f * a * gk));
            } else {
                Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, 0.045f * s, Vfx.with(Vfx.white(c2, 0.5f), 0.7f * a), false);
            }
        }
        if (glow && t < 0.25f) {
            float k = t / 0.25f, f = (1f - k) * (1f - k);
            Vfx.hot(out, 0f, gy + 1f * s, 0f, 1.1f * s, Vfx.with(hot, 0.8f * f * e * gk));
            Vfx.glint(out, 0f, gy + 1f * s, 0f, 1.4f * s, 0.3f + t, Vfx.with(hot, 0.9f * f * e * gk));
        }
        FxBits.embers(out, fx, t, 0f, gy + 0.2f, 0f, 9, 0.7f * s, 2.2f, 0.15f, 0.5f, c2, s, 3);
    }

    private static void blast(Fx fx, float t, FxOut out, float s, float gy, float e, float gk, int c, int c2) {
        boolean glow = out.glowPass();
        float y0 = gy + 0.9f * s;
        int hot = Vfx.white(c, 0.7f), dark = Vfx.mix(c, 0xFF1A1414, 0.75f);
        FxBits.impact(out, fx, t, 0f, y0, 0f, gy, 1.15f * s, c);
        // The fireball: white-hot, swelling, cooling to the colour and gone.
        if (t < 0.7f) {
            float k = t / 0.7f;
            float r = (0.9f + 1.9f * Vfx.outCubic(k)) * s;
            float a = (float) Math.pow(1f - k, 1.6f) * e;
            if (glow) {
                Vfx.glow(out, 0f, y0 + 0.4f * k * s, 0f, r * 1.7f, Vfx.with(Vfx.mix(hot, c, k), 0.75f * a * gk));
                Vfx.hot(out, 0f, y0 + 0.3f * k * s, 0f, r * 1.1f, Vfx.with(Vfx.mix(0xFFFFFFFF, c, k), (1f - k) * (1f - k) * e * gk));
                for (int i = 0; i < 5; i++) {
                    float ox = Vfx.s(fx.seed, i + 40) * 0.5f * s * (0.4f + k), oy = Vfx.s(fx.seed, i + 50) * 0.4f * s * (0.4f + k),
                            oz = Vfx.s(fx.seed, i + 60) * 0.5f * s * (0.4f + k);
                    Vfx.hot(out, ox, y0 + oy + 0.5f * k * s, oz, r * 0.6f, Vfx.with(Vfx.white(c, 0.3f), 0.6f * a * gk));
                }
            } else {
                for (int i = 0; i < 7; i++) {
                    float ox = Vfx.s(fx.seed, i + 40) * 0.6f * s * (0.4f + k), oy = Vfx.s(fx.seed, i + 50) * 0.45f * s * (0.4f + k),
                            oz = Vfx.s(fx.seed, i + 60) * 0.6f * s * (0.4f + k);
                    Vfx.sprite(out, FxSoft.SMOKE[i & 3], ox, y0 + oy + 0.5f * k * s, oz, r * 0.55f, Vfx.h(fx.seed, i + 70) * 6f + k,
                            Vfx.with(Vfx.mix(Vfx.white(c, 0.6f), dark, Math.min(1f, k * k * 2.2f)), 0.6f * a));
                }
            }
        }
        // Two shock rings: out over the ground and rising, the second a moment later, smaller and higher.
        for (int k = 0; k < 2; k++) {
            float u = (t - k * 0.09f) / 0.6f;
            if (u <= 0f || u >= 1f) {
                continue;
            }
            float r = (0.3f + (k == 0 ? 3.6f : 2.5f) * Vfx.outExpo(u)) * s;
            float y = gy + 0.05f + (k == 0 ? 0.35f : 1.1f) * s * Vfx.outCubic(u);
            float a = (float) Math.pow(1f - u, 1.4f) * e;
            int col = k == 0 ? c : c2;
            if (glow) {
                Vfx.ring(out, FxSoft.FRONT, 0f, y, 0f, r, (0.9f - 0.4f * u) * s, Vfx.with(Vfx.white(col, 0.3f), a * gk), k == 0);
            } else {
                Vfx.ring(out, FxSoft.TUBE, 0f, y, 0f, r, 0.07f * s, Vfx.with(Vfx.white(col, 0.55f), 0.55f * a), k == 0);
            }
        }
        FxBits.sparks(out, fx, t, 0f, y0, 0f, 30, 9f * s, 0.45f, -14f, 1.4f, 0.95f, c, s, gy, 5);
        FxBits.smoke(out, fx, t - 0.12f, 0f, y0, 0f, 7, 0.7f * s, 1.1f * s, 0.2f, 1.25f, 0.55f * s, Vfx.mix(dark, 0xFF6E6A66, 0.5f), 0.4f * e, 9);
        FxBits.embers(out, fx, t - 0.1f, 0f, y0, 0f, 10, 1.1f * s, 1.6f, 0.3f, 1.0f, c, s, 11);
        if (!glow) {
            Vfx.decalGround(out, FxSoft.SCORCH, 0f, gy, 0f, 1.5f * s * Vfx.outCubic(Math.min(1f, t / 0.25f)), fx.seed * 0.3f,
                    Vfx.with(dark, 0.7f * Vfx.life(t, 1.5f, 0f, 0.6f) * e));
        }
    }

    private static String trimmed(double v) {
        return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
    }

    /** Whether the hit will break it: the game breaks a crystal on any hit that does damage (weakness can stop it). */
    private static boolean breaks(Player player, Entity crystal) {
        if (crystal.isInvulnerable() || player.isSpectator()) {
            return false;
        }
        return player.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0;
    }

    // ------------------------------------------------------------------ the crystals' look (EndCrystalRendererMixin)

    /** The crystals' size (1 = the game's). */
    public static float crystalScale() {
        CrystalPvp c = Modules.CRYSTAL_PVP;
        return c.enabled() ? (float) (c.size.get() / 100.0) : 1f;
    }

    /** How fast the crystals turn (1 = the game's): the Crystal part's setting in Sol Styles. */
    public static float crystalSpin() {
        return io.github.georgwav.cosmetics.sol.styles.Styles.crystalSpin();
    }

    /** Whether the crystals bob up and down: the Crystal part's setting in Sol Styles. */
    public static boolean crystalBounce() {
        return io.github.georgwav.cosmetics.sol.styles.Styles.crystalBounce();
    }

    /** The spin and bounce moved to Sol Styles' Crystal part: an older save's values go there once. */
    @Override
    protected void migrate(com.google.gson.JsonObject settings) {
        Float spin = null;
        Boolean bounce = null;
        if (settings.has("crystal_spin")) {
            try {
                spin = (float) (settings.get("crystal_spin").getAsDouble() / 100.0);
            } catch (RuntimeException e) {
                spin = null;
            }
            settings.remove("crystal_spin");
        }
        if (settings.has("crystal_bounce")) {
            try {
                bounce = settings.get("crystal_bounce").getAsBoolean();
            } catch (RuntimeException e) {
                bounce = null;
            }
            settings.remove("crystal_bounce");
        }
        if (spin != null || bounce != null) {
            io.github.georgwav.cosmetics.sol.styles.Styles.legacyCrystal(spin, bounce);
        }
    }

    // ------------------------------------------------------------------ totems

    /** The server removed these entities (CrystalRemovalMixin; on the network thread too). */
    public static void serverRemoved(it.unimi.dsi.fastutil.ints.IntList ids) {
        Map<Integer, Hidden> h = Modules.CRYSTAL_PVP.hidden;
        if (!h.isEmpty()) {
            for (int i = 0; i < ids.size(); i++) {
                h.remove(ids.getInt(i));
            }
        }
    }

    /** Puts back in view the crystals the server kept (the hit didn't count). */
    private void restoreKept(Minecraft mc) {
        if (hidden.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        hidden.entrySet().removeIf(e -> {
            Hidden h = e.getValue();
            if (h.level() != mc.level) {
                return true;
            }
            if (now - h.at() < CONFIRM_MS) {
                return false;
            }
            if (mc.level.getEntity(e.getKey()) == null) {
                EndCrystal back = new EndCrystal(mc.level, h.x(), h.y(), h.z());
                back.setId(e.getKey());
                back.setShowBottom(h.bottom());
                mc.level.addEntity(back);
            }
            return true;
        });
    }

    /** Someone popped a totem (the game's entity event 35, TotemPopMixin). */
    public static void popped(LivingEntity entity) {
        CrystalPvp c = Modules.CRYSTAL_PVP;
        if (!c.enabled() || !(entity instanceof Player p)) {
            return;
        }
        int n = c.popped.merge(p.getUUID(), 1, Integer::sum);
        if (!c.pops.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean you = p == mc.player;
        Component line = you ? Component.translatable("cosmeticsmod.module.crystal_pvp.you_popped", n)
                : Component.translatable("cosmeticsmod.module.crystal_pvp.popped", p.getName().getString(), n);
        //? if >=26.2 {
        /*mc.gui.hud.getChat().addClientSystemMessage(line);
        *///?} elif >=26.1 {
        /*mc.gui.getChat().addClientSystemMessage(line);
        *///?} else {
        mc.gui.getChat().addMessage(line);
        //?}
    }

    @Override
    protected void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            popped.clear();
            hidden.clear();
            warned = false;
            return;
        }
        restoreKept(mc);
        // Counts start again when someone dies or is gone.
        popped.keySet().removeIf(id -> {
            Player p = mc.level.getPlayerByUUID(id);
            return p == null || p.isDeadOrDying();
        });
        boolean missing = missingTotem(mc.player);
        if (missing && !warned) {
            warnedAt = System.currentTimeMillis();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.7f));
        }
        warned = missing;
    }

    /** No totem in the offhand while the inventory has one, in survival or adventure. */
    private boolean missingTotem(Player player) {
        if (!offhand.get() || player.isCreative() || player.isSpectator() || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
            return false;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(Items.TOTEM_OF_UNDYING)) {
                return true;
            }
        }
        return false;
    }

    /** Drawn over the HUD (Hud): the low health pulse round the edges and the offhand warning above the hotbar. */
    public static void drawHud(Canvas g, Minecraft mc, int w, int h) {
        CrystalPvp c = Modules.CRYSTAL_PVP;
        if (!c.enabled() || mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
            return;
        }
        float t = (System.currentTimeMillis() % 100000L) / 1000f;
        float health = mc.player.getHealth() + mc.player.getAbsorptionAmount();
        float at = (float) (c.lowAt.get() * 2.0);
        if (c.lowHealth.get() && health > 0f && health <= at) {
            // Stronger the lower it gets, pulsing like a heartbeat.
            float k = 1f - health / at;
            float a = (0.35f + 0.45f * k) * (0.7f + 0.3f * (float) Math.sin(t * (5f + 5f * k)));
            int rgb = c.lowColor.get() & 0xFFFFFF;
            int edge = Math.max(12, Math.min(w, h) / 6);
            int strips = 12;
            for (int i = 0; i < strips; i++) {
                float f = 1f - i / (float) strips;
                int col = (Math.round(a * f * f * 255f) << 24) | rgb;
                int s0 = edge * i / strips, s1 = edge * (i + 1) / strips;
                g.fill(s0, s0, w - s0, s1, col);
                g.fill(s0, h - s1, w - s0, h - s0, col);
                g.fill(s0, s1, s1, h - s1, col);
                g.fill(w - s1, s1, w - s0, h - s1, col);
            }
        }
        if (c.warned) {
            float since = (System.currentTimeMillis() - c.warnedAt) / 1000f;
            float a = since < 3f ? 1f : 0.55f + 0.45f * (float) Math.sin(t * 6f);
            String text = Component.translatable("cosmeticsmod.module.crystal_pvp.no_totem").getString();
            var font = mc.font;
            int tw = font.width(text);
            int x = (w - tw) / 2, y = h - 70;
            g.fill(x - 6, y - 4, x + tw + 6, y + 12, (Math.round(0.55f * a * 255f) << 24));
            g.text(font, text, x, y, (Math.round(a * 255f) << 24) | 0xFFD23F);
        }
    }

    @Override
    protected void onDisable(Minecraft mc) {
        popped.clear();
        warned = false;
        if (mc.level != null) {
            // Back in view at once: nothing would watch for them any more.
            hidden.replaceAll((id, h) -> new Hidden(0L, h.level(), h.x(), h.y(), h.z(), h.bottom()));
            restoreKept(mc);
        }
        hidden.clear();
    }
}
