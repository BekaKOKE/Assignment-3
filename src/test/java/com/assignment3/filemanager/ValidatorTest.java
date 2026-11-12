package com.assignment3.filemanager;

import com.assignment3.filemanager.exception.ValidationException;
import com.assignment3.filemanager.model.FileType;
import com.assignment3.filemanager.util.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {"report", "Q3 report 2024", "résumé", "日本語", "a-b_c (1)"})
    @DisplayName("TC-V01 valid names are accepted")
    void validNames(String name) {
        assertEquals(name, Validator.validateName(name, "file name"));
    }

    @Test
    @DisplayName("TC-V02 names are trimmed")
    void namesAreTrimmed() {
        assertEquals("report", Validator.validateName("  report  ", "file name"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "a/b", "a\\b", "a:b", "a*b", "a?b", "a\"b", "a<b", "a>b", "a|b",
            "..", ".", "name.", "a\tb", "../../etc/passwd"})
    @DisplayName("TC-V03 empty, whitespace, path and wildcard names are rejected")
    void invalidNames(String name) {
        assertThrows(ValidationException.class, () -> Validator.validateName(name, "file name"));
    }

    @Test
    @DisplayName("TC-V04 name longer than the limit is rejected, name at the limit is accepted")
    void nameLengthLimit() {
        assertEquals(Validator.MAX_NAME_LENGTH,
                Validator.validateName("x".repeat(Validator.MAX_NAME_LENGTH), "file name").length());
        assertThrows(ValidationException.class,
                () -> Validator.validateName("x".repeat(Validator.MAX_NAME_LENGTH + 1), "file name"));
    }

    @Test
    @DisplayName("TC-V05 content: empty and unicode allowed, null and oversize rejected")
    void contentRules() {
        assertArrayEquals(new byte[0], Validator.validateContent(""));
        assertEquals(6, Validator.validateContent("日本").length);
        assertThrows(ValidationException.class, () -> Validator.validateContent(null));
        assertEquals(Validator.MAX_CONTENT_BYTES,
                Validator.validateContent("x".repeat(Validator.MAX_CONTENT_BYTES)).length);
        assertThrows(ValidationException.class,
                () -> Validator.validateContent("x".repeat(Validator.MAX_CONTENT_BYTES + 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"txt", "TXT", ".txt", " Md ", ".JSON", "csv", "xml", "log"})
    @DisplayName("TC-V06 known file types parse regardless of case, dot and spaces")
    void knownTypes(String raw) {
        FileType.parse(raw);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"exe", "tx", "txt.exe", "   "})
    @DisplayName("TC-V07 unknown or empty file types are rejected")
    void unknownTypes(String raw) {
        assertThrows(ValidationException.class, () -> FileType.parse(raw));
    }
}
