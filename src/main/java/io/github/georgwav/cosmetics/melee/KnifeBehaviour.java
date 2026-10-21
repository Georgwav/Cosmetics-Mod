package io.github.georgwav.cosmetics.melee;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * What a knife does, as in its own game (the three packs share LR Tactical's default script, with per-knife
 * variations; this is that behaviour written out, the packs' scripts aren't run):
 * <ul>
 *   <li>equipping plays a random draw at once, and resets the light attacks' turn;</li>
 *   <li>a normal hit or a miss plays the light attacks in turn ({@code melee_1}, {@code melee_2}), each cross-fading from
 *       the last (about 0.08 s; the packs use 0.2 s, but Minecraft's hits are instant);</li>
 *   <li>a critical hit plays the heavy attack ({@code melee_3}), the right-click attack in all three games;</li>
 *   <li>the inspect key plays a random inspect; pressed again, even mid-inspect, a fresh random one starts at once
 *       (CS's butterfly flip spam), with no cross-fade;</li>
 *   <li>attacking, using, switching items or opening a screen cancels an inspect and stops its sounds;</li>
 *   <li>holding attack on a block repeats the light attack at Minecraft's swing rate, never restarting mid-strike.</li>
 * </ul>
 * Timing is fitted to Minecraft's sword ({@link KnifeTiming}) unless "original timing" is on. Pure Java: the clock,
 * the dice and the sounds come from outside; nothing is allocated per frame.
 */
public final class KnifeBehaviour {
    public enum Kind { NONE, DRAW, LIGHT, HEAVY, INSPECT }

    /** Plays and stops a knife's sounds ({@code namespace:path} keys). */
    public interface Sounds {
        /** Plays a sound; {@code owner} is the animation's start (so its sounds can be stopped with it). */
        void play(String key, long owner);

        /** Stops the sounds an animation started. */
        void stop(long owner);
    }

    /** Cross-fade into an attack, and into anything else. */
    static final float ATTACK_FADE = 0.07f, FADE = 0.12f;

    public final KnifeRig rig;
    private final RandomGenerator random;
    private final Sounds sounds;

    // The main animation: what, from when, its warp; and the one it fades out of.
    private KnifeClip main, prev;
    private Kind kind = Kind.NONE;
    private float mainStart, prevStart, fadeStart, fadeLength;
    private final KnifeTiming.Warp warp = new KnifeTiming.Warp(), prevWarp = new KnifeTiming.Warp();
    private long owner, prevOwner;
    private float lastClipTime = -1f;
    private boolean nextLightSecond;
    /** Dark Star: the Angel model for this draw (and what follows until the next). */
    private boolean angel;

    // This frame's sampled state, for the renderer.
    public KnifeClip outMain, outPrev;
    public float outMainT, outPrevT, outFade, outMainWeight = 1f;

    public KnifeBehaviour(KnifeRig rig, RandomGenerator random, Sounds sounds) {
        this.rig = rig;
        this.random = random;
        this.sounds = sounds;
    }

    public Kind kind() {
        return kind;
    }

    /** Whether the Angel model shows (Dark Star). */
    public boolean angel() {
        return angel;
    }

    /** The knife comes out: a random draw. {@code angelChoice}: 0 random each draw, 1 Devil, 2 Angel. */
    public void equip(float now, float cooldown, boolean fit, int angelChoice) {
        nextLightSecond = false;
        angel = rig.angelSet && (angelChoice == 2 || angelChoice == 0 && random.nextBoolean());
        KnifeClip d = pick(rig.draws);
        if (d == null) {
            stopMain();
            main = null;
            kind = Kind.NONE;
            return;
        }
        d = rig.angel(d, angel);
        float settle = settle(d);
        start(now, d, Kind.DRAW, 0f);
        if (fit) {
            KnifeTiming.draw(warp, settle, cooldown);
        } else {
            KnifeTiming.original(warp, d.length);
        }
    }

    /** An attack: the heavy one on a critical hit (when {@code heavyOnCrit}), else the next light one. */
    public void attack(float now, boolean crit, boolean heavyOnCrit, float cooldown, float swing, boolean fit) {
        KnifeClip c;
        Kind k;
        if (crit && heavyOnCrit && rig.heavy != null) {
            c = rig.heavy;
            k = Kind.HEAVY;
        } else {
            c = nextLightSecond && rig.light2 != null ? rig.light2 : rig.light1 != null ? rig.light1 : rig.light2;
            nextLightSecond = !nextLightSecond;
            k = Kind.LIGHT;
        }
        if (c == null) {
            return;
        }
        c = rig.angel(c, angel);
        start(now, c, k, ATTACK_FADE);
        float[] t = rig.timing.get(c.name);
        if (fit && t != null) {
            KnifeTiming.attack(warp, t[0], t[1], cooldown, swing);
        } else {
            KnifeTiming.original(warp, c.length);
        }
    }

    /**
     * Holding attack on a block: another light attack once the current one's strike has landed (or nothing plays),
     * so it repeats at the swing rate without restarting mid-strike.
     */
    public void swingRepeat(float now, float cooldown, float swing, boolean fit) {
        if (kind == Kind.LIGHT || kind == Kind.HEAVY) {
            if (now - mainStart < warp.real(impactOf(main))) {
                return;
            }
        }
        attack(now, false, false, cooldown, swing, fit);
    }

    /** The inspect key: a fresh random inspect at once. */
    public void inspect(float now) {
        KnifeClip c = pick(rig.inspects);
        if (c == null) {
            return;
        }
        c = rig.angel(c, angel);
        boolean again = kind == Kind.INSPECT;
        start(now, c, Kind.INSPECT, again ? 0f : FADE);
        KnifeTiming.original(warp, c.length);
    }

    /** Attacking, using, switching or a screen: an inspect stops, sounds and all. */
    public void cancelInspect(float now) {
        if (kind == Kind.INSPECT) {
            stopMain();
            prev = main;
            prevStart = mainStart;
            prevOwner = 0L;
            copy(warp, prevWarp);
            fadeStart = now;
            fadeLength = FADE;
            main = null;
            kind = Kind.NONE;
        }
    }

    /** Whether an inspect is playing. */
    public boolean inspecting() {
        return kind == Kind.INSPECT;
    }

    /** The draw is still playing (the knife isn't ready). */
    public boolean drawing() {
        return kind == Kind.DRAW;
    }

    /**
     * Moves time on to {@code now}: fires the main animation's sounds it passed, ends it once done, and sets this
     * frame's {@code out*} state.
     */
    public void update(float now) {
        if (main != null) {
            float tau = now - mainStart;
            if (tau >= warp.end) {
                // Done: the knife is back at rest; the base idle carries on.
                outPrev = main;
                outPrevT = warp.clip(Math.max(0f, warp.end));
                prev = null;
                main = null;
                kind = Kind.NONE;
                lastClipTime = -1f;
                outMain = null;
                outMainT = 0f;
                outFade = 1f;
                outMainWeight = 1f;
                outPrev = null;
                return;
            }
            float t = Math.min(warp.clip(tau), main.length);
            // Sounds whose time the animation passed this frame (all of them at the very start).
            for (int i = 0; i < main.sounds.length; i++) {
                float st = main.soundTimes[i];
                if (st <= t && st > lastClipTime || lastClipTime < 0f && st <= 0f) {
                    sounds.play(main.sounds[i], owner);
                }
            }
            lastClipTime = t;
            outMain = main;
            outMainT = t;
            outMainWeight = warp.weight(tau);
        } else {
            outMain = null;
            outMainT = 0f;
            outMainWeight = 1f;
        }
        if (prev != null) {
            float f = fadeLength <= 0f ? 1f : (now - fadeStart) / fadeLength;
            if (f >= 1f) {
                prev = null;
            } else {
                outPrev = prev;
                outPrevT = Math.min(prevWarp.clip(now - prevStart), prev.length);
                outFade = Math.max(0f, f);
                return;
            }
        }
        outPrev = null;
        outFade = 1f;
    }

    private void start(float now, KnifeClip c, Kind k, float fade) {
        // What's playing fades out (its sounds cut off with it).
        stopMain();
        if (main != null && fade > 0f) {
            prev = main;
            prevStart = mainStart;
            copy(warp, prevWarp);
            fadeStart = now;
            fadeLength = fade;
        } else {
            prev = null;
        }
        main = c;
        kind = k;
        mainStart = now;
        lastClipTime = -1f;
        owner = ownerId(now);
    }

    private long ownerSeq;

    private long ownerId(float now) {
        return ++ownerSeq;
    }

    private void stopMain() {
        if (main != null && owner != 0L) {
            sounds.stop(owner);
        }
    }

    private KnifeClip pick(List<KnifeClip> from) {
        if (from.isEmpty()) {
            return null;
        }
        return from.get(from.size() == 1 ? 0 : random.nextInt(from.size()));
    }

    private float settle(KnifeClip c) {
        float[] t = rig.timing.get(c.name);
        return t != null ? t[1] : c.length;
    }

    private float impactOf(KnifeClip c) {
        float[] t = c == null ? null : rig.timing.get(c.name);
        return t != null ? t[0] : 0f;
    }

    private static void copy(KnifeTiming.Warp from, KnifeTiming.Warp to) {
        to.set(from.tauMid, from.clipMid, from.k1, from.k2, from.end, from.fadeStart, from.fadeEnd);
    }
}
