package com.assignment3.filemanager.util;

import com.assignment3.filemanager.exception.ValidationException;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class Validator {

    public static final int MAX_NAME_LENGTH = 100;
    public static final int MAX_CONTENT_BYTES = 1_000_000;

    private static final Pattern FORBIDDEN_IN_NAME = Pattern.compile("[\\\\/:*?\"<>|\\p{Cntrl}]");

    private Validator() {
    }

    public static String validateName(String raw, String what) {
        if (raw == null || raw.isBlank()) {
            throw new ValidationException(capitalize(what) + " must not be empty.");
        }
        String name = raw.trim();
        if (name.length() > MAX_NAME_LENGTH) {
            throw new ValidationException(capitalize(what) + " must be at most " + MAX_NAME_LENGTH + " characters.");
        }
        if (name.equals("..") || name.endsWith(".")) {
            throw new ValidationException(capitalize(what) + " '" + name + "' is not allowed.");
        }
        if (FORBIDDEN_IN_NAME.matcher(name).find()) {
            throw new ValidationException(capitalize(what)
                    + " must not contain any of: \\ / : * ? \" < > | or control characters.");
        }
        return name;
    }

    public static byte[] validateContent(String content) {
        if (content == null) {
            throw new ValidationException("File content must not be null.");
        }
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTENT_BYTES) {
            throw new ValidationException("File content is too large (" + bytes.length
                    + " bytes, limit " + MAX_CONTENT_BYTES + ").");
        }
        return bytes;
    }

    public static String validateId(String id, String what) {
        if (id == null || id.isBlank()) {
            throw new ValidationException(capitalize(what) + " must not be empty.");
        }
        return id.trim();
    }

    public static <T> T require(T value, String what) {
        if (value == null) {
            throw new ValidationException(capitalize(what) + " must not be null.");
        }
        return value;
    }

    private static String capitalize(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
