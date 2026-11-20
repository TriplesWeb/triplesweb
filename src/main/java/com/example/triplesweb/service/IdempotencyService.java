package com.example.triplesweb.service;

import com.example.triplesweb.entity.IdempotencyRecord;
import com.example.triplesweb.repository.IdempotencyRecordRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

/**
 * 동일 Idempotency-Key 재요청 시 이전 성공 응답(리다이렉트 위치)을 돌려준다.
 * 업로드 부작용 전에 키를 선점해 Blue-Green·재시도 시 이중 등록을 막는다.
 */
@Service
public class IdempotencyService {

    public static final String SCOPE_ARCHIVE_UPLOAD = "archive-upload";
    static final String PENDING = "__PENDING__";

    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * @return 이미 완료된 리다이렉트 경로. 비어 있으면 이 호출자가 작업을 진행한다.
     * @throws ResponseStatusException 다른 요청이 같은 키로 처리 중이면 409
     */
    @Transactional
    public Optional<String> claimOrReplay(String scope, String key) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(scope)) {
            return Optional.empty();
        }
        String trimmed = key.trim();
        IdempotencyRecord.Pk pk = new IdempotencyRecord.Pk(scope, trimmed);
        Optional<IdempotencyRecord> existing = repository.findById(pk);
        if (existing.isPresent()) {
            return replayOrConflict(existing.get());
        }
        IdempotencyRecord record = new IdempotencyRecord();
        record.setScope(scope);
        record.setIdempotencyKey(trimmed);
        record.setResponseLocation(PENDING);
        record.setCreatedAt(Instant.now());
        try {
            repository.saveAndFlush(record);
            return Optional.empty();
        } catch (DataIntegrityViolationException race) {
            IdempotencyRecord won = repository.findById(pk)
                    .orElseThrow(() -> race);
            return replayOrConflict(won);
        }
    }

    @Transactional
    public void complete(String scope, String key, String responseLocation) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(scope) || !StringUtils.hasText(responseLocation)) {
            return;
        }
        repository.findById(new IdempotencyRecord.Pk(scope, key.trim())).ifPresent(record -> {
            record.setResponseLocation(responseLocation);
            repository.saveAndFlush(record);
        });
    }

    /** 업로드 실패 시 선점을 풀어 같은 키로 재시도할 수 있게 한다. */
    @Transactional
    public void abandon(String scope, String key) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(scope)) {
            return;
        }
        repository.findById(new IdempotencyRecord.Pk(scope, key.trim())).ifPresent(record -> {
            if (PENDING.equals(record.getResponseLocation())) {
                repository.delete(record);
            }
        });
    }

    private static Optional<String> replayOrConflict(IdempotencyRecord record) {
        if (PENDING.equals(record.getResponseLocation())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 업로드가 아직 처리 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        return Optional.of(record.getResponseLocation());
    }
}
