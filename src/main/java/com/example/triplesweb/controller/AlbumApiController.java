package com.example.triplesweb.controller;

import com.example.triplesweb.dto.AlbumAdminDto;
import com.example.triplesweb.dto.AlbumSectionDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.AlbumAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/album")
@RequiredArgsConstructor
public class AlbumApiController {

    private final AlbumAdminService albumAdminService;
    private final AboutAdminAuth aboutAdminAuth;

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

    @GetMapping("/units")
    public List<AlbumAdminDto.UnitOption> units() {
        return albumAdminService.listUnitOptions();
    }

    @GetMapping("/sections")
    public List<AlbumSectionDto> sections() {
        return albumAdminService.listSections();
    }

    @PostMapping("/sections")
    public AlbumSectionDto createSection(
            @RequestBody Map<String, String> body,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return albumAdminService.createSection(body != null ? body.get("name") : null);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/sections/{id:[0-9]+}")
    public AlbumSectionDto updateSection(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            String name = body != null && body.get("name") != null ? String.valueOf(body.get("name")) : null;
            Integer sortOrder = null;
            if (body != null && body.get("sortOrder") instanceof Number n) {
                sortOrder = n.intValue();
            }
            return albumAdminService.updateSection(id, name, sortOrder);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/sections/{id:[0-9]+}/move")
    public List<AlbumSectionDto> moveSection(
            @PathVariable long id,
            @RequestParam int direction,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return albumAdminService.moveSection(id, direction);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/sections/{id:[0-9]+}")
    public Map<String, Object> deleteSection(
            @PathVariable long id,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            albumAdminService.deleteSection(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    public AlbumAdminDto get(@PathVariable long id) {
        try {
            return albumAdminService.get(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    public AlbumAdminDto create(
            @RequestParam String albumName,
            @RequestParam String releaseDate,
            @RequestParam(required = false, defaultValue = "") String albumInfo,
            @RequestParam(required = false, defaultValue = "SUBUNIT") String albumType,
            @RequestParam(required = false) String albumFormat,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false, defaultValue = "[]") String tracksJson,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return albumAdminService.create(
                    albumName, releaseDate, albumInfo, albumType, albumFormat, unitId, sectionId,
                    tracksJson, image, imageUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}")
    public AlbumAdminDto update(
            @PathVariable long id,
            @RequestParam String albumName,
            @RequestParam String releaseDate,
            @RequestParam(required = false, defaultValue = "") String albumInfo,
            @RequestParam(required = false, defaultValue = "SUBUNIT") String albumType,
            @RequestParam(required = false) String albumFormat,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long sectionId,
            @RequestParam(required = false, defaultValue = "[]") String tracksJson,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return albumAdminService.update(
                    id, albumName, releaseDate, albumInfo, albumType, albumFormat, unitId, sectionId,
                    tracksJson, image, imageUrl, clearImage);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id:[0-9]+}")
    public Map<String, Object> delete(@PathVariable long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            albumAdminService.delete(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
