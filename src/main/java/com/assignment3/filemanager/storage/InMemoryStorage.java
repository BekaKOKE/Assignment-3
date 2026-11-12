package com.assignment3.filemanager.storage;

import com.assignment3.filemanager.exception.NotFoundException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryStorage implements StorageBackend {

    private final Map<String, byte[]> contents = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public void write(String contentId, byte[] data) {
        contents.put(contentId, data.clone());
    }

    @Override
    public byte[] read(String contentId) {
        byte[] data = contents.get(contentId);
        if (data == null) {
            throw new NotFoundException("Content '" + contentId + "' not found in memory storage.");
        }
        return data.clone();
    }

    @Override
    public void delete(String contentId) {
        contents.remove(contentId);
    }

    @Override
    public boolean exists(String contentId) {
        return contents.containsKey(contentId);
    }
}
