package com.assignment3.filemanager;

import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.model.UserDirectory;
import com.assignment3.filemanager.proxy.PermissionChecker;
import com.assignment3.filemanager.proxy.ProtectionProxy;
import com.assignment3.filemanager.repository.Database;
import com.assignment3.filemanager.repository.MetadataRepository;
import com.assignment3.filemanager.repository.SqliteMetadataRepository;
import com.assignment3.filemanager.service.DefaultFileManager;
import com.assignment3.filemanager.service.FileService;
import com.assignment3.filemanager.storage.DatabaseStorage;
import com.assignment3.filemanager.storage.InMemoryStorage;
import com.assignment3.filemanager.storage.LocalFileStorage;
import com.assignment3.filemanager.storage.StorageBackend;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class TestSupport {

    enum Kind { MEMORY, LOCAL, DATABASE }

    private TestSupport() {
    }

    static StorageBackend backend(Kind kind, Path dir, Database db) {
        return switch (kind) {
            case MEMORY -> new InMemoryStorage();
            case LOCAL -> new LocalFileStorage(dir.resolve("files"));
            case DATABASE -> new DatabaseStorage(db);
        };
    }

    static final class Harness {
        final UserDirectory users = UserDirectory.predefined();
        final User beksultan = users.find("beksultan").orElseThrow();
        final User ruslan = users.find("ruslan").orElseThrow();
        final User kuanysh = users.find("kuanysh").orElseThrow();
        final Database db;
        final MetadataRepository metadata;
        final DefaultFileManager manager;
        final List<String> realSubjectCalls = new ArrayList<>();
        final List<String> audit = new ArrayList<>();
        final ProtectionProxy proxy;

        Harness(Kind kind, Path dir) {
            db = new Database(dir.resolve("test.db"));
            metadata = new SqliteMetadataRepository(db);
            manager = new DefaultFileManager(backend(kind, dir, db), metadata, users);
            proxy = new ProtectionProxy(spy(manager, realSubjectCalls), new PermissionChecker(), users, audit::add);
        }

        boolean realSubjectWasCalled(String method) {
            return realSubjectCalls.contains(method);
        }
    }

    static Harness harness(Kind kind, Path dir) {
        return new Harness(kind, dir);
    }

    static FileService spy(FileService target, List<String> calls) {
        return (FileService) Proxy.newProxyInstance(
                FileService.class.getClassLoader(), new Class<?>[]{FileService.class},
                (proxy, method, args) -> {
                    calls.add(method.getName());
                    try {
                        return method.invoke(target, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
