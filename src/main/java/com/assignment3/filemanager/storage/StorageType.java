package com.assignment3.filemanager.storage;

import com.assignment3.filemanager.exception.ValidationException;

import java.util.Locale;

public enum StorageType {
    LOCAL, DATABASE;

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }

    public StorageType other() {
        return this == LOCAL ? DATABASE : LOCAL;
    }

    public static StorageType parse(String raw) {
        for (StorageType type : values()) {
            if (raw != null && type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        throw new ValidationException("Unknown storage '" + raw + "'. Choose one of: local, database");
    }
}
