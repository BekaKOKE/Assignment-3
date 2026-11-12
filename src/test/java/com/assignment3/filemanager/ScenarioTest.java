package com.assignment3.filemanager;

import com.assignment3.filemanager.app.FileSystemContext;
import com.assignment3.filemanager.exception.AccessDeniedException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.service.FileService;
import com.assignment3.filemanager.storage.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioTest {

    @ParameterizedTest
    @EnumSource(StorageType.class)
    @DisplayName("TC-E01 required scenario: create -> store -> share -> second user reads through the proxy")
    void requiredScenario(StorageType storage, @TempDir Path dir) {
        List<String> audit = new ArrayList<>();
        FileSystemContext ctx = FileSystemContext.open(dir, storage, audit::add);
        FileService service = ctx.service();
        User beksultan = ctx.users().find("beksultan").orElseThrow();
        User ruslan = ctx.users().find("ruslan").orElseThrow();

        FileRecord file = service.createFile(beksultan, "plan", FileType.TXT, "launch on Friday");
        assertEquals(storage, ctx.storageType());
        if (storage == StorageType.LOCAL) {
            assertTrue(Files.exists(dir.resolve("files").resolve(file.id() + ".dat")), "content must be a file on disk");
        }

        assertThrows(AccessDeniedException.class, () -> service.readFile(ruslan, file.id()));

        service.shareFile(beksultan, file.id(), ruslan);
        assertEquals("launch on Friday", service.readFile(ruslan, file.id()));

        assertTrue(audit.stream().anyMatch(l -> l.contains("ruslan") && l.contains("READ") && l.contains("DENIED")));
        assertTrue(audit.stream().anyMatch(l -> l.contains("ruslan") && l.contains("READ") && l.contains("GRANTED")));
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    @DisplayName("TC-E02 metadata, content and shares persist across an application restart")
    void persistence(StorageType storage, @TempDir Path dir) {
        FileSystemContext first = FileSystemContext.open(dir, storage, null);
        User beksultan = first.users().find("beksultan").orElseThrow();
        User ruslan = first.users().find("ruslan").orElseThrow();
        FileRecord file = first.service().createFile(beksultan, "persist", FileType.MD, "still here");
        first.service().shareFile(beksultan, file.id(), ruslan);
        var folder = first.service().createFolder(beksultan, "keep");
        first.service().moveFileToFolder(beksultan, file.id(), folder.id());

        FileSystemContext second = FileSystemContext.open(dir, null, null);
        assertEquals(storage, second.storageType());
        FileRecord loaded = second.service().getFileInfo(ruslan, file.id());
        assertEquals("persist", loaded.name());
        assertEquals(folder.id(), loaded.folderId());
        assertEquals("still here", second.service().readFile(ruslan, file.id()));
        assertThrows(AccessDeniedException.class,
                () -> second.service().renameFile(ruslan, file.id(), "x"));
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    @DisplayName("TC-E03 BRIDGE: storage is swapped at runtime and by restart option; same code, same results")
    void swapStorage(StorageType start, @TempDir Path dir) {
        FileSystemContext ctx = FileSystemContext.open(dir, start, null);
        User beksultan = ctx.users().find("beksultan").orElseThrow();
        User ruslan = ctx.users().find("ruslan").orElseThrow();
        FileRecord file = ctx.service().createFile(beksultan, "move-me", FileType.TXT, "portable");
        ctx.service().shareFile(beksultan, file.id(), ruslan);

        ctx.switchStorage(start.other());
        assertEquals(start.other(), ctx.storageType());
        assertEquals("portable", ctx.service().readFile(ruslan, file.id()));
        assertThrows(AccessDeniedException.class, () -> ctx.service().deleteFile(ruslan, file.id()));

        FileSystemContext reopened = FileSystemContext.open(dir, start, null);
        assertEquals(start, reopened.storageType());
        assertEquals("portable", reopened.service().readFile(ruslan, file.id()));
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    @DisplayName("TC-E04 the old backend is cleaned up after a swap (no duplicated content)")
    void oldBackendCleanedUp(StorageType start, @TempDir Path dir) {
        FileSystemContext ctx = FileSystemContext.open(dir, start, null);
        User beksultan = ctx.users().find("beksultan").orElseThrow();
        FileRecord file = ctx.service().createFile(beksultan, "once", FileType.TXT, "only one copy");
        Path onDisk = dir.resolve("files").resolve(file.id() + ".dat");

        ctx.switchStorage(start.other());
        boolean localIsNowActive = ctx.storageType() == StorageType.LOCAL;
        assertEquals(localIsNowActive, Files.exists(onDisk), "content must exist on disk only while LOCAL is active");
    }

    @ParameterizedTest
    @EnumSource(StorageType.class)
    @DisplayName("TC-E05 deleting a file removes it for every user and from storage")
    void deleteRemovesEverywhere(StorageType storage, @TempDir Path dir) {
        FileSystemContext ctx = FileSystemContext.open(dir, storage, null);
        User beksultan = ctx.users().find("beksultan").orElseThrow();
        User ruslan = ctx.users().find("ruslan").orElseThrow();
        FileRecord file = ctx.service().createFile(beksultan, "temp", FileType.LOG, "x");
        ctx.service().shareFile(beksultan, file.id(), ruslan);
        ctx.service().deleteFile(beksultan, file.id());
        assertThrows(NotFoundException.class, () -> ctx.service().readFile(ruslan, file.id()));
        assertTrue(ctx.service().listFiles(ruslan).isEmpty());
        assertTrue(Files.notExists(dir.resolve("files").resolve(file.id() + ".dat")));
    }
}
