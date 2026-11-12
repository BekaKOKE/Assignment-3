package com.assignment3.filemanager.proxy;

import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.Permission;
import com.assignment3.filemanager.model.Role;
import com.assignment3.filemanager.model.User;

import java.util.Optional;

public final class PermissionChecker {

    public Optional<Role> roleOf(User user, FileRecord file) {
        if (user == null || file == null) {
            return Optional.empty();
        }
        if (file.isOwnedBy(user)) {
            return Optional.of(Role.OWNER);
        }
        if (file.isSharedWith(user)) {
            return Optional.of(Role.VIEWER);
        }
        return Optional.empty();
    }

    public boolean isAllowed(User user, FileRecord file, Permission permission) {
        return roleOf(user, file).map(role -> role.allows(permission)).orElse(false);
    }

    public boolean ownsFolder(User user, Folder folder) {
        return folder != null && folder.isOwnedBy(user);
    }
}
