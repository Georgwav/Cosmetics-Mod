package io.github.georgwav.cosmetics.sol.client;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hypixel's Mod API packets, as bytes: the {@code hypixel:register} a client sends (after Hypixel's
 * {@code hypixel:hello}) to hear events, and the {@code hypixel:location} event saying where the player is (the
 * server, its game type, lobby, mode and map). Strings are a VarInt length and UTF-8, optional values a boolean
 * before them, the game's own encoding. Pure; unit tested.
 */
public final class HypixelPackets {
    /** The register packet's version, and the location event's. */
    public static final int REGISTER_VERSION = 1;
    public static final int LOCATION_VERSION = 1;
    public static final String LOCATION = "hypixel:location";

    private HypixelPackets() {
    }

    /** Where the player is: in a lobby {@code lobbyName} is set; in a game {@code mode} (BEDWARS_EIGHT_ONE...). */
    public record Location(String serverName, Optional<String> serverType, Optional<String> lobbyName,
                           Optional<String> mode, Optional<String> map) {
        /** In a game (not a lobby, and a mode known). */
        public boolean inGame() {
            return lobbyName.isEmpty() && mode.isPresent();
        }

        /** The {@code /play} name for this game's mode (Hypixel's ids are its modes in lower case), or empty. */
        public Optional<String> playId() {
            return inGame() ? mode.map(m -> m.toLowerCase(java.util.Locale.ROOT)) : Optional.empty();
        }
    }

    /** The register packet asking for {@code events} (id to version). */
    public static byte[] register(Map<String, Integer> events) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        varInt(out, REGISTER_VERSION);
        varInt(out, events.size());
        for (Map.Entry<String, Integer> e : events.entrySet()) {
            string(out, e.getKey());
            varInt(out, e.getValue());
        }
        return out.toByteArray();
    }

    /** The register packet asking for the location event. */
    public static byte[] registerLocation() {
        Map<String, Integer> events = new LinkedHashMap<>();
        events.put(LOCATION, LOCATION_VERSION);
        return register(events);
    }

    /** A location event, or null when it's broken or of a version this doesn't read. */
    public static Location location(byte[] data) {
        try {
            Reader r = new Reader(data);
            int version = r.varInt();
            if (version != LOCATION_VERSION) {
                return null;
            }
            return new Location(r.string(), r.optional(), r.optional(), r.optional(), r.optional());
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) {
            return null;
        }
    }

    /** For tests: a location event as Hypixel writes it. */
    public static byte[] writeLocation(Location l) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        varInt(out, LOCATION_VERSION);
        string(out, l.serverName());
        for (Optional<String> o : java.util.List.of(l.serverType(), l.lobbyName(), l.mode(), l.map())) {
            out.write(o.isPresent() ? 1 : 0);
            o.ifPresent(s -> string(out, s));
        }
        return out.toByteArray();
    }

    static void varInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    static void string(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        varInt(out, b.length);
        out.write(b, 0, b.length);
    }

    /** Reads the game's encoding from bytes; throws on anything broken. */
    static final class Reader {
        private final byte[] data;
        private int at;

        Reader(byte[] data) {
            this.data = data;
        }

        int varInt() {
            int value = 0;
            for (int shift = 0; shift < 35; shift += 7) {
                int b = data[at++] & 0xFF;
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw new IllegalArgumentException("VarInt too long");
        }

        String string() {
            int length = varInt();
            if (length < 0 || length > 32767 * 3 || at + length > data.length) {
                throw new IllegalArgumentException("bad string length " + length);
            }
            String s = new String(data, at, length, StandardCharsets.UTF_8);
            at += length;
            return s;
        }

        Optional<String> optional() {
            int present = data[at++];
            if (present != 0 && present != 1) {
                throw new IllegalArgumentException("bad optional flag " + present);
            }
            return present == 1 ? Optional.of(string()) : Optional.empty();
        }
    }
}
