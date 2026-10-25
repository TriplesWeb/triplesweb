package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArchiveThumbnailTest {
    final ArchiveRepository repository = mock(ArchiveRepository.class);
    final FileStorageService storage = mock(FileStorageService.class);
    final ArchiveService service = new ArchiveService(repository, storage);
    final MockMultipartFile video = new MockMultipartFile("file", "v.mp4", "video/mp4", new byte[]{1});
    final MockMultipartFile image = new MockMultipartFile("thumbnail", "poster.JFIF", "application/octet-stream", new byte[]{2});
    Archive archive;

    @BeforeEach void setUp() throws Exception {
        archive = new Archive("영상", SavedMeta.builder().url("/uploads/videos/v.mp4").mediaType(MediaType.VIDEO).build());
        archive.setThumbnailUrl("/uploads/images/old.webp");
        when(repository.findById(7L)).thenReturn(Optional.of(archive));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storage.saveWithMeta(video)).thenReturn(archive.getFile());
        when(storage.saveWithMeta(image)).thenReturn(SavedMeta.builder().url("/uploads/images/new.jfif").mediaType(MediaType.IMAGE).build());
    }

    @Test void uploadStoresCustomThumbnail() throws Exception {
        Archive saved = service.uploadVideoOnly(video, "영상", MediaType.VIDEO, "서연", image);
        assertThat(saved.getThumbnailUrl()).isEqualTo("/uploads/images/new.jfif");
        assertThat(saved.getFile().getUrl()).isEqualTo("/uploads/videos/v.mp4");
    }

    @Test void uploadWithoutThumbnailRemainsSupported() throws Exception {
        assertThat(service.uploadVideoOnly(video, "영상", MediaType.VIDEO, "서연").getThumbnailUrl()).isNull();
    }

    @Test void metadataEditKeepsThumbnail() throws Exception {
        assertThat(service.update(7L, "제목 수정", null, "서연", null, false).getThumbnailUrl()).isEqualTo("/uploads/images/old.webp");
        verify(storage, never()).deleteFile(any());
    }

    @Test void replacingThumbnailCleansUpOldImageWithoutTouchingVideo() throws Exception {
        assertThat(service.update(7L, "영상", null, "서연", image, false).getThumbnailUrl()).isEqualTo("/uploads/images/new.jfif");
        verify(storage).deleteFile("/uploads/images/old.webp");
        verify(storage, never()).deleteFile("/uploads/videos/v.mp4");
    }

    @Test void clearingAndDeletingCleanUpThumbnail() throws Exception {
        assertThat(service.update(7L, "영상", null, "서연", null, true).getThumbnailUrl()).isNull();
        verify(storage).deleteFile("/uploads/images/old.webp");
        archive.setThumbnailUrl("/uploads/images/new.jfif");
        assertThat(service.delete(7L)).isTrue();
        verify(storage).deleteFile("/uploads/images/new.jfif");
    }

    @Test void rejectsNonImageThumbnailBeforeWritingFiles() {
        assertThatThrownBy(() -> service.uploadVideoOnly(video, "영상", MediaType.VIDEO, "서연", video))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("썸네일");
        verifyNoInteractions(storage);
    }
}
