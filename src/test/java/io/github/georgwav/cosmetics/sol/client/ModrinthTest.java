package io.github.georgwav.cosmetics.sol.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthTest {
    @Test
    void searchAskesForFabricModsOfThisVersion() {
        String url = Modrinth.searchUrl("sodium extra", "1.21.11");
        assertTrue(url.contains("query=sodium+extra"));
        assertTrue(url.contains("index=relevance"));
        String decoded = java.net.URLDecoder.decode(url, java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(decoded.contains("[[\"categories:fabric\"],[\"versions:1.21.11\"],[\"project_type:mod\"]]"));
        assertTrue(Modrinth.searchUrl(" ", "1.21.11").contains("index=downloads"));
    }

    @Test
    void readsHitsAndPicksThePrimaryFile() {
        var hits = Modrinth.hits("{\"hits\":[{\"project_id\":\"AANobbMI\",\"slug\":\"sodium\",\"title\":\"Sodium\",\"author\":\"jellysquid3\","
                + "\"description\":\"Fast\",\"icon_url\":\"https://cdn/icon.png\",\"downloads\":123456}]}");
        assertEquals(1, hits.size());
        assertEquals("Sodium", hits.get(0).title());
        assertEquals(123456L, hits.get(0).downloads());
        var d = Modrinth.pick("[{\"files\":[{\"url\":\"https://cdn/a-sources.jar\",\"filename\":\"a-sources.jar\",\"primary\":false,\"hashes\":{\"sha1\":\"00\"}},"
                + "{\"url\":\"https://cdn/a.jar\",\"filename\":\"a.jar\",\"primary\":true,\"hashes\":{\"sha1\":\"ab\"}}],"
                + "\"dependencies\":[{\"project_id\":\"P7dR8mSH\",\"dependency_type\":\"required\"},{\"project_id\":\"x\",\"dependency_type\":\"optional\"}]},"
                + "{\"files\":[{\"url\":\"https://cdn/old.jar\",\"filename\":\"old.jar\"}]}]");
        assertNotNull(d);
        assertEquals("a.jar", d.fileName());
        assertEquals("ab", d.sha1());
        assertEquals(java.util.List.of("P7dR8mSH"), d.requires());
        assertNull(Modrinth.pick("[]"));
    }
}
