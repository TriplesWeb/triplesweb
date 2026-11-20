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
    @Test void downloadsActiveContentFromDiskAndDatabase() throws Exception {
        var store = mock(UploadContentStore.class);
        var controller = new UploadsController(temp.toString(), "", store);
        Files.writeString(temp.resolve("page.html"), "<script>alert(1)</script>");
        var request = new MockHttpServletRequest("GET", "/uploads/page.html");
        var response = controller.serve(request);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
        Files.delete(temp.resolve("page.html"));
        var blob = new UploadBlob();
        blob.setContentType("text/html");
        blob.setData(new byte[]{1});
        when(store.find("page.html")).thenReturn(Optional.of(blob));
        assertThat(controller.serve(request).getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment");
    }

    @Test void blocksTraversalSymlinksAndPrivateSidecars() throws Exception {
        var store = mock(UploadContentStore.class);
        Path uploads = temp.resolve("uploads");
        Files.createDirectories(uploads);
        var controller = new UploadsController(uploads.toString(), "", store);
        Files.writeString(temp.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(uploads.resolve("link.txt"), temp.resolve("secret.txt"));
        assertThat(controller.serve(new MockHttpServletRequest("GET", "/uploads/link.txt")).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.serve(new MockHttpServletRequest("GET", "/uploads/%2e%2e/secret.txt")).getStatusCode().value()).isEqualTo(403);
        assertThat(controller.serve(new MockHttpServletRequest("GET", "/uploads/photo.jpg.orig")).getStatusCode().value()).isEqualTo(404);
        org.mockito.Mockito.verifyNoInteractions(store);
    }
}
