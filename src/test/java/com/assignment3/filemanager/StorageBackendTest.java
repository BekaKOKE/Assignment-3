package com.assignment3.filemanager;

import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.exception.ValidationException;
import com.assignment3.filemanager.repository.Database;
import com.assignment3.filemanager.storage.LocalFileStorage;
import com.assignment3.filemanager.storage.StorageBackend;
import com.assignment3.filemanager.TestSupport.Kind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageBackendTest {

    private static StorageBackend create(Kind kind, Path dir) {
        return TestSupport.backend(kind, dir, new Database(dir.resolve("s.db")));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-S01 write then read returns the same bytes (incl. unicode and binary)")
    void roundTrip(Kind kind, @TempDir Path dir) {
        StorageBackend storage = create(kind, dir);
        byte[] text = "héllo 日本語".getBytes(StandardCharsets.UTF_8);
        byte[] binary = {0, 1, 2, (byte) 0xFF, (byte) 0x80};
        storage.write("text1", text);
        storage.write("bin1", binary);
        assertArrayEquals(text, storage.read("text1"));
        assertArrayEquals(binary, storage.read("bin1"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-S02 writing an existing key overwrites it")
    void overwrite(Kind kind, @TempDir Path dir) {
        StorageBackend storage = create(kind, dir);
        storage.write("k1", "old".getBytes(StandardCharsets.UTF_8));
        storage.write("k1", "new".getBytes(StandardCharsets.UTF_8));
        assertEquals("new", new String(storage.read("k1"), StandardCharsets.UTF_8));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-S03 reading a missing key throws NotFoundException")
    void readMissing(Kind kind, @TempDir Path dir) {
        StorageBackend storage = create(kind, dir);
        assertThrows(NotFoundException.class, () -> storage.read("missing"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-S04 exists / delete; deleting a missing key is not an error")
    void existsAndDelete(Kind kind, @TempDir Path dir) {
        StorageBackend storage = create(kind, dir);
        assertFalse(storage.exists("k2"));
        storage.write("k2", new byte[]{1});
        assertTrue(storage.exists("k2"));
        storage.delete("k2");
        assertFalse(storage.exists("k2"));
        storage.delete("k2");
        assertThrows(NotFoundException.class, () -> storage.read("k2"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-S05 empty content can be stored")
    void emptyContent(Kind kind, @TempDir Path dir) {
        StorageBackend storage = create(kind, dir);
        storage.write("empty", new byte[0]);
        assertEquals(0, storage.read("empty").length);
    }

    @Test
    @DisplayName("TC-S06 local storage rejects ids that could escape the storage directory")
    void localPathTraversal(@TempDir Path dir) throws Exception {
        LocalFileStorage storage = new LocalFileStorage(dir.resolve("files"));
        for (String evil : new String[]{"../evil", "..\\evil", "a/b", "C:\\x", "", "x.y", null}) {
            assertThrows(ValidationException.class, () -> storage.write(evil, new byte[]{1}), "id: " + evil);
        }
        try (var listing = Files.list(dir)) {
            assertEquals(1, listing.count(), "nothing may be written outside the storage directory");
        }
    }

    @Test
    @DisplayName("TC-S07 local storage keeps the content as a real file on disk")
    void localWritesRealFile(@TempDir Path dir) {
        LocalFileStorage storage = new LocalFileStorage(dir.resolve("files"));
        storage.write("abc123", "hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(Files.exists(dir.resolve("files").resolve("abc123.dat")));
    }
}
