package com.example.triplesweb.controller;

import com.example.triplesweb.entity.SavedMeta;
import com.example.triplesweb.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

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
