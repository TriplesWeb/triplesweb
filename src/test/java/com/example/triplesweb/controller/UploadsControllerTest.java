package com.example.triplesweb.controller;

import com.example.triplesweb.entity.UploadBlob;
import com.example.triplesweb.service.UploadContentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadsControllerTest {
    @TempDir Path temp;

    @ParameterizedTest
    @CsvSource({"photo.jfif, image/jpeg", "photo.WEBP, image/webp"})
    void servesImageFormatsFromLocalAndDatabaseCopies(String filename, String expectedType) throws Exception {
        var store = mock(UploadContentStore.class);
        var controller = new UploadsController(temp.toString(), "", store);
        var request = new MockHttpServletRequest("GET", "/uploads/" + filename);
        Files.write(temp.resolve(filename), new byte[]{1, 2, 3});
        assertThat(controller.serve(request).getHeaders().getContentType().toString()).isEqualTo(expectedType);

        Files.delete(temp.resolve(filename));
        var blob = new UploadBlob();
        blob.setContentType("application/octet-stream");
        blob.setData(new byte[]{1, 2, 3});
        when(store.find(filename)).thenReturn(Optional.of(blob));
        assertThat(controller.serve(request).getHeaders().getContentType().toString()).isEqualTo(expectedType);
    }

    @Test void servesDatabaseCopyWhenLocalFileIsMissing() {
        UploadContentStore store = mock(UploadContentStore.class);
        UploadBlob blob = new UploadBlob();
        blob.setRelativePath("board/a.jpg");
        blob.setContentType("image/jpeg");
        blob.setData(new byte[]{1, 2, 3});
        when(store.find("board/a.jpg")).thenReturn(Optional.of(blob));

        UploadsController controller = new UploadsController(temp.toString(), "", store);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uploads/board/a.jpg");

        var response = controller.serve(request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType().toString()).contains("image/jpeg");
    }
}
