package com.assignment3.filemanager.web;

final class HttpError extends RuntimeException {

    private final int status;

    HttpError(int status, String message) {
        super(message);
        this.status = status;
    }

    int status() {
        return status;
    }

    static HttpError notFound() {
        return new HttpError(404, "Unknown API route.");
    }

    static HttpError methodNotAllowed() {
        return new HttpError(405, "Method not allowed.");
    }
}
