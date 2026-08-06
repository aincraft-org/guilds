package com.azoth.territory.influence;

import com.azoth.territory.influence.Declaration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfluenceStoreTest {

    @TempDir
    Path tempDir;

    private InfluenceStore store() {
        return new InfluenceStore(tempDir.resolve("influence.json"));
    }

    private static InfluenceState stateWithEverything() {
        InfluenceState state = new InfluenceState();
        TerritoryEntry entry = new TerritoryEntry();
        entry.ownerGuildId = "everfall-town";
        entry.cooldownUntilEpochMs = 0L;
        entry.bars.put("rival-guild", 62.5);
        entry.bars.put("other-guild", 100.0);
        entry.declaration = new Declaration(
                "rival-guild", 1780000000000L, 1780086400000L);
        entry.pendingFlip = new PendingFlip(
                "everfall", "everfall-town", "rival-guild",
                1780086400000L, 1780691200000L);
        state.entries.put("everfall", entry);
        return state;
    }

    @Test
    void missingFile_loadsEmptyState() throws IOException {
        InfluenceState loaded = store().load();
        assertTrue(loaded.entries.isEmpty());
    }

    @Test
    void roundTrip_preservesAllFields() throws IOException {
        InfluenceStore s = store();
        s.save(stateWithEverything());

        InfluenceState loaded = s.load();
        TerritoryEntry e = loaded.entries.get("everfall");
        assertEquals("everfall-town", e.ownerGuildId);
        assertEquals(0L, e.cooldownUntilEpochMs);
        assertEquals(62.5, e.bars.get("rival-guild"), 0.001);
        assertEquals(100.0, e.bars.get("other-guild"), 0.001);
        assertEquals("rival-guild", e.declaration.guildId());
        assertEquals(1780086400000L, e.declaration.flipAtEpochMs());
        assertEquals("everfall-town", e.pendingFlip.oldOwnerGuildId());
        assertEquals(1780691200000L, e.pendingFlip.cooldownUntilEpochMs());
    }

    @Test
    void save_createsParentDirectories() throws IOException {
        InfluenceStore s = new InfluenceStore(tempDir.resolve("nested/dir/influence.json"));
        s.save(new InfluenceState());
        assertTrue(Files.isRegularFile(tempDir.resolve("nested/dir/influence.json")));
    }

    @Test
    void save_leavesNoTempFileBehind() throws IOException {
        InfluenceStore s = store();
        s.save(stateWithEverything());
        Path temp = tempDir.resolve("influence.json.tmp");
        assertFalse(Files.exists(temp), "temp file must be moved/removed");
    }

    @Test
    void corruptFile_throwsIOException() throws Exception {
        Path file = tempDir.resolve("influence.json");
        Files.writeString(file, "{ not json");
        assertThrows(IOException.class, () -> store().load());
    }

    @Test
    void wrongRootType_throwsIOException() throws Exception {
        Files.writeString(tempDir.resolve("influence.json"), "[1,2,3]");
        assertThrows(IOException.class, () -> store().load());
    }

    @Test
    void wrongVersion_throwsIOException() throws Exception {
        Files.writeString(tempDir.resolve("influence.json"), """
                {"version": 99, "territories": {}}
                """);
        assertThrows(IOException.class, () -> store().load());
    }

    @Test
    void backupCorrupt_movesFileAsidePreservingContent() throws Exception {
        Path file = tempDir.resolve("influence.json");
        Files.writeString(file, "{ corrupt content");
        Path backup = store().backupCorrupt();

        assertFalse(Files.exists(file), "original must be moved away");
        assertTrue(Files.isRegularFile(backup), "backup must exist: " + backup);
        assertEquals("{ corrupt content", Files.readString(backup));
        assertTrue(backup.getFileName().toString().startsWith("influence.json.corrupt-"));
    }

    @Test
    void backupCorrupt_withoutFile_throwsIOException() {
        assertThrows(IOException.class, () -> store().backupCorrupt());
    }

    @Test
    void missingOptionalFields_loadAsDefaults() throws IOException {
        Files.writeString(tempDir.resolve("influence.json"), """
                {"version": 1, "territories": {"everfall": {"ownerGuildId": "everfall-town"}}}
                """);
        TerritoryEntry e = store().load().entries.get("everfall");
        assertEquals("everfall-town", e.ownerGuildId);
        assertEquals(0L, e.cooldownUntilEpochMs);
        assertTrue(e.bars.isEmpty());
        assertNull(e.declaration);
        assertNull(e.pendingFlip);
    }

    @Test
    void jsonIsStableAndSorted() throws IOException {
        InfluenceState state = new InfluenceState();
        TerritoryEntry a = new TerritoryEntry();
        a.ownerGuildId = "g1";
        a.bars.put("zeta", 1.0);
        a.bars.put("alpha", 2.0);
        TerritoryEntry b = new TerritoryEntry();
        b.ownerGuildId = "g2";
        state.entries.put("t2", b);
        state.entries.put("t1", a);
        store().save(state);

        String raw = Files.readString(tempDir.resolve("influence.json"));
        assertTrue(raw.contains("\"t1\""), "entries sorted by territory id: " + raw);
        assertTrue(raw.contains("\"t2\""), "entries sorted by territory id: " + raw);
        assertTrue(raw.indexOf("\"t1\"") < raw.indexOf("\"t2\""), "t1 before t2");
    }
}
