package com.assignment3.filemanager;

import com.assignment3.filemanager.TestSupport.Harness;
import com.assignment3.filemanager.TestSupport.Kind;
import com.assignment3.filemanager.exception.AccessDeniedException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionProxyTest {

    private static FileRecord sharedReport(Harness h) {
        FileRecord file = h.proxy.createFile(h.beksultan, "report", FileType.TXT, "secret numbers");
        h.proxy.shareFile(h.beksultan, file.id(), h.ruslan);
        h.realSubjectCalls.clear();
        return file;
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P01 OWNER can read, rename, share and delete")
    void ownerHasAllPermissions(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = h.proxy.createFile(h.beksultan, "mine", FileType.TXT, "data");
        assertEquals("data", h.proxy.readFile(h.beksultan, file.id()));
        assertEquals("renamed", h.proxy.renameFile(h.beksultan, file.id(), "renamed").name());
        assertTrue(h.proxy.shareFile(h.beksultan, file.id(), h.ruslan).isSharedWith(h.ruslan));
        h.proxy.deleteFile(h.beksultan, file.id());
        assertThrows(NotFoundException.class, () -> h.proxy.readFile(h.beksultan, file.id()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P02 VIEWER can read a shared file")
    void viewerCanRead(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = sharedReport(h);
        assertEquals("secret numbers", h.proxy.readFile(h.ruslan, file.id()));
        assertEquals("report", h.proxy.getFileInfo(h.ruslan, file.id()).name());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P03 VIEWER cannot rename, delete, share or move; the file stays untouched")
    void viewerCannotModify(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = sharedReport(h);
        Folder ruslansFolder = h.proxy.createFolder(h.ruslan, "mine");
        h.realSubjectCalls.clear();

        assertThrows(AccessDeniedException.class, () -> h.proxy.renameFile(h.ruslan, file.id(), "hacked"));
        assertThrows(AccessDeniedException.class, () -> h.proxy.deleteFile(h.ruslan, file.id()));
        assertThrows(AccessDeniedException.class, () -> h.proxy.shareFile(h.ruslan, file.id(), h.kuanysh));
        assertThrows(AccessDeniedException.class, () -> h.proxy.moveFileToFolder(h.ruslan, file.id(), ruslansFolder.id()));

        FileRecord after = h.manager.getFileInfo(h.beksultan, file.id());
        assertEquals("report", after.name());
        assertFalse(after.isSharedWith(h.kuanysh));
        assertEquals("secret numbers", h.manager.readFile(h.beksultan, file.id()));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P04 denied requests never reach the real subject")
    void deniedRequestsAreNotForwarded(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = sharedReport(h);

        assertThrows(AccessDeniedException.class, () -> h.proxy.renameFile(h.ruslan, file.id(), "x"));
        assertThrows(AccessDeniedException.class, () -> h.proxy.deleteFile(h.ruslan, file.id()));
        assertThrows(AccessDeniedException.class, () -> h.proxy.readFile(h.kuanysh, file.id()));

        assertFalse(h.realSubjectWasCalled("renameFile"));
        assertFalse(h.realSubjectWasCalled("deleteFile"));
        assertFalse(h.realSubjectWasCalled("readFile"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P05 granted requests ARE forwarded to the real subject")
    void grantedRequestsAreForwarded(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = sharedReport(h);
        h.proxy.readFile(h.ruslan, file.id());
        h.proxy.renameFile(h.beksultan, file.id(), "final");
        assertTrue(h.realSubjectWasCalled("readFile"));
        assertTrue(h.realSubjectWasCalled("renameFile"));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P06 a user with no access cannot read, see or search the file")
    void unsharedUserHasNoAccess(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = sharedReport(h);
        assertThrows(AccessDeniedException.class, () -> h.proxy.readFile(h.kuanysh, file.id()));
        assertThrows(AccessDeniedException.class, () -> h.proxy.getFileInfo(h.kuanysh, file.id()));
        assertTrue(h.proxy.listFiles(h.kuanysh).isEmpty());
        assertTrue(h.proxy.searchByName(h.kuanysh, "report").isEmpty());
        assertEquals(1, h.proxy.listFiles(h.ruslan).size());
        assertEquals(1, h.proxy.searchByName(h.ruslan, "report").size());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P07 permissions come from stored data: sharing changes what the proxy allows")
    void sharingChangesAccess(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        FileRecord file = h.proxy.createFile(h.beksultan, "late", FileType.TXT, "x");
        assertThrows(AccessDeniedException.class, () -> h.proxy.readFile(h.ruslan, file.id()));
        h.proxy.shareFile(h.beksultan, file.id(), h.ruslan);
        assertEquals("x", h.proxy.readFile(h.ruslan, file.id()));
    }

    @Test
    @DisplayName("TC-P08 the same user is OWNER of own files and VIEWER of shared ones")
    void roleIsPerFile(@TempDir Path dir) {
        Harness h = TestSupport.harness(Kind.MEMORY, dir);
        FileRecord beksultans = h.proxy.createFile(h.beksultan, "from-beksultan", FileType.TXT, "a");
        FileRecord ruslans = h.proxy.createFile(h.ruslan, "from-ruslan", FileType.TXT, "b");
        h.proxy.shareFile(h.beksultan, beksultans.id(), h.ruslan);

        h.proxy.renameFile(h.ruslan, ruslans.id(), "ruslans-renamed");
        assertThrows(AccessDeniedException.class,
                () -> h.proxy.renameFile(h.ruslan, beksultans.id(), "nope"));
    }

    @Test
    @DisplayName("TC-P09 unknown users and null users are denied everything")
    void unknownUser(@TempDir Path dir) {
        Harness h = TestSupport.harness(Kind.MEMORY, dir);
        FileRecord file = h.proxy.createFile(h.beksultan, "f", FileType.TXT, "x");
        User mallory = new User("mallory", "Mallory");
        assertThrows(AccessDeniedException.class, () -> h.proxy.readFile(mallory, file.id()));
        assertThrows(AccessDeniedException.class, () -> h.proxy.createFile(mallory, "m", FileType.TXT, "x"));
        assertThrows(AccessDeniedException.class, () -> h.proxy.listFiles(null));
    }

    @Test
    @DisplayName("TC-P10 every decision is written to the audit log (GRANTED and DENIED)")
    void auditLog(@TempDir Path dir) {
        Harness h = TestSupport.harness(Kind.MEMORY, dir);
        FileRecord file = sharedReport(h);
        h.audit.clear();
        h.proxy.readFile(h.ruslan, file.id());
        assertThrows(AccessDeniedException.class, () -> h.proxy.deleteFile(h.ruslan, file.id()));
        assertTrue(h.audit.stream().anyMatch(l -> l.contains("ruslan") && l.contains("READ") && l.contains("GRANTED")));
        assertTrue(h.audit.stream().anyMatch(l -> l.contains("ruslan") && l.contains("DELETE") && l.contains("DENIED")));
    }

    @Test
    @DisplayName("TC-P11 the denial message names the user, role, operation and file")
    void denialMessage(@TempDir Path dir) {
        Harness h = TestSupport.harness(Kind.MEMORY, dir);
        FileRecord file = sharedReport(h);
        AccessDeniedException e = assertThrows(AccessDeniedException.class,
                () -> h.proxy.renameFile(h.ruslan, file.id(), "x"));
        assertTrue(e.getMessage().contains("ruslan") && e.getMessage().contains("VIEWER")
                && e.getMessage().contains("RENAME") && e.getMessage().contains("report.txt"), e.getMessage());
    }

    @Test
    @DisplayName("TC-P12 sharing with an unknown user or with the owner is rejected")
    void shareValidation(@TempDir Path dir) {
        Harness h = TestSupport.harness(Kind.MEMORY, dir);
        FileRecord file = h.proxy.createFile(h.beksultan, "f", FileType.TXT, "x");
        User ghost = new User("ghost", "Ghost");
        assertThrows(NotFoundException.class, () -> h.proxy.shareFile(h.beksultan, file.id(), ghost));
        assertThrows(com.assignment3.filemanager.exception.ValidationException.class,
                () -> h.proxy.shareFile(h.beksultan, file.id(), h.beksultan));
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P13 only the folder owner can delete a folder or move files into it")
    void folderOwnership(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        Folder docs = h.proxy.createFolder(h.beksultan, "docs");
        FileRecord ruslansFile = h.proxy.createFile(h.ruslan, "ruslans", FileType.TXT, "x");
        assertThrows(AccessDeniedException.class, () -> h.proxy.deleteFolder(h.ruslan, docs.id()));
        assertThrows(AccessDeniedException.class, () -> h.proxy.moveFileToFolder(h.ruslan, ruslansFile.id(), docs.id()));
        h.proxy.deleteFolder(h.beksultan, docs.id());
    }

    @ParameterizedTest
    @EnumSource(Kind.class)
    @DisplayName("TC-P14 a viewer sees a folder only through files shared with them")
    void folderVisibility(Kind kind, @TempDir Path dir) {
        Harness h = TestSupport.harness(kind, dir);
        Folder docs = h.proxy.createFolder(h.beksultan, "docs");
        FileRecord shared = h.proxy.createFile(h.beksultan, "shared", FileType.TXT, "s");
        FileRecord hidden = h.proxy.createFile(h.beksultan, "hidden", FileType.TXT, "h");
        h.proxy.moveFileToFolder(h.beksultan, shared.id(), docs.id());
        h.proxy.moveFileToFolder(h.beksultan, hidden.id(), docs.id());

        assertTrue(h.proxy.listFolders(h.ruslan).isEmpty());
        assertThrows(AccessDeniedException.class, () -> h.proxy.listFolderContents(h.ruslan, docs.id()));

        h.proxy.shareFile(h.beksultan, shared.id(), h.ruslan);
        assertEquals(1, h.proxy.listFolders(h.ruslan).size());
        var visible = h.proxy.listFolderContents(h.ruslan, docs.id());
        assertEquals(1, visible.size());
        assertEquals("shared", visible.get(0).name());
        assertEquals(2, h.proxy.listFolderContents(h.beksultan, docs.id()).size());
    }
}
