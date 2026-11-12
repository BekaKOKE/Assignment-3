package com.assignment3.filemanager.service;

import com.assignment3.filemanager.exception.StorageException;
import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.repository.MetadataRepository;
import com.assignment3.filemanager.storage.StorageBackend;

import java.util.ArrayList;
import java.util.List;

/**
 * The Abstraction of the Bridge pattern. It does not know where file contents are kept: it only
 * holds a StorageBackend (composition) and passes content to it. That is why the storage can be
 * replaced, even at runtime with switchStorage, without changing this class or its subclasses.
 */
public abstract class FileManager implements FileService {

    private StorageBackend storage;
    protected final MetadataRepository metadata;

    protected FileManager(StorageBackend storage, MetadataRepository metadata) {
        this.storage = storage;
        this.metadata = metadata;
    }

    protected final void storeContent(String contentId, byte[] data) {
        storage.write(contentId, data);
    }

    protected final byte[] loadContent(String contentId) {
        return storage.read(contentId);
    }

    protected final void removeContent(String contentId) {
        storage.delete(contentId);
    }

    public final String storageName() {
        return storage.name();
    }

    public final synchronized void switchStorage(StorageBackend target) {
        if (target == storage) {
            return;
        }
        List<String> copied = new ArrayList<>();
        try {
            for (FileRecord file : metadata.findAllFiles()) {
                if (storage.exists(file.id())) {
                    target.write(file.id(), storage.read(file.id()));
                    copied.add(file.id());
                }
            }
        } catch (RuntimeException e) {
            copied.forEach(target::delete);
            throw new StorageException("Switching storage to '" + target.name()
                    + "' failed; staying on '" + storage.name() + "'.", e);
        }

        // the old backend is emptied only after every file made it to the new one
        StorageBackend previous = storage;
        storage = target;
        copied.forEach(previous::delete);
    }
}
