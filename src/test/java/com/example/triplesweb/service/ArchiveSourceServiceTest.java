package com.example.triplesweb.service;

import com.example.triplesweb.entity.ArchiveSource;
import com.example.triplesweb.repository.ArchiveRepository;
import com.example.triplesweb.repository.ArchiveSourceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
    void createNormalizesCodeAndColor() {
        when(archiveSourceRepository.existsByCodeIgnoreCase("FAN_MEET")).thenReturn(false);
        when(archiveSourceRepository.findAllByOrderBySortOrderAscIdAsc()).thenReturn(List.of());
        when(archiveSourceRepository.save(any(ArchiveSource.class))).thenAnswer(invocation -> {
            ArchiveSource source = invocation.getArgument(0);
            source.setId(11L);
            return source;
        });

        var dto = service.create(" fan-meet ", " 팬미팅 ", "#AABBCC", null, true);

        assertThat(dto.code()).isEqualTo("FAN_MEET");
        assertThat(dto.slug()).isEqualTo("fan_meet");
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
    void createRejectsReservedTeaserCode() {
        assertThatThrownBy(() -> service.create("TEASER", "티저", "#00f5d4", null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TEASER_BEHIND");
    }
}
