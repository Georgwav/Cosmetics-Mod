package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UpdatesTest {
    @org.junit.jupiter.api.BeforeAll
    static void noSecrets() {
        io.github.georgwav.cosmetics.sol.config.Secrets.useFile(java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),
                "sol-updates-test-no-secrets.json"));
    }

    @Test
    void versionsCompareByNumber() {
        assertTrue(Updates.newer("0.2.0", "0.1.0"));
        assertTrue(Updates.newer("v0.10.0", "0.9.9"));
        assertTrue(Updates.newer("1.0", "0.99.1"));
        assertFalse(Updates.newer("0.1.0", "0.1.0"));
        assertFalse(Updates.newer("0.1", "0.1.0"));
        assertFalse(Updates.newer("0.1.0", "0.1.1"));
        assertTrue(Updates.newer("0.1.1-beta", "0.1.0"));
    }

    @Test
    void picksTheJarForThisMinecraft() {
        String json = "{\"tag_name\":\"v0.2.0\",\"name\":\"Sol Client 0.2\",\"body\":\"Notes\",\"assets\":["
                + "{\"name\":\"solclient-0.2.0+mc1.21.10.jar\",\"url\":\"https://api/1\",\"browser_download_url\":\"https://dl/1\"},"
                + "{\"name\":\"solclient-0.2.0+mc1.21.11.jar\",\"url\":\"https://api/2\",\"browser_download_url\":\"https://dl/2\"}]}";
        Updates.Release r = Updates.parse(json, "1.21.11");
        assertNotNull(r);
        assertEquals("0.2.0", r.version());
        assertEquals("solclient-0.2.0+mc1.21.11.jar", r.asset());
        assertEquals("Sol Client 0.2", r.name());
        assertNull(Updates.parse(json, "26.3"));
    }
}
