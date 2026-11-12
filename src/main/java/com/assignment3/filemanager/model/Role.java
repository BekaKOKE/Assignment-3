package com.assignment3.filemanager.model;

import java.util.EnumSet;
import java.util.Set;

public enum Role {
    OWNER(EnumSet.allOf(Permission.class)),
    VIEWER(EnumSet.of(Permission.READ));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public boolean allows(Permission permission) {
        return permissions.contains(permission);
    }
}
