package io.github.georgwav.cosmetics.sol.music;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * The system's player on Linux (MPRIS, through {@code playerctl}): follows its song and state, and asks for the
 * position every two seconds while it plays (a seek shows up). Idle without playerctl.
 */
public final class MprisSource implements MusicSource {
    private static final String FORMAT = "{{status}}\t{{xesam:title}}\t{{xesam:artist}}\t{{xesam:album}}\t{{mpris:length}}\t{{position}}\t{{mpris:artUrl}}";
    private volatile Process follow;
    private volatile boolean up;
    private volatile NowPlaying last;
    private volatile Thread reader;
    private volatile Thread poller;

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
        poller = new Thread(this::poll, "Sol MPRIS position");
        poller.setDaemon(true);
        poller.start();
    }

    private void poll() {
        while (poller == Thread.currentThread()) {
            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                return;
            }
            NowPlaying n = last;
            if (!up || n == null || !n.playing()) {
                continue;
            }
            try {
                Process p = new ProcessBuilder("playerctl", "position").redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
                if (p.waitFor() == 0 && !out.isEmpty() && last == n) {
                    last = n.withPosition(Math.round(Double.parseDouble(out) * 1000.0), true);
                    MusicService.report(this, last);
                }
            } catch (IOException | NumberFormatException e) {
                // Keep counting on.
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void run() {
        Process p = null;
        try {
            p = new ProcessBuilder("playerctl", "--follow", "metadata", "--format", FORMAT).redirectErrorStream(true).start();
            follow = p;
            // Stopped while starting: this playerctl goes again at once (stop() may not have seen it).
            if (reader != Thread.currentThread()) {
                return;
            }
            up = true;
            try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null && reader == Thread.currentThread()) {
                    last = parse(line);
                    MusicService.report(this, last);
                }
            }
        } catch (IOException e) {
            // No playerctl: this source stays idle.
        } finally {
            if (p != null && reader != Thread.currentThread()) {
                p.destroy();
            }
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
        reader = null;
        Process p = follow;
        if (p != null) {
            p.destroy();
            follow = null;
        }
        if (poller != null) {
            poller.interrupt();
            poller = null;
        }
        last = null;
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
