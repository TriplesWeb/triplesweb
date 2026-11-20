package com.example.triplesweb.service;

import com.example.triplesweb.entity.IdempotencyRecord;
import com.example.triplesweb.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock IdempotencyRecordRepository repository;
    IdempotencyService service;

    @BeforeEach
    void setUp() {
        service = new IdempotencyService(repository);
    }

    @Test
    void rememberPersistsNewKey() {
        when(repository.existsById(any())).thenReturn(false);
        service.remember(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "k1", "/archive?folder=s");
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("k1");
        assertThat(captor.getValue().getResponseLocation()).isEqualTo("/archive?folder=s");
    }

    @Test
    void rememberSkipsBlankKey() {
        service.remember(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "  ", "/archive");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void completedLocationReturnsStoredPath() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResponseLocation("/archive?folder=s");
        when(repository.findById(any())).thenReturn(Optional.of(record));
        assertThat(service.completedLocation(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "k1"))
                .contains("/archive?folder=s");
    }
}
