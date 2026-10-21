package io.github.georgwav.cosmetics.sol.styles;

import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The Sol Styles pack (B.2.7): a pack of Sol's own that copies nothing. It answers each path from wherever the current
 * mix says: a pack's file read straight from its zip or folder, the game's own file, or one of Sol's made files from
 * the cache. Its folder holds only its pack.mcmeta. It reads the mix each time it's asked, so the next reload always
 * sees the latest one.
 */
final class MixPack extends PathPackResources {
    MixPack(PackLocationInfo info, Path root) {
        super(info, root);
    }

    //? if >=1.21.11 {
    @Override
    public IoSupplier<InputStream> getResource(PackType type, net.minecraft.resources.Identifier id) {
    //?} else {
    /*@Override
    public IoSupplier<InputStream> getResource(PackType type, net.minecraft.resources.ResourceLocation id) {
    *///?}
        if (type == PackType.CLIENT_RESOURCES) {
            Mixer.Source s = Styles.plan().files().get(key(id.getNamespace(), id.getPath()));
            if (s != null) {
                return () -> open(s);
            }
        }
        return super.getResource(type, id);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput out) {
        super.listResources(type, namespace, path, out);
        if (type != PackType.CLIENT_RESOURCES) {
            return;
        }
        String prefix = path.endsWith("/") ? path : path + "/";
        for (Map.Entry<String, Mixer.Source> e : Styles.plan().files().entrySet()) {
            String k = e.getKey();
            String ns = "minecraft", p = k;
            int colon = k.indexOf(':');
            if (colon > 0) {
                ns = k.substring(0, colon);
                p = k.substring(colon + 1);
            }
            if (ns.equals(namespace) && p.startsWith(prefix)) {
                Mixer.Source s = e.getValue();
                out.accept(id(ns, p), () -> open(s));
            }
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        Set<String> out = new HashSet<>(super.getNamespaces(type));
        if (type == PackType.CLIENT_RESOURCES) {
            out.add("minecraft");
            for (String k : Styles.plan().files().keySet()) {
                int colon = k.indexOf(':');
                if (colon > 0) {
                    out.add(k.substring(0, colon));
                }
            }
        }
        return out;
    }

    private static InputStream open(Mixer.Source s) throws IOException {
        byte[] data = Styles.read(s);
        if (data == null) {
            throw new java.io.FileNotFoundException("Sol Styles: " + s.identity());
        }
        return new ByteArrayInputStream(data);
    }

    /** A resource's key in the mix: its path, with its namespace when it isn't minecraft. */
    static String key(String namespace, String path) {
        return namespace.equals("minecraft") ? path : namespace + ":" + path;
    }

    //? if >=1.21.11 {
    private static net.minecraft.resources.Identifier id(String ns, String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath(ns, path);
    }
    //?} else {
    /*private static net.minecraft.resources.ResourceLocation id(String ns, String path) {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ns, path);
    }
    *///?}
}
