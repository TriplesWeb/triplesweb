package com.example.triplesweb.service;

import com.example.triplesweb.entity.IdempotencyRecord;
import com.example.triplesweb.repository.IdempotencyRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    void claimInsertsPendingMarker() {
        when(repository.findById(any())).thenReturn(Optional.empty());
        assertThat(service.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "k1")).isEmpty();
        ArgumentCaptor<IdempotencyRecord> captor = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(repository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getResponseLocation()).isEqualTo(IdempotencyService.PENDING);
    }

    @Test
    void claimReplaysCompletedLocation() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResponseLocation("/archive?folder=s");
        when(repository.findById(any())).thenReturn(Optional.of(record));
        assertThat(service.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "k1"))
                .contains("/archive?folder=s");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void claimConflictsWhilePending() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResponseLocation(IdempotencyService.PENDING);
        when(repository.findById(any())).thenReturn(Optional.of(record));
        assertThatThrownBy(() -> service.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "k1"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void completeUpdatesLocation() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResponseLocation(IdempotencyService.PENDING);
        when(repository.findById(any())).thenReturn(Optional.of(record));
        service.complete(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "k1", "/archive?folder=s");
        assertThat(record.getResponseLocation()).isEqualTo("/archive?folder=s");
        verify(repository).saveAndFlush(record);
    }

    @Test
    void abandonDeletesPendingOnly() {
        IdempotencyRecord record = new IdempotencyRecord();
        record.setResponseLocation(IdempotencyService.PENDING);
        when(repository.findById(any())).thenReturn(Optional.of(record));
        service.abandon(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "k1");
        verify(repository).delete(record);
    }

    @Test
    void claimSkipsBlankKey() {
        assertThat(service.claimOrReplay(IdempotencyService.SCOPE_ARCHIVE_UPLOAD, "  ")).isEmpty();
        verify(repository, never()).saveAndFlush(any());
    }
}
