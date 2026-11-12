package com.assignment3.filemanager.storage;

/**
 * The Implementor of the Bridge pattern: the only thing FileManager needs to know about storage.
 * Implemented by LocalFileStorage (files on disk), DatabaseStorage (SQLite BLOBs) and
 * InMemoryStorage (tests only). A new storage means one new class, nothing else changes.
 */
public interface StorageBackend {

    String name();

    void write(String contentId, byte[] data);

    byte[] read(String contentId);

    void delete(String contentId);

    boolean exists(String contentId);
}
