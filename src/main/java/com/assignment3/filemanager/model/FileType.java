package com.assignment3.filemanager.model;

import com.assignment3.filemanager.exception.ValidationException;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum FileType {
    TXT, MD, JSON, CSV, XML, LOG;

    public String extension() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FileType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException("File type must not be empty. Allowed: " + allowed());
        }
        String value = raw.trim();
        if (value.startsWith(".")) {
            value = value.substring(1);
        }
        for (FileType type : values()) {
            if (type.name().equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new ValidationException("Unsupported file type '" + raw.trim() + "'. Allowed: " + allowed());
    }

    public static String allowed() {
        return Arrays.stream(values()).map(FileType::extension).collect(Collectors.joining(", "));
    }
}
