package com.assignment3.filemanager.app;

import com.assignment3.filemanager.model.UserDirectory;
import com.assignment3.filemanager.proxy.PermissionChecker;
import com.assignment3.filemanager.proxy.ProtectionProxy;
import com.assignment3.filemanager.repository.Database;
import com.assignment3.filemanager.repository.SqliteMetadataRepository;
import com.assignment3.filemanager.service.DefaultFileManager;
import com.assignment3.filemanager.service.FileService;
import com.assignment3.filemanager.storage.StorageFactory;
import com.assignment3.filemanager.storage.StorageType;

import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Composition root: builds the storage, the real file manager and the proxy and connects them.
 * The real manager stays private; clients only get the proxy from service(), so there is no way
 * to call the file manager and skip the permission checks.
 */
public final class FileSystemContext {

    private static final String STORAGE_SETTING = "storage";

    private final Path dataDir;
    private final Database database;
    private final UserDirectory users = UserDirectory.predefined();
    private final DefaultFileManager manager;
    private final FileService proxy;
    private StorageType storageType;

    private FileSystemContext(Path dataDir, StorageType requested, Consumer<String> auditLog) {
        this.dataDir = dataDir;
        this.database = new Database(dataDir.resolve("filemanager.db"));
        this.storageType = database.getSetting(STORAGE_SETTING)
                .map(StorageType::parse)
                .orElse(requested != null ? requested : StorageType.LOCAL);
        this.manager = new DefaultFileManager(
                StorageFactory.create(storageType, dataDir, database),
                new SqliteMetadataRepository(database),
                users);
        this.proxy = new ProtectionProxy(manager, new PermissionChecker(), users, auditLog);
        database.putSetting(STORAGE_SETTING, storageType.name());
    }

    public static FileSystemContext open(Path dataDir, StorageType requested, Consumer<String> auditLog) {
        FileSystemContext context = new FileSystemContext(dataDir, requested, auditLog);
        if (requested != null) {
            context.switchStorage(requested);
        }
        return context;
    }

    public FileService service() {
        return proxy;
    }

    public UserDirectory users() {
        return users;
    }

    public StorageType storageType() {
        return storageType;
    }

    public Path dataDir() {
        return dataDir;
    }

    public void switchStorage(StorageType target) {
        if (target == storageType) {
            return;
        }
        manager.switchStorage(StorageFactory.create(target, dataDir, database));
        database.putSetting(STORAGE_SETTING, target.name());
        storageType = target;
    }
}
