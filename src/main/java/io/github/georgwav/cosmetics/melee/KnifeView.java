package io.github.georgwav.cosmetics.melee;

import java.util.random.RandomGenerator;

/**
 * One knife in your hand: what it's doing ({@link KnifeBehaviour}) and its pose this frame. The base track is
 * {@code static_idle}, looping (the Classic Knife's {@code static_idle_1} takes a turn after each loop); {@code idle}
 * is blended on top all the time, and movement when the caller sets {@link #move}. A main animation easing out at its
 * end (see {@link KnifeTiming}) hands over to the base. Pure Java; nothing allocated per frame.
 */
public final class KnifeView {
    public final Knife knife;
    public final KnifeBehaviour behaviour;
    public final KnifePose pose;
    /** The blended {@code idle}, and movement (set by the caller: clip, time and weight). */
    public final KnifePose.Blend idle = new KnifePose.Blend(), move = new KnifePose.Blend();
    private final KnifePose.Blend[] blends = {idle, move};
    /** The eye in knife space: the model's, moved by the knife's camera fix. */
    public final float[] eye = new float[3];
    private float baseStart = Float.NaN;
    private boolean alt;

    public KnifeView(Knife knife, RandomGenerator random, KnifeBehaviour.Sounds sounds) {
        this.knife = knife;
        this.behaviour = new KnifeBehaviour(knife.rig, random, sounds);
        this.pose = new KnifePose(knife.rig.geo);
        System.arraycopy(knife.rig.eye, 0, eye, 0, 3);
        float[] c = knife.spec.camera();
        if (c != null) {
            eye[0] += c[0];
            eye[1] += c[1];
            eye[2] += c[2];
        }
    }

    /** Moves the knife on to {@code now} (seconds) and poses it; {@code showHands}: show hands the pack hides. */
    public void frame(float now, boolean showHands) {
        KnifeRig rig = knife.rig;
        behaviour.update(now);
        boolean angel = behaviour.angel();
        if (Float.isNaN(baseStart)) {
            baseStart = now;
        }
        KnifeClip base = rig.angel(alt && rig.staticIdleAlt != null ? rig.staticIdleAlt : rig.staticIdle, angel);
        float baseT = 0f;
        if (base != null && base.length > 0f) {
            baseT = now - baseStart;
            if (baseT >= base.length) {
                int laps = (int) (baseT / base.length);
                baseStart += laps * base.length;
                baseT -= laps * base.length;
                if (rig.staticIdleAlt != null && (laps & 1) == 1) {
                    alt = !alt;
                    base = rig.angel(alt ? rig.staticIdleAlt : rig.staticIdle, angel);
                    baseT = Math.min(baseT, base != null ? base.length : 0f);
                }
            }
        }
        KnifeClip idleClip = rig.angel(rig.idle, angel);
        idle.clip = idleClip;
        idle.weight = 1f;
        idle.time = idleClip != null && idleClip.length > 0f ? now % idleClip.length : 0f;
        pose.handFix = showHands ? rig.handFix : null;
        KnifeBehaviour b = behaviour;
        if (b.outPrev == null && b.outMain != null && b.outMainWeight < 1f) {
            // Easing out at the end: the main animation fades into the base.
            pose.pose(base, baseT, b.outMain, b.outMainT, null, 0f, 1f - b.outMainWeight, blends);
        } else {
            pose.pose(base, baseT, b.outPrev, b.outPrevT, b.outMain, b.outMainT, b.outFade, blends);
        }
    }
}
