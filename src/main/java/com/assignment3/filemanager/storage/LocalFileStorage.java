package com.assignment3.filemanager.storage;

import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.exception.StorageException;
import com.assignment3.filemanager.exception.ValidationException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.regex.Pattern;

public final class LocalFileStorage implements StorageBackend {

    // ids become file names, so anything that could form a path is rejected
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final Path root;

    public LocalFileStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new StorageException("Cannot create storage directory " + this.root, e);
        }
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public void write(String contentId, byte[] data) {
        try {
            Files.write(pathOf(contentId), data);
        } catch (IOException e) {
            throw new StorageException("Could not write content '" + contentId + "' to disk", e);
        }
    }

    @Override
    public byte[] read(String contentId) {
        try {
            return Files.readAllBytes(pathOf(contentId));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("Content '" + contentId + "' not found in local storage.");
        } catch (IOException e) {
            throw new StorageException("Could not read content '" + contentId + "' from disk", e);
        }
    }

    @Override
    public void delete(String contentId) {
        try {
            Files.deleteIfExists(pathOf(contentId));
        } catch (IOException e) {
            throw new StorageException("Could not delete content '" + contentId + "' from disk", e);
        }
    }

    @Override
    public boolean exists(String contentId) {
        return Files.exists(pathOf(contentId));
    }

    private Path pathOf(String contentId) {
        if (contentId == null || !SAFE_ID.matcher(contentId).matches()) {
            throw new ValidationException("Invalid content id.");
        }
        return root.resolve(contentId + ".dat");
    }
}
