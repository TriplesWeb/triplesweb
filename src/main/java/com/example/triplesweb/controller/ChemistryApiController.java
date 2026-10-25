package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ChemistryDto;
import com.example.triplesweb.dto.ChemistryGroupDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ChemistryGroupService;
import com.example.triplesweb.service.ChemistrySeedService;
import com.example.triplesweb.service.ChemistryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chemistry")
@RequiredArgsConstructor
public class ChemistryApiController {

    private final ChemistryService chemistryService;
    private final ChemistryGroupService chemistryGroupService;
    private final ChemistrySeedService chemistrySeedService;
    private final AboutAdminAuth aboutAdminAuth;

    @GetMapping
    public List<ChemistryDto> list() {
        return chemistryService.findAll();
    }

    @GetMapping("/admin/status")
    public Map<String, Object> adminStatus(HttpServletRequest request, HttpSession session) {
        return aboutAdminAuth.status(request, session);
    }

    @PostMapping("/admin/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body,
                                     HttpServletRequest request, HttpSession session) {
        return aboutAdminAuth.login(body, request, session);
    }

    @PostMapping("/admin/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpSession session) {
        return aboutAdminAuth.logout(request, session);
    }

    @PostMapping
    public ChemistryDto upsert(
            @RequestParam String member1,
            @RequestParam String member2,
            @RequestParam String label,
            @RequestParam(required = false, defaultValue = "") String labelEn,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "") String descriptionEn,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) List<MultipartFile> images,
            @RequestParam(required = false, defaultValue = "") String keepImageUrlsJson,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            @RequestParam(required = false, defaultValue = "") String linksJson,
            @RequestParam(required = false, defaultValue = "NONE") String labelTone,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return chemistryService.upsert(
                    member1, member2, label, labelEn, description, descriptionEn,
                    image, images, keepImageUrlsJson, clearImage, linksJson, labelTone);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        chemistryService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteByMembers(
            @RequestParam String member1,
            @RequestParam String member2,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            chemistryService.deleteByMembers(member1, member2);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** 시드 JSON의 누락 페어를 Artist FK로 재적재 (기존 행은 유지). */
    @PostMapping("/admin/reseed")
    public Map<String, Object> reseed(HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        int inserted = chemistrySeedService.reseedMissingPairs();
        int enFilled = chemistrySeedService.applyEnglishDescriptionsIfBlank();
        return Map.of(
                "inserted", inserted,
                "enFilled", enFilled,
                "total", chemistryService.findAll().size()
        );
    }

    @GetMapping("/groups")
    public List<ChemistryGroupDto> listGroups() {
        return chemistryGroupService.findAll();
    }

    @PostMapping("/groups")
    public ChemistryGroupDto upsertGroup(
            @RequestParam List<String> members,
            @RequestParam String label,
            @RequestParam(required = false, defaultValue = "") String labelEn,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam(required = false, defaultValue = "") String descriptionEn,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) List<MultipartFile> images,
            @RequestParam(required = false, defaultValue = "") String keepImageUrlsJson,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            @RequestParam(required = false, defaultValue = "") String linksJson,
            @RequestParam(required = false, defaultValue = "NONE") String labelTone,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return chemistryGroupService.upsert(
                    members, label, labelEn, description, descriptionEn,
                    image, images, keepImageUrlsJson, clearImage, linksJson, labelTone);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Void> deleteGroup(
            @PathVariable Long id,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        chemistryGroupService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/groups")
    public ResponseEntity<Void> deleteGroupByMembers(
            @RequestParam List<String> members,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            chemistryGroupService.deleteByMembers(members);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
