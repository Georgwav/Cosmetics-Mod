package io.github.georgwav.cosmetics.sol.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Server folders by name (ServerListUpgrade): a server's folder is the part of its name before a slash, so
 * "PvP/Hypixel" is in "PvP". Pure; unit tested.
 */
public final class ServerFolders {
    private ServerFolders() {
    }

    /** A server's folder, or null when its name has none. */
    public static String of(String name) {
        if (name == null) {
            return null;
        }
        int slash = name.indexOf('/');
        if (slash <= 0 || slash == name.length() - 1) {
            return null;
        }
        String f = name.substring(0, slash).strip();
        return f.isEmpty() ? null : f;
    }

    /** The folders among these names, A to Z, each once (any case; the first spelling met is kept). */
    public static List<String> all(List<String> names) {
        TreeMap<String, String> found = new TreeMap<>();
        for (String n : names) {
            String f = of(n);
            if (f != null) {
                found.putIfAbsent(f.toLowerCase(Locale.ROOT), f);
            }
        }
        return new ArrayList<>(found.values());
    }

    /** The places of the names in {@code folder} (any case), in order. */
    public static int[] in(List<String> names, String folder) {
        List<Integer> at = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            if (folder.equalsIgnoreCase(of(names.get(i)))) {
                at.add(i);
            }
        }
        return at.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Two places in a folder's list → their places in the whole list, or null when one is outside the folder. */
    public static int[] pair(int[] shown, int first, int second) {
        if (shown == null) {
            return new int[]{first, second};
        }
        if (first < 0 || second < 0 || first >= shown.length || second >= shown.length) {
            return null;
        }
        return new int[]{shown[first], shown[second]};
    }
}
