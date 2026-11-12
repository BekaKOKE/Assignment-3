package com.assignment3.filemanager.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public record FileRecord(String id,
                         String name,
                         FileType type,
                         long size,
                         Instant createdAt,
                         String ownerId,
                         String folderId,
                         Set<String> sharedWith) {

    public FileRecord {
        sharedWith = sharedWith == null ? Set.of() : Set.copyOf(sharedWith);
    }

    public String displayName() {
        return name + "." + type.extension();
    }

    public boolean isOwnedBy(User user) {
        return user != null && ownerId.equals(user.id());
    }

    public boolean isSharedWith(User user) {
        return user != null && sharedWith.contains(user.id());
    }

    public FileRecord withName(String newName) {
        return new FileRecord(id, newName, type, size, createdAt, ownerId, folderId, sharedWith);
    }

    public FileRecord withFolder(String newFolderId) {
        return new FileRecord(id, name, type, size, createdAt, ownerId, newFolderId, sharedWith);
    }

    public FileRecord withSharedUser(String userId) {
        Set<String> shared = new HashSet<>(sharedWith);
        shared.add(userId);
        return new FileRecord(id, name, type, size, createdAt, ownerId, folderId, shared);
    }
}
