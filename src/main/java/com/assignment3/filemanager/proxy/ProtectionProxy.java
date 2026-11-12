package com.assignment3.filemanager.proxy;

import com.assignment3.filemanager.exception.AccessDeniedException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.Permission;
import com.assignment3.filemanager.model.Role;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.model.UserDirectory;
import com.assignment3.filemanager.service.FileService;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The Protection Proxy. It implements the same FileService as the real manager and stands in
 * front of it. For each restricted call it loads the file's stored owner and share list, asks
 * PermissionChecker for the user's role, and either forwards the call or throws
 * AccessDeniedException. A denied call never reaches the real subject. Every decision is logged.
 *
 * <pre>
 *             read   rename   delete   share   move
 *   OWNER      yes     yes      yes     yes    yes
 *   VIEWER     yes     no       no      no     no
 * </pre>
 */
public final class ProtectionProxy implements FileService {

    private final FileService realSubject;
    private final PermissionChecker checker;
    private final UserDirectory users;
    private final Consumer<String> auditLog;

    public ProtectionProxy(FileService realSubject, PermissionChecker checker, UserDirectory users,
                           Consumer<String> auditLog) {
        this.realSubject = realSubject;
        this.checker = checker;
        this.users = users;
        this.auditLog = auditLog != null ? auditLog : message -> { };
    }

    @Override
    public FileRecord createFile(User actor, String name, FileType type, String content) {
        requireKnownUser(actor, "CREATE file");
        FileRecord file = realSubject.createFile(actor, name, type, content);
        audit(actor, "CREATE", "file '" + file.id() + "'", true, "OWNER");
        return file;
    }

    @Override
    public String readFile(User actor, String fileId) {
        authorize(actor, fileId, Permission.READ);
        return realSubject.readFile(actor, fileId);
    }

    @Override
    public FileRecord renameFile(User actor, String fileId, String newName) {
        authorize(actor, fileId, Permission.RENAME);
        return realSubject.renameFile(actor, fileId, newName);
    }

    @Override
    public void deleteFile(User actor, String fileId) {
        authorize(actor, fileId, Permission.DELETE);
        realSubject.deleteFile(actor, fileId);
    }

    @Override
    public FileRecord shareFile(User actor, String fileId, User target) {
        authorize(actor, fileId, Permission.SHARE);
        return realSubject.shareFile(actor, fileId, target);
    }

    @Override
    public FileRecord getFileInfo(User actor, String fileId) {
        return authorize(actor, fileId, Permission.READ);
    }

    @Override
    public List<FileRecord> listFiles(User actor) {
        requireKnownUser(actor, "LIST files");
        return readableBy(actor, realSubject.listFiles(actor));
    }

    @Override
    public List<FileRecord> searchByName(User actor, String query) {
        requireKnownUser(actor, "SEARCH files");
        return readableBy(actor, realSubject.searchByName(actor, query));
    }

    @Override
    public Folder createFolder(User actor, String name) {
        requireKnownUser(actor, "CREATE folder");
        return realSubject.createFolder(actor, name);
    }

    @Override
    public Folder getFolder(User actor, String folderId) {
        requireKnownUser(actor, "VIEW folder");
        Folder folder = realSubject.getFolder(actor, folderId);
        if (!canSee(actor, folder)) {
            throw denyFolder(actor, "VIEW", folder);
        }
        return folder;
    }

    @Override
    public List<Folder> listFolders(User actor) {
        requireKnownUser(actor, "LIST folders");
        return realSubject.listFolders(actor).stream()
                .filter(folder -> canSee(actor, folder))
                .toList();
    }

    @Override
    public List<FileRecord> listFolderContents(User actor, String folderId) {
        requireKnownUser(actor, "LIST folder contents");
        Folder folder = realSubject.getFolder(actor, folderId);
        List<FileRecord> visible = readableBy(actor, realSubject.listFolderContents(actor, folderId));
        if (visible.isEmpty() && !checker.ownsFolder(actor, folder)) {
            throw denyFolder(actor, "VIEW", folder);
        }
        return visible;
    }

    @Override
    public FileRecord moveFileToFolder(User actor, String fileId, String folderId) {
        authorize(actor, fileId, Permission.MOVE);
        if (folderId != null && !folderId.isBlank()) {
            Folder folder = realSubject.getFolder(actor, folderId);
            if (!checker.ownsFolder(actor, folder)) {
                throw denyFolder(actor, "MOVE a file into", folder);
            }
        }
        return realSubject.moveFileToFolder(actor, fileId, folderId);
    }

    @Override
    public void deleteFolder(User actor, String folderId) {
        requireKnownUser(actor, "DELETE folder");
        Folder folder = realSubject.getFolder(actor, folderId);
        if (!checker.ownsFolder(actor, folder)) {
            throw denyFolder(actor, "DELETE", folder);
        }
        realSubject.deleteFolder(actor, folderId);
        audit(actor, "DELETE", "folder '" + folderId + "'", true, "OWNER");
    }

    private FileRecord authorize(User actor, String fileId, Permission permission) {
        requireKnownUser(actor, permission + " file");
        FileRecord file = realSubject.getFileInfo(actor, fileId);
        Optional<Role> role = checker.roleOf(actor, file);
        boolean granted = role.isPresent() && role.get().allows(permission);
        audit(actor, permission.name(), "file '" + file.id() + "'", granted,
                role.map(Role::name).orElse("NO ACCESS"));

        if (granted) {
            return file;
        }
        if (role.isEmpty()) {
            throw new AccessDeniedException(
                    "Access denied: " + actor.id() + " has no access to file '" + file.displayName() + "'.");
        }
        throw new AccessDeniedException("Access denied: " + actor.id() + " (" + role.get()
                + ") is not allowed to " + permission + " file '" + file.displayName() + "'.");
    }

    private void requireKnownUser(User actor, String action) {
        if (!users.contains(actor)) {
            audit(actor, action, "-", false, "UNKNOWN USER");
            throw new AccessDeniedException("Access denied: unknown user '" + idOf(actor) + "'.");
        }
    }

    private List<FileRecord> readableBy(User actor, List<FileRecord> files) {
        return files.stream()
                .filter(file -> checker.isAllowed(actor, file, Permission.READ))
                .toList();
    }

    private boolean canSee(User actor, Folder folder) {
        return checker.ownsFolder(actor, folder)
                || !readableBy(actor, realSubject.listFolderContents(actor, folder.id())).isEmpty();
    }

    private AccessDeniedException denyFolder(User actor, String action, Folder folder) {
        audit(actor, action, "folder '" + folder.id() + "'", false, "NOT OWNER");
        return new AccessDeniedException("Access denied: " + actor.id() + " is not allowed to " + action
                + " folder '" + folder.name() + "' (owned by " + folder.ownerId() + ").");
    }

    private void audit(User actor, String operation, String resource, boolean granted, String role) {
        auditLog.accept("[PROXY] " + idOf(actor) + " -> " + operation + " " + resource
                + " : " + (granted ? "GRANTED" : "DENIED") + " (" + role + ")");
    }

    private static String idOf(User actor) {
        return actor == null ? "<none>" : actor.id();
    }
}
