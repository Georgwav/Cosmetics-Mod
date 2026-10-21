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
    void aCurrentIdWinsOverAnOldOneWhateverTheOrder() {
        for (boolean oldFirst : new boolean[]{true, false}) {
            com.google.gson.JsonObject modules = new com.google.gson.JsonObject();
            com.google.gson.JsonObject old = com.google.gson.JsonParser.parseString("{\"enabled\":false,\"x\":1}").getAsJsonObject();
            com.google.gson.JsonObject current = com.google.gson.JsonParser.parseString("{\"enabled\":true,\"x\":2}").getAsJsonObject();
            if (oldFirst) {
                modules.add("coords", old);
                modules.add("coordinates", current);
            } else {
                modules.add("coordinates", current);
                modules.add("coords", old);
            }
            com.google.gson.JsonObject out = Migrations.migrateModules(modules);
            assertEquals(2, out.getAsJsonObject("coordinates").get("x").getAsInt(), "old id first: " + oldFirst);
            assertFalse(out.has("coords"));
        }
    }

    @Test
    void pvpEffectsKillEffectMovesToKillEffects() {
        JsonObject m = Migrations.migrateModules(json("{\"pvp_effects\":{\"enabled\":true,\"settings\":"
                + "{\"kill_effect\":\"lightning\",\"players_only\":true,\"hit_sound\":\"bell\"}}}"));
        JsonObject kill = m.getAsJsonObject("kill_effects");
        assertTrue(kill.get("enabled").getAsBoolean());
        assertEquals("lightning", kill.getAsJsonObject("settings").get("kill_style").getAsString());
        assertEquals("players", kill.getAsJsonObject("settings").get("kill_who").getAsString());
        // Only once: a file that has Kill Effects keeps its own.
        JsonObject again = Migrations.migrateModules(json("{\"pvp_effects\":{\"enabled\":true,\"settings\":"
                + "{\"kill_effect\":\"burst\"}},\"kill_effects\":{\"enabled\":false,\"settings\":{\"kill_style\":\"confetti\"}}}"));
        assertEquals("confetti", again.getAsJsonObject("kill_effects").getAsJsonObject("settings").get("kill_style").getAsString());
        // No kill effect (or just a sound): nothing to move.
        assertFalse(Migrations.migrateModules(json("{\"pvp_effects\":{\"enabled\":true,\"settings\":{\"kill_effect\":\"sound\"}}}"))
                .has("kill_effects"));
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

    @Test
    void pinsOnRemovedModules() {
        // The old default pin on Zoom moves to Camera; pins on modules the client dropped go.
        assertEquals("camera", Migrations.pin("zoom"));
        assertNull(Migrations.pin("hypixel"));
        assertNull(Migrations.pin("dropped_items"));
        assertNull(Migrations.pin("sounds"));
        assertEquals("glint", Migrations.pin("glint"));
        assertEquals(java.util.List.of("crosshair", "glint", "atmosphere", "camera"), Migrations.DEFAULT_PINS);
    }
}
