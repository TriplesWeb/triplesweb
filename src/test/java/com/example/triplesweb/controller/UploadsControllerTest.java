package com.example.triplesweb.controller;

import com.example.triplesweb.service.UploadImageResizeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadsControllerTest {
    @TempDir Path temp;
    private final UploadImageResizeService resize = new UploadImageResizeService();

    @ParameterizedTest
    @CsvSource({"photo.jfif, image/jpeg", "photo.WEBP, image/webp"})
    void servesImageFormatsFromDisk(String filename, String expectedType) throws Exception {
        var controller = new UploadsController(temp.toString(), "", resize);
        var request = new MockHttpServletRequest("GET", "/uploads/" + filename);
        Files.write(temp.resolve(filename), new byte[]{1, 2, 3});
        assertThat(serve(controller, request).getHeaders().getContentType().toString()).isEqualTo(expectedType);
    }

    @Test void returnsNotFoundWhenLocalFileIsMissingWithoutFallback() {
        UploadsController controller = new UploadsController(temp.toString(), "", resize);
        var response = serve(controller, new MockHttpServletRequest("GET", "/uploads/board/a.jpg"));
        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test void downloadsActiveContentFromDisk() throws Exception {
        var controller = new UploadsController(temp.toString(), "", resize);
        Files.writeString(temp.resolve("page.html"), "<script>alert(1)</script>");
        var request = new MockHttpServletRequest("GET", "/uploads/page.html");
        var response = serve(controller, request);
        assertThat(response.getHeaders().getFirst("Content-Disposition")).isEqualTo("attachment");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @ParameterizedTest
    @CsvSource({
            "images/artist/0123456789abcdef0123456789abcdef.jpg, 'public, max-age=31536000, immutable'",
            "0123456789abcdef0123456789abcdef.card-1600.jpg, 'public, max-age=604800'",
            "0123456789abcdef0123456789abcdef.card2-1600.jpg, 'public, max-age=604800'",
            "banner1.jpg, 'public, max-age=86400'"
    })
    void validatesDiskCacheBeforeProcessingRange(String relativePath, String cacheControl) throws Exception {
        long modified = Instant.parse("2026-11-24T00:00:00.123Z").toEpochMilli();
        Path file = temp.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});
        Files.setLastModifiedTime(file, FileTime.fromMillis(modified));
        var mvc = MockMvcBuilders.standaloneSetup(
                new UploadsController(temp.toString(), "", resize)).build();
        String url = "/uploads/" + relativePath;

        var initial = mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, cacheControl))
                .andExpect(header().dateValue(HttpHeaders.LAST_MODIFIED, modified / 1000 * 1000))
                .andReturn().getResponse();

        mvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=1-2")
                        .header(HttpHeaders.IF_MODIFIED_SINCE, initial.getHeader(HttpHeaders.LAST_MODIFIED)))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, cacheControl))
                .andExpect(content().bytes(new byte[0]));

        mvc.perform(get(url).header(HttpHeaders.RANGE, "bytes=1-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-2/3"))
                .andExpect(content().bytes(new byte[]{2, 3}));
    }

    @Test void widthQueryUsesVariantCachePolicyAndVariantLastModified() throws Exception {
        String stem = "0123456789abcdef0123456789abcdef";
        Path original = temp.resolve(stem + ".jpg");
        Path variant = temp.resolve(stem + ".card2-1600.jpg");
        Files.write(original, new byte[]{1, 2, 3});
        Files.write(variant, new byte[]{4, 5});
        long originalModified = Instant.parse("2026-11-24T00:00:00Z").toEpochMilli();
        long variantModified = originalModified + 1000;
        Files.setLastModifiedTime(original, FileTime.fromMillis(originalModified));
        Files.setLastModifiedTime(variant, FileTime.fromMillis(variantModified));
        var mvc = MockMvcBuilders.standaloneSetup(
                new UploadsController(temp.toString(), "", resize)).build();
        String url = "/uploads/" + original.getFileName();

        var response = mvc.perform(get(url).param("w", "1600"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=604800"))
                .andExpect(header().dateValue(HttpHeaders.LAST_MODIFIED, variantModified))
                .andExpect(content().bytes(new byte[]{4, 5}))
                .andReturn().getResponse();
        mvc.perform(get(url).param("w", "1600")
                        .header(HttpHeaders.IF_MODIFIED_SINCE, response.getHeader(HttpHeaders.LAST_MODIFIED)))
                .andExpect(status().isNotModified())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=604800"));
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable"))
                .andExpect(header().dateValue(HttpHeaders.LAST_MODIFIED, originalModified));
    }

    private static ResponseEntity<Resource> serve(
            UploadsController controller, MockHttpServletRequest request) {
        return controller.serve(request, new MockHttpServletResponse());
    }

    @Test void blocksTraversalSymlinksAndPrivateSidecars() throws Exception {
        Path uploads = temp.resolve("uploads");
        Files.createDirectories(uploads);
        var controller = new UploadsController(uploads.toString(), "", resize);
        Files.writeString(temp.resolve("secret.txt"), "secret");
        Files.createSymbolicLink(uploads.resolve("link.txt"), temp.resolve("secret.txt"));
        assertThat(serve(controller, new MockHttpServletRequest("GET", "/uploads/link.txt")).getStatusCode().value()).isEqualTo(403);
        assertThat(serve(controller, new MockHttpServletRequest("GET", "/uploads/%2e%2e/secret.txt")).getStatusCode().value()).isEqualTo(403);
        assertThat(serve(controller, new MockHttpServletRequest("GET", "/uploads/photo.jpg.orig")).getStatusCode().value()).isEqualTo(404);
    }
}
