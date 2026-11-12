package com.assignment3.filemanager.repository;

import com.assignment3.filemanager.model.FileRecord;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.model.Folder;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SqliteMetadataRepository implements MetadataRepository {

    private static final String SELECT_FILES = """
            SELECT id, name, type, size, created_at, owner_id, folder_id,
                   (SELECT group_concat(user_id) FROM file_shares WHERE file_id = files.id) AS shared_with
            FROM files
            """;

    private static final String SELECT_FOLDERS = "SELECT id, name, owner_id, created_at FROM folders ";

    private final Database db;

    public SqliteMetadataRepository(Database db) {
        this.db = db;
        db.update("""
                CREATE TABLE IF NOT EXISTS folders (
                    id         TEXT PRIMARY KEY,
                    name       TEXT NOT NULL,
                    owner_id   TEXT NOT NULL,
                    created_at INTEGER NOT NULL)
                """);
        db.update("""
                CREATE TABLE IF NOT EXISTS files (
                    id         TEXT PRIMARY KEY,
                    name       TEXT NOT NULL,
                    type       TEXT NOT NULL,
                    size       INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    owner_id   TEXT NOT NULL,
                    folder_id  TEXT REFERENCES folders(id))
                """);
        db.update("""
                CREATE TABLE IF NOT EXISTS file_shares (
                    file_id TEXT NOT NULL REFERENCES files(id) ON DELETE CASCADE,
                    user_id TEXT NOT NULL,
                    PRIMARY KEY (file_id, user_id))
                """);
    }

    @Override
    public void saveFile(FileRecord file) {
        db.inTransaction(connection -> {
            Database.execute(connection, """
                            INSERT INTO files(id, name, type, size, created_at, owner_id, folder_id)
                            VALUES(?, ?, ?, ?, ?, ?, ?)
                            ON CONFLICT(id) DO UPDATE SET name = excluded.name, folder_id = excluded.folder_id
                            """,
                    file.id(), file.name(), file.type().name(), file.size(),
                    file.createdAt().toEpochMilli(), file.ownerId(), file.folderId());

            Database.execute(connection, "DELETE FROM file_shares WHERE file_id = ?", file.id());
            for (String userId : file.sharedWith()) {
                Database.execute(connection,
                        "INSERT INTO file_shares(file_id, user_id) VALUES(?, ?)", file.id(), userId);
            }
        });
    }

    @Override
    public Optional<FileRecord> findFile(String id) {
        return db.query(SELECT_FILES + "WHERE id = ?", SqliteMetadataRepository::toFile, id)
                .stream().findFirst();
    }

    @Override
    public List<FileRecord> findAllFiles() {
        return db.query(SELECT_FILES + "ORDER BY created_at, id", SqliteMetadataRepository::toFile);
    }

    @Override
    public List<FileRecord> findFilesInFolder(String folderId) {
        return db.query(SELECT_FILES + "WHERE folder_id = ? ORDER BY created_at, id",
                SqliteMetadataRepository::toFile, folderId);
    }

    @Override
    public void deleteFile(String id) {
        db.update("DELETE FROM files WHERE id = ?", id);
    }

    @Override
    public void saveFolder(Folder folder) {
        db.update("""
                        INSERT INTO folders(id, name, owner_id, created_at) VALUES(?, ?, ?, ?)
                        ON CONFLICT(id) DO UPDATE SET name = excluded.name
                        """,
                folder.id(), folder.name(), folder.ownerId(), folder.createdAt().toEpochMilli());
    }

    @Override
    public Optional<Folder> findFolder(String id) {
        return db.query(SELECT_FOLDERS + "WHERE id = ?", SqliteMetadataRepository::toFolder, id)
                .stream().findFirst();
    }

    @Override
    public List<Folder> findAllFolders() {
        return db.query(SELECT_FOLDERS + "ORDER BY created_at, id", SqliteMetadataRepository::toFolder);
    }

    @Override
    public void deleteFolder(String id) {
        db.update("DELETE FROM folders WHERE id = ?", id);
    }

    private static FileRecord toFile(ResultSet row) throws SQLException {
        String sharedWith = row.getString("shared_with");
        return new FileRecord(
                row.getString("id"),
                row.getString("name"),
                FileType.valueOf(row.getString("type")),
                row.getLong("size"),
                Instant.ofEpochMilli(row.getLong("created_at")),
                row.getString("owner_id"),
                row.getString("folder_id"),
                sharedWith == null ? Set.of() : Set.of(sharedWith.split(",")));
    }

    private static Folder toFolder(ResultSet row) throws SQLException {
        return new Folder(
                row.getString("id"),
                row.getString("name"),
                row.getString("owner_id"),
                Instant.ofEpochMilli(row.getLong("created_at")));
    }
}
