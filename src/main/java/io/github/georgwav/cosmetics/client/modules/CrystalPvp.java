package io.github.georgwav.cosmetics.client.modules;

import io.github.georgwav.cosmetics.client.Module;
import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.Setting;
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
 * sooner; one the server keeps comes back after a second and a half); the crystals' size, spin and bounce; a line in chat when anyone near you pops a totem, with their count;
 * a warning when your offhand has no totem but your inventory does; and a red pulse round the screen when your health
 * runs low. All on your side only: nothing is sent that the game doesn't send anyway.
 */
public final class CrystalPvp extends Module {
    final Setting.Toggle optimizer = add(new Setting.Toggle("optimizer", true).group("crystal"));
    final Setting.Slider size = add(new Setting.Slider("crystal_size", 100, 25, 150, 5, v -> Math.round(v) + "%").group("crystal"));
    final Setting.Slider spin = add(new Setting.Slider("crystal_spin", 100, 0, 300, 10, v -> Math.round(v) + "%").group("crystal"));
    final Setting.Toggle bounce = add(new Setting.Toggle("crystal_bounce", true).group("crystal"));
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

    /** How fast the crystals turn (1 = the game's). */
    public static float crystalSpin() {
        CrystalPvp c = Modules.CRYSTAL_PVP;
        return c.enabled() ? (float) (c.spin.get() / 100.0) : 1f;
    }

    /** Whether the crystals bob up and down. */
    public static boolean crystalBounce() {
        CrystalPvp c = Modules.CRYSTAL_PVP;
        return !c.enabled() || c.bounce.get();
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
