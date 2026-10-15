package io.github.georgwav.cosmetics.sol.music;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import com.sun.jna.ptr.PointerByReference;
import io.github.georgwav.cosmetics.CosmeticsMod;

/**
 * Windows: what the default output plays, through WASAPI in loopback mode (shared mode, the mix format, usually 32-bit
 * float at 48 kHz), called through the JNA the game ships (COM by its vtables; only ole32 is loaded). Checked before
 * use; any failure means no capture.
 */
final class WasapiCapture implements AudioCapture.Input {
    private static final int CLSCTX_ALL = 0x17, COINIT_MULTITHREADED = 0x0, E_RENDER = 0, E_CONSOLE = 0;
    private static final int SHAREMODE_SHARED = 0, STREAMFLAGS_LOOPBACK = 0x00020000, BUFFERFLAGS_SILENT = 0x2;

    private final Pointer client, capture;
    private final int rate, channels, bits;
    private final boolean floats;
    private final float[] pending = new float[48000];
    private int pendingCount;

    private WasapiCapture(Pointer client, Pointer capture, int rate, int channels, int bits, boolean floats) {
        this.client = client;
        this.capture = capture;
        this.rate = rate;
        this.channels = channels;
        this.bits = bits;
        this.floats = floats;
    }

    static AudioCapture.Input open() {
        try {
            Class.forName("com.sun.jna.Native");
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
        try {
            Function.getFunction("ole32", "CoInitializeEx").invokeInt(new Object[]{Pointer.NULL, COINIT_MULTITHREADED});
            PointerByReference enumerator = new PointerByReference();
            int hr = Function.getFunction("ole32", "CoCreateInstance").invokeInt(new Object[]{
                    guid(0xBCDE0395, 0xE52F, 0x467C, 0x8E, 0x3D, 0xC4, 0x57, 0x92, 0x91, 0x69, 0x2E), Pointer.NULL, CLSCTX_ALL,
                    guid(0xA95664D2, 0x9614, 0x4F35, 0xA7, 0x46, 0xDE, 0x8D, 0xB6, 0x36, 0x17, 0xE6), enumerator});
            if (hr < 0) {
                return null;
            }
            PointerByReference device = new PointerByReference();
            if (call(enumerator.getValue(), 4, E_RENDER, E_CONSOLE, device) < 0) {
                return null;
            }
            PointerByReference audioClient = new PointerByReference();
            if (call(device.getValue(), 3, guid(0x1CB9AD4C, 0xDBFA, 0x4C32, 0xB1, 0x78, 0xC2, 0xF5, 0x68, 0xA7, 0x03, 0xB2),
                    CLSCTX_ALL, Pointer.NULL, audioClient) < 0) {
                return null;
            }
            Pointer client = audioClient.getValue();
            PointerByReference formatRef = new PointerByReference();
            if (call(client, 8, formatRef) < 0) {
                return null;
            }
            Pointer format = formatRef.getValue();
            int tag = format.getShort(0) & 0xFFFF, channels = format.getShort(2), rate = format.getInt(4), bits = format.getShort(14);
            boolean floats = tag == 3 || tag == 0xFFFE && bits == 32;
            if (call(client, 3, SHAREMODE_SHARED, STREAMFLAGS_LOOPBACK, 10_000_000L, 0L, format, Pointer.NULL) < 0) {
                return null;
            }
            PointerByReference captureRef = new PointerByReference();
            if (call(client, 14, guid(0xC8ADBD64, 0xE71E, 0x48A0, 0xA4, 0xDE, 0x18, 0x5C, 0x39, 0x5C, 0xD3, 0x17), captureRef) < 0) {
                return null;
            }
            if (call(client, 10) < 0) {
                return null;
            }
            return new WasapiCapture(client, captureRef.getValue(), rate, Math.max(1, channels), bits, floats);
        } catch (RuntimeException | LinkageError e) {
            CosmeticsMod.LOGGER.debug("[Sol] WASAPI loopback unavailable", e);
            return null;
        }
    }

    /** A COM method by its vtable slot. */
    private static int call(Pointer obj, int slot, Object... args) {
        Pointer vtbl = obj.getPointer(0);
        Function f = Function.getFunction(vtbl.getPointer((long) slot * Native.POINTER_SIZE));
        Object[] all = new Object[args.length + 1];
        all[0] = obj;
        System.arraycopy(args, 0, all, 1, args.length);
        return f.invokeInt(all);
    }

    private static Memory guid(int d1, int d2, int d3, int... d4) {
        Memory m = new Memory(16);
        m.setInt(0, d1);
        m.setShort(4, (short) d2);
        m.setShort(6, (short) d3);
        for (int i = 0; i < 8; i++) {
            m.setByte(8 + i, (byte) d4[i]);
        }
        return m;
    }

    @Override
    public int rate() {
        return rate;
    }

    /** Fills {@code out}; when the output plays nothing for 50 ms (no packets come in loopback then), silence. */
    @Override
    public int read(float[] out) {
        long giveUp = System.nanoTime() + 50_000_000L;
        while (pendingCount < out.length) {
            if (System.nanoTime() > giveUp) {
                java.util.Arrays.fill(pending, pendingCount, out.length, 0f);
                pendingCount = out.length;
                break;
            }
            IntByReference packet = new IntByReference();
            if (call(capture, 5, packet) < 0) {
                return -1;
            }
            if (packet.getValue() == 0) {
                try {
                    Thread.sleep(5L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return -1;
                }
                continue;
            }
            PointerByReference data = new PointerByReference();
            IntByReference frames = new IntByReference(), flags = new IntByReference();
            if (call(capture, 3, data, frames, flags, new LongByReference(), new LongByReference()) < 0) {
                return -1;
            }
            int n = frames.getValue();
            boolean silent = (flags.getValue() & BUFFERFLAGS_SILENT) != 0;
            Pointer p = data.getValue();
            for (int f = 0; f < n && pendingCount < pending.length; f++) {
                float sum = 0f;
                if (!silent) {
                    for (int c = 0; c < channels; c++) {
                        long at = ((long) f * channels + c) * (bits / 8);
                        sum += floats ? p.getFloat(at) : bits == 16 ? p.getShort(at) / 32768f : p.getInt(at) / 2147483648f;
                    }
                }
                pending[pendingCount++] = sum / channels;
            }
            call(capture, 4, n);
        }
        System.arraycopy(pending, 0, out, 0, out.length);
        System.arraycopy(pending, out.length, pending, 0, pendingCount - out.length);
        pendingCount -= out.length;
        return out.length;
    }

    @Override
    public void close() {
        try {
            call(client, 11);
            call(capture, 2);
            call(client, 2);
        } catch (RuntimeException | LinkageError ignored) {
            // Already gone.
        }
    }
}
