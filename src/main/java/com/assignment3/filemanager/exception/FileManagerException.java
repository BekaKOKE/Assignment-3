package com.assignment3.filemanager.exception;

public class FileManagerException extends RuntimeException {

    public FileManagerException(String message) {
        super(message);
    }

    public FileManagerException(String message, Throwable cause) {
        super(message, cause);
    }
}
