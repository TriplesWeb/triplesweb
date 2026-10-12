package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.service.ArchiveService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
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
                              Model model, Authentication auth) {

        boolean isAuthenticated = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        boolean isAdmin = isAuthenticated
                && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String key = typeSlug.trim().toUpperCase()
                .replace('-', '_');                         // image-archive → IMAGE_ARCHIVE
        if (key.endsWith("_ARCHIVE")) key = key.substring(0, key.length() - "_ARCHIVE".length());

        MediaType type = MediaType.valueOf(key);

        Page<Archive> archives = archiveService.pageByType(type,page,size,q);

        model.addAttribute("archives", archives);
        model.addAttribute("page", archives);
        model.addAttribute("type",type);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        return (type == MediaType.IMAGE) ? "archive/image_archive" : "archive/video_archive";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/upload")
    public String uploadArchive(@RequestParam("title") String title,
                                @RequestParam("type") MediaType type,
                                @RequestParam("file") MultipartFile file) throws IOException {
        archiveService.save(file, title, type);
        return (type == MediaType.IMAGE)
                ? "redirect:/archive/image_archive"
                : "redirect:/archive/video_archive";
    }

    @PostMapping("/upload/image")
    @PreAuthorize("hasRole('ADMIN')")
    public String uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title) throws IOException {
        archiveService.uploadImageOnly(file, title, MediaType.IMAGE);
        return "redirect:/archive/image_archive";
    }

    /** 비디오 전용 업로드 (원하면 유지, 아니면 삭제 가능) */
    @PostMapping("/upload/video")
    @PreAuthorize("hasRole('ADMIN')")
    public String uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title) throws IOException {
        archiveService.uploadVideoOnly(file, title, MediaType.VIDEO);
        return "redirect:/archive/video_archive";
    }
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteArchive (@PathVariable long id) {
        boolean ok = archiveService.delete(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

}
