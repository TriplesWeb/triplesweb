package com.example.triplesweb.controller;

import com.example.triplesweb.entity.Archive;
import com.example.triplesweb.entity.MediaType;
import com.example.triplesweb.service.ArchiveService;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.repository.ArtistRepository;
import com.example.triplesweb.entity.Artist;
import com.example.triplesweb.util.MemberPortraitOptions;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
                              @RequestParam(defaultValue = "9") int size,
                              @RequestParam(required = false) String q,
                              @RequestParam(required = false) List<String> members,
                              HttpServletRequest request, HttpSession session, Model model) {

        boolean isAdmin = aboutAdminAuth.isAdmin(request, session);
        boolean isAuthenticated = isAdmin;

        String key = typeSlug.trim().toUpperCase()
                .replace('-', '_');
        if (key.endsWith("_ARCHIVE")) key = key.substring(0, key.length() - "_ARCHIVE".length());

        MediaType type = MediaType.valueOf(key);

        List<String> selectedMembers = normalizeMembers(members);
        Page<Archive> archives = selectedMembers.isEmpty()
                ? archiveService.pageByType(type, page, size, q)
                : archiveService.pageByType(type, page, size, q, selectedMembers);

        List<Artist> artists = artistRepository.findAll();
        List<Map<String, Object>> cards = archiveCards(archives, artists);
        model.addAttribute("archives", archives);
        model.addAttribute("archiveCards", cards);
        model.addAttribute("artistOptions", artists);
        model.addAttribute("memberPickerOptions", memberOptions(artists, selectedMembers));
        model.addAttribute("selectedMembers", selectedMembers);
        model.addAttribute("hasSelectedMembers", !selectedMembers.isEmpty());
        model.addAttribute("page", archives);
        model.addAttribute("type", type);
        model.addAttribute("isAuthenticated", isAuthenticated);
        model.addAttribute("isAdmin", isAdmin);
        addArchivePage(model, archives, type, q, memberQuery(selectedMembers), cards);
        return (type == MediaType.IMAGE) ? "archive/image_archive" : "archive/video_archive";
    }

    private static List<String> normalizeMembers(List<String> members) {
        if (members == null) return List.of();
        Set<String> unique = new LinkedHashSet<>();
        for (String member : members) {
            if (member != null && !member.isBlank()) unique.add(member.trim());
        }
        return List.copyOf(unique);
    }

    private static List<Map<String, Object>> memberOptions(List<Artist> artists, List<String> selected) {
        Set<String> chosen = Set.copyOf(selected);
        List<Map<String, Object>> options = MemberPortraitOptions.fromArtists(artists, false);
        for (Map<String, Object> option : options) {
            option.put("active", chosen.contains(String.valueOf(option.get("value"))));
        }
        return options;
    }

    private static String memberQuery(List<String> selected) {
        if (selected.isEmpty()) return "";
        StringBuilder query = new StringBuilder();
        for (String member : selected) {
            query.append("&members=").append(URLEncoder.encode(member, StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    private static List<Map<String, Object>> archiveCards(Page<Archive> archives, List<Artist> artists) {
        Map<String, String> colors = new LinkedHashMap<>();
        for (Artist artist : artists) {
            String color = artist.getColor();
            if (color == null || !color.matches("#[0-9a-fA-F]{6}")) color = "#b9cac4";
            if (artist.getName() != null) colors.put(artist.getName().trim().toLowerCase(Locale.ROOT), color);
            if (artist.getEngName() != null) colors.put(artist.getEngName().trim().toLowerCase(Locale.ROOT), color);
        }
        long offset = archives.getPageable().isPaged() ? archives.getPageable().getOffset() : 0;
        List<Map<String, Object>> cards = new ArrayList<>();
        for (Archive archive : archives) {
            List<Map<String, String>> badges = new ArrayList<>();
            for (String name : normalizeMembers(archive.getArtistNames() == null ? List.of() : List.of(archive.getArtistNames().split(",")))) {
                badges.add(Map.of("name", name, "color", colors.getOrDefault(name.toLowerCase(Locale.ROOT), "#b9cac4")));
            }
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("id", archive.getId());
            card.put("title", archive.getTitle());
            card.put("artistNames", archive.getArtistNames());
            card.put("file", archive.getFile());
            card.put("thumbnailUrl", archive.getThumbnailUrl());
            card.put("displayNumber", offset + cards.size() + 1);
            card.put("memberBadges", badges);
            cards.add(card);
        }
        return cards;
    }

    private void addArchivePage(Model model, Page<Archive> archives, MediaType type, String q, String memberQuery, List<Map<String, Object>> cards) {
        int totalPages = archives.getTotalPages();
        int current = archives.getNumber();
        String query = q == null ? "" : q.trim();
        model.addAttribute("archiveTotal", archives.getTotalElements());
        model.addAttribute("archivePageCount", archives.getNumberOfElements());
        model.addAttribute("archiveOffset", archives.getPageable().isPaged() ? archives.getPageable().getOffset() : 0);
        model.addAttribute("archivePageDisplay", current + 1);
        model.addAttribute("archiveTotalPages", totalPages);
        model.addAttribute("hasArchivePrev", archives.hasPrevious());
        model.addAttribute("hasArchiveNext", archives.hasNext());
        model.addAttribute("archivePrevPage", Math.max(0, current - 1));
        model.addAttribute("archiveNextPage", current + 1);
        model.addAttribute("hasArchives", !archives.isEmpty());
        model.addAttribute("archiveHighlights", cards.stream().limit(8).toList());
        model.addAttribute("memberQuery", memberQuery == null ? "" : memberQuery);
        model.addAttribute("query", query);
        model.addAttribute("hasQuery", !query.isEmpty());
        model.addAttribute("archivePath", type == MediaType.IMAGE ? "/archive/image_archive" : "/archive/video_archive");
        model.addAttribute("imageArchive", type == MediaType.IMAGE);
        List<Map<String, Object>> archivePages = new ArrayList<>();
        if (totalPages > 0) {
            int start = Math.max(0, current - 2);
            int end = Math.min(totalPages - 1, start + 4);
            start = Math.max(0, end - 4);
            for (int i = start; i <= end; i++) {
                archivePages.add(Map.of("num", i, "label", i + 1, "active", i == current));
            }
        }
        model.addAttribute("archivePages", archivePages);
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
                              @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                              @RequestParam("title") String title, @RequestParam(value="artistNames", required=false) List<String> artistNames, HttpServletRequest request, HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        archiveService.uploadVideoOnly(file, title, MediaType.VIDEO, artistNames == null ? "" : String.join(",", artistNames), thumbnail);
        return "redirect:/archive/video_archive";
    }

    @PostMapping("/{id}")
    @ResponseBody
    public ResponseEntity<?> updateArchive(@PathVariable long id,
                                           @RequestParam("title") String title,
                                           @RequestParam(value = "file", required = false) MultipartFile file,
                                           @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
                                           @RequestParam(defaultValue = "false") boolean clearThumbnail,
                                           @RequestParam(value = "artistNames", required = false) String artistNames,
                                           HttpServletRequest request,
                                           HttpSession session) throws IOException {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            archiveService.update(id, title, file, artistNames, thumbnail, clearThumbnail);
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

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseBody
    public ResponseEntity<String> invalidUpload(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }
}
