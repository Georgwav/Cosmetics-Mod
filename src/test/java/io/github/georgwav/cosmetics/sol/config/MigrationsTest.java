package io.github.georgwav.cosmetics.sol.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MigrationsTest {
    private static JsonObject json(String s) {
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void clientBranchProfileBecomesSchemaTwo() {
        JsonObject old = json("{\"modules\":{"
                + "\"time\":{\"enabled\":true,\"settings\":{\"time\":13000}},"
                + "\"weather\":{\"enabled\":false,\"settings\":{\"weather\":\"rain\"}},"
                + "\"toggle_sprint\":{\"enabled\":true,\"settings\":{}},"
                + "\"coords\":{\"enabled\":true,\"settings\":{\"font\":\"mono\"},\"state\":{\"x\":4}},"
                + "\"crosshair\":{\"enabled\":true,\"settings\":{\"size\":3}},"
                + "\"vanilla_hud\":{\"hotbar\":[0,0]}}}");
        assertTrue(Migrations.needsMigration(old));
        JsonObject p = Migrations.migrateProfile(old);
        assertFalse(Migrations.needsMigration(p));
        assertEquals(2, p.get("schema").getAsInt());
        JsonObject m = p.getAsJsonObject("modules");
        assertFalse(m.has("time"));
        assertFalse(m.has("weather"));
        assertTrue(m.has("movement"));
        assertEquals("mono", m.getAsJsonObject("coordinates").getAsJsonObject("settings").get("font").getAsString());
        assertEquals(4, m.getAsJsonObject("coordinates").getAsJsonObject("state").get("x").getAsInt(), "position kept");
        assertEquals(3, m.getAsJsonObject("crosshair").getAsJsonObject("settings").get("size").getAsInt());
        JsonObject atm = m.getAsJsonObject("atmosphere").getAsJsonObject("settings");
        assertTrue(atm.get("lock_time").getAsBoolean());
        assertEquals(13000, atm.get("time").getAsInt());
        assertFalse(atm.get("lock_weather").getAsBoolean());
        assertEquals("rain", atm.get("weather").getAsString());
        assertTrue(m.has("vanilla_hud"));
        assertEquals("deep_field", p.getAsJsonObject("theme").get("id").getAsString());
        assertEquals(4, p.getAsJsonArray("pinned").size());
    }

    @Test
    void schemaTwoIsLeftAlone() {
        JsonObject p = json("{\"schema\":2,\"modules\":{},\"theme\":{\"id\":\"grace\"},\"pinned\":[],\"future\":7}");
        assertFalse(Migrations.needsMigration(p));
        JsonObject again = Migrations.migrateProfile(p);
        assertEquals("grace", again.getAsJsonObject("theme").get("id").getAsString());
        assertEquals(7, again.get("future").getAsInt(), "unknown fields kept");
    }

    @Test
    void solConfigRoundTripAndBadValues() throws Exception {
        Path dir = Files.createTempDirectory("solcfg");
        Path file = dir.resolve("sol.json");
        Files.writeString(file, "{\"schema\":1,\"activeProfile\":\"\",\"motion\":\"wild\",\"music\":{\"ciderPort\":-4}}");
        SolConfig.useFile(file);
        SolConfig cfg = SolConfig.get();
        assertEquals("Deep Field", cfg.activeProfile);
        assertEquals("full", cfg.motion);
        assertEquals(10767, cfg.music.ciderPort);
        cfg.activeProfile = "Red PvP";
        SolConfig.save();
        SolConfig.useFile(file);
        assertEquals("Red PvP", SolConfig.get().activeProfile);
        Files.writeString(file, "not json");
        SolConfig.useFile(file);
        assertEquals("Deep Field", SolConfig.get().activeProfile);
    }
}
