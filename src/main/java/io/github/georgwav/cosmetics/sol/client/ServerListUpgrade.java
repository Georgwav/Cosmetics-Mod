package io.github.georgwav.cosmetics.sol.client;

import io.github.georgwav.cosmetics.client.Modules;
import io.github.georgwav.cosmetics.client.modules.Servers;
import io.github.georgwav.cosmetics.ui.Canvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Servers' server list upgrade: each server's ping written in milliseconds where the game draws its bars (its
 * player count moved left to make room); how many players are on the servers in the list, at the top; and folders.
 * A folder is the part of a server's name before a slash ("PvP/Hypixel" is in "PvP"), so folders are made with the
 * game's own Edit button and kept in its own server file. A chip per folder above the list shows only that folder's
 * servers; moving a server up or down then moves it among them.
 */
public final class ServerListUpgrade {
    /** The folder shown, or null for all servers. */
    private static String filter;
    /** The list's positions in the server file while a folder is shown (null: all, in order). */
    private static int[] shown;
    /** Pings written, and lists shown by folder (for the self-test). */
    public static int pingsWritten, filtered;

    private ServerListUpgrade() {
    }

    public static boolean on() {
        return Servers.serverList();
    }

    // ------------------------------------------------------------------ ping and count

    private static String pingText(ServerData data) {
        return Math.min(data.ping, 9999) + "ms";
    }

    private static boolean hasPing(ServerData data) {
        return on() && data != null && data.state() == ServerData.State.SUCCESSFUL && data.ping >= 0;
    }

    /** Writes the ping where the game draws its bars ({@code width} wide at x, y); false to let the game draw them. */
    public static boolean ping(Canvas g, ServerData data, int x, int y, int width) {
        if (!hasPing(data)) {
            return false;
        }
        pingsWritten++;
        var font = Minecraft.getInstance().font;
        String text = pingText(data);
        long ms = data.ping;
        int colour = ms < 150 ? 0xFF55FF55 : ms < 300 ? 0xFFFFFF55 : ms < 600 ? 0xFFFFAA00 : 0xFFFF5555;
        g.text(font, text, x + width - font.width(text), y, colour, true);
        return true;
    }

    /** How far the player count moves left for the ping text (0 with the game's bars). */
    public static int countShift(ServerData data) {
        return hasPing(data) ? Math.max(0, Minecraft.getInstance().font.width(pingText(data)) - 10) : 0;
    }

    /** Players on the listed servers that answered, and how many answered. */
    public static int[] total(ServerList list) {
        int players = 0, servers = 0;
        for (int i = 0; i < list.size(); i++) {
            ServerData d = list.get(i);
            if (d.state() == ServerData.State.SUCCESSFUL && d.players != null) {
                players += d.players.online();
                servers++;
            }
        }
        return new int[]{players, servers};
    }

    /** The players-online line at the top right of the server list. */
    public static void drawTotal(Canvas g, ServerList list, int width) {
        if (!on() || list == null) {
            return;
        }
        int[] t = total(list);
        if (t[1] == 0) {
            return;
        }
        var font = Minecraft.getInstance().font;
        String text = Component.translatable("sol.servers.total", String.format(Locale.ROOT, "%,d", t[0]), t[1]).getString();
        g.text(font, text, width - 6 - font.width(text), 8, 0xFFB0B0B0, true);
    }

    // ------------------------------------------------------------------ folders

    private static List<String> names(ServerList list) {
        List<String> names = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            names.add(list.get(i).name);
        }
        return names;
    }

    /** The folders in the list, A to Z. */
    public static List<String> folders(ServerList list) {
        return ServerFolders.all(names(list));
    }

    public static String filter() {
        return filter;
    }

    /** Shows one folder (null: all); a folder no longer in the list shows all. */
    public static void show(String folder, ServerList list) {
        filter = folder != null && folders(list).stream().anyMatch(f -> f.equalsIgnoreCase(folder)) ? folder : null;
    }

    /** Works out which servers the list shows now (called as the list fills). */
    public static void prepare(ServerList list) {
        if (!on() || filter == null) {
            shown = null;
            return;
        }
        int[] in = ServerFolders.in(names(list), filter);
        if (in.length == 0) {
            filter = null;
            shown = null;
            return;
        }
        filtered++;
        shown = in;
    }

    /** How many servers the list shows. */
    public static int size(ServerList list) {
        int[] s = shown;
        return s == null ? list.size() : s.length;
    }

    /** The server at a place in the list as shown. */
    public static ServerData get(ServerList list, int index) {
        return list.get(real(index));
    }

    /** A place in the list as shown → its place in the server file. */
    public static int real(int index) {
        int[] s = shown;
        return s == null || index < 0 || index >= s.length ? index : s[index];
    }

    /** Whether the list shows one folder now. */
    public static boolean filtering() {
        return shown != null;
    }

    /** Two places in the list as shown → their places in the server file, or null when one is outside it. */
    public static int[] realPair(int first, int second) {
        return ServerFolders.pair(shown, first, second);
    }
}
