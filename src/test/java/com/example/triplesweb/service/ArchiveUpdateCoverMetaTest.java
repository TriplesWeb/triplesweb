package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveUpdateCoverMetaTest {

    @Mock ArchiveRepository archiveRepository;
    @Mock FileStorageService fileStorageService;
    ObjectMapper objectMapper = new ObjectMapper();
    ArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService = new ArchiveService(archiveRepository, fileStorageService, objectMapper);
    }

    @Test
    void updateKeepsFileTypeWhenRemovingSecondaryImage() throws Exception {
        Archive existing = imagePost(
                "/uploads/images/a.jpg",
                "[\"/uploads/images/a.jpg\",\"/uploads/images/b.jpg\"]");
        when(archiveRepository.findById(179L)).thenReturn(Optional.of(existing));
        when(archiveRepository.save(any(Archive.class))).thenAnswer(invocation -> invocation.getArgument(0));

        archiveService.update(
                179L,
                "",
                List.of(),
                "윤서연",
                null,
                false,
                LocalDate.of(2023, 4, 24),
                ArchiveSource.MAGAZINE,
                "Vanity Teen",
                "[\"/uploads/images/a.jpg\"]",
                null);

        ArgumentCaptor<Archive> saved = ArgumentCaptor.forClass(Archive.class);
        verify(archiveRepository).save(saved.capture());
        SavedMeta file = saved.getValue().getFile();
        assertThat(file.getUrl()).isEqualTo("/uploads/images/a.jpg");
        assertThat(file.getFileType()).isEqualTo("image/jpeg");
        assertThat(file.getOriginalName()).isEqualTo("cover.jpg");
        assertThat(file.getSavedName()).isEqualTo("images/a.jpg");
    }

    @Test
    void updateFillsFileMetaWhenCoverSwitchesToKeptImage() throws Exception {
        Archive existing = imagePost(
                "/uploads/images/a.jpg",
                "[\"/uploads/images/a.jpg\",\"/uploads/images/b.png\"]");
        when(archiveRepository.findById(180L)).thenReturn(Optional.of(existing));
        when(archiveRepository.save(any(Archive.class))).thenAnswer(invocation -> invocation.getArgument(0));

        archiveService.update(
                180L,
                "",
                List.of(),
                "윤서연",
                null,
                false,
                LocalDate.of(2023, 4, 24),
                ArchiveSource.MAGAZINE,
                "Vanity Teen",
                "[\"/uploads/images/b.png\",\"/uploads/images/a.jpg\"]",
                "[\"/uploads/images/b.png\",\"/uploads/images/a.jpg\"]");

        ArgumentCaptor<Archive> saved = ArgumentCaptor.forClass(Archive.class);
        verify(archiveRepository).save(saved.capture());
        SavedMeta file = saved.getValue().getFile();
        assertThat(file.getUrl()).isEqualTo("/uploads/images/b.png");
        assertThat(file.getFileType()).isEqualTo("image/png");
        assertThat(file.getSavedName()).isEqualTo("images/b.png");
        assertThat(file.getOriginalName()).isEqualTo("b.png");
        assertThat(file.getMediaType()).isEqualTo(MediaType.IMAGE);
    }

    private static Archive imagePost(String coverUrl, String imagesJson) {
        Archive archive = new Archive();
        archive.setId(179L);
        archive.setTitle("");
        archive.setArtistNames("윤서연");
        archive.setImagesJson(imagesJson);
        archive.setFile(SavedMeta.builder()
                .url(coverUrl)
                .originalName("cover.jpg")
                .savedName(coverUrl.startsWith("/uploads/") ? coverUrl.substring("/uploads/".length()) : coverUrl)
                .size(12L)
                .fileType("image/jpeg")
                .mediaType(MediaType.IMAGE)
                .build());
        return archive;
    }
}
