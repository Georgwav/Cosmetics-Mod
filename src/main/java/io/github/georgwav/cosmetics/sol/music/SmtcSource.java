package io.github.georgwav.cosmetics.sol.music;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Windows' media session (SMTC: what the volume flyout shows for any player): a PowerShell helper reads the current
 * session through WinRT once a second and prints one JSON line; the controls are one-shot PowerShell calls. Idle on
 * other systems or when PowerShell can't reach WinRT. No covers (Windows keeps them as streams).
 */
public final class SmtcSource implements MusicSource {
    private static final String PRELUDE = "$ErrorActionPreference='Stop';"
            + "Add-Type -AssemblyName System.Runtime.WindowsRuntime;"
            + "$as=([System.WindowsRuntimeSystemExtensions].GetMethods()|?{$_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1"
            + " -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'})[0];"
            + "function Await($o,$t){$k=$as.MakeGenericMethod($t).Invoke($null,@($o));$k.Wait(-1)|Out-Null;$k.Result};"
            + "[Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]|Out-Null;"
            + "$m=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync())"
            + " ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]);";
    private static final String LOOP = PRELUDE + "while($true){$s=$m.GetCurrentSession();if($s){"
            + "$p=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]);"
            + "$t=$s.GetTimelineProperties();$b=$s.GetPlaybackInfo();"
            + "[pscustomobject]@{app=$s.SourceAppUserModelId;title=$p.Title;artist=$p.Artist;album=$p.AlbumTitle;"
            + "pos=[int64]$t.Position.TotalMilliseconds;dur=[int64]$t.EndTime.TotalMilliseconds;"
            + "playing=($b.PlaybackStatus -eq 'Playing')}|ConvertTo-Json -Compress}else{'{}'};"
            + "[Console]::Out.Flush();Start-Sleep -Milliseconds 1000}";
    private volatile Process helper;
    private volatile boolean up;
    private Thread reader;

    @Override
    public String id() {
        return "smtc";
    }

    static boolean windows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public void start() {
        if (reader != null || !windows()) {
            return;
        }
        reader = new Thread(this::run, "Sol SMTC");
        reader.setDaemon(true);
        reader.start();
    }

    private void run() {
        try {
            helper = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", LOOP)
                    .redirectError(ProcessBuilder.Redirect.DISCARD).start();
            try (BufferedReader in = new BufferedReader(new InputStreamReader(helper.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = in.readLine()) != null) {
                    up = true;
                    MusicService.report(this, parse(line));
                }
            }
        } catch (IOException e) {
            // No PowerShell: this source stays idle.
        } finally {
            up = false;
        }
    }

    /** One helper line as what's playing; null for none (or for an app another source already follows). */
    static NowPlaying parse(String line) {
        try {
            JsonObject o = JsonParser.parseString(line).getAsJsonObject();
            if (!o.has("title") || o.get("title").isJsonNull() || o.get("title").getAsString().isBlank()) {
                return null;
            }
            String app = o.has("app") && !o.get("app").isJsonNull() ? o.get("app").getAsString().toLowerCase(Locale.ROOT) : "";
            // Cider and Spotify have sources of their own, with lyrics and covers.
            if (app.contains("cider") || app.contains("spotify")) {
                return null;
            }
            String title = o.get("title").getAsString(), artist = str(o, "artist"), album = str(o, "album");
            long pos = o.has("pos") ? o.get("pos").getAsLong() : 0L, dur = o.has("dur") ? o.get("dur").getAsLong() : 0L;
            boolean playing = o.has("playing") && o.get("playing").getAsBoolean();
            return new NowPlaying("smtc", "smtc:" + title + "|" + artist, title, artist, album, dur, pos, System.nanoTime(), playing, null, null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }

    @Override
    public void stop() {
        if (helper != null) {
            helper.destroy();
            helper = null;
        }
        reader = null;
        up = false;
    }

    @Override
    public boolean connected() {
        return up;
    }

    private static void control(String call) {
        Thread t = new Thread(() -> {
            try {
                new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                        PRELUDE + "$s=$m.GetCurrentSession();if($s){Await ($s." + call + "()) ([bool])|Out-Null}")
                        .redirectErrorStream(true).start().waitFor();
            } catch (IOException e) {
                // Not there.
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Sol SMTC control");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void playPause() {
        control("TryTogglePlayPauseAsync");
    }

    @Override
    public void next() {
        control("TrySkipNextAsync");
    }

    @Override
    public void previous() {
        control("TrySkipPreviousAsync");
    }
}
