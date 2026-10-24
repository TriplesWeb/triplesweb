package com.example.triplesweb.service;

import com.example.triplesweb.entity.UploadBlob;
import com.example.triplesweb.repository.UploadBlobRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 업로드 바이트를 공유 DB에 둔다. 용량 한도를 넘기면 디스크 저장만 유지한다.
 */
@Service
@Slf4j
public class UploadContentStore {
    private final UploadBlobRepository repository;
    private final long maxBytes;

    public UploadContentStore(
            UploadBlobRepository repository,
            @Value("${upload.database-copy-max-bytes:33554432}") long maxBytes
    ) {
        this.repository = repository;
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }

    @Transactional
    public void save(String relativePath, byte[] data, String contentType) {
        if (relativePath == null || relativePath.isBlank() || data == null || data.length == 0) return;
        if (data.length > maxBytes) {
            log.info("DB에는 복사하지 않음 ({} bytes > {}): {}", data.length, maxBytes, relativePath);
            return;
        }
        UploadBlob blob = repository.findById(relativePath).orElseGet(UploadBlob::new);
        blob.setRelativePath(relativePath);
        blob.setContentType(contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType);
        blob.setSizeBytes(data.length);
        blob.setData(data);
        repository.save(blob);
    }

    @Transactional(readOnly = true)
    public Optional<UploadBlob> find(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) return Optional.empty();
        return repository.findById(relativePath);
    }

    @Transactional
    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || !repository.existsById(relativePath)) return;
        repository.deleteById(relativePath);
    }
}
