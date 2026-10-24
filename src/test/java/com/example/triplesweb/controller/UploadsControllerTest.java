package com.example.triplesweb.controller;

import com.example.triplesweb.entity.UploadBlob;
import com.example.triplesweb.service.UploadContentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadsControllerTest {
    @TempDir Path temp;

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
