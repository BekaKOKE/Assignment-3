package com.assignment3.filemanager;

import com.assignment3.filemanager.exception.StorageException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.repository.Database;
import com.assignment3.filemanager.repository.MetadataRepository;
import com.assignment3.filemanager.repository.SqliteMetadataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MetadataRepositoryTest {

    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Test
    @DisplayName("TC-M01 file metadata round-trips through SQLite, including shares and creation date")
    void roundTrip(@TempDir Path dir) {
        Path dbFile = dir.resolve("m.db");
        MetadataRepository repo = new SqliteMetadataRepository(new Database(dbFile));
        FileRecord file = new FileRecord("f1", "report", FileType.JSON, 42, NOW, "beksultan", null, Set.of("ruslan", "kuanysh"));
        repo.saveFile(file);

        MetadataRepository reopened = new SqliteMetadataRepository(new Database(dbFile));
        assertEquals(file, reopened.findFile("f1").orElseThrow());
        assertEquals(1, reopened.findAllFiles().size());
    }

    @Test
    @DisplayName("TC-M02 saving again updates name, folder and shares")
    void update(@TempDir Path dir) {
        MetadataRepository repo = new SqliteMetadataRepository(new Database(dir.resolve("m.db")));
        repo.saveFolder(new Folder("d1", "docs", "beksultan", NOW));
        FileRecord file = new FileRecord("f1", "old", FileType.TXT, 1, NOW, "beksultan", null, Set.of());
        repo.saveFile(file);
        repo.saveFile(file.withName("new").withFolder("d1").withSharedUser("ruslan"));

        FileRecord loaded = repo.findFile("f1").orElseThrow();
        assertEquals("new", loaded.name());
        assertEquals("d1", loaded.folderId());
        assertEquals(Set.of("ruslan"), loaded.sharedWith());
        assertEquals(1, repo.findFilesInFolder("d1").size());
    }

    @Test
    @DisplayName("TC-M03 deleting a file also removes its shares")
    void deleteCascadesShares(@TempDir Path dir) {
        MetadataRepository repo = new SqliteMetadataRepository(new Database(dir.resolve("m.db")));
        repo.saveFile(new FileRecord("f1", "a", FileType.TXT, 1, NOW, "beksultan", null, Set.of("ruslan")));
        repo.deleteFile("f1");
        assertTrue(repo.findFile("f1").isEmpty());
        repo.saveFile(new FileRecord("f1", "a", FileType.TXT, 1, NOW, "beksultan", null, Set.of()));
        assertTrue(repo.findFile("f1").orElseThrow().sharedWith().isEmpty());
    }

    @Test
    @DisplayName("TC-M04 the database refuses a file that points at a missing folder (integrity)")
    void foreignKeyIsEnforced(@TempDir Path dir) {
        MetadataRepository repo = new SqliteMetadataRepository(new Database(dir.resolve("m.db")));
        FileRecord orphan = new FileRecord("f1", "a", FileType.TXT, 1, NOW, "beksultan", "nofolder", Set.of());
        assertThrows(StorageException.class, () -> repo.saveFile(orphan));
        assertTrue(repo.findFile("f1").isEmpty(), "failed save must roll back completely");
    }

    @Test
    @DisplayName("TC-M05 folders round-trip and can be deleted")
    void folders(@TempDir Path dir) {
        Path dbFile = dir.resolve("m.db");
        MetadataRepository repo = new SqliteMetadataRepository(new Database(dbFile));
        Folder folder = new Folder("d1", "docs", "beksultan", NOW);
        repo.saveFolder(folder);
        assertEquals(folder, new SqliteMetadataRepository(new Database(dbFile)).findFolder("d1").orElseThrow());
        repo.deleteFolder("d1");
        assertTrue(repo.findAllFolders().isEmpty());
    }
}
