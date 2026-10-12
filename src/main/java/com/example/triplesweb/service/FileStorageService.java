package com.example.triplesweb.service;

import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.entity.SavedMeta;
import jakarta.annotation.Nullable;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

public interface FileStorageService {

    String save(MultipartFile file) throws IOException;

    String save(MultipartFile file, MediaType type) throws IOException;

    SavedMeta saveWithMeta(MultipartFile file) throws IOException;

    boolean deleteFile(String fileChannel);

    String toRelativePath(String fileChannel);

    /** fileChannel(공개 URL 또는 상대경로)을 실제 디스크의 절대 Path로 해석 */
    Path resolvePhysicalPath(String fileChannel);

    SavedMeta saveWithMeta(MultipartFile file, @jakarta.annotation.Nullable String subdir) throws IOException;

    @Nullable
    String readOriginalName(String fileChannel);
    void deleteSidecar(String fileChannel);
}
