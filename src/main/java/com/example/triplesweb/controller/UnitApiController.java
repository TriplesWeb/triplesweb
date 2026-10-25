package com.example.triplesweb.controller;

import com.example.triplesweb.dto.UnitAdminDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.UnitAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/unit")
@RequiredArgsConstructor
public class UnitApiController {

    private final UnitAdminService unitAdminService;
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

    @GetMapping("/options")
    public List<UnitAdminDto.UnitOption> options() {
        return unitAdminService.listUnitOptions();
    }

    @GetMapping("/artists")
    public List<UnitAdminDto.MemberOption> artists() {
        return unitAdminService.listArtistOptions();
    }

    @GetMapping("/{id:[0-9]+}")
    public UnitAdminDto get(@PathVariable long id) {
        try {
            return unitAdminService.get(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping
    public UnitAdminDto create(
            @RequestParam String unitName,
            @RequestParam String smallName,
            @RequestParam String formationDate,
            @RequestParam(required = false, defaultValue = "") String debutDate,
            @RequestParam(required = false, defaultValue = "true") boolean visible,
            @RequestParam(required = false) Long parentUnitId,
            @RequestParam(required = false) List<Long> artistIds,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return unitAdminService.create(
                    unitName, smallName, formationDate, debutDate, visible, parentUnitId,
                    artistIds != null ? artistIds : new ArrayList<>(), image, imageUrl);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}")
    public UnitAdminDto update(
            @PathVariable long id,
            @RequestParam String unitName,
            @RequestParam String smallName,
            @RequestParam String formationDate,
            @RequestParam(required = false, defaultValue = "") String debutDate,
            @RequestParam(required = false, defaultValue = "true") boolean visible,
            @RequestParam(required = false) Long parentUnitId,
            @RequestParam(required = false) List<Long> artistIds,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) String imageUrl,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return unitAdminService.update(
                    id, unitName, smallName, formationDate, debutDate, visible, parentUnitId,
                    artistIds != null ? artistIds : new ArrayList<>(), image, imageUrl, clearImage);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @DeleteMapping("/{id:[0-9]+}")
    public Map<String, Object> delete(@PathVariable long id, HttpServletRequest request, HttpSession session) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            unitAdminService.delete(id);
            return Map.of("deleted", true);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
