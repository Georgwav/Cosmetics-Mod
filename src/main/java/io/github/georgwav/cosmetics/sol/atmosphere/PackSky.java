package io.github.georgwav.cosmetics.sol.atmosphere;

import io.github.georgwav.cosmetics.sol.render.SolTexture;
import io.github.georgwav.cosmetics.sol.render.WorldDraw;
import io.github.georgwav.cosmetics.sol.styles.PackInfo;
import io.github.georgwav.cosmetics.sol.styles.PackSkies;
import io.github.georgwav.cosmetics.sol.styles.Pixels;
import io.github.georgwav.cosmetics.sol.styles.StyleSetup;
import io.github.georgwav.cosmetics.sol.styles.Styles;
import io.github.georgwav.cosmetics.sol.styles.StylesIndex;
import io.github.georgwav.cosmetics.sol.util.Colors;
import io.github.georgwav.cosmetics.sol.util.SolExecutors;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * A pack's sky in Sol's sky pass (B.2.5): when Styles' Sky part comes from a pack (or the base pack has one), its
 * OptiFine or FabricSkyboxes layers are drawn as boxes round the camera after the stars, each faded by its times,
 * added or blended, turning with the day when it should, and hidden in weather it isn't for. The layers are read
 * and decoded off the render thread; nothing is made per frame.
 */
public final class PackSky {
    private record Loaded(PackSkies.Layer layer, SolTexture[] textures) {
    }

    private record Ready(PackSkies.Layer layer, Pixels.Image[] images) {
    }

    private static String forPack;
    private static long forStamp = -1L;
    private static List<Loaded> layers = List.of();
    private static volatile List<Ready> ready;
    private static final float[] c = new float[3];
    /** How many layers were drawn last frame (the scenario checks it). */
    public static int drawn;

    private PackSky() {
    }

    /** The pack whose sky shows: the Sky part's, else the base's. */
    private static PackInfo pack(StylesIndex.Snapshot idx, StyleSetup setup) {
        String key = setup.choice("sky").packKey();
        PackInfo p = key == null ? null : idx.get(key);
        return p != null ? p : setup.base() == null ? null : idx.get(setup.base());
    }

    static void render(float sunAngle, float rain) {
        drawn = 0;
        StylesIndex.Snapshot idx = Styles.index();
        if (idx == null) {
            return;
        }
        PackInfo p = pack(idx, Styles.setup());
        String key = p == null ? null : p.key();
        long stamp = p == null ? -1L : p.stamp();
        if (!java.util.Objects.equals(key, forPack) || stamp != forStamp) {
            load(p);
        }
        List<Ready> r = ready;
        if (r != null) {
            ready = null;
            upload(r);
        }
        if (layers.isEmpty() || Minecraft.getInstance().level == null) {
            return;
        }
        long day = io.github.georgwav.cosmetics.sol.render.Mc.dayTime(Minecraft.getInstance().level);
        boolean raining = rain > 0.2f;
        for (Loaded l : layers) {
            PackSkies.Layer layer = l.layer();
            float b = PackSkies.brightness(layer.fade(), day) * layer.maxAlpha();
            if (raining && !layer.weather().contains("rain") && !layer.weather().contains("thunder")) {
                b *= 1f - rain;
            } else if (!raining && !layer.weather().contains("clear")) {
                b = 0f;
            }
            if (b <= 0.004f) {
                continue;
            }
            float angle = layer.rotate() ? sunAngle * layer.speed() : 0f;
            int argb = layer.colour() != 0 ? Colors.withAlpha(layer.colour() | 0xFF000000, b * (layer.colour() >>> 24) / 255f)
                    : Colors.withAlpha(0xFFFFFFFF, b);
            int kind = layer.additive() ? WorldDraw.SKY_ADD : WorldDraw.SKY_ALPHA;
            if (layer.colour() != 0 || l.textures().length == 0) {
                WorldDraw.begin(kind, null);
                for (int f = 0; f < 6; f++) {
                    face(f, layer, angle, 0f, 0f, 1f, 1f, argb);
                }
                WorldDraw.end();
            } else if (l.textures().length == 6) {
                for (int f = 0; f < 6; f++) {
                    if (l.textures()[f] == null) {
                        continue;
                    }
                    WorldDraw.begin(kind, l.textures()[f]);
                    face(f, layer, angle, 0f, 0f, 1f, 1f, argb);
                    WorldDraw.end();
                }
            } else if (l.textures()[0] != null) {
                WorldDraw.begin(kind, l.textures()[0]);
                for (int f = 0; f < 6; f++) {
                    float u0 = (f % 3) / 3f, v0 = (f / 3) / 2f;
                    face(f, layer, angle, u0, v0, u0 + 1f / 3f, v0 + 0.5f, argb);
                }
                WorldDraw.end();
            }
            drawn++;
        }
    }

