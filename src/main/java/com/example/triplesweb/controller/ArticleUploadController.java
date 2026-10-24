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
 * 게시판 글쓰기는 로그인 사용자(공개 폼)도 쓰므로 관리자 인증을 걸지 않는다.
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
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        SavedMeta meta = fileStorageService.saveWithMeta(file, category);
        return ResponseEntity.ok(meta);
    }
}
