package com.assignment3.filemanager.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

record Request(String method, List<String> path, Map<String, String> query, Map<String, String> form, String user) {

    static final String API_PREFIX = "/api/";

    private static final int MAX_BODY_BYTES = 4 * 1024 * 1024;

    static Request from(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String route = path.length() > API_PREFIX.length() ? path.substring(API_PREFIX.length()) : "";
        return new Request(
                method,
                route.isEmpty() ? List.of() : Arrays.asList(route.split("/")),
                parseForm(exchange.getRequestURI().getRawQuery()),
                method.equals("POST") ? parseForm(readBody(exchange)) : Map.of(),
                exchange.getRequestHeaders().getFirst("X-User"));
    }

    boolean is(String expectedMethod) {
        return method.equals(expectedMethod);
    }

    void require(String expectedMethod) {
        if (!is(expectedMethod)) {
            throw HttpError.methodNotAllowed();
        }
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        String length = exchange.getRequestHeaders().getFirst("Content-Length");
        try {
            if (length != null && Long.parseLong(length.trim()) > MAX_BODY_BYTES) {
                throw new HttpError(413, "Request body too large.");
            }
        } catch (NumberFormatException e) {
            throw new HttpError(400, "Invalid Content-Length.");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            throw new HttpError(413, "Request body too large.");
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseForm(String encoded) {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return values;
        }
        try {
            for (String pair : encoded.split("&")) {
                String[] parts = pair.split("=", 2);
                String value = parts.length == 2 ? parts[1] : "";
                values.putIfAbsent(decode(parts[0]), decode(value));
            }
        } catch (IllegalArgumentException e) {
            throw new HttpError(400, "Malformed form data.");
        }
        return values;
    }

    private static String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }
}
