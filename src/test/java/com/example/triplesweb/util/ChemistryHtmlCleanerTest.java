package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChemistryHtmlCleanerTest {

    @Test
    void stripsEventAttributesFromAllowedTags() {
        String cleaned = ChemistryHtmlCleaner.clean(
                "<b onclick=\"alert(1)\">bold</b><strong onmouseover=\"x()\">s</strong>");
        assertFalse(cleaned.toLowerCase().contains("onclick"));
        assertFalse(cleaned.toLowerCase().contains("onmouseover"));
        assertTrue(cleaned.contains("<b>bold</b>") || cleaned.contains("<b>bold</b>".toLowerCase()));
        assertTrue(cleaned.contains("bold"));
        assertTrue(cleaned.contains("s"));
    }

    @Test
    void unwrapsDisallowedNestedTagsAndReinspectsChildren() {
        String cleaned = ChemistryHtmlCleaner.clean(
                "<div><script>alert(1)</script><b onclick=\"x()\">ok</b></div>");
        assertFalse(cleaned.toLowerCase().contains("script"));
        assertFalse(cleaned.toLowerCase().contains("onclick"));
        assertFalse(cleaned.toLowerCase().contains("alert"));
        assertTrue(cleaned.contains("ok"));
    }

    @Test
    void rejectsJavascriptUrls() {
        String cleaned = ChemistryHtmlCleaner.clean(
                "<a href=\"javascript:alert(1)\">x</a><a href=\"https://example.com\">y</a>");
        assertFalse(cleaned.toLowerCase().contains("javascript"));
        assertTrue(cleaned.contains("https://example.com"));
        assertTrue(cleaned.contains("y"));
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertEquals("", ChemistryHtmlCleaner.clean(null));
        assertEquals("", ChemistryHtmlCleaner.clean("   "));
    }
}
