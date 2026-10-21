package io.github.georgwav.cosmetics.sol.fx;

import io.github.georgwav.cosmetics.client.Module;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One place for what the effects react to (C.3): a kill (something you hit in the last three seconds dies, with a copy
 * of its last pose), a hit, a totem pop (anyone's, with their count), a jump and a landing, the elytra opening, closing
 * and a rocket, eating or drinking, a block's break progress, a module switched, the song changing, and low health.
 *
 * <p>A listener says whether it's listening (its module is on); a kind of event nobody listens to costs nothing: its
 * checks are skipped. Client side only, from your own view of the game.
 */
public final class FxEvents {
    /** A listener: whether it wants events now (its module is on). */
    public interface Listener {
        boolean listening();
    }

    public interface Kill extends Listener {
        void killed(LivingEntity dead, PoseSnapshot copy);
    }

    public interface Hit extends Listener {
        void hit(LivingEntity target, double x, double y, double z, boolean crit, float healthBefore);
    }

    /** Health lost by something you hit lately (the damage numbers). */
    public interface Damage extends Listener {
        void damaged(LivingEntity target, float amount, boolean crit);
    }

    public interface Totem extends Listener {
        void popped(LivingEntity who, int count);
    }

    public interface Jump extends Listener {
        void jumped(Player p);

        void landed(Player p, float fallHeight, float fallSpeed, BlockState on);
    }

    public interface Elytra extends Listener {
        void opened(Player p);

        void closed(Player p);

        void rocket(Player p);
    }

    public interface Eat extends Listener {
        void ate(LivingEntity who, ItemStack food);
    }

    public interface Break extends Listener {
        void progress(BlockPos pos, int stage);
    }

    public interface Toggle extends Listener {
        void toggled(Module m, boolean on);
    }

    public interface Song extends Listener {
        void changed(String title, String artist);
    }

    public interface LowHealth extends Listener {
        void low(float health);
    }

    /** An explosion near you, or an end crystal placed near you ({@code crystal}). */
    public interface Blast extends Listener {
        void blast(double x, double y, double z, boolean crystal);
    }

    public static final List<Kill> KILL = new ArrayList<>();
    public static final List<Hit> HIT = new ArrayList<>();
    public static final List<Damage> DAMAGE = new ArrayList<>();
    public static final List<Totem> TOTEM = new ArrayList<>();
    public static final List<Jump> JUMP = new ArrayList<>();
    public static final List<Elytra> ELYTRA = new ArrayList<>();
    public static final List<Eat> EAT = new ArrayList<>();
    public static final List<Break> BREAK = new ArrayList<>();
    public static final List<Toggle> TOGGLE = new ArrayList<>();
    public static final List<Song> SONG = new ArrayList<>();
    public static final List<LowHealth> LOW = new ArrayList<>();
    public static final List<Blast> BLAST = new ArrayList<>();

    /** Who you hit in the last three seconds (and how much health each had), and whether your last hit was a crit. */
    static final KillWindow WINDOW = new KillWindow(3000L);
    private static final JumpLand JUMPS = new JumpLand();
    private static final Map<UUID, Integer> POPS = new HashMap<>();
    private static boolean wasFlying, lowNow;
    private static int lastBreakStage = -1, eatTick;
    private static String song = "";
    private static boolean started;
    /** The last hit's crit, and when (ms), for the damage it does. */
    private static boolean lastCrit;
    private static long lastHitAt;
    private static final int[] DEAD = new int[KillWindow.SIZE];

    private FxEvents() {
    }

    static boolean any(List<? extends Listener> list) {
        for (int i = 0, n = list.size(); i < n; i++) {
            if (list.get(i).listening()) {
                return true;
            }
        }
        return false;
    }

    /** Registers the game hooks once. */
    public static void init() {
        if (started) {
            return;
        }
        started = true;
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            Minecraft mc = Minecraft.getInstance();
            if (level.isClientSide() && player == mc.player && entity instanceof LivingEntity living) {
                io.github.georgwav.cosmetics.sol.Guard.run(io.github.georgwav.cosmetics.sol.Feature.FX, () -> attacked(mc, player, living));
            }
            return InteractionResult.PASS;
        });
        // A rocket appearing beside you while you glide is yours.
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof net.minecraft.world.entity.projectile.FireworkRocketEntity) {
                rocketNear(entity);
            } else if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EndCrystal && near(entity.getX(), entity.getY(),
                    entity.getZ())) {
                blast(entity.getX(), entity.getY(), entity.getZ(), true);
            }
        });
    }

    private static void attacked(Minecraft mc, Player you, LivingEntity target) {
        boolean wantKill = any(KILL), wantHit = any(HIT), wantDamage = any(DAMAGE);
        if (!wantKill && !wantHit && !wantDamage) {
            return;
        }
        long now = System.currentTimeMillis();
        WINDOW.hit(target.getId(), now, target.getHealth());
        boolean crit = crit(you);
        lastCrit = crit;
        lastHitAt = now;
        if (wantHit) {
            HitResult hr = mc.hitResult;
            double x, y, z;
            if (hr instanceof EntityHitResult eh && eh.getEntity() == target) {
                x = hr.getLocation().x;
                y = hr.getLocation().y;
                z = hr.getLocation().z;
            } else {
                x = target.getX();
                y = target.getY() + target.getBbHeight() * 0.6;
                z = target.getZ();
            }
            for (Hit h : HIT) {
                if (h.listening()) {
                    h.hit(target, x, y, z, crit, target.getHealth());
                }
            }
        }
    }

    /** Within 32 blocks of you. */
    private static boolean near(double x, double y, double z) {
        Player you = Minecraft.getInstance().player;
        return you != null && you.distanceToSqr(x, y, z) < 32 * 32;
    }

    /** An explosion the server told us of (ExplosionMixin), or a crystal that appeared: near you only. */
    public static void blast(double x, double y, double z, boolean crystal) {
        if (!any(BLAST) || !near(x, y, z)) {
            return;
        }
        for (int i = 0, n = BLAST.size(); i < n; i++) {
            Blast b = BLAST.get(i);
            if (b.listening()) {
                b.blast(x, y, z, crystal);
            }
        }
    }

    /** Whether a hit is critical, as the game decides it. */
    public static boolean crit(Player p) {
        return p.fallDistance > 0f && !p.onGround() && !p.onClimbable() && !p.isInWater() && !p.isPassenger() && !p.isSprinting()
                && p.getAttackStrengthScale(0.5f) > 0.9f;
    }

    /** Someone popped a totem (TotemPopMixin). */
    public static void totem(LivingEntity who) {
        if (!any(TOTEM)) {
            return;
        }
        int n = POPS.merge(who.getUUID(), 1, Integer::sum);
        for (Totem t : TOTEM) {
            if (t.listening()) {
                t.popped(who, n);
            }
        }
    }

    /** A module was switched (Module.setEnabled). */
    public static void toggled(Module m, boolean on) {
        for (int i = 0, n = TOGGLE.size(); i < n; i++) {
            Toggle t = TOGGLE.get(i);
            if (t.listening()) {
                t.toggled(m, on);
            }
        }
    }

    /** Once a client tick. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            WINDOW.clear();
            POPS.clear();
            JUMPS.reset();
            return;
        }
        Player you = mc.player;
        PoseSnapshot.HISTORY.tick(you);
        long now = System.currentTimeMillis();
        // Kills and damage: who you hit lately.
        if (WINDOW.size() > 0) {
            boolean wantDamage = any(DAMAGE);
            for (int i = 0; i < WINDOW.size(); i++) {
                Entity e = mc.level.getEntity(WINDOW.id(i));
                if (e instanceof LivingEntity living && wantDamage) {
                    float lost = WINDOW.health(i) - living.getHealth();
                    if (lost > 0.01f) {
                        boolean crit = lastCrit && now - lastHitAt < 600L;
                        for (Damage d : DAMAGE) {
                            if (d.listening()) {
                                d.damaged(living, lost, crit);
                            }
                        }
                    }
                    WINDOW.setHealth(i, living.getHealth());
                }
            }
            int dead = WINDOW.poll(now, id -> {
                Entity e = mc.level.getEntity(id);
                return e instanceof LivingEntity l && (l.isDeadOrDying() || !l.isAlive());
            }, DEAD);
            if (dead > 0 && any(KILL)) {
                for (int k = 0; k < dead; k++) {
                    if (mc.level.getEntity(DEAD[k]) instanceof LivingEntity l) {
                        PoseSnapshot copy = PoseSnapshot.of(l, 1f);
                        for (Kill kl : KILL) {
                            if (kl.listening()) {
                                kl.killed(l, copy);
                            }
                        }
                    }
                }
            }
        }
        // Counts of totems start again when someone dies.
        if (!POPS.isEmpty()) {
            POPS.keySet().removeIf(id -> {
                Player p = mc.level.getPlayerByUUID(id);
                return p == null || p.isDeadOrDying();
            });
        }
        // Jumping and landing.
        if (any(JUMP)) {
            boolean busy = you.getAbilities().flying || you.isFallFlying() || you.isInWater() || you.onClimbable() || you.isPassenger();
            int edge = JUMPS.step(you.onGround(), you.getY(), you.getDeltaMovement().y, busy);
            if (edge == JumpLand.JUMP) {
                for (Jump j : JUMP) {
                    if (j.listening()) {
                        j.jumped(you);
                    }
                }
            } else if (edge == JumpLand.LAND) {
                BlockState on = mc.level.getBlockState(you.blockPosition().below());
                for (Jump j : JUMP) {
                    if (j.listening()) {
                        j.landed(you, JUMPS.fallHeight, JUMPS.fallSpeed, on);
                    }
                }
            }
        } else {
            JUMPS.reset();
        }
        // The elytra.
        boolean flying = you.isFallFlying();
        if (flying != wasFlying && any(ELYTRA)) {
            for (Elytra el : ELYTRA) {
                if (el.listening()) {
                    if (flying) {
                        el.opened(you);
                    } else {
                        el.closed(you);
                    }
                }
            }
        }
        wasFlying = flying;
        // Eating and drinking, every fourth tick of it, for anyone near you.
        if (any(EAT) && ++eatTick % 4 == 0) {
            for (Player p : mc.level.players()) {
                if (p.isUsingItem() && p.distanceToSqr(you) < 48 * 48) {
                    ItemStack use = p.getUseItem();
                    if (food(use)) {
                        for (Eat ea : EAT) {
                            if (ea.listening()) {
                                ea.ate(p, use);
                            }
                        }
                    }
                }
            }
        }
        // Breaking a block.
        if (any(BREAK) && mc.gameMode != null) {
            int stage = mc.gameMode.isDestroying() ? mc.gameMode.getDestroyStage() : -1;
            if (stage != lastBreakStage && stage >= 0 && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult bh) {
                for (Break b : BREAK) {
                    if (b.listening()) {
                        b.progress(bh.getBlockPos(), stage);
                    }
                }
            }
            lastBreakStage = stage;
        }
        // The song.
        if (any(SONG)) {
            var np = io.github.georgwav.cosmetics.sol.music.MusicService.now();
            String key = np == null ? "" : np.key();
            if (key != null && !key.equals(song)) {
                song = key;
                if (np != null && !key.isEmpty()) {
                    for (Song s : SONG) {
                        if (s.listening()) {
                            s.changed(np.title(), np.artist());
                        }
                    }
                }
            }
        }
        // Low health: three hearts or less, once each time it gets there.
        float hp = you.getHealth();
        boolean low = hp > 0f && hp <= 6f;
        if (low && !lowNow && any(LOW)) {
            for (LowHealth l : LOW) {
                if (l.listening()) {
                    l.low(hp);
                }
            }
        }
        lowNow = low;
    }

    /** A rocket used while gliding (the firework's own entity appearing beside you). */
    public static void rocketNear(Entity rocket) {
        Minecraft mc = Minecraft.getInstance();
        Player you = mc.player;
        if (you == null || !you.isFallFlying() || rocket.distanceToSqr(you) > 4.0 || !any(ELYTRA)) {
            return;
        }
        for (Elytra el : ELYTRA) {
            if (el.listening()) {
                el.rocket(you);
            }
        }
    }

    private static boolean food(ItemStack s) {
        if (s.isEmpty()) {
            return false;
        }
        var anim = s.getUseAnimation();
        String name = String.valueOf(anim).toLowerCase(java.util.Locale.ROOT);
        return name.equals("eat") || name.equals("drink");
    }

    // ------------------------------------------------------------------ the pure parts (unit-tested)

    /** Who you hit and when, for three seconds: the kill window. */
    static final class KillWindow {
        static final int SIZE = 16;
        private final long window;
        private final int[] ids = new int[SIZE];
        private final long[] at = new long[SIZE];
        private final float[] health = new float[SIZE];
        private int n;

        KillWindow(long windowMs) {
            this.window = windowMs;
        }

        int size() {
            return n;
        }

        int id(int i) {
            return ids[i];
        }

        float health(int i) {
            return health[i];
        }

        void setHealth(int i, float h) {
            health[i] = h;
        }

        void clear() {
            n = 0;
        }

        /** A hit on {@code id} at {@code now}: it's tracked (again) for the window. */
        void hit(int id, long now, float healthNow) {
            for (int i = 0; i < n; i++) {
                if (ids[i] == id) {
                    at[i] = now;
                    return;
                }
            }
            if (n == SIZE) {
                // Full: the one hit longest ago goes.
                int oldest = 0;
                for (int i = 1; i < n; i++) {
                    if (at[i] < at[oldest]) {
                        oldest = i;
                    }
                }
                remove(oldest);
            }
            ids[n] = id;
            at[n] = now;
            health[n] = healthNow;
            n++;
        }

        /**
         * Drops the ones hit longer ago than the window, and those that died: the dead go into {@code out}; returns
         * how many died (each kill counts once).
         */
        int poll(long now, java.util.function.IntPredicate dead, int[] out) {
            int found = 0;
            for (int i = n - 1; i >= 0; i--) {
                if (dead.test(ids[i])) {
                    if (now - at[i] <= window) {
                        out[found++] = ids[i];
                    }
                    remove(i);
                } else if (now - at[i] > window) {
                    remove(i);
                }
            }
            return found;
        }

        private void remove(int i) {
            n--;
            ids[i] = ids[n];
            at[i] = at[n];
            health[i] = health[n];
        }
    }

    /** Jumps and landings from your ground contact, height and vertical speed, tick by tick. */
    static final class JumpLand {
        static final int NONE = 0, JUMP = 1, LAND = 2;
        /** The fall of the last landing: blocks from the highest point, and the speed (blocks a tick) as it hit. */
        float fallHeight, fallSpeed;
        private boolean grounded = true, jumped, known;
        private double top;
        private double lastVy;
        private int airTicks;

        void reset() {
            known = false;
            grounded = true;
            jumped = false;
            airTicks = 0;
        }

        /**
         * The next tick's state; returns {@link #JUMP} when you left the ground going up, {@link #LAND} when you came
         * down from a jump or a fall of more than a block. {@code busy} (flying, gliding, swimming, climbing, riding)
         * starts over.
         */
        int step(boolean onGround, double y, double vy, boolean busy) {
            if (busy) {
                reset();
                return NONE;
            }
            if (!known) {
                known = true;
                grounded = onGround;
                top = y;
                lastVy = vy;
                return NONE;
            }
            int edge = NONE;
            if (grounded && !onGround) {
                airTicks = 0;
                top = y;
                jumped = vy > 0.2;
                if (jumped) {
                    edge = JUMP;
                }
            } else if (!grounded && onGround) {
                fallHeight = (float) Math.max(0.0, top - y);
                fallSpeed = (float) Math.max(0.0, -lastVy);
                if (jumped || fallHeight > 1.0f) {
                    edge = LAND;
                }
                jumped = false;
            }
            if (!onGround) {
                airTicks++;
                top = Math.max(top, y);
            }
            grounded = onGround;
            lastVy = vy;
            return edge;
        }
    }
}
