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
                  <span class="remove-btn">×</span>
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
    void blankMarkupHasNoVisibleContentAndMatchesTheSameText() {
        assertFalse(ArticleHtmlCleaner.hasVisibleContent("<p><br></p>"));
        assertTrue(ArticleHtmlCleaner.hasVisibleContent("<p>본문</p>"));
        assertTrue(ArticleHtmlCleaner.hasVisibleContent("<img src=\"/uploads/a.jpg\">"));
        assertEquals(ArticleHtmlCleaner.fingerprint("<p>같은 글</p>"),
                ArticleHtmlCleaner.fingerprint("<div class=\"content-media\"><p>같은 글</p></div>"));
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertEquals("", ArticleHtmlCleaner.clean(null));
        assertEquals("", ArticleHtmlCleaner.clean("   "));
    }
}
