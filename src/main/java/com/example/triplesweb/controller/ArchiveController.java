package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
@RequiredArgsConstructor
@RequestMapping("/archive")
public class ArchiveController {

    private final ArchiveService archiveService;

    @GetMapping("/{type}_archive")
    public String showArchive(@PathVariable("type") String typeSlug,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "24") int size,
                              @RequestParam(required = false) String q,
                              Model model) { // [주석] 로그인 기능 삭제로 인해 Authentication auth 파라미터 제거

        // [주석] 로그인 및 관리자 여부 체크 로직 삭제 -> 항상 인증 및 관리자 권한이 있는 것처럼 처리
        boolean isAuthenticated = true;
        boolean isAdmin = true;

        String key = typeSlug.trim().toUpperCase()
                .replace('-', '_');
        if (key.endsWith("_ARCHIVE")) key = key.substring(0, key.length() - "_ARCHIVE".length());

        MediaType type = MediaType.valueOf(key);

        Page<Archive> archives = archiveService.pageByType(type, page, size, q);

        model.addAttribute("archives", archives);
        model.addAttribute("page", archives);
        model.addAttribute("type", type);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        return (type == MediaType.IMAGE) ? "archive/image_archive" : "archive/video_archive";
    }

    // [주석] @PreAuthorize("hasRole('ADMIN')") 제한 해제 (누구나 업로드 가능)
    @PostMapping("/upload")
    public String uploadArchive(@RequestParam("title") String title,
                                @RequestParam("type") MediaType type,
                                @RequestParam("file") MultipartFile file) throws IOException {
        archiveService.save(file, title, type);
        return (type == MediaType.IMAGE)
                ? "redirect:/archive/image_archive"
                : "redirect:/archive/video_archive";
    }

    // [주석] 관리자 전용 어노테이션 제거
    @PostMapping("/upload/image")
    public String uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title) throws IOException {
        archiveService.uploadImageOnly(file, title, MediaType.IMAGE);
        return "redirect:/archive/image_archive";
    }

    // [주석] 관리자 전용 어노테이션 제거
    @PostMapping("/upload/video")
    public String uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title) throws IOException {
        archiveService.uploadVideoOnly(file, title, MediaType.VIDEO);
        return "redirect:/archive/video_archive";
    }

    // [주석] 관리자 전용 어노테이션 제거 및 누구나 삭제 가능하도록 변경
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteArchive(@PathVariable long id) {
        boolean ok = archiveService.delete(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}