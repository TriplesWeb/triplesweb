package com.example.triplesweb.service;

import com.example.triplesweb.dto.ArchiveSourceDto;
import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.repository.ArchiveSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveSourceServiceTest {
    @Mock ArchiveSourceRepository archiveSourceRepository;
    @Mock ArchiveRepository archiveRepository;
    ArchiveSourceService service;

    @BeforeEach
    void setUp() {
        service = new ArchiveSourceService(archiveSourceRepository, archiveRepository);
    }

    @Test
    void createGeneratesAValidCodeAndNormalizesColor() {
        when(archiveSourceRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of());
        when(archiveSourceRepository.save(any(ArchiveSource.class))).thenAnswer(invocation -> {
            ArchiveSource source = invocation.getArgument(0);
            source.setId(11L);
            return source;
        });

        var dto = service.create(" 팬미팅 ", "#AABBCC", null);

        assertThat(dto.code()).matches("^[A-Z][A-Z0-9_]{0,31}$").startsWith("SOURCE_");
        assertThat(dto.slug()).isEqualTo(dto.code().toLowerCase(java.util.Locale.ROOT));
        assertThat(dto.color()).isEqualTo("#aabbcc");
        assertThat(dto.sortOrder()).isEqualTo(10);
    }

    @Test
    void deleteRejectsWhenArchivesReferenceSource() {
        ArchiveSource source = new ArchiveSource();
        source.setId(2L);
        source.setCode("EXTERNAL");
        when(archiveSourceRepository.findById(2L)).thenReturn(Optional.of(source));
        when(archiveRepository.countBySourceIgnoreCase("EXTERNAL")).thenReturn(3L);

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void deleteRemovesUnusedSource() {
        ArchiveSource source = new ArchiveSource();
        source.setId(4L);
        source.setCode("POLAROID");
        when(archiveSourceRepository.findById(4L)).thenReturn(Optional.of(source));
        when(archiveRepository.countBySourceIgnoreCase("POLAROID")).thenReturn(0L);

        service.delete(4L);

        ArgumentCaptor<ArchiveSource> deleted = ArgumentCaptor.forClass(ArchiveSource.class);
        verify(archiveSourceRepository).delete(deleted.capture());
        assertThat(deleted.getValue().getCode()).isEqualTo("POLAROID");
    }

    @Test
    void findMapsLegacyTeaserToTeaserBehind() {
        ArchiveSource behind = new ArchiveSource();
        behind.setCode("TEASER_BEHIND");
        behind.setLabel("티저/비하인드");
        behind.setVisible(true);
        when(archiveSourceRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of(behind));

        assertThat(service.find("teaser").getCode()).isEqualTo("TEASER_BEHIND");
        assertThat(service.find("TEASER").getCode()).isEqualTo("TEASER_BEHIND");
        assertThat(service.resolveCode("TEASER_BEHIND")).isEqualTo("TEASER_BEHIND");
    }

    @Test
    void listsSourcesFromOneQueryForEachMediaTypeWithoutUsingTheLegacyFlag() {
        var legacyDisabled = source(1L, 10);
        legacyDisabled.setVisible(false);
        var videoOnly = source(2L, 20);
        var empty = source(3L, 30);
        when(archiveSourceRepository.findAllByOrderBySortOrderAscIdAsc())
                .thenReturn(List.of(legacyDisabled, videoOnly, empty));
        when(archiveRepository.findSourceCodesWithPosts(MediaType.IMAGE)).thenReturn(List.of(legacyDisabled.getCode()));
        when(archiveRepository.findSourceCodesWithPosts(MediaType.VIDEO)).thenReturn(List.of(videoOnly.getCode()));

        var photos = service.listTabs(MediaType.IMAGE, false);
        var adminVideos = service.listTabs(MediaType.VIDEO, true);

        assertThat(photos).extracting(tab -> tab.source().id()).containsExactly(1L);
        assertThat(adminVideos).extracting(ArchiveSourceService.SourceTab::hasPosts).containsExactly(false, true, false);
        verify(archiveRepository).findSourceCodesWithPosts(MediaType.IMAGE);
        verify(archiveRepository).findSourceCodesWithPosts(MediaType.VIDEO);
    }

    @Test
    void movingBeyondEitherBoundaryPreservesOrder() {
        ArchiveSource first = source(1L, 10);
        ArchiveSource last = source(2L, 30);
        when(archiveSourceRepository.findAllForReorder()).thenReturn(List.of(first, last));

        assertThat(service.move(1L, -1)).extracting(ArchiveSourceDto::id).containsExactly(1L, 2L);
        assertThat(service.move(2L, 1)).extracting(ArchiveSourceDto::id).containsExactly(1L, 2L);
        assertThat(first.getSortOrder()).isEqualTo(10);
        assertThat(last.getSortOrder()).isEqualTo(30);
    }

    @Test
    void movesOnePositionAndKeepsExistingDistinctRanks() {
        ArchiveSource first = source(1L, 10);
        ArchiveSource legacyDisabled = source(2L, 30);
        legacyDisabled.setVisible(false);
        ArchiveSource last = source(3L, 70);
        when(archiveSourceRepository.findAllForReorder()).thenReturn(List.of(first, legacyDisabled, last));

        assertThat(moveWithinTransaction(3L, -1)).extracting(ArchiveSourceDto::id).containsExactly(1L, 3L, 2L);
        assertThat(first.getSortOrder()).isEqualTo(10);
        assertThat(last.getSortOrder()).isEqualTo(30);
        assertThat(legacyDisabled.getSortOrder()).isEqualTo(70);
    }

    @Test
    void reordersAdjacentSourcesWithEqualRanks() {
        ArchiveSource first = source(1L, 10);
        ArchiveSource second = source(2L, 10);
        ArchiveSource last = source(3L, 30);
        when(archiveSourceRepository.findAllForReorder()).thenReturn(List.of(first, second, last));

        assertThat(moveWithinTransaction(1L, 1)).extracting(ArchiveSourceDto::id).containsExactly(2L, 1L, 3L);
        assertThat(second.getSortOrder()).isLessThan(first.getSortOrder());
        assertThat(first.getSortOrder()).isLessThan(last.getSortOrder());
    }

    private List<ArchiveSourceDto> moveWithinTransaction(long id, int direction) {
        TransactionSynchronizationManager.initSynchronization();
        try {
            return service.move(id, direction);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private static ArchiveSource source(long id, int rank) {
        ArchiveSource source = new ArchiveSource();
        source.setId(id);
        source.setCode("SOURCE_" + id);
        source.setSortOrder(rank);
        return source;
    }

    @Test
    void createRetriesWhenTheGeneratedCodeAlreadyExists() {
        when(archiveSourceRepository.existsByCodeIgnoreCase(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true, false);
        when(archiveSourceRepository.save(any(ArchiveSource.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service.create("팬미팅", null, 30);

        ArgumentCaptor<String> candidates = ArgumentCaptor.forClass(String.class);
        verify(archiveSourceRepository, org.mockito.Mockito.times(2)).existsByCodeIgnoreCase(candidates.capture());
        assertThat(candidates.getAllValues()).doesNotHaveDuplicates();
        assertThat(dto.code()).isEqualTo(candidates.getValue());
    }
}
