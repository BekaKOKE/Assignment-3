package com.assignment3.filemanager;

import com.assignment3.filemanager.TestSupport.Harness;
import com.assignment3.filemanager.TestSupport.Kind;
import com.assignment3.filemanager.exception.ConflictException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.exception.StorageException;
import com.assignment3.filemanager.exception.ValidationException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.service.DefaultFileManager;
import com.assignment3.filemanager.service.FileManager;
import com.assignment3.filemanager.storage.InMemoryStorage;
import com.assignment3.filemanager.storage.StorageBackend;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileManagerBridgeTest {

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-F01 create stores content via the backend and returns correct metadata")
    void create(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = h.manager.createFile(h.beksultan, "notes", FileType.MD, "# Hello");
        assertEquals("notes", file.name());
        assertEquals(FileType.MD, file.type());
        assertEquals(7, file.size());
        assertEquals("beksultan", file.ownerId());
        assertNotNull(file.createdAt());
        assertEquals("# Hello", h.manager.readFile(h.beksultan, file.id()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-F02 list shows all files, search is case-insensitive and partial")
    void listAndSearch(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        h.manager.createFile(h.beksultan, "Budget 2024", FileType.CSV, "a,b");
        h.manager.createFile(h.beksultan, "diary", FileType.TXT, "dear diary");
        assertEquals(2, h.manager.listFiles(h.beksultan).size());
        List<FileRecord> found = h.manager.searchByName(h.beksultan, "budg");
        assertEquals(1, found.size());
        assertEquals("Budget 2024", found.get(0).name());
        assertTrue(h.manager.searchByName(h.beksultan, "zzz").isEmpty());
        assertThrows(ValidationException.class, () -> h.manager.searchByName(h.beksultan, "  "));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-F03 rename changes the name only; content and metadata are kept")
    void rename(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = h.manager.createFile(h.beksultan, "old", FileType.TXT, "data");
        FileRecord renamed = h.manager.renameFile(h.beksultan, file.id(), "new");
        assertEquals("new", renamed.name());
        assertEquals(file.createdAt(), renamed.createdAt());
        assertEquals("data", h.manager.readFile(h.beksultan, file.id()));
        assertEquals("new", h.manager.getFileInfo(h.beksultan, file.id()).name());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-F04 delete removes metadata and content")
    void delete(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = h.manager.createFile(h.beksultan, "gone", FileType.TXT, "x");
        h.manager.deleteFile(h.beksultan, file.id());
        assertThrows(NotFoundException.class, () -> h.manager.getFileInfo(h.beksultan, file.id()));
        assertThrows(NotFoundException.class, () -> h.manager.readFile(h.beksultan, file.id()));
        assertTrue(h.manager.listFiles(h.beksultan).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-F05 duplicate name+type for one owner is rejected, other owner or other type is fine")
    void duplicateNames(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        h.manager.createFile(h.beksultan, "same", FileType.TXT, "1");
        assertThrows(ConflictException.class, () -> h.manager.createFile(h.beksultan, "SAME", FileType.TXT, "2"));
        h.manager.createFile(h.beksultan, "same", FileType.MD, "3");
        h.manager.createFile(h.ruslan, "same", FileType.TXT, "4");
        FileRecord other = h.manager.createFile(h.beksultan, "other", FileType.TXT, "5");
        assertThrows(ConflictException.class, () -> h.manager.renameFile(h.beksultan, other.id(), "same"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-F06 invalid input is rejected and leaves no half-created file behind")
    void invalidInput(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        assertThrows(ValidationException.class, () -> h.manager.createFile(h.beksultan, "a/b", FileType.TXT, "x"));
        assertThrows(ValidationException.class, () -> h.manager.createFile(h.beksultan, "ok", null, "x"));
        assertThrows(ValidationException.class, () -> h.manager.createFile(h.beksultan, "ok", FileType.TXT, null));
        assertThrows(ValidationException.class, () -> h.manager.createFile(null, "ok", FileType.TXT, "x"));
        assertThrows(NotFoundException.class, () -> h.manager.readFile(h.beksultan, "doesnotexist"));
        assertThrows(ValidationException.class, () -> h.manager.readFile(h.beksultan, " "));
        assertTrue(h.manager.listFiles(h.beksultan).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-B01 BRIDGE: switching the storage implementation keeps all content readable")
    void switchStoragePreservesContent(Kind from, @TempDir Path dir) {
        Harness h = TestSupport.harness(from, dir);
        FileRecord a = h.manager.createFile(h.beksultan, "a", FileType.TXT, "alpha");
        FileRecord b = h.manager.createFile(h.beksultan, "b", FileType.JSON, "{\"k\":1}");

        InMemoryStorage target = new InMemoryStorage();
        h.manager.switchStorage(target);

        assertEquals("memory", h.manager.storageName());
        assertEquals("alpha", h.manager.readFile(h.beksultan, a.id()));
        assertEquals("{\"k\":1}", h.manager.readFile(h.beksultan, b.id()));
        assertTrue(target.exists(a.id()));
        assertTrue(target.exists(b.id()));
    }

    @Test
    @DisplayName("TC-B02 BRIDGE: a failing target backend leaves the system on the old backend, data intact")
    void switchStorageFailureRollsBack(@TempDir Path dir) {
        Harness h = TestSupport.harness(Kind.LOCAL, dir);
        FileRecord file = h.manager.createFile(h.beksultan, "safe", FileType.TXT, "precious");

        StorageBackend broken = new StorageBackend() {
            public String name() { return "broken"; }
            public void write(String id, byte[] data) { throw new StorageException("disk full"); }
            public byte[] read(String id) { throw new StorageException("unavailable"); }
            public void delete(String id) { }
            public boolean exists(String id) { return false; }
        };

        assertThrows(StorageException.class, () -> h.manager.switchStorage(broken));
        assertEquals("local", h.manager.storageName());
        assertEquals("precious", h.manager.readFile(h.beksultan, file.id()));
    }

    @Test
    @DisplayName("TC-B03 BRIDGE: the abstraction depends only on the StorageBackend interface")
    void abstractionHasNoConcreteBackendDependency() {
        for (Class<?> type : new Class<?>[]{FileManager.class, DefaultFileManager.class}) {
            for (Field field : type.getDeclaredFields()) {
                String typeName = field.getType().getSimpleName();
                assertFalse(typeName.equals("LocalFileStorage") || typeName.equals("DatabaseStorage")
                                || typeName.equals("InMemoryStorage"),
                        type.getSimpleName() + "." + field.getName() + " must not reference a concrete backend");
            }
        }
        boolean hasBridgeField = false;
        for (Field field : FileManager.class.getDeclaredFields()) {
            hasBridgeField |= field.getType() == StorageBackend.class;
        }
        assertTrue(hasBridgeField, "FileManager must hold the StorageBackend by composition");
    }
}
