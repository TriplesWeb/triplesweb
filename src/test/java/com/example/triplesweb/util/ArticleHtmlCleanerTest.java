package com.example.triplesweb.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleHtmlCleanerTest {

    @Test
    void stripsEditorChromeAndNormalizesUploadSrc() {
        String html = """
                <div class="content-media">
                  <img src="https://example.com/uploads/images/free/2026-10-24/a.jpg" data-saved-name="a.jpg">
                  <span class="remove-btn"><i class="bi bi-x"></i></span>
                </div>
                <p>본문</p>
                """;

        String cleaned = ArticleHtmlCleaner.clean(html);

        assertFalse(cleaned.contains("remove-btn"));
        assertFalse(cleaned.contains("content-media"));
        assertTrue(cleaned.contains("src=\"/uploads/images/free/2026-10-24/a.jpg\""));
        assertTrue(cleaned.contains("<p>본문</p>"));
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertEquals("", ArticleHtmlCleaner.clean(null));
        assertEquals("", ArticleHtmlCleaner.clean("   "));
    }
}
