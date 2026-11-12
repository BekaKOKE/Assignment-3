package com.assignment3.filemanager.storage;

import com.assignment3.filemanager.exception.NotFoundException;
import com.assignment3.filemanager.repository.Database;

import java.util.List;

public final class DatabaseStorage implements StorageBackend {

    private final Database db;

    public DatabaseStorage(Database db) {
        this.db = db;
        db.update("CREATE TABLE IF NOT EXISTS file_content (content_id TEXT PRIMARY KEY, data BLOB NOT NULL)");
    }

    @Override
    public String name() {
        return "database";
    }

    @Override
    public void write(String contentId, byte[] data) {
        db.update("""
                INSERT INTO file_content(content_id, data) VALUES(?, ?)
                ON CONFLICT(content_id) DO UPDATE SET data = excluded.data
                """, contentId, data);
    }

    @Override
    public byte[] read(String contentId) {
        List<byte[]> rows = db.query(
                "SELECT data FROM file_content WHERE content_id = ?", row -> row.getBytes(1), contentId);
        if (rows.isEmpty()) {
            throw new NotFoundException("Content '" + contentId + "' not found in database storage.");
        }
        byte[] data = rows.get(0);
        return data == null ? new byte[0] : data;
    }

    @Override
    public void delete(String contentId) {
        db.update("DELETE FROM file_content WHERE content_id = ?", contentId);
    }

    @Override
    public boolean exists(String contentId) {
        return !db.query("SELECT 1 FROM file_content WHERE content_id = ?", row -> true, contentId).isEmpty();
    }
}
