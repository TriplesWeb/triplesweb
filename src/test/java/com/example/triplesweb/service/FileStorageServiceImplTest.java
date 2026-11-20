package com.example.triplesweb.service;

import com.example.triplesweb.entity.MediaType;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceImplTest {
    @TempDir Path directory;

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
        var store = mock(UploadContentStore.class);
        when(store.maxBytes()).thenReturn(1024L);
        var storage = new FileStorageServiceImpl(directory.toString(), sync, store, Runnable::run);
        byte[] original = new byte[]{1, 2, 3, 4};
        var saved = storage.saveWithMeta(new MockMultipartFile("file", filename, declaredType, original));

        assertThat(saved.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(saved.getFileType()).isEqualTo(storedType);
        assertThat(saved.getUrl()).startsWith("/uploads/images/");
        assertThat(Files.readAllBytes(storage.resolvePhysicalPath(saved.getUrl()))).isEqualTo(original);
        verify(store).save(eq(storage.toRelativePath(saved.getUrl())), any(byte[].class), eq(storedType));
    }
    @org.junit.jupiter.api.Test
    void rejectsSubdirectoryTraversalBeforeWriting() throws Exception {
        var sync = mock(UploadSyncService.class);
        var store = mock(UploadContentStore.class);
        var root = directory.resolve("uploads");
        var storage = new FileStorageServiceImpl(root.toString(), sync, store, Runnable::run);
        var file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1});
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> storage.saveWithMeta(file, "../../escaped"))
                .isInstanceOf(com.example.triplesweb.media.FileStorageException.class);
        assertThat(Files.exists(directory.resolve("escaped"))).isFalse();
        org.mockito.Mockito.verifyNoInteractions(sync, store);
    }

    @org.junit.jupiter.api.Test
    void skipsOversizedDatabaseCopiesBeforeEnqueueing() {
        var store = mock(UploadContentStore.class);
        when(store.maxBytes()).thenReturn(1L);
        var executor = mock(java.util.concurrent.Executor.class);
        var storage = new FileStorageServiceImpl(directory.toString(), mock(UploadSyncService.class), store, executor);
        storage.save(new MockMultipartFile("file", "large.jpg", "image/jpeg", new byte[]{1, 2}));
        org.mockito.Mockito.verifyNoInteractions(executor);
    }
}
