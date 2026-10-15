package io.github.georgwav.cosmetics.sol.client;

import com.google.gson.JsonObject;
import io.github.georgwav.cosmetics.CosmeticsMod;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Discord status through Discord's local IPC (no library): a Unix socket ({@code discord-ipc-0..9} in the runtime or
 * temp folder, Flatpak and Snap ones too) or the Windows pipe of that name; frames are a little-endian opcode and
 * length then JSON. Handshake with the player's Discord application id, then SET_ACTIVITY. One thread; a closed
 * Discord is tried again every 15 seconds. Framing is pure and unit tested.
 */
public final class DiscordRpc {
    static final int HANDSHAKE = 0, FRAME = 1, CLOSE = 2;

    /** What to show: two lines, when the session started (ms), and a large image key (or null). */
    public record Activity(String details, String state, long startMs, String image, String imageText) {
    }

    private interface Pipe extends AutoCloseable {
        void write(byte[] b) throws IOException;

        int read(byte[] b, int off, int len) throws IOException;

        @Override
        void close() throws IOException;
    }

    private static volatile Activity wanted;
    private static volatile String appId = "";
    private static volatile boolean running;
    private static Thread thread;
    private static volatile boolean connected;

    private DiscordRpc() {
    }

    public static boolean connected() {
        return connected;
    }

    /** Shows this (null clears it) with the player's application id; starts the thread on first use. */
    public static synchronized void show(String applicationId, Activity activity) {
        appId = applicationId == null ? "" : applicationId.strip();
        wanted = activity;
        if (!running && activity != null && !appId.isEmpty()) {
            running = true;
            thread = new Thread(DiscordRpc::run, "Sol Discord");
            thread.setDaemon(true);
            thread.start();
        }
    }

    public static synchronized void stop() {
        wanted = null;
        running = false;
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }

    private static void run() {
        Activity sent = null;
        String sentFor = null;
        while (running) {
            try (Pipe p = open()) {
                if (p == null) {
                    connected = false;
                    Thread.sleep(15_000L);
                    continue;
                }
                String id = appId;
                JsonObject hello = new JsonObject();
                hello.addProperty("v", 1);
                hello.addProperty("client_id", id);
                p.write(frame(HANDSHAKE, hello.toString()));
                read(p);
                connected = true;
                sent = null;
                sentFor = id;
                while (running && id.equals(appId)) {
                    Activity a = wanted;
                    if (!java.util.Objects.equals(a, sent)) {
                        p.write(frame(FRAME, setActivity(a, ProcessHandle.current().pid(), UUID.randomUUID().toString())));
                        read(p);
                        sent = a;
                    }
                    Thread.sleep(1000L);
                }
            } catch (InterruptedException e) {
                break;
            } catch (IOException | RuntimeException e) {
                connected = false;
                CosmeticsMod.LOGGER.debug("[Sol] Discord IPC dropped", e);
                try {
                    Thread.sleep(15_000L);
                } catch (InterruptedException ie) {
                    break;
                }
            }
        }
        connected = false;
    }

    /** A frame: opcode and length (little endian), then the UTF-8 JSON. */
    static byte[] frame(int opcode, String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer b = ByteBuffer.allocate(8 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(opcode).putInt(body.length).put(body);
        return b.array();
    }

    /** The SET_ACTIVITY command (a null activity clears the status). */
    static String setActivity(Activity a, long pid, String nonce) {
        JsonObject args = new JsonObject();
        args.addProperty("pid", pid);
        if (a != null) {
            JsonObject act = new JsonObject();
            act.addProperty("details", clip(a.details()));
            act.addProperty("state", clip(a.state()));
            if (a.startMs() > 0) {
                JsonObject ts = new JsonObject();
                ts.addProperty("start", a.startMs() / 1000L);
                act.add("timestamps", ts);
            }
            if (a.image() != null && !a.image().isBlank()) {
                JsonObject assets = new JsonObject();
                assets.addProperty("large_image", a.image());
                assets.addProperty("large_text", clip(a.imageText()));
                act.add("assets", assets);
            }
            args.add("activity", act);
        }
        JsonObject cmd = new JsonObject();
        cmd.addProperty("cmd", "SET_ACTIVITY");
        cmd.add("args", args);
        cmd.addProperty("nonce", nonce);
        return cmd.toString();
    }

    /** Discord's limit is 128 characters a line (and at least 2). */
    private static String clip(String s) {
        if (s == null || s.isBlank()) {
            return "  ";
        }
        return s.length() > 128 ? s.substring(0, 127) + "…" : s.length() < 2 ? s + " " : s;
    }

    /** Reads one reply frame (and ignores it); throws when Discord closed. */
    private static void read(Pipe p) throws IOException {
        byte[] head = new byte[8];
        readFully(p, head);
        ByteBuffer b = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN);
        int op = b.getInt(), len = b.getInt();
        if (len < 0 || len > 1_000_000) {
            throw new IOException("bad frame");
        }
        readFully(p, new byte[len]);
        if (op == CLOSE) {
            throw new IOException("closed by Discord");
        }
    }

    private static void readFully(Pipe p, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int n = p.read(b, off, b.length - off);
            if (n < 0) {
                throw new IOException("end");
            }
            off += n;
        }
    }

    /** The places Discord's socket can be (the runtime folder, temp, and inside Flatpak and Snap folders). */
    static List<Path> socketDirs(String runtime, String tmp) {
        List<Path> dirs = new ArrayList<>();
        for (String base : new String[]{runtime, tmp, "/tmp"}) {
            if (base == null || base.isBlank()) {
                continue;
            }
            Path b = Path.of(base);
            for (Path p : new Path[]{b, b.resolve("app/com.discordapp.Discord"), b.resolve("snap.discord"), b.resolve(".flatpak/dev.vencord.Vesktop/xdg-run")}) {
                if (!dirs.contains(p)) {
                    dirs.add(p);
                }
            }
        }
        return dirs;
    }

    private static Pipe open() {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        for (int i = 0; i < 10; i++) {
            if (windows) {
                try {
                    RandomAccessFile f = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                    return new Pipe() {
                        public void write(byte[] b) throws IOException {
                            f.write(b);
                        }

                        public int read(byte[] b, int off, int len) throws IOException {
                            return f.read(b, off, len);
                        }

                        public void close() throws IOException {
                            f.close();
                        }
                    };
                } catch (IOException e) {
                    continue;
                }
            }
            for (Path dir : socketDirs(System.getenv("XDG_RUNTIME_DIR"), System.getenv("TMPDIR"))) {
                Path sock = dir.resolve("discord-ipc-" + i);
                if (!Files.exists(sock)) {
                    continue;
                }
                try {
                    SocketChannel ch = SocketChannel.open(StandardProtocolFamily.UNIX);
                    ch.connect(UnixDomainSocketAddress.of(sock));
                    return new Pipe() {
                        public void write(byte[] b) throws IOException {
                            ByteBuffer buf = ByteBuffer.wrap(b);
                            while (buf.hasRemaining()) {
                                ch.write(buf);
                            }
                        }

                        public int read(byte[] b, int off, int len) throws IOException {
                            return ch.read(ByteBuffer.wrap(b, off, len));
                        }

                        public void close() throws IOException {
                            ch.close();
                        }
                    };
                } catch (IOException e) {
                    // The next one.
                }
            }
        }
        return null;
    }
}
