package com.example.triplesweb.service;

import com.example.triplesweb.entity.IdempotencyRecord;
import com.example.triplesweb.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Optional;

/**
 * 동일 Idempotency-Key 재요청 시 이전 성공 응답(리다이렉트 위치)을 돌려준다.
 */
@Service
public class IdempotencyService {

    public static final String SCOPE_ARCHIVE_UPLOAD = "archive-upload";

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<String> completedLocation(String scope, String key) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(scope)) {
            return Optional.empty();
        }
        return repository.findById(new IdempotencyRecord.Pk(scope, key.trim()))
                .map(IdempotencyRecord::getResponseLocation);
    }

    @Transactional
    public void remember(String scope, String key, String responseLocation) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(scope) || !StringUtils.hasText(responseLocation)) {
            return;
        }
        String trimmed = key.trim();
        if (repository.existsById(new IdempotencyRecord.Pk(scope, trimmed))) {
            return;
        }
        IdempotencyRecord record = new IdempotencyRecord();
        record.setScope(scope);
        record.setIdempotencyKey(trimmed);
        record.setResponseLocation(responseLocation);
        record.setCreatedAt(Instant.now());
        try {
            repository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ignored) {
            // 동시 저장 — 기존 행을 사용하면 됨
        }
    }
}
