package io.github.georgwav.cosmetics.client.skins;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reads a launcher database made by SQLite itself (src/test/resources/skins, made with Python's sqlite3): 1 KiB
 * pages, so the PNGs spill onto overflow pages; the textures table WITHOUT ROWID; and a copy whose newest skin is
 * only in the write-ahead log.
 */
class SkinLibraryTest {
    private static final UUID MINE = UUID.fromString("6f1c2a3b-4d5e-4f60-7182-93a4b5c6d7e8");

    private static Path copy(Path dir, String name) throws IOException {
        try (InputStream in = SkinLibraryTest.class.getResourceAsStream("/skins/" + name)) {
            Path to = dir.resolve(name);
            Files.copy(in, to);
            return to;
        }
    }

    private static List<String> keys() throws IOException {
        try (InputStream in = SkinLibraryTest.class.getResourceAsStream("/skins/keys.txt")) {
            return List.of(new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().split("\n"));
        }
    }

    private static String sha256(byte[] bytes) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    @Test
    void readsAPlayersSkinsInTheLaunchersOrder() throws Exception {
        Path dir = Files.createTempDirectory("skins");
        List<SkinLibrary.Skin> skins = SkinLibrary.load(copy(dir, "app.db"), MINE);
        List<String> keys = keys();
        // Saved in the order 5, 4, 3, 2, 1, 0, then 9; skin 2 again with a cape shows once.
        List<String> expected = List.of(keys.get(5), keys.get(4), keys.get(3), keys.get(2), keys.get(1), keys.get(0), keys.get(9));
        assertEquals(expected, skins.stream().map(SkinLibrary.Skin::key).toList());
        for (SkinLibrary.Skin skin : skins) {
            // Each PNG is whole: its key is the hash of its bytes.
            assertEquals(skin.key(), "local-" + sha256(skin.png()));
        }
        assertTrue(skins.get(0).slim());
        assertFalse(skins.get(1).slim());
    }

    @Test
    void readsWhatIsOnlyInTheWriteAheadLog() throws Exception {
        Path dir = Files.createTempDirectory("skins");
        copy(dir, "wal.db-wal");
        List<SkinLibrary.Skin> skins = SkinLibrary.load(copy(dir, "wal.db"), MINE);
        assertEquals(7, skins.size());
        assertEquals(keys().get(9), skins.get(6).key());
    }

    @Test
    void showsEveryAccountsSkinsWhenNoneAreThePlayers() throws Exception {
        Path dir = Files.createTempDirectory("skins");
        List<SkinLibrary.Skin> skins = SkinLibrary.load(copy(dir, "app.db"), UUID.randomUUID());
        assertEquals(10, skins.size());
    }

    @Test
    void readsIdsAsBytesOrText() {
        assertEquals(MINE, SkinLibrary.uuid("6f1c2a3b4d5e4f60718293a4b5c6d7e8"));
        assertEquals(MINE, SkinLibrary.uuid("6F1C2A3B-4D5E-4F60-7182-93A4B5C6D7E8"));
        assertEquals(MINE, SkinLibrary.uuid(HexFormat.of().parseHex("6f1c2a3b4d5e4f60718293a4b5c6d7e8")));
    }
}
