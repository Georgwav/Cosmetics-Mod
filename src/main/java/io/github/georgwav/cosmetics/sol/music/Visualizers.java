package io.github.georgwav.cosmetics.sol.music;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.hud.MusicHud;
import io.github.georgwav.cosmetics.sol.atmosphere.MusicSky;
import io.github.georgwav.cosmetics.sol.config.SolConfig;

/**
 * The music in the sky (render thread, once per sky frame): while a song plays with the Music module on and
 * visualizers allowed, listens ({@link AudioCapture}) and turns the spectrum into what the sky reads from
 * {@link MusicSky}: the star pulse, the horizon EQ (40 columns), the bass aurora, beat rings round the sun, the album
 * sun, music notes among the particles and the horizon band breathing. Each has its own switch in the Music module.
 */
public final class Visualizers {
    public static final int EQ_COLUMNS = 40;
    private static final float[] eq = new float[EQ_COLUMNS];
    private static long lastNanos;
    private static long seenBeat;
    private static long ringStart = Long.MIN_VALUE;
    private static int beats;

    private Visualizers() {
    }

    public static void frame() {
        MusicHud m = Modules.MUSIC;
        NowPlaying n = MusicService.now();
        boolean on = m.enabled() && SolConfig.get().music.visualizers && n != null && n.playing();
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (!on) {
            off();
            return;
        }
        AudioCapture.want();
        Spectrum s = AudioCapture.latest();
        boolean live = s.atNanos() != 0L && now - s.atNanos() < 500_000_000L;
        // Without sound to hear (no capture here): a calm idle sway, so the switches still show something.
        double t = now / 1e9;
        float level = live ? s.level() : 0.25f + 0.1f * (float) Math.sin(t * 1.3);
        float bass = live ? s.bass() : 0.2f + 0.1f * (float) Math.sin(t * 0.9);
        float high = live ? s.high() : 0.2f;
        float k = Math.min(1f, dt * 14f);
        MusicSky.level += (level - MusicSky.level) * k;
        MusicSky.bass += (bass - MusicSky.bass) * k;
        MusicSky.high += (high - MusicSky.high) * k;
        if (live && s.beatNanos() != 0L && s.beatNanos() != seenBeat) {
            seenBeat = s.beatNanos();
            beats++;
            MusicSky.beat = 1f;
            ringStart = now;
        } else {
            MusicSky.beat *= (float) Math.exp(-dt / 0.15f);
        }
        MusicSky.stars = m.viz("stars");
        MusicSky.aurora = m.viz("aurora");
        MusicSky.breathe = m.viz("breathe");
        MusicSky.sunBeat = m.viz("rings");
        MusicSky.notes = m.viz("notes");
        MusicSky.ringPhase = m.viz("rings") && ringStart != Long.MIN_VALUE ? Math.min(2f, (now - ringStart) / 0.9e9f) : 2f;
        Covers.Cover c = m.viz("album_sun") ? Covers.current() : null;
        MusicSky.album = c == null ? null : c.sun();
        if (m.viz("eq")) {
            float[] bands = s.bands();
            for (int i = 0; i < EQ_COLUMNS; i++) {
                // Low notes in the middle of the view's horizon ring, both ways round (a mirrored EQ).
                float f = Math.abs(i - (EQ_COLUMNS - 1) / 2f) / (EQ_COLUMNS / 2f);
                float pos = f * (Fft.BANDS - 1);
                int b = (int) pos;
                float v = live ? bands[b] + (bands[Math.min(Fft.BANDS - 1, b + 1)] - bands[b]) * (pos - b)
                        : 0.15f + 0.1f * (float) Math.sin(t * 2.0 + i * 0.6);
                eq[i] += (v - eq[i]) * Math.min(1f, dt * (v > eq[i] ? 30f : 8f));
            }
            MusicSky.eq = eq;
        } else {
            MusicSky.eq = null;
        }
    }

    /** How many beats the sky has shown (for the scenario's check). */
    public static int beats() {
        return beats;
    }

    private static void off() {
        MusicSky.level = 0f;
        MusicSky.beat = 0f;
        MusicSky.bass = 0f;
        MusicSky.high = 0f;
        MusicSky.stars = false;
        MusicSky.aurora = false;
        MusicSky.breathe = false;
        MusicSky.sunBeat = false;
        MusicSky.notes = false;
        MusicSky.album = null;
        MusicSky.eq = null;
        MusicSky.ringPhase = 2f;
    }
}
