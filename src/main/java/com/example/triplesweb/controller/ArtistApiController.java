package com.example.triplesweb.controller;

import com.example.triplesweb.dto.ArtistAdminDto;
import com.example.triplesweb.dto.GroupIntroDto;
import com.example.triplesweb.service.AboutAdminAuth;
import com.example.triplesweb.service.ArtistAdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/artist")
@RequiredArgsConstructor
public class ArtistApiController {

    private final ArtistAdminService artistAdminService;
    private final AboutAdminAuth aboutAdminAuth;

    @GetMapping("/admin/status")
    public Map<String, Object> adminStatus(HttpServletRequest request, HttpSession session) {
        return aboutAdminAuth.status(request, session);
    }

    @PostMapping("/admin/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        return aboutAdminAuth.login(body, session);
    }

    @PostMapping("/admin/logout")
    public Map<String, Object> logout(HttpServletRequest request, HttpSession session) {
        return aboutAdminAuth.logout(request, session);
    }

    @GetMapping("/units")
    public List<ArtistAdminDto.UnitOption> units() {
        return artistAdminService.listUnitOptions();
    }

    @GetMapping("/group")
    public GroupIntroDto group() {
        return artistAdminService.getGroupIntro();
    }

    @PostMapping("/group")
    public GroupIntroDto updateGroup(
            @RequestParam(required = false, defaultValue = "") String title,
            @RequestParam(required = false, defaultValue = "") String body,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false) MultipartFile tabImage,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            @RequestParam(required = false, defaultValue = "false") boolean clearTabImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            return artistAdminService.updateGroupIntro(title, body, image, tabImage, clearImage, clearTabImage);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{id:[0-9]+}")
    public ArtistAdminDto get(@PathVariable long id) {
        try {
            return artistAdminService.getArtist(id);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{id:[0-9]+}")
    public ArtistAdminDto update(
            @PathVariable long id,
            @RequestParam String name,
            @RequestParam String engName,
            @RequestParam String nationality,
            @RequestParam String birthDate,
            @RequestParam(required = false, defaultValue = "") String bornPlace,
            @RequestParam(required = false, defaultValue = "") String birthPlace,
            @RequestParam(required = false, defaultValue = "") String realName,
            @RequestParam(required = false, defaultValue = "") String mbti,
            @RequestParam String debutDate,
            @RequestParam String color,
            @RequestParam(required = false, defaultValue = "") String soloContent,
            @RequestParam(required = false, defaultValue = "") String broadcastContent,
            @RequestParam(required = false) List<Long> unitIds,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(required = false, defaultValue = "false") boolean clearImage,
            @RequestParam(required = false) MultipartFile signatureImage,
            @RequestParam(required = false, defaultValue = "false") boolean clearSignatureImage,
            HttpServletRequest request,
            HttpSession session
    ) {
        aboutAdminAuth.requireAdmin(request, session);
        try {
            List<Long> ids = unitIds != null ? unitIds : new ArrayList<>();
            return artistAdminService.updateArtist(
                    id, name, engName, nationality, birthDate, bornPlace, birthPlace, realName, mbti,
                    debutDate, color, soloContent, broadcastContent, ids, image, clearImage,
                    signatureImage, clearSignatureImage
            );
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }
}
