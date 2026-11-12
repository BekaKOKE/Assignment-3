package com.assignment3.filemanager.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonTest {

    @Test
    @DisplayName("TC-W11 JSON writer: structure, null and numbers")
    void structure() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", List.of("x", "y"));
        map.put("c", null);
        map.put("d", true);
        assertEquals("{\"a\":1,\"b\":[\"x\",\"y\"],\"c\":null,\"d\":true}", Json.toJson(map));
    }

    @Test
    @DisplayName("TC-W12 JSON writer escapes quotes, control characters and markup characters")
    void escaping() {
        String json = Json.toJson(Map.of("t", "say \"hi\"\n<script>alert(1)</script> & \\  "));
        assertFalse(json.contains("<"), json);
        assertFalse(json.contains(">"), json);
        assertFalse(json.contains("\n"), json);
        assertFalse(json.contains(" "), json);
        assertTrue(json.contains("\\\"hi\\\""), json);
        assertTrue(json.contains("\\u003cscript"), json);
        assertTrue(json.contains("\\n"), json);
    }

    @Test
    @DisplayName("TC-W13 JSON writer refuses types it cannot represent")
    void unsupported() {
        assertThrows(IllegalArgumentException.class, () -> Json.toJson(new Object()));
    }
}
