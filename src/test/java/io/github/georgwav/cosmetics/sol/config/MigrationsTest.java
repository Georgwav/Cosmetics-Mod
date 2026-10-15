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
    void fpsPingAndClockBecomeStatsLines() {
        JsonObject m = Migrations.migrateModules(json("{"
                + "\"fps\":{\"enabled\":false,\"settings\":{\"font\":\"minecraft\"},\"state\":{\"x\":1}},"
                + "\"ping\":{\"enabled\":true,\"settings\":{\"font\":\"geist\",\"color\":\"cyan\"},\"state\":{\"x\":9}},"
                + "\"clock\":{\"enabled\":true,\"settings\":{\"format\":\"12h\"}}}"));
        assertFalse(m.has("fps"));
        assertFalse(m.has("ping"));
        assertFalse(m.has("clock"));
        JsonObject stats = m.getAsJsonObject("stats");
        assertTrue(stats.get("enabled").getAsBoolean());
        JsonObject s = stats.getAsJsonObject("settings");
        assertFalse(s.get("fps").getAsBoolean(), "FPS was off");
        assertTrue(s.get("ping").getAsBoolean());
        assertTrue(s.get("clock").getAsBoolean());
        assertEquals("12h", s.get("format").getAsString());
        assertEquals("geist", s.get("font").getAsString(), "the first module that was on gives the look");
        assertEquals(9, stats.getAsJsonObject("state").get("x").getAsInt(), "and its place");
        // Nothing was on: Stats stays off, showing FPS when switched on.
        JsonObject off = Migrations.migrateModules(json("{\"clock\":{\"enabled\":false,\"settings\":{}}}"))
                .getAsJsonObject("stats");
        assertFalse(off.get("enabled").getAsBoolean());
        assertTrue(off.getAsJsonObject("settings").get("fps").getAsBoolean());
        // A file that already has Stats keeps it, and migrating twice changes nothing.
        JsonObject again = Migrations.migrateModules(m);
        assertEquals(m, again);
    }

    @Test
    void anOffTimeModuleKeepsAtmospheresOwnLock() {
        JsonObject m = Migrations.migrateModules(json("{"
                + "\"atmosphere\":{\"enabled\":true,\"settings\":{\"lock_time\":true,\"time\":1000}},"
                + "\"time\":{\"enabled\":false,\"settings\":{\"time\":18000}},"
                + "\"weather\":{\"enabled\":true,\"settings\":{\"weather\":\"thunder\"}}}"));
        JsonObject atm = m.getAsJsonObject("atmosphere").getAsJsonObject("settings");
        assertTrue(atm.get("lock_time").getAsBoolean());
        assertEquals(1000, atm.get("time").getAsInt());
        assertTrue(atm.get("lock_weather").getAsBoolean());
        assertEquals("thunder", atm.get("weather").getAsString());
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
