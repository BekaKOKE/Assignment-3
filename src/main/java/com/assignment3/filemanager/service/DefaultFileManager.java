package com.assignment3.filemanager.service;

import com.assignment3.filemanager.exception.ConflictException;
import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.exception.ValidationException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;
import com.assignment3.filemanager.model.User;
import com.assignment3.filemanager.model.UserDirectory;
import com.assignment3.filemanager.repository.MetadataRepository;
import com.assignment3.filemanager.storage.StorageBackend;
import com.assignment3.filemanager.util.Validator;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Refined Abstraction of the Bridge and Real Subject of the Proxy: all file and folder rules
 * (validation, unique names, folder policy). It deliberately does no permission checks;
 * that is the job of ProtectionProxy, which always stands in front of this class.
 */
public class DefaultFileManager extends FileManager {

    private static final Comparator<FileRecord> BY_NAME = Comparator
            .comparing((FileRecord file) -> file.name().toLowerCase(Locale.ROOT))
            .thenComparing(FileRecord::id);

    private final UserDirectory users;

    public DefaultFileManager(StorageBackend storage, MetadataRepository metadata, UserDirectory users) {
        super(storage, metadata);
        this.users = users;
    }

    @Override
    public FileRecord createFile(User actor, String name, FileType type, String content) {
        Validator.require(actor, "user");
        Validator.require(type, "file type");
        String fileName = Validator.validateName(name, "file name");
        byte[] bytes = Validator.validateContent(content);
        checkFileNameIsFree(actor.id(), fileName, type, null);

        FileRecord file = new FileRecord(newId(), fileName, type, bytes.length, now(), actor.id(), null, Set.of());
        storeContent(file.id(), bytes);
        try {
            metadata.saveFile(file);
        } catch (RuntimeException e) {
            removeContent(file.id());
            throw e;
        }
        return file;
    }

    @Override
    public String readFile(User actor, String fileId) {
        FileRecord file = findFile(fileId);
        return new String(loadContent(file.id()), StandardCharsets.UTF_8);
    }

    @Override
    public FileRecord renameFile(User actor, String fileId, String newName) {
        FileRecord file = findFile(fileId);
        String fileName = Validator.validateName(newName, "new file name");
        checkFileNameIsFree(file.ownerId(), fileName, file.type(), file.id());
        return save(file.withName(fileName));
    }

    @Override
    public void deleteFile(User actor, String fileId) {
        FileRecord file = findFile(fileId);
        metadata.deleteFile(file.id());
        removeContent(file.id());
    }

    @Override
    public FileRecord shareFile(User actor, String fileId, User target) {
        FileRecord file = findFile(fileId);
        Validator.require(target, "target user");
        if (!users.contains(target)) {
            throw new NotFoundException("Unknown user: " + target.id());
        }
        if (file.isOwnedBy(target)) {
            throw new ValidationException("The file already belongs to " + target.id() + ".");
        }
        return save(file.withSharedUser(target.id()));
    }

    @Override
    public FileRecord getFileInfo(User actor, String fileId) {
        return findFile(fileId);
    }

    @Override
    public List<FileRecord> listFiles(User actor) {
        return metadata.findAllFiles().stream().sorted(BY_NAME).toList();
    }

    @Override
    public List<FileRecord> searchByName(User actor, String query) {
        if (query == null || query.isBlank()) {
            throw new ValidationException("Search text must not be empty.");
        }
        String text = query.trim().toLowerCase(Locale.ROOT);
        return metadata.findAllFiles().stream()
                .filter(file -> file.name().toLowerCase(Locale.ROOT).contains(text))
                .sorted(BY_NAME)
                .toList();
    }

    @Override
    public Folder createFolder(User actor, String name) {
        Validator.require(actor, "user");
        String folderName = Validator.validateName(name, "folder name");
        boolean taken = metadata.findAllFolders().stream()
                .anyMatch(folder -> folder.isOwnedBy(actor) && folder.name().equalsIgnoreCase(folderName));
        if (taken) {
            throw new ConflictException("You already have a folder named '" + folderName + "'.");
        }
        Folder folder = new Folder("d" + newId(), folderName, actor.id(), now());
        metadata.saveFolder(folder);
        return folder;
    }

    @Override
    public Folder getFolder(User actor, String folderId) {
        return findFolder(folderId);
    }

    @Override
    public List<Folder> listFolders(User actor) {
        return metadata.findAllFolders().stream()
                .sorted(Comparator.comparing(folder -> folder.name().toLowerCase(Locale.ROOT)))
                .toList();
    }

    @Override
    public List<FileRecord> listFolderContents(User actor, String folderId) {
        Folder folder = findFolder(folderId);
        return metadata.findFilesInFolder(folder.id()).stream().sorted(BY_NAME).toList();
    }

    @Override
    public FileRecord moveFileToFolder(User actor, String fileId, String folderId) {
        FileRecord file = findFile(fileId);
        if (folderId == null || folderId.isBlank()) {
            return save(file.withFolder(null));
        }
        Folder folder = findFolder(folderId);
        if (!folder.ownerId().equals(file.ownerId())) {
            throw new ConflictException("A file can only be moved into a folder of the same owner.");
        }
        return save(file.withFolder(folder.id()));
    }

    @Override
    public void deleteFolder(User actor, String folderId) {
        Folder folder = findFolder(folderId);
        int count = metadata.findFilesInFolder(folder.id()).size();
        if (count > 0) {
            throw new ConflictException("Folder '" + folder.name() + "' is not empty (" + count
                    + (count == 1 ? " file" : " files") + "). Move or delete its files first.");
        }
        metadata.deleteFolder(folder.id());
    }

    private FileRecord save(FileRecord file) {
        metadata.saveFile(file);
        return file;
    }

    private FileRecord findFile(String fileId) {
        String id = Validator.validateId(fileId, "file id");
        return metadata.findFile(id).orElseThrow(() -> new NotFoundException("File not found: " + id));
    }

    private Folder findFolder(String folderId) {
        String id = Validator.validateId(folderId, "folder id");
        return metadata.findFolder(id).orElseThrow(() -> new NotFoundException("Folder not found: " + id));
    }

    private void checkFileNameIsFree(String ownerId, String name, FileType type, String exceptFileId) {
        boolean taken = metadata.findAllFiles().stream()
                .filter(file -> !file.id().equals(exceptFileId))
                .anyMatch(file -> file.ownerId().equals(ownerId)
                        && file.type() == type
                        && file.name().equalsIgnoreCase(name));
        if (taken) {
            throw new ConflictException("You already have a file named '" + name + "." + type.extension() + "'.");
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MILLIS);
    }
}
