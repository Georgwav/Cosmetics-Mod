package io.github.georgwav.cosmetics.sol.music;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The system's player on Linux (MPRIS, through {@code playerctl}): follows its song and state, and asks for the
 * position every two seconds. Idle without playerctl.
 */
public final class MprisSource implements MusicSource {
    private static final String FORMAT = "{{status}}\t{{xesam:title}}\t{{xesam:artist}}\t{{xesam:album}}\t{{mpris:length}}\t{{position}}\t{{mpris:artUrl}}";
    private volatile Process follow;
    private volatile boolean up;
    private Thread reader;

    @Override
    public String id() {
        return "mpris";
    }

    @Override
    public void start() {
        if (reader != null || !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("linux")) {
            return;
        }
        reader = new Thread(this::run, "Sol MPRIS");
        reader.setDaemon(true);
        reader.start();
    }

    private void run() {
        try {
            follow = new ProcessBuilder("playerctl", "--follow", "metadata", "--format", FORMAT).redirectErrorStream(true).start();
            up = true;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(follow.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    MusicService.report(this, parse(line));
                }
            }
        } catch (IOException e) {
            // No playerctl: this source stays idle.
        } finally {
            up = false;
        }
    }

    /** One line of playerctl's format: status, title, artist, album, length µs, position µs, cover. */
    static NowPlaying parse(String line) {
        String[] f = line.split("\t", -1);
        if (f.length < 7 || f[1].isBlank()) {
            return null;
        }
        long length = parseLong(f[4]) / 1000L, position = parseLong(f[5]) / 1000L;
        String art = f[6].isBlank() ? null : f[6];
        return new NowPlaying("mpris", "mpris:" + f[1] + "|" + f[2], f[1], f[2], f[3], length, position, System.nanoTime(),
                "Playing".equalsIgnoreCase(f[0]), art, null);
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public void stop() {
        if (follow != null) {
            follow.destroy();
            follow = null;
        }
        reader = null;
        up = false;
    }

    @Override
    public boolean connected() {
        return up;
    }

    private static void run(String... cmd) {
        try {
            new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            // Not there.
        }
    }

    @Override
    public void playPause() {
        run("playerctl", "play-pause");
    }

    @Override
    public void next() {
        run("playerctl", "next");
    }

    @Override
    public void previous() {
        run("playerctl", "previous");
    }
}
