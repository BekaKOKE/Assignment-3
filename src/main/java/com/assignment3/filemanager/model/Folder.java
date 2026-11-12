package com.assignment3.filemanager.model;

import java.time.Instant;

public record Folder(String id, String name, String ownerId, Instant createdAt) {

    public boolean isOwnedBy(User user) {
        return user != null && ownerId.equals(user.id());
    }
}
