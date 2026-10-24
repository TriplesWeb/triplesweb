package com.example.triplesweb.controller;

import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 게시글 작성·수정 폼/에디터용 파일 업로드 REST API.
 * URL: {@code POST /api/article/{category}/uploadFile}
 * 레이어: OK — FileStorageService만 사용한다.
 * 게시글 작성은 비로그인·비관리자도 가능하므로 업로드도 동일하게 공개한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/article")
public class ArticleUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/{category}/uploadFile")
    public ResponseEntity<SavedMeta> upload(
            @PathVariable String category,
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        // category에 따라 하위 폴더 구분 (ex: free)
        SavedMeta meta = fileStorageService.saveWithMeta(file, category);
        return ResponseEntity.ok(meta);
    }
}