    /** One face of the box: its corners turned with the sky, its part of the texture. */
    private static void face(int face, PackSkies.Layer layer, float angle, float u0, float v0, float u1, float v1, int argb) {
        corner(face, layer, angle, 0f, 0f, u0, v0, argb);
        corner(face, layer, angle, 0f, 1f, u0, v1, argb);
        corner(face, layer, angle, 1f, 1f, u1, v1, argb);
        corner(face, layer, angle, 1f, 0f, u1, v0, argb);
    }

    private static void corner(int face, PackSkies.Layer layer, float angle, float s, float t, float u, float v, int argb) {
        PackSkies.corner(face, s, t, c);
        if (angle != 0f) {
            rotate(c, layer.axis(), angle);
        }
        SkyDraw.sphere(c[0], c[1], c[2], u, v, argb);
    }

    /** Turns a direction about an axis (Rodrigues). */
    private static void rotate(float[] v, float[] axis, float angle) {
        float ax = axis[0], ay = axis[1], az = axis[2];
        float len = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 1e-6f) {
            return;
        }
        ax /= len;
        ay /= len;
        az /= len;
        float cos = (float) Math.cos(angle), sin = (float) Math.sin(angle);
        float dot = ax * v[0] + ay * v[1] + az * v[2];
        float cx = ay * v[2] - az * v[1], cy = az * v[0] - ax * v[2], cz = ax * v[1] - ay * v[0];
        float x = v[0] * cos + cx * sin + ax * dot * (1f - cos);
        float y = v[1] * cos + cy * sin + ay * dot * (1f - cos);
        float z = v[2] * cos + cz * sin + az * dot * (1f - cos);
        v[0] = x;
        v[1] = y;
        v[2] = z;
    }

    /** Reads the pack's layers and their pictures off the render thread. */
    private static void load(PackInfo p) {
        forPack = p == null ? null : p.key();
        forStamp = p == null ? -1L : p.stamp();
        release();
        if (p == null) {
            return;
        }
        List<String> files = new ArrayList<>(p.files().keySet());
        SolExecutors.IO.execute(() -> {
            List<PackSkies.Layer> ls = PackSkies.read(path -> {
                byte[] d = p.has(path) ? Styles.readPackFile(p, path) : null;
                return d == null ? null : new String(d, java.nio.charset.StandardCharsets.UTF_8);
            }, p::has, files);
            List<Ready> out = new ArrayList<>();
            for (PackSkies.Layer l : ls) {
                Pixels.Image[] imgs = new Pixels.Image[l.textures().size()];
                for (int i = 0; i < imgs.length; i++) {
                    try {
                        byte[] d = Styles.readPackFile(p, l.textures().get(i));
                        imgs[i] = d == null ? null : io.github.georgwav.cosmetics.sol.styles.Builder.decode(d);
                    } catch (java.io.IOException | RuntimeException e) {
                        imgs[i] = null;
                    }
                }
                out.add(new Ready(l, imgs));
            }
            ready = out;
        });
    }

    private static int made;

    private static void upload(List<Ready> r) {
        release();
        List<Loaded> out = new ArrayList<>();
        for (Ready rd : r) {
            SolTexture[] tex = new SolTexture[rd.images().length];
            for (int i = 0; i < tex.length; i++) {
                Pixels.Image img = rd.images()[i];
                if (img != null) {
                    tex[i] = SolTexture.of("pack_sky_" + made++, img.w(), img.h(), true, img.argb());
                }
            }
            out.add(new Loaded(rd.layer(), tex));
        }
        layers = out;
    }

    private static void release() {
        for (Loaded l : layers) {
            for (SolTexture t : l.textures()) {
                if (t != null) {
                    t.release();
                }
            }
        }
        layers = List.of();
    }
}
