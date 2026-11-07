package com.example.triplesweb.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StaticAssetVersionServiceTest {

    private final StaticAssetVersionService service = new StaticAssetVersionService();

    @Test
    void versionedUrlAppendsContentHashQuery() {
        String url = service.versionedUrl("/js/chemistry.js");
        assertThat(url).startsWith("/js/chemistry.js?v=");
        assertThat(url.substring(url.indexOf("?v=") + 3)).hasSize(8);
    }

    @Test
    void versionedUrlIgnoresExistingQuery() {
        String a = service.versionedUrl("/js/chemistry.js");
        String b = service.versionedUrl("/js/chemistry.js?v=68");
        assertThat(a).isEqualTo(b);
    }
}
