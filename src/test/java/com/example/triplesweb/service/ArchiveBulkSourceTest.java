package com.example.triplesweb.service;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.repository.ArchiveRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveBulkSourceTest {

    @Mock ArchiveRepository archiveRepository;
    @Mock FileStorageService fileStorageService;
    @Mock EntityManager entityManager;
    ArchiveService archiveService;

    @BeforeEach
    void setUp() {
        archiveService = new ArchiveService(archiveRepository, fileStorageService, new ObjectMapper(), entityManager);
    }

    @Test
    void bulkUpdateSourceUpdatesMatchingRows() {
        Archive first = image(1L, "COSMO_ROOM");
        Archive second = image(2L, "MAGAZINE");
        when(archiveRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any()))
                .thenReturn(List.of(first, second));
        when(archiveRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        int updated = archiveService.bulkUpdateSource(List.of(1L, 2L, 2L, 99L), "OFFICIAL_SNS");

        assertThat(updated).isEqualTo(2);
        assertThat(first.getSource()).isEqualTo("OFFICIAL_SNS");
        assertThat(second.getSource()).isEqualTo("OFFICIAL_SNS");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Archive>> captor = ArgumentCaptor.forClass(List.class);
        verify(archiveRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).containsExactly(first, second);
    }

    @Test
    void bulkUpdateSourceRejectsEmptySelection() {
        assertThatThrownBy(() -> archiveService.bulkUpdateSource(List.of(), "OFFICIAL_SNS"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("선택");
        verify(archiveRepository, never()).findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any());
    }

    @Test
    void bulkUpdateSourceRejectsBlankSource() {
        assertThatThrownBy(() -> archiveService.bulkUpdateSource(List.of(1L), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("출처");
        verify(archiveRepository, never()).findAllById(org.mockito.ArgumentMatchers.<Iterable<Long>>any());
    }

    private static Archive image(long id, String source) {
        SavedMeta meta = new SavedMeta();
        meta.setUrl("/uploads/images/" + id + ".jpg");
        meta.setMediaType(MediaType.IMAGE);
        Archive archive = new Archive("shot-" + id, meta);
        archive.setId(id);
        archive.setSource(source);
        return archive;
    }
}
