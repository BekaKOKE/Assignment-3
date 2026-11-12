package com.assignment3.filemanager.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;

record Response(int status, String contentType, byte[] body) {

    static Response ok(Object payload) {
        return json(200, payload);
    }

    static Response created(Object payload) {
        return json(201, payload);
    }

    static Response error(int status, String message) {
        return json(status, Map.of("error", message));
    }

    static Response noContent() {
        return new Response(204, null, new byte[0]);
    }

    private static Response json(int status, Object payload) {
        byte[] body = Json.toJson(payload).getBytes(StandardCharsets.UTF_8);
        return new Response(status, "application/json; charset=utf-8", body);
    }
}
