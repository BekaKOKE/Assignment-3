package com.assignment3.filemanager.storage;

import com.assignment3.filemanager.repository.Database;

import java.nio.file.Path;

public final class StorageFactory {

    private StorageFactory() {
    }

    public static StorageBackend create(StorageType type, Path dataDir, Database database) {
        return switch (type) {
            case LOCAL -> new LocalFileStorage(dataDir.resolve("files"));
            case DATABASE -> new DatabaseStorage(database);
        };
    }
}
