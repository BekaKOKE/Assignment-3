package com.assignment3.filemanager;

import com.assignment3.filemanager.TestSupport.Harness;
import com.assignment3.filemanager.TestSupport.Kind;
import com.assignment3.filemanager.exception.ConflictException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.exception.ValidationException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FolderTest {

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-D01 create a folder and list folders")
    void createAndList(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        Folder docs = h.manager.createFolder(h.beksultan, "docs");
        assertEquals("docs", docs.name());
        assertEquals("beksultan", docs.ownerId());
        assertEquals(1, h.manager.listFolders(h.beksultan).size());
        assertEquals("docs", h.manager.getFolder(h.beksultan, docs.id()).name());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-D02 invalid or duplicate folder names are rejected")
    void folderNameRules(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        h.manager.createFolder(h.beksultan, "docs");
        assertThrows(ConflictException.class, () -> h.manager.createFolder(h.beksultan, "DOCS"));
        assertThrows(ValidationException.class, () -> h.manager.createFolder(h.beksultan, ""));
        assertThrows(ValidationException.class, () -> h.manager.createFolder(h.beksultan, "a/b"));
        h.manager.createFolder(h.ruslan, "docs");
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-D03 move a file into a folder, see it in the contents, and move it back to the root")
    void moveFile(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        Folder docs = h.manager.createFolder(h.beksultan, "docs");
        FileRecord file = h.manager.createFile(h.beksultan, "a", FileType.TXT, "x");

        FileRecord moved = h.manager.moveFileToFolder(h.beksultan, file.id(), docs.id());
        assertEquals(docs.id(), moved.folderId());
        assertEquals(1, h.manager.listFolderContents(h.beksultan, docs.id()).size());
        assertEquals("x", h.manager.readFile(h.beksultan, file.id()));

        FileRecord back = h.manager.moveFileToFolder(h.beksultan, file.id(), null);
        assertNull(back.folderId());
        assertTrue(h.manager.listFolderContents(h.beksultan, docs.id()).isEmpty());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-D04 moving into a missing folder or another owner's folder is rejected")
    void moveRules(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = h.manager.createFile(h.beksultan, "a", FileType.TXT, "x");
        Folder ruslansFolder = h.manager.createFolder(h.ruslan, "ruslans");
        assertThrows(NotFoundException.class, () -> h.manager.moveFileToFolder(h.beksultan, file.id(), "nofolder"));
        assertThrows(ConflictException.class, () -> h.manager.moveFileToFolder(h.beksultan, file.id(), ruslansFolder.id()));
        assertNull(h.manager.getFileInfo(h.beksultan, file.id()).folderId());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-D05 POLICY: a non-empty folder cannot be deleted; its files are untouched")
    void nonEmptyFolderIsRefused(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        Folder docs = h.manager.createFolder(h.beksultan, "docs");
        FileRecord file = h.manager.createFile(h.beksultan, "a", FileType.TXT, "keep me");
        h.manager.moveFileToFolder(h.beksultan, file.id(), docs.id());

        ConflictException e = assertThrows(ConflictException.class, () -> h.manager.deleteFolder(h.beksultan, docs.id()));
        assertTrue(e.getMessage().contains("not empty"));
        assertEquals("keep me", h.manager.readFile(h.beksultan, file.id()));
        assertEquals(1, h.manager.listFolders(h.beksultan).size());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-D06 an empty folder can be deleted (also after its files were moved out)")
    void emptyFolderCanBeDeleted(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        Folder docs = h.manager.createFolder(h.beksultan, "docs");
        FileRecord file = h.manager.createFile(h.beksultan, "a", FileType.TXT, "x");
        h.manager.moveFileToFolder(h.beksultan, file.id(), docs.id());
        h.manager.moveFileToFolder(h.beksultan, file.id(), null);
        h.manager.deleteFolder(h.beksultan, docs.id());
        assertTrue(h.manager.listFolders(h.beksultan).isEmpty());
        assertThrows(NotFoundException.class, () -> h.manager.deleteFolder(h.beksultan, docs.id()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-D07 deleting a file inside a folder empties the folder, so it can then be deleted")
    void deleteFileThenFolder(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        Folder docs = h.manager.createFolder(h.beksultan, "docs");
        FileRecord file = h.manager.createFile(h.beksultan, "a", FileType.TXT, "x");
        h.manager.moveFileToFolder(h.beksultan, file.id(), docs.id());
        h.manager.deleteFile(h.beksultan, file.id());
        h.manager.deleteFolder(h.beksultan, docs.id());
        assertTrue(h.manager.listFolders(h.beksultan).isEmpty());
    }
}
