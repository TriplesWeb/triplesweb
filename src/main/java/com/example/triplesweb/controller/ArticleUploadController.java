package com.example.triplesweb.controller;

import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.service.FileStorageService;
import com.example.triplesweb.service.AboutAdminAuth;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 게시글 작성·수정 폼/에디터용 파일 업로드 REST API.
 * URL: {@code POST /api/article/{category}/uploadFile}
 * 레이어: OK — FileStorageService만 사용한다.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/article")
public class ArticleUploadController {

    private final FileStorageService fileStorageService;
    private final AboutAdminAuth aboutAdminAuth;

    @PostMapping("/{category}/uploadFile")
    public ResponseEntity<SavedMeta> upload(
            @PathVariable String category,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest request,
            HttpSession session
    ) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        SavedMeta meta = fileStorageService.saveWithMeta(file, category);
        return ResponseEntity.ok(meta);
    }
}
