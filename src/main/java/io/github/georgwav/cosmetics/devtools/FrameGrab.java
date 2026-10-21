package io.github.georgwav.cosmetics.devtools;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.georgwav.cosmetics.CosmeticsMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The scenario's own screenshots: the last frame the game drew, written as a PNG into the game folder. Much quicker
 * than the CI script's screenshots (no waiting on a marker), so an effect can be caught at six moments of its life.
 */
final class FrameGrab {
    /** Frames asked for and not written yet. */
    static final AtomicInteger PENDING = new AtomicInteger();

    private FrameGrab() {
    }

    static void grab(Minecraft mc, String name) {
        Path out = mc.gameDirectory.toPath().resolve(name + ".png");
        PENDING.incrementAndGet();
        try {
            //? if >=1.21.5 {
            Screenshot.takeScreenshot(io.github.georgwav.cosmetics.sol.render.Mc.mainTarget(), image -> write(image, out));
            //?} else {
            /*write(Screenshot.takeScreenshot(io.github.georgwav.cosmetics.sol.render.Mc.mainTarget()), out);
            *///?}
        } catch (RuntimeException e) {
            PENDING.decrementAndGet();
            CosmeticsMod.LOGGER.warn("[Sol] Couldn't grab {}", name, e);
        }
    }

    private static void write(NativeImage image, Path out) {
        int w = image.getWidth(), h = image.getHeight();
        int[] argb;
        try (image) {
            argb = image.makePixelArray();
        }
        io.github.georgwav.cosmetics.sol.util.SolExecutors.IO.execute(() -> {
            try {
                java.nio.file.Files.write(out, png(w, h, argb));
            } catch (Exception e) {
                CosmeticsMod.LOGGER.warn("[Sol] Couldn't write {}", out.getFileName(), e);
            } finally {
                PENDING.decrementAndGet();
            }
        });
    }

    /** A plain PNG (8-bit RGB, every row unfiltered), as the CI script reads them. */
    static byte[] png(int w, int h, int[] argb) throws java.io.IOException {
        java.io.ByteArrayOutputStream raw = new java.io.ByteArrayOutputStream(h * (w * 3 + 1));
        for (int y = 0; y < h; y++) {
            raw.write(0);
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                raw.write((c >> 16) & 255);
                raw.write((c >> 8) & 255);
                raw.write(c & 255);
            }
        }
        java.io.ByteArrayOutputStream z = new java.io.ByteArrayOutputStream();
        try (java.util.zip.DeflaterOutputStream d = new java.util.zip.DeflaterOutputStream(z, new java.util.zip.Deflater(6))) {
            raw.writeTo(d);
        }
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
        java.nio.ByteBuffer ihdr = java.nio.ByteBuffer.allocate(13).putInt(w).putInt(h).put((byte) 8).put((byte) 2).put((byte) 0)
                .put((byte) 0).put((byte) 0);
        chunk(out, "IHDR", ihdr.array());
        chunk(out, "IDAT", z.toByteArray());
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void chunk(java.io.ByteArrayOutputStream out, String kind, byte[] data) throws java.io.IOException {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        byte[] k = kind.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        crc.update(k);
        crc.update(data);
        out.write(java.nio.ByteBuffer.allocate(4).putInt(data.length).array());
        out.write(k);
        out.write(data);
        out.write(java.nio.ByteBuffer.allocate(4).putInt((int) crc.getValue()).array());
    }
}
