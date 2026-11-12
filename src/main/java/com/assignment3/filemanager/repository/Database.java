package com.assignment3.filemanager.repository;

import com.assignment3.filemanager.exception.StorageException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class Database {

    @FunctionalInterface
    public interface Transaction {
        void run(Connection connection) throws SQLException;
    }

    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet row) throws SQLException;
    }

    private final String url;

    public Database(Path file) {
        Path path = file.toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException e) {
            throw new StorageException("Cannot create database directory for " + path, e);
        }
        this.url = "jdbc:sqlite:" + path;
        update("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
    }

    public void update(String sql, Object... params) {
        inTransaction(connection -> execute(connection, sql, params));
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection connection = connect();
             PreparedStatement statement = prepare(connection, sql, params);
             ResultSet rows = statement.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rows.next()) {
                result.add(mapper.map(rows));
            }
            return result;
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public void inTransaction(Transaction work) {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                work.run(connection);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw failure(e);
        }
    }

    public static void execute(Connection connection, String sql, Object... params) throws SQLException {
        try (PreparedStatement statement = prepare(connection, sql, params)) {
            statement.executeUpdate();
        }
    }

    public Optional<String> getSetting(String key) {
        return query("SELECT value FROM settings WHERE key = ?", row -> row.getString(1), key)
                .stream().findFirst();
    }

    public void putSetting(String key, String value) {
        update("INSERT INTO settings(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                key, value);
    }

    private Connection connect() throws SQLException {
        Connection connection = DriverManager.getConnection(url);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object... params)
            throws SQLException {
        PreparedStatement statement = connection.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            statement.setObject(i + 1, params[i]);
        }
        return statement;
    }

    private static StorageException failure(SQLException e) {
        return new StorageException("Database error: " + e.getMessage(), e);
    }
}
