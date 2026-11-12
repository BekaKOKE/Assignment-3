package com.assignment3.filemanager;

import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Permission;
import com.assignment3.filemanager.model.Role;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.proxy.PermissionChecker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionCheckerTest {

    private final PermissionChecker checker = new PermissionChecker();
    private final User beksultan = new User("beksultan", "Beksultan");
    private final User ruslan = new User("ruslan", "Ruslan");
    private final User kuanysh = new User("kuanysh", "Kuanysh");
    private final FileRecord file = new FileRecord("id1", "f", FileType.TXT, 1, Instant.now(), "beksultan", null, Set.of("ruslan"));

    @Test
    @DisplayName("TC-R01 role is derived from the stored owner and share list")
    void roles() {
        assertEquals(Role.OWNER, checker.roleOf(beksultan, file).orElseThrow());
        assertEquals(Role.VIEWER, checker.roleOf(ruslan, file).orElseThrow());
        assertTrue(checker.roleOf(kuanysh, file).isEmpty());
        assertTrue(checker.roleOf(null, file).isEmpty());
    }

    @Test
    @DisplayName("TC-R02 OWNER has every permission")
    void ownerPermissions() {
        for (Permission p : Permission.values()) {
            assertTrue(checker.isAllowed(beksultan, file, p), p.name());
        }
    }

    @Test
    @DisplayName("TC-R03 VIEWER may only READ")
    void viewerPermissions() {
        assertTrue(checker.isAllowed(ruslan, file, Permission.READ));
        for (Permission p : Permission.values()) {
            if (p != Permission.READ) {
                assertFalse(checker.isAllowed(ruslan, file, p), p.name());
            }
        }
    }

    @Test
    @DisplayName("TC-R04 a user with no role has no permission")
    void noRoleNoPermission() {
        for (Permission p : Permission.values()) {
            assertFalse(checker.isAllowed(kuanysh, file, p), p.name());
        }
    }
}
