package com.example.triplesweb.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChemistryImagesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void resolveFallsBackToSingleImageUrl() {
        assertThat(ChemistryImages.resolve(null, "/uploads/a.jpg", mapper))
                .containsExactly("/uploads/a.jpg");
        assertThat(ChemistryImages.resolve("[\"/a.jpg\",\"/b.jpg\"]", "/uploads/a.jpg", mapper))
                .containsExactly("/a.jpg", "/b.jpg");
    }

    @Test
    void mergeKeepsOrderAddsUploadsAndReportsRemoved() throws Exception {
        var upload = new MockMultipartFile("images", "c.jpg", "image/jpeg", new byte[]{1});
        ChemistryImages.Result result = ChemistryImages.merge(
                List.of("/a.jpg", "/b.jpg"),
                "[\"/b.jpg\"]",
                List.of(upload),
                false,
                mapper,
                file -> "/c.jpg");
        assertThat(result.urls()).containsExactly("/b.jpg", "/c.jpg");
        assertThat(result.removed()).containsExactly("/a.jpg");
    }

    @Test
    void mergeRejectsOverMax() {
        assertThatThrownBy(() -> ChemistryImages.merge(
                List.of(),
                "[]",
                List.of(
                        new MockMultipartFile("i", "1.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "2.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "3.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "4.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "5.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "6.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "7.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "8.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "9.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "10.jpg", "image/jpeg", new byte[]{1}),
                        new MockMultipartFile("i", "11.jpg", "image/jpeg", new byte[]{1})
                ),
                false,
                mapper,
                file -> "/x.jpg"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("최대");
    }
}
