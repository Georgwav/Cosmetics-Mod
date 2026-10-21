package io.github.georgwav.cosmetics.sol.styles;

import java.util.Map;

/**
 * One pack as Styles knows it: where it is, what it changes and how much. Only paths, sizes, CRCs and counts, never an
 * image. {@code files} holds what the pack really changes or adds (its copies of vanilla files are left out).
 *
 * @param key      stable id: "rp:" + its path in resourcepacks, "personal:" + tab/style, "user:" + its folder in
 *                 client/styles
 * @param name     what the menu shows
 * @param origin   where it came from
 * @param location the zip file or the pack's folder
 * @param stamp    size and modified time (folders: of every file), to know when it changed
 * @param files    path under assets/minecraft/ (or "ns:path" for a sky mod's namespace) to its CRC and size
 * @param changed  vanilla textures it changes
 * @param added    files vanilla doesn't have
 * @param copies   files that are vanilla's own (ignored)
 * @param kind     full, part, overlay or empty
 * @param tab      for a personal style, the tab its folder is under (else null)
 */
public record PackInfo(String key, String name, Origin origin, String location, long stamp, Map<String, VanillaIndex.Entry> files,
                       int changed, int added, int copies, Kind kind, String tab) {
    public enum Origin {
        /** A pack in the resourcepacks folder (or one of its groups). */
        RESOURCE_PACK,
        /** A style in the personal build's styles/ folder. */
        PERSONAL,
        /** A small pack in client/styles (the texture editor saves there). */
        USER
    }

    public enum Kind {
        /** Changes more than 40% of vanilla's textures (Bare Bones, Faithful). */
        FULL,
        /** Changes a few parts (a crystal pack, a sword pack). */
        PART,
        /** Only adds files vanilla doesn't have (CIT, skies, fonts). */
        OVERLAY,
        /** Nothing Styles can use. */
        EMPTY
    }

    /** Whether the pack has its own file at a path (not a vanilla copy). */
    public boolean has(String path) {
        return files.containsKey(path);
    }

    /** How the pack is described on its card: "2,341 of 3,812 textures". */
    public String coverage(int vanillaTextures) {
        return String.format(java.util.Locale.ROOT, "%,d of %,d", changed, vanillaTextures);
    }
}
