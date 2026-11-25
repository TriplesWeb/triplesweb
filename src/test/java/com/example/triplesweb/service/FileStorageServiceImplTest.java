package com.example.triplesweb.service;

import com.example.triplesweb.entity.MediaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FileStorageServiceImplTest {
    @TempDir Path directory;

    @Test void deletesBothGenerationsOfImageVariantsWithOriginal() throws Exception {
        var sync = mock(UploadSyncService.class);
        var storage = new FileStorageServiceImpl(directory.toString(), sync);
        Files.createDirectories(directory.resolve("images"));
        Path original = Files.createFile(directory.resolve("images/photo.jpg"));
        Path legacy = Files.createFile(directory.resolve("images/photo.card-480.jpg"));
        Path current = Files.createFile(directory.resolve("images/photo.card2-1600.jpg"));
        Path unrelated = Files.createFile(directory.resolve("images/other.card2-1600.jpg"));

        assertThat(storage.deleteFile("/uploads/images/photo.jpg")).isTrue();
        assertThat(original).doesNotExist();
        assertThat(legacy).doesNotExist();
        assertThat(current).doesNotExist();
        assertThat(unrelated).exists();
        verify(sync).deleteRelative("images/photo.jpg");
    }

    @ParameterizedTest
    @CsvSource({
            "portrait.jfif, application/octet-stream, image/jpeg",
            "portrait.JFIF, image/jfif, image/jpeg",
            "portrait.webp, application/octet-stream, image/webp",
            "portrait.WEBP, image/webp, image/webp",
            "portrait.jpg, image/jpeg, image/jpeg"
    })
    void storesImageFormatsWithoutConversionAndWithCanonicalContentType(String filename, String declaredType, String storedType) throws Exception {
        var sync = mock(UploadSyncService.class);
        var storage = new FileStorageServiceImpl(directory.toString(), sync);
        byte[] original = new byte[]{1, 2, 3, 4};
        var saved = storage.saveWithMeta(new MockMultipartFile("file", filename, declaredType, original));

        assertThat(saved.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(saved.getFileType()).isEqualTo(storedType);
        assertThat(saved.getUrl()).startsWith("/uploads/images/");
        assertThat(Files.readAllBytes(storage.resolvePhysicalPath(saved.getUrl()))).isEqualTo(original);
        verify(sync).pushRelative(storage.toRelativePath(saved.getUrl()));
    }
    @org.junit.jupiter.api.Test
    void rejectsSubdirectoryTraversalBeforeWriting() throws Exception {
        var sync = mock(UploadSyncService.class);
        var root = directory.resolve("uploads");
        var storage = new FileStorageServiceImpl(root.toString(), sync);
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1});
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.saveWithMeta(file, "../../escaped"))
                .isInstanceOf(com.example.triplesweb.media.FileStorageException.class);
        assertThat(Files.exists(directory.resolve("escaped"))).isFalse();
        org.mockito.Mockito.verifyNoInteractions(sync);
    }
}
