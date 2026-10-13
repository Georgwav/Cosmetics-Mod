package io.github.georgwav.cosmetics.render;

import io.github.georgwav.cosmetics.model.CosmeticDefinition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How a player's wings flap right now. Each player has four smoothed weights (standing, moving, flying,
 * sneaking) that follow what they do, and the time spent in each. A cosmetic's flap is its per-state
 * settings mixed by those weights, and its phase is each state's speed times the time spent in it, so the
 * flap speeds up and slows down smoothly instead of jumping when the player starts running or flying.
 * None of this depends on the cosmetic, so one small state per player serves every pair of wings.
 *
 * <p>Plain maths without Minecraft types (unit-tested). Render thread only.
 */
public final class WingMotion {
    /** Horizontal speed, in blocks per tick, that counts as fully "moving" (sprinting is about 0.28). */
    static final float FULL_SPEED = 0.25f;
    /** Seconds for the weights to get about two thirds of the way to a new state. */
    static final float SMOOTHING = 0.2f;
    private static final int MAX_PLAYERS = 512;

    /** The flap for one cosmetic, reused between calls: read it before the next {@link #pose}. */
    public static final class Pose {
        /** Degrees each wing turns outwards from its modelled position (resting angle plus flap). */
        public float angle;
        /** The resting angle alone, for when animation is off. */
        public float base;
        /** Flap cycles so far (only the fraction matters), for skeletal wings that play one clip per flap. */
        public double phase;
        /** 0..1, how much the player is sneaking (for the sneak offset). */
        public float sneak;
    }

    private static final class State {
        float standing = 1f;
        float moving;
        float flying;
        float sneaking;
        double standingTime;
        double movingTime;
        double flyingTime;
        double sneakingTime;
        long lastNanos;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();
    private static final Pose POSE = new Pose();

    private WingMotion() {
    }

    /**
     * Brings the player's state up to {@code nanos} and returns the flap of {@code wings} for them.
     *
     * @param moveSpeed horizontal speed in blocks per tick
     * @param flying    creative flight or gliding with an elytra
     */
    public static Pose pose(UUID player, float moveSpeed, boolean sneaking, boolean flying,
                            CosmeticDefinition.Wings wings, long nanos) {
        State s = STATES.get(player);
        if (s == null) {
            if (STATES.size() >= MAX_PLAYERS) {
                STATES.clear();
            }
            s = new State();
            s.lastNanos = nanos;
            STATES.put(player, s);
        }
        advance(s, moveSpeed, sneaking, flying, nanos);
        CosmeticDefinition.WingState idle = wings.idle();
        CosmeticDefinition.WingState moving = wings.moving();
        CosmeticDefinition.WingState fly = wings.flying();
        CosmeticDefinition.WingState sneak = wings.sneaking();
        float base = s.standing * idle.base() + s.moving * moving.base() + s.flying * fly.base() + s.sneaking * sneak.base();
        float amplitude = s.standing * idle.amplitude() + s.moving * moving.amplitude() + s.flying * fly.amplitude()
                + s.sneaking * sneak.amplitude();
        double phase = idle.speed() * s.standingTime + moving.speed() * s.movingTime + fly.speed() * s.flyingTime
                + sneak.speed() * s.sneakingTime;
        Pose pose = POSE;
        pose.phase = phase;
        pose.base = base;
        pose.angle = base + amplitude * (float) Math.sin((phase - Math.floor(phase)) * 2.0 * Math.PI);
        pose.sneak = s.sneaking;
        return pose;
    }

    /** Moves the weights towards what the player does now and adds the elapsed time to each state. */
    static void advance(State s, float moveSpeed, boolean sneaking, boolean flying, long nanos) {
        double dt = Math.max(0.0, Math.min(0.25, (nanos - s.lastNanos) / 1.0e9));
        s.lastNanos = nanos;
        if (dt == 0.0) {
            return;
        }
        float tStanding = 0f, tMoving = 0f, tFlying = 0f, tSneaking = 0f;
        if (flying) {
            tFlying = 1f;
        } else if (sneaking) {
            tSneaking = 1f;
        } else {
            tMoving = Math.max(0f, Math.min(1f, moveSpeed / FULL_SPEED));
            tStanding = 1f - tMoving;
        }
        float k = (float) (1.0 - Math.exp(-dt / SMOOTHING));
        s.standing += (tStanding - s.standing) * k;
        s.moving += (tMoving - s.moving) * k;
        s.flying += (tFlying - s.flying) * k;
        s.sneaking += (tSneaking - s.sneaking) * k;
        s.standingTime += s.standing * dt;
        s.movingTime += s.moving * dt;
        s.flyingTime += s.flying * dt;
        s.sneakingTime += s.sneaking * dt;
    }

    /** Forgets every player (world change, resource reload). */
    public static void reset() {
        STATES.clear();
    }
}
