package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.entity.Artist;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
    private final AboutAdminAuth aboutAdminAuth;
    private final ArtistRepository artistRepository;

    @GetMapping("/{type}_archive")
    public String showArchive(@PathVariable("type") String typeSlug,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "24") int size,
                              @RequestParam(required = false) String q,
                              HttpServletRequest request, HttpSession session, Model model) {

        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        boolean isAuthenticated = isAdmin;

        String key = typeSlug.trim().toUpperCase()
                .replace('-', '_');
        if (key.endsWith("_ARCHIVE")) key = key.substring(0, key.length() - "_ARCHIVE".length());

        MediaType type = MediaType.valueOf(key);

        Page<Archive> archives = archiveService.pageByType(type, page, size, q);

        model.addAttribute("archives", archives);
        model.addAttribute("artistOptions", artistRepository.findAll());
        model.addAttribute("page", archives);
        model.addAttribute("type", type);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        return (type == MediaType.IMAGE) ? "archive/image_archive" : "archive/video_archive";
    }

    @PostMapping("/upload")
    public String uploadArchive(@RequestParam("title") String title,
                                @RequestParam("type") MediaType type,
                                @RequestParam("file") MultipartFile file, @RequestParam(value="artistNames", required=false) List<String> artistNames, HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        archiveService.save(file, title, type, artistNames == null ? "" : String.join(",", artistNames));
        return (type == MediaType.IMAGE)
                ? "redirect:/archive/image_archive"
                : "redirect:/archive/video_archive";
    }

    @PostMapping("/upload/image")
    public String uploadImage(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames, HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        archiveService.uploadImageOnly(file, title, MediaType.IMAGE, artistNames == null ? "" : String.join(",", artistNames));
        return "redirect:/archive/image_archive";
    }

    @PostMapping("/upload/video")
    public String uploadVideo(@RequestParam("file") MultipartFile file,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames, HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        archiveService.uploadVideoOnly(file, title, MediaType.VIDEO, artistNames == null ? "" : String.join(",", artistNames));
        return "redirect:/archive/video_archive";
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateArchive(@PathVariable long id,
                                           @RequestParam("title") String title,
                                           @RequestParam(value = "file", required = false) MultipartFile file,
                                           @RequestParam(value = "artistNames", required = false) String artistNames,
                                           HttpServletRequest request,
                                           HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            archiveService.update(id, title, file, artistNames);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteArchive(@PathVariable long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        boolean ok = archiveService.delete(id);
        return ok ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }
}