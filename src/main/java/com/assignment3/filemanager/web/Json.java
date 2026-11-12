package com.assignment3.filemanager.web;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

final class Json {

    private Json() {
    }

    static String toJson(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof String || value instanceof Instant || value instanceof Enum<?>) {
            return quote(value.toString());
        }
        if (value instanceof Map<?, ?> map) {
            StringJoiner object = new StringJoiner(",", "{", "}");
            map.forEach((key, item) -> object.add(quote(String.valueOf(key)) + ":" + toJson(item)));
            return object.toString();
        }
        if (value instanceof Iterable<?> items) {
            StringJoiner array = new StringJoiner(",", "[", "]");
            items.forEach(item -> array.add(toJson(item)));
            return array.toString();
        }
        if (value instanceof Record record) {
            return toJson(fieldsOf(record));
        }
        throw new IllegalArgumentException("Cannot serialise " + value.getClass().getName());
    }

    private static Map<String, Object> fieldsOf(Record record) {
        Map<String, Object> fields = new LinkedHashMap<>();
        try {
            for (RecordComponent component : record.getClass().getRecordComponents()) {
                fields.put(component.getName(), component.getAccessor().invoke(record));
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Cannot serialise " + record.getClass().getName(), e);
        }
        return fields;
    }

    private static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : text.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (needsEscape(c)) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }

    // besides control characters, also the ones that are unsafe inside an HTML page
    private static boolean needsEscape(char c) {
        return c < 0x20 || c == '<' || c == '>' || c == '&' || c == 0x2028 || c == 0x2029;
    }
}
