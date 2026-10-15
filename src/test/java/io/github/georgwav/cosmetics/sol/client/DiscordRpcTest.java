package io.github.georgwav.cosmetics.sol.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DiscordRpcTest {
    @Test
    void framesAreOpcodeLengthThenJson() {
        byte[] f = DiscordRpc.frame(DiscordRpc.HANDSHAKE, "{\"v\":1}");
        ByteBuffer b = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0, b.getInt());
        assertEquals(7, b.getInt());
        assertEquals("{\"v\":1}", new String(f, 8, 7, StandardCharsets.UTF_8));
    }

    @Test
    void setActivityCarriesTheStatus() {
        JsonObject o = JsonParser.parseString(DiscordRpc.setActivity(new DiscordRpc.Activity("Playing on mc.example.net",
                "Sol Client · Ember Solstice", 1_700_000_000_000L, "sol", "Sol Client"), 42L, "n")).getAsJsonObject();
        assertEquals("SET_ACTIVITY", o.get("cmd").getAsString());
        JsonObject args = o.getAsJsonObject("args");
        assertEquals(42L, args.get("pid").getAsLong());
        JsonObject act = args.getAsJsonObject("activity");
        assertEquals("Playing on mc.example.net", act.get("details").getAsString());
        assertEquals(1_700_000_000L, act.getAsJsonObject("timestamps").get("start").getAsLong());
        assertEquals("sol", act.getAsJsonObject("assets").get("large_image").getAsString());
        // Cleared: no activity.
        JsonObject cleared = JsonParser.parseString(DiscordRpc.setActivity(null, 1L, "n")).getAsJsonObject();
        assertFalse(cleared.getAsJsonObject("args").has("activity"));
        // Discord's 128-character limit.
        String longLine = "x".repeat(300);
        JsonObject clipped = JsonParser.parseString(DiscordRpc.setActivity(new DiscordRpc.Activity(longLine, "a", 0L, null, null), 1L, "n"))
                .getAsJsonObject().getAsJsonObject("args").getAsJsonObject("activity");
        assertEquals(128, clipped.get("details").getAsString().length());
        assertEquals(2, clipped.get("state").getAsString().length());
    }

    @Test
    void looksInTheRuntimeFolderFlatpakAndSnap() {
        var dirs = DiscordRpc.socketDirs("/run/user/1000", null);
        assertTrue(dirs.contains(Path.of("/run/user/1000")));
        assertTrue(dirs.contains(Path.of("/run/user/1000/app/com.discordapp.Discord")));
        assertTrue(dirs.contains(Path.of("/run/user/1000/snap.discord")));
        assertTrue(dirs.contains(Path.of("/tmp")));
    }
}
