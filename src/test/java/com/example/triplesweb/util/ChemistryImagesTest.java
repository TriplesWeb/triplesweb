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
    void isDeletableAdminUploadOnlyDateFolderPaths() {
        assertThat(ChemistryImages.isDeletableAdminUpload(
                "images/chemistry/2026-11-20/abcdef0123456789abcdef0123456789.png")).isTrue();
        assertThat(ChemistryImages.isDeletableAdminUpload("images/chemistry/S1/꼬꼬마즈.png")).isFalse();
        assertThat(ChemistryImages.isDeletableAdminUpload("images/chemistry/chem/S1/a.png")).isFalse();
        assertThat(ChemistryImages.isDeletableAdminUpload("chemistry/chem/S1/a.png")).isFalse();
        assertThat(ChemistryImages.isDeletableAdminUpload(null)).isFalse();
    }

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

    @Test
    void mergeInOrderPlacesUploadsWhereTheScreenPutThem() throws Exception {
        var upload = new MockMultipartFile("images", "c.jpg", "image/jpeg", new byte[]{1});
        ChemistryImages.Result result = ChemistryImages.mergeInOrder(
                List.of("/uploads/a.jpg", "/uploads/b.jpg"),
                "[\"/uploads/b.jpg\",\"/uploads/a.jpg\"]",
                "[\"/uploads/b.jpg\",\"\",\"/uploads/a.jpg\"]",
                List.of(upload),
                false,
                mapper,
                file -> "/uploads/new-" + file.getOriginalFilename(),
                ChemistryImages.MAX_IMAGES);
        assertThat(result.urls()).containsExactly("/uploads/b.jpg", "/uploads/new-c.jpg", "/uploads/a.jpg");
        assertThat(result.removed()).isEmpty();
    }

    @Test
    void mergeInOrderRejectsOverMaxBeforeSavingUploads() {
        var upload = new MockMultipartFile("images", "c.jpg", "image/jpeg", new byte[]{1});
        assertThatThrownBy(() -> ChemistryImages.mergeInOrder(
                List.of("/uploads/a.jpg"),
                "[\"/uploads/a.jpg\"]",
                "[\"/uploads/a.jpg\",\"\"]",
                List.of(upload),
                false,
                mapper,
                file -> { throw new AssertionError("업로드를 저장하면 안 된다"); },
                1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
